/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.webrender

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import mozilla.components.browser.engine.gecko.GeckoEngine
import android.graphics.Bitmap
import mozilla.components.browser.state.action.SearchAction
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.Engine
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.support.utils.SafeIntent
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.atmofox.tv.FirefoxApplication
import org.atmofox.tv.R
import org.atmofox.tv.utils.BuildConstants
import org.atmofox.tv.utils.Settings

/**
 * Helper class for lazily instantiating and keeping references to components needed by the
 * application.
 */
private class VisualContextWrapper(
    base: Context,
    private val activityProvider: () -> Activity?
) : ContextWrapper(base) {
    override fun getSystemService(name: String): Any? {
        if (Context.WINDOW_SERVICE == name) {
            val activity = activityProvider()
            if (activity != null && !activity.isDestroyed) {
                return activity.getSystemService(name)
            }
        }
        return super.getSystemService(name)
    }
}

class WebRenderComponents(applicationContext: Context, systemUserAgent: String) {
    // The first intent the App was launched with.  Used to pass configuration through to Gecko.
    private var launchSafeIntent: SafeIntent? = null

    fun notifyLaunchWithSafeIntent(safeIntent: SafeIntent): Boolean {
        // We can't access the property reference outside of our own lexical scope,
        // so this helper must be in this class.
        if (launchSafeIntent == null) {
            launchSafeIntent = safeIntent
            return true
        }
        return false
    }

    val engine: Engine by lazy {
        fun getUserAgent(): String = UserAgent.buildUserAgentString(
                applicationContext,
                systemUserAgent = systemUserAgent,
                appName = applicationContext.resources.getString(R.string.useragent_appname))

        val runtimeSettingsBuilder = GeckoRuntimeSettings.Builder()
        if (BuildConstants.isDevBuild) {
            // In debug builds, allow to invoke via an Intent that has extras customizing Gecko.
            // In particular, this allows to add command line arguments for custom profiles, etc.
            val extras = launchSafeIntent?.extras
            if (extras != null) {
                runtimeSettingsBuilder.extras(extras)
            }
        }
        // autoplayDefault removed in v56+ GeckoRuntimeSettings. Autoplay now configured via DefaultSettings.mediaPlaybackRequiresUserGesture.
        val visualContext = VisualContextWrapper(applicationContext) {
            (applicationContext as? FirefoxApplication)?.visibilityLifeCycleCallback?.currentActivity
        }
        val runtime = GeckoRuntime.create(visualContext,
                runtimeSettingsBuilder.build())

        GeckoEngine(applicationContext, DefaultSettings(
                trackingProtectionPolicy = Settings.getInstance(applicationContext).trackingProtectionPolicy,
                requestInterceptor = CustomContentRequestInterceptor(applicationContext),
                userAgentString = getUserAgent(),

                displayZoomControls = false,
                loadWithOverviewMode = true, // To respect the html viewport

                // We don't have a reason for users to access local files; assets can still
                // be loaded via file:///android_asset/
                allowFileAccess = false,
                allowContentAccess = false,

                remoteDebuggingEnabled = BuildConstants.isDevBuild,

                mediaPlaybackRequiresUserGesture = false // Allows auto-play (which improves YouTube experience).
        ), runtime)
    }

    val store by lazy {
        val browserStore = BrowserStore(
            middleware = EngineMiddleware.create(engine)
        )
        initializeSearchEngines(browserStore)
        browserStore
    }

    val sessionUseCases by lazy { SessionUseCases(store) }

    private fun initializeSearchEngines(browserStore: BrowserStore) {
        val placeholderIcon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        val google = SearchEngine(
            id = "google",
            name = "Google",
            icon = placeholderIcon,
            inputEncoding = "UTF-8",
            type = SearchEngine.Type.APPLICATION,
            resultUrls = listOf("https://www.google.com/search?q={searchTerms}"),
            suggestUrl = "https://www.google.com/complete/search?client=firefox&q={searchTerms}",
            isGeneral = true
        )
        val bing = SearchEngine(
            id = "bing",
            name = "Bing",
            icon = placeholderIcon,
            inputEncoding = "UTF-8",
            type = SearchEngine.Type.APPLICATION,
            resultUrls = listOf("https://www.bing.com/search?q={searchTerms}"),
            suggestUrl = null,
            isGeneral = true
        )
        val duckduckgo = SearchEngine(
            id = "ddg",
            name = "DuckDuckGo",
            icon = placeholderIcon,
            inputEncoding = "UTF-8",
            type = SearchEngine.Type.APPLICATION,
            resultUrls = listOf("https://duckduckgo.com/?q={searchTerms}"),
            suggestUrl = null,
            isGeneral = true
        )

        browserStore.dispatch(SearchAction.SetSearchEnginesAction(
            regionSearchEngines = listOf(google, bing, duckduckgo),
            customSearchEngines = emptyList(),
            hiddenSearchEngines = emptyList(),
            disabledSearchEngineIds = emptyList(),
            additionalSearchEngines = emptyList(),
            additionalAvailableSearchEngines = emptyList(),
            userSelectedSearchEngineId = null,
            userSelectedSearchEngineName = null,
            regionDefaultSearchEngineId = google.id,
            regionSearchEnginesOrder = listOf(google.id, bing.id, duckduckgo.id)
        ))
    }
}
