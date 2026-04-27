/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.webrender

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import mozilla.components.browser.engine.gecko.GeckoEngineView
import org.mozilla.geckoview.GeckoSession
import org.atmofox.tv.ext.canGoBackTwice
import org.atmofox.tv.ext.webRenderComponents
import org.atmofox.tv.session.SessionRepo

/**
 * Caches a [GeckoEngineView], which internally maintains a [WebView].
 *
 * This allows us to maintain [WebView] state when the view would otherwise
 * be destroyed
 */
class EngineViewCache(private val sessionRepo: SessionRepo) : LifecycleObserver {

    companion object {
        // According to Android docs, WebView.saveState and WebView.restoreState do "not restore
        // display data"[1] (the exact meaning of this is not specified). Some discussion about
        // them online implies that they do not behave as expected[2][3], and that previous
        // versions had broken implementations[4]. However, we ship to a limited number of devices,
        // and after thorough testing on each these methods have been found to restore state
        // relatively well.
        //
        // If we encounter strange state bugs in the WebView, this code should be considered
        // suspect. But until then, it solves some very important problems[5][6].
        //
        // [1] https://developer.android.com/reference/android/webkit/WebView.html?hl=es#restoreState(android.os.Bundle)
        // [2] https://stackoverflow.com/a/32867602
        // [3] https://stackoverflow.com/a/33326970
        // [4] https://stackoverflow.com/a/17543769
        // [5] https://github.com/mozilla-mobile/firefox-tv/issues/1276
        // [6] https://github.com/mozilla-mobile/firefox-tv/issues/1256
        private var state: Bundle? = null
    }

    private var cachedView: GeckoEngineView? = null
    private var shouldPersist = true
    private var browserHistoryState: SessionRepo.BrowserHistoryState? = null

    private fun String.isInternalBrowserUrl(): Boolean {
        return this == "about:blank" ||
            this == "about:home" ||
            this == "data:text/html,<html></html>" ||
            this.startsWith("data:text/html")
    }

    private fun SessionRepo.BrowserHistoryState.visibleUrl(): String? {
        val currentHistoryUrl = urls.getOrNull(currentIndex)
        if (currentHistoryUrl == null || !currentHistoryUrl.isInternalBrowserUrl()) {
            return currentHistoryUrl
        }

        return urls.subList(0, currentIndex)
            .asReversed()
            .firstOrNull { !it.isInternalBrowserUrl() }
    }

    private fun SessionRepo.BrowserHistoryState.previousRealPageIndex(): Int? {
        val targetIndex = urls
            .subList(0, currentIndex)
            .asReversed()
            .indexOfFirst { !it.isInternalBrowserUrl() }

        return if (targetIndex >= 0) currentIndex - targetIndex - 1 else null
    }

    fun getEngineView(
        context: Context,
        attrs: AttributeSet? = null,
        initialize: GeckoEngineView.() -> Unit = {}
    ): GeckoEngineView {
        fun View?.removeFromParentIfAble() {
            // If the WebView has already been added to the view hierarchy, we
            // need to remove it from its parent before attempting to add it
            // again. Otherwise an IllegalStateException will be thrown
            (this?.parent as? ViewGroup)?.removeView(cachedView)
        }

        fun createAndCacheEngineView(): GeckoEngineView {
            // This will need to be updated for GeckoView.
            val engineView = context.webRenderComponents.engine.createView(context, attrs) as GeckoEngineView
            return engineView.apply {
                initialize()
            }.also {
                cachedView = it
            }
        }

        cachedView?.removeFromParentIfAble()

        sessionRepo.canGoBackTwice = {
            val hasHistory = browserHistoryState?.let {
                val prevIndex = it.previousRealPageIndex()
                Log.d("EngineViewCache", "canGoBackTwice: prevIndex=$prevIndex, currentIndex=${it.currentIndex}, urls=${it.urls}")
                prevIndex != null
            } ?: (cachedView?.canGoBackTwice() == true)
            Log.d("EngineViewCache", "canGoBackTwice returning: $hasHistory")
            hasHistory
        }
        return cachedView ?: createAndCacheEngineView()
    }

