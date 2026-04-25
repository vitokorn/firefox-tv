/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv

import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.support.utils.toSafeIntent
import org.atmofox.tv.components.locale.LocaleAwareAppCompatActivity
import org.atmofox.tv.components.locale.LocaleManager
import org.atmofox.tv.compose.FirefoxTvApp
import org.atmofox.tv.compose.theme.FirefoxTvTheme
import org.atmofox.tv.ext.application
import org.atmofox.tv.ext.serviceLocator
import org.atmofox.tv.ext.webRenderComponents
import org.atmofox.tv.telemetry.TelemetryIntegration
import org.atmofox.tv.telemetry.UrlTextInputLocation
import org.atmofox.tv.utils.OnUrlEnteredListener
import org.atmofox.tv.utils.URLs
import org.atmofox.tv.utils.publicsuffix.PublicSuffix
import org.atmofox.tv.webrender.VideoVoiceCommandMediaSession
import org.atmofox.tv.widget.InlineAutocompleteEditText

interface MediaSessionHolder {
    val videoVoiceCommandMediaSession: VideoVoiceCommandMediaSession
}

class MainActivity : LocaleAwareAppCompatActivity(), MediaSessionHolder, OnUrlEnteredListener {
    private val LOG_TAG = "MainActivity"
    private val startStopCompositeDisposable = CompositeDisposable()

    // MediaSession stub for compatibility with WebRenderFragment during migration.
    override val videoVoiceCommandMediaSession: VideoVoiceCommandMediaSession
        get() = throw NotImplementedError("MediaSession removed during Compose migration")

    enum class Command {
        BEGIN_LOGIN
    }

    override fun applyLocale() {
        // No-op: Compose UI updates itself when locale changes.
    }

    private fun initMediaSession() {
        // MediaSession removed during Compose migration. Stub for compilation.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // We override onSaveInstanceState to not save state (for handling Clear Data), so startup flow
        // goes through onCreate.
        super.onCreate(savedInstanceState)

        // Register this Activity as the current visual context BEFORE any webRenderComponents
        // access, because lazy engine initialization calls GeckoRuntime.create() which may need
        // a visual Context for WindowManager on API 31+.
        (application as FirefoxApplication).visibilityLifeCycleCallback.currentActivity = this

        PublicSuffix.init(this) // Used by Pocket Video feed & custom home tiles.
        initMediaSession()

        // The launch intent is needed to create the engines in the engine cache.
        val safeIntent = intent.toSafeIntent()
        webRenderComponents.notifyLaunchWithSafeIntent(safeIntent)

        lifecycle.addObserver(serviceLocator.engineViewCache)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        TelemetryIntegration.INSTANCE.startSession(this)

        // Ensure at least one tab exists so SessionRepo can emit initial state.
        if (webRenderComponents.store.state.selectedTab == null) {
            val newTab = TabSessionState(
                id = "initial-session",
                content = ContentState(url = URLs.APP_URL_HOME)
            )
            webRenderComponents.store.dispatch(TabListAction.AddTabAction(newTab, select = true))
        }
        serviceLocator.sessionRepo.update()

        setContent {
            FirefoxTvTheme {
                FirefoxTvApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onStop() {
        super.onStop()
        LocaleManager.getInstance().resetLocaleIfChanged(applicationContext)
        TelemetryIntegration.INSTANCE.stopSession(this)
        TelemetryIntegration.INSTANCE.stopMainActivity()
        startStopCompositeDisposable.clear()
    }

    override fun onStart() {
        super.onStart()

        @Suppress("DEPRECATION")
        (application as FirefoxApplication).mainActivityCommandBus
            .subscribe { command ->
                when (command) {
                    Command.BEGIN_LOGIN -> serviceLocator.fxaLoginUseCase.beginLogin(supportFragmentManager)
                    null -> { /* do nothing */ }
                }
            }
            .addTo(startStopCompositeDisposable)

        // Received tabs and polling removed with ADMIntegration in v56+.
    }

    override fun onNonTextInputUrlEntered(urlStr: String) {
        serviceLocator.screenController.onUrlEnteredInner(this, supportFragmentManager, urlStr, false, null, null)
    }

    override fun onTextInputUrlEntered(
        urlStr: String,
        autocompleteResult: InlineAutocompleteEditText.AutocompleteResult?,
        inputLocation: UrlTextInputLocation?
    ) {
        serviceLocator.screenController.onUrlEnteredInner(this, supportFragmentManager, urlStr, true, autocompleteResult, inputLocation)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
