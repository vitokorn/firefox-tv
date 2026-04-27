/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv

import android.os.Bundle
import android.os.Trace
import android.view.KeyEvent
import android.view.View
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
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
import org.atmofox.tv.utils.publicsuffix.PublicSuffix
import org.atmofox.tv.webrender.VideoVoiceCommandMediaSession
import org.atmofox.tv.widget.InlineAutocompleteEditText

interface MediaSessionHolder {
    val videoVoiceCommandMediaSession: VideoVoiceCommandMediaSession
}

class MainActivity : LocaleAwareAppCompatActivity(), MediaSessionHolder, OnUrlEnteredListener {
    private val LOG_TAG = "MainActivity"
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

    private inline fun <T> traceStartupSection(name: String, block: () -> T): T {
        Trace.beginSection(name)
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // We override onSaveInstanceState to not save state (for handling Clear Data), so startup flow
        // goes through onCreate.
        super.onCreate(savedInstanceState)

        // Register this Activity as the current visual context BEFORE any webRenderComponents
        // access, because lazy engine initialization calls GeckoRuntime.create() which may need
        // a visual Context for WindowManager on API 31+.
        (application as FirefoxApplication).visibilityLifeCycleCallback.currentActivity = this

        traceStartupSection("fftv.main.public_suffix_init") {
            PublicSuffix.init(this) // Used by custom home tiles.
        }
        initMediaSession()

        // The launch intent is needed to create the engines in the engine cache.
        traceStartupSection("fftv.main.notify_launch_intent") {
            val safeIntent = intent.toSafeIntent()
            webRenderComponents.notifyLaunchWithSafeIntent(safeIntent)
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        traceStartupSection("fftv.main.start_telemetry_session") {
            TelemetryIntegration.INSTANCE.startSession(this)
        }

        traceStartupSection("fftv.main.set_content") {
            setContent {
                FirefoxTvTheme {
                    FirefoxTvApp()
                }
            }
        }

        window.decorView.post {
            traceStartupSection("fftv.main.deferred_app_startup") {
                (application as FirefoxApplication).maybeInitDeferredStartup()
            }
        }

        @Suppress("DEPRECATION")
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as FirefoxApplication).mainActivityCommandBus.collect { command ->
                    when (command) {
                        Command.BEGIN_LOGIN -> serviceLocator.fxaLoginUseCase.beginLogin()
                    }
                }
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
    }

    override fun onStart() {
        super.onStart()
        // Received tabs and polling removed with ADMIntegration in v56+.
    }

    override fun onNonTextInputUrlEntered(urlStr: String) {
        serviceLocator.screenController.onUrlEnteredInner(this, urlStr, false, null, null)
    }

    override fun onTextInputUrlEntered(
        urlStr: String,
        autocompleteResult: InlineAutocompleteEditText.AutocompleteResult?,
        inputLocation: UrlTextInputLocation?
    ) {
        serviceLocator.screenController.onUrlEnteredInner(this, urlStr, true, autocompleteResult, inputLocation)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Back presses are all handled through onBackPressed.
        //
        // Note: on device, back presses emit one KEYCODE_BACK. On emulator, they
        // emit one KEYCODE_BACK **AND** one KEYCODE_DEL. We short on both to make
        // code paths consistent between the two.
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) onBackPressed()
            return true
        }

        if (event.keyCode == KeyEvent.KEYCODE_DEL) return true

        TelemetryIntegration.INSTANCE.saveRemoteControlInformation(applicationContext, event)

        // Route dpad/cursor events to the cursor model when the browser screen is active.
        val activeScreen = serviceLocator.screenController.currentActiveScreen.value
        if (activeScreen == ScreenControllerStateMachine.ActiveScreen.WEB_RENDER &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                    event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    event.keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            val handled = serviceLocator.cursorModel.handleKeyEvent(event)
            handled.simulatedTouch?.let {
                dispatchTouchEvent(it)
                it.recycle()
            }
            if (handled.wasKeyEventConsumed) return true
        }

        return super.dispatchKeyEvent(event)
    }
}
