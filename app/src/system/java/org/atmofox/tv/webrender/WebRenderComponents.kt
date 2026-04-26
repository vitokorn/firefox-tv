/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.webrender

import android.content.Context
import mozilla.components.browser.engine.system.SystemEngine
import android.graphics.Bitmap
import mozilla.components.browser.state.action.SearchAction
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.Engine
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.support.utils.SafeIntent
import org.atmofox.tv.R
import org.atmofox.tv.utils.BuildConstants
import org.atmofox.tv.utils.Settings

/**
 * Helper class for lazily instantiating and keeping references to components needed by the
 * application.
 */
class WebRenderComponents(applicationContext: Context, systemUserAgent: String) {
    fun notifyLaunchWithSafeIntent(@Suppress("UNUSED_PARAMETER") safeIntent: SafeIntent): Boolean {
        // For the system WebView, we don't need the initial launch intent right now.  In the
        // future, we might configure a proxy server using this intent for automation.
        return false
    }

    val engine: Engine by lazy {
        fun getUserAgent(): String = UserAgent.buildUserAgentString(
                applicationContext,
                systemUserAgent = systemUserAgent,
                appName = applicationContext.resources.getString(R.string.useragent_appname))

        SystemEngine(applicationContext, DefaultSettings(
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
        ))
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
