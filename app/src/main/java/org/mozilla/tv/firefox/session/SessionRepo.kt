/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.session

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.AnyThread
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.session.SessionUseCases
import org.mozilla.tv.firefox.ext.isYoutubeTV
import org.mozilla.tv.firefox.ext.toUri
import org.mozilla.tv.firefox.telemetry.TelemetryIntegration
import org.mozilla.tv.firefox.utils.UrlUtils
import org.mozilla.tv.firefox.utils.TurboMode
import org.mozilla.tv.firefox.webrender.EngineViewCache

/**
 * Repository that is responsible for storing state related to the browser.
 */
class SessionRepo(
    private val store: BrowserStore,
    private val sessionUseCases: SessionUseCases,
    private val turboMode: TurboMode
) {

    data class State(
        val backEnabled: Boolean,
        val forwardEnabled: Boolean,
        val desktopModeActive: Boolean,
        val turboModeActive: Boolean,
        val currentUrl: String,
        val loading: Boolean
    )

    enum class Event {
        YouTubeBack, ExitYouTube
    }

    data class BrowserHistoryState(
        val currentIndex: Int,
        val urls: List<String>
    )

    private val _state: BehaviorSubject<State> = BehaviorSubject.create()
    val state: Observable<State> = _state.hide()

    fun currentState(): State? = _state.value

    private val _events: Subject<Event> = PublishSubject.create()
    val events: Observable<Event> = _events.hide()

    var canGoBackTwice: (() -> Boolean?)? = null
    var browserHistoryState: (() -> BrowserHistoryState?)? = null
    var browserHistoryNavigateToIndex: ((Int) -> Unit)? = null
    private var previousURLHost: String? = null

    private fun String.isInternalBrowserUrl(): Boolean {
        return this == "about:blank" || this == "data:text/html,<html></html>" ||
                UrlUtils.isInternalErrorURL(this)
    }

    private fun BrowserHistoryState.visibleUrl(): String? {
        val visibleIndex = when (val currentHistoryUrl = urls.getOrNull(currentIndex)) {
            null -> null
            else -> if (currentHistoryUrl.isInternalBrowserUrl()) {
                urls.subList(0, currentIndex)
                    .asReversed()
                    .indexOfFirst { !it.isInternalBrowserUrl() }
                    .takeIf { it >= 0 }
                    ?.let { currentIndex - it - 1 }
            } else {
                currentIndex
            }
        } ?: return null

        return urls.getOrNull(visibleIndex)
    }

    private fun BrowserHistoryState.previousRealPageIndex(): Int? {
        val visibleIndex = when (val currentHistoryUrl = urls.getOrNull(currentIndex)) {
            null -> null
            else -> if (currentHistoryUrl.isInternalBrowserUrl()) {
                urls.subList(0, currentIndex)
                    .asReversed()
                    .indexOfFirst { !it.isInternalBrowserUrl() }
                    .takeIf { it >= 0 }
                    ?.let { currentIndex - it - 1 }
            } else {
                currentIndex
            }
        } ?: return null

        val targetIndex = urls.subList(0, visibleIndex)
            .asReversed()
            .indexOfFirst { !it.isInternalBrowserUrl() }

        return if (targetIndex >= 0) visibleIndex - targetIndex - 1 else null
    }

    fun observeSources() {
        turboMode.observable.observeForever { update() }
        // BrowserStore state observation - update whenever state changes
        store.observeManually { update() }
    }

    /**
     * Force a state update with the given loading flag and URL.
     * Used by Gecko progress delegate when BrowserStore observation isn't emitting.
     */
    fun forceUpdate(loading: Boolean, url: String) {
        val currentState = _state.value
        val newState = if (currentState != null) {
            currentState.copy(loading = loading, currentUrl = url)
        } else {
            val tab = store.state.selectedTab
            State(
                backEnabled = canGoBackTwice?.invoke() ?: tab?.content?.canGoBack ?: false,
                forwardEnabled = tab?.content?.canGoForward ?: false,
                desktopModeActive = false,
                turboModeActive = turboMode.isEnabled,
                currentUrl = url,
                loading = loading
            )
        }
        Log.d("SessionRepo", "forceUpdate: loading=$loading, url=$url")
        _state.onNext(newState)
    }

    @AnyThread
    fun update() {
        store.state.selectedTab?.let { tab ->
            fun isHostDifferentFromPrevious(): Boolean {
                val currentURLHost = tab.content.url.toUri()?.host ?: return true

                return (previousURLHost != currentURLHost).also {
                    previousURLHost = currentURLHost
                }
            }
            fun disableDesktopMode() {
                setDesktopMode(false)
                tab.content.url.toUri()?.let { loadURL(it) }
            }
            fun causeSideEffects() {
                // desktopMode removed in v72+. Defaults to false.
            }

            fun <T : Any> BehaviorSubject<T>.onNextIfNew(value: T) {
                if (this.value != value) this.onNext(value)
            }

            val browserHistorySnapshot = browserHistoryState?.invoke()
            val displayUrl = browserHistorySnapshot?.visibleUrl() ?: tab.content.url
            Log.d("SessionRepo", "update: displayUrl=$displayUrl, browserHistorySnapshot=$browserHistorySnapshot, tab.url=${tab.content.url}, loading=${tab.content.loading}")

            causeSideEffects()

            val newState = State(
                // The menu back button should not be enabled if the previous screen was our initial url (home)
                backEnabled = canGoBackTwice?.invoke() ?: false,
                forwardEnabled = tab.content.canGoForward,
                desktopModeActive = false, // desktopMode removed in v72+
                turboModeActive = turboMode.isEnabled,
                currentUrl = displayUrl,
                loading = tab.content.loading
            )
            _state.onNextIfNew(newState)
        }
    }

    fun currentURLScreenshot(): Bitmap? = null // thumbnail removed in v72+

    /**
     * @param forceYouTubeExit if true while YouTube is active, back out of the
     * site instead of moving focus
     *
     * @Returns true if the event was consumed
     */
    fun attemptBack(forceYouTubeExit: Boolean = false): Boolean {
        val tab = store.state.selectedTab ?: return false.also { Log.d("SessionRepo", "attemptBack: no selected tab") }
        Log.d("SessionRepo", "attemptBack: url=${tab.content.url}, canGoBack=${tab.content.canGoBack}")

        if (tab.isYoutubeTV && forceYouTubeExit) {
            _events.onNext(Event.ExitYouTube)
            return true
        }

        if (tab.isYoutubeTV && !forceYouTubeExit) {
            _events.onNext(Event.YouTubeBack)
            return true
        }

        browserHistoryState?.invoke()?.let { historyState ->
            Log.d("SessionRepo", "attemptBack: browserHistoryState present, currentIndex=${historyState.currentIndex}, urls=${historyState.urls}")
            val targetIndex = historyState.previousRealPageIndex()
            Log.d("SessionRepo", "attemptBack: previousRealPageIndex returned $targetIndex, browserHistoryNavigateToIndex=${browserHistoryNavigateToIndex != null}")

            if (targetIndex != null && browserHistoryNavigateToIndex != null) {
                Log.d("SessionRepo", "attemptBack: navigating to index $targetIndex")
                browserHistoryNavigateToIndex?.invoke(targetIndex)
                TelemetryIntegration.INSTANCE.browserBackControllerEvent()
                return true
            }

            // If we have history state but no valid previous page (targetIndex is null),
            // we're at the first real page - return false to let the state machine show overlay
            Log.d("SessionRepo", "attemptBack: no valid previous page in history, returning false to show overlay")
            return false
        } ?: Log.d("SessionRepo", "attemptBack: browserHistoryState is null")

        if (tab.content.canGoBack) {
            Log.d("SessionRepo", "attemptBack: using sessionUseCases.goBack")
            exitFullScreenIfPossible()
            Handler(Looper.getMainLooper()).post {
                sessionUseCases.goBack.invoke()
            }
            TelemetryIntegration.INSTANCE.browserBackControllerEvent()
            return true
        }

        Log.d("SessionRepo", "attemptBack: cannot go back, returning false")
        return false
    }

    fun goForward() {
        if (store.state.selectedTab?.content?.canGoForward == true) sessionUseCases.goForward.invoke()
    }

    fun reload() = sessionUseCases.reload.invoke()

    fun setDesktopMode(active: Boolean) {} // desktopMode removed in v72+

    /**
     * Causes [state] to emit its most recently pushed value. This can be used
     * to reset UI that has been adjusted by the user (e.g., EditText text)
     */
    fun pushCurrentValue() = _state.onNext(_state.value!!) // TODO does this do anything? If not,
    // we can have state.distinctUntilChanged and get rid of postIfNew

    fun loadURL(url: Uri) = sessionUseCases.loadUrl.invoke(url.toString())

    fun setTurboModeEnabled(enabled: Boolean) {
        turboMode.isEnabled = enabled
    }

    @Suppress("DEPRECATION")
    fun clearBrowsingData(engineViewCache: EngineViewCache) {
        // clearData() removed in v72+. SessionFeature handles data clearing.
        store.dispatch(TabListAction.RemoveAllTabsAction())
        engineViewCache.doNotPersist()
    }

    /**
     * Returns true if fullscreen was exited.
     * NOTE: fullScreenMode property removed in v56+. Fullscreen handling now
     * lives in BrowserState and should be observed there if needed.
     */
    fun exitFullScreenIfPossible(): Boolean {
        // v56+: fullScreenMode no longer on Session object.
        // Stubbed out; fullscreen exit should be handled via BrowserState observers.
        return false
    }
}