    private val handler = Handler(Looper.getMainLooper())
    private var setupAttempts = 0
    private val maxSetupAttempts = 10
    private var delegateSetupFailed = false
    private var delegatedSession: GeckoSession? = null
    private var sessionMonitorAttempts = 0
    private val maxSessionMonitorAttempts = 30

    private val monitorSessionRunnable = object : Runnable {
        override fun run() {
            if (sessionMonitorAttempts >= maxSessionMonitorAttempts) {
                return
            }
            val engineView = cachedView ?: return
            val geckoView = (engineView.asView() as? FrameLayout)?.getChildAt(0) as? org.mozilla.geckoview.GeckoView
            val currentSession = geckoView?.session
            if (currentSession != null && delegatedSession != null && currentSession !== delegatedSession) {
                Log.d("EngineViewCache", "Detected GeckoSession swap, rebinding delegates. old=$delegatedSession, new=$currentSession")
                setupAttempts = 0
                delegateSetupFailed = false
                setupSessionDelegateIfNeeded()
                return
            }
            sessionMonitorAttempts++
            handler.postDelayed(this, 100)
        }
    }

    /**
     * Must be called AFTER SessionFeature.start() has attached the session to the GeckoView.
     * This is typically called from onEngineViewCreated() in WebRenderFragment.
     * Will retry with delay if session is not yet available.
     */
    fun setupSessionDelegateIfNeeded() {
        if (delegateSetupFailed) {
            return
        }

        val engineView = cachedView ?: return
        val geckoView = (engineView.asView() as FrameLayout).getChildAt(0) as? org.mozilla.geckoview.GeckoView
        val session = geckoView?.session
        Log.d("EngineViewCache", "setupSessionDelegateIfNeeded: attempt=${setupAttempts + 1}/$maxSetupAttempts, geckoView=$geckoView, session=$session")

        if (session == null) {
            if (setupAttempts < maxSetupAttempts) {
                setupAttempts++
                // First attempt: try immediately without delay
                val delayMs = if (setupAttempts == 1) 0L else 100L
                Log.d("EngineViewCache", "Session is null, retrying in ${delayMs}ms (attempt $setupAttempts/$maxSetupAttempts)")
                if (delayMs > 0) {
                    handler.postDelayed({ setupSessionDelegateIfNeeded() }, delayMs)
                } else {
                    setupSessionDelegateIfNeeded()
                }
            } else {
                Log.w("EngineViewCache", "Session is null after $maxSetupAttempts attempts, giving up")
                delegateSetupFailed = true
            }
            return
        }

        // Reset attempts on success
        setupAttempts = 0
        delegateSetupFailed = false

        if (delegatedSession === session) {
            Log.d("EngineViewCache", "Delegates already attached to current session, skipping rebind")
            return
        }
        delegatedSession = session

        // Keep last known real URL; fallback paths may update this when first onPageStart is missed.
        var lastNonInternalUrl = ""
        var hasSignaledLoading = false
        Log.d("EngineViewCache", "Session delegate setup starting: initialUrl=$lastNonInternalUrl")

        fun captureRealUrl(url: String?, shouldSignalLoading: Boolean) {
            if (url.isNullOrEmpty() || url.isInternalBrowserUrl()) {
                return
            }
            lastNonInternalUrl = url
            if (shouldSignalLoading && !hasSignaledLoading) {
                hasSignaledLoading = true
                sessionRepo.forceUpdate(loading = true, url = url)
                Log.d("EngineViewCache", "Captured real URL and signaled loading: $url")
            } else if (!shouldSignalLoading && sessionRepo.currentState().currentUrl != url) {
                // Keep visible URL in sync even if loading already finished before delegate attached.
                sessionRepo.forceUpdate(loading = sessionRepo.currentState().loading, url = url)
                Log.d("EngineViewCache", "Captured real URL without loading transition: $url")
            }
        }

        session.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                Log.d("EngineViewCache", "onPageStart: url=$url, hasSignaledLoading=$hasSignaledLoading")
                captureRealUrl(url, shouldSignalLoading = true)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                Log.d("EngineViewCache", "onPageStop: success=$success, lastUrl=$lastNonInternalUrl, hasSignaledLoading=$hasSignaledLoading")
                // Only signal loading=false if we previously signaled loading=true for a real URL.
                if (hasSignaledLoading && lastNonInternalUrl.isNotEmpty()) {
                    hasSignaledLoading = false
                    sessionRepo.forceUpdate(loading = false, url = lastNonInternalUrl)
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) = Unit

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation
            ) = Unit

            override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
                val urls = sessionState.map { it.uri }
                Log.d("EngineViewCache", "onSessionStateChange: currentIndex=${sessionState.currentIndex}, urls=$urls, hasSignaledLoading=$hasSignaledLoading")
                browserHistoryState = SessionRepo.BrowserHistoryState(
                    currentIndex = sessionState.currentIndex,
                    urls = urls
                )

                val currentUrl = urls.getOrNull(sessionState.currentIndex)
                val shouldSignalLoading = sessionRepo.currentState().loading
                captureRealUrl(currentUrl, shouldSignalLoading)
            }
        })

        session.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                Log.d("EngineViewCache", "onLocationChange: url=$url, hasSignaledLoading=$hasSignaledLoading")
                // Fallback path: on first load we may miss the real onPageStart, but still receive location updates.
                captureRealUrl(url, shouldSignalLoading = true)
            }
        })
        
        sessionRepo.browserHistoryState = { browserHistoryState }
        sessionRepo.browserHistoryNavigateToIndex = { index ->
            Log.d("EngineViewCache", "browserHistoryNavigateToIndex: navigating to index $index")
            session.gotoHistoryIndex(index)
        }

        // Fallback for first attach timing: poll BrowserStore-backed state briefly.
        // Important: this never forces loading=true unless store currently reports loading=true.
        var fallbackAttempts = 0
        fun pollRepoForRealUrl() {
            if (fallbackAttempts >= maxSetupAttempts || hasSignaledLoading) {
                return
            }
            fallbackAttempts++
            val repoState = sessionRepo.currentState()
            captureRealUrl(repoState.currentUrl, shouldSignalLoading = repoState.loading)
            if (!hasSignaledLoading) {
                handler.postDelayed({ pollRepoForRealUrl() }, 100)
            }
        }
        handler.postDelayed({ pollRepoForRealUrl() }, 100)

        // Monitor initial startup for session swaps; Gecko may replace the session after first attach.
        sessionMonitorAttempts = 0
        handler.removeCallbacks(monitorSessionRunnable)
        handler.postDelayed(monitorSessionRunnable, 100)

        Log.d("EngineViewCache", "Session delegate setup complete")
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    private fun onCreate() {
        shouldPersist = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private fun onDestroy() {
        clear()
    }

    private fun clear() {
        handler.removeCallbacksAndMessages(null)
        setupAttempts = 0
        delegateSetupFailed = false
        delegatedSession = null
        sessionMonitorAttempts = 0
        sessionRepo.canGoBackTwice = null
        sessionRepo.browserHistoryState = null
        sessionRepo.browserHistoryNavigateToIndex = null
        browserHistoryState = null
        cachedView?.onStop()
        cachedView?.onDestroy()
        cachedView = null
    }

    fun doNotPersist() {
        shouldPersist = false
    }

    /**
     * Asynchronously captures the current thumbnail from the cached [GeckoEngineView].
     */
    suspend fun captureThumbnail(): Bitmap? = suspendCancellableCoroutine { continuation ->
        val view = cachedView
        if (view == null) {
            continuation.resume(null) {}
            return@suspendCancellableCoroutine
        }
        view.captureThumbnail { bitmap ->
            continuation.resume(bitmap) {}
        }
    }
}
