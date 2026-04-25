/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv

import android.content.Context
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mozilla.components.browser.state.state.TabSessionState
import org.atmofox.tv.ScreenControllerStateMachine.ActiveScreen
import org.atmofox.tv.ScreenControllerStateMachine.Transition
import org.atmofox.tv.channels.SettingsScreen
import org.atmofox.tv.session.SessionRepo
import org.atmofox.tv.telemetry.TelemetryIntegration
import org.atmofox.tv.telemetry.UrlTextInputLocation
import org.atmofox.tv.utils.URLs
import org.atmofox.tv.utils.UrlUtils
import org.atmofox.tv.widget.InlineAutocompleteEditText

class ScreenController(private val sessionRepo: SessionRepo) {

    private val _currentActiveScreen = MutableStateFlow(ActiveScreen.NAVIGATION_OVERLAY)
    /**
     * Observers will be notified just before the fragment transaction is committed
     */
    val currentActiveScreen: StateFlow<ActiveScreen> = _currentActiveScreen.asStateFlow()

    fun setActiveScreenForCompose(screen: ActiveScreen) {
        _currentActiveScreen.value = screen
    }

    fun setUpFragmentsForNewSession(session: TabSessionState) {
        _currentActiveScreen.value = ActiveScreen.NAVIGATION_OVERLAY
    }

    /**
     * Loads the given url. If isTextInput is true, there should be no null parameters.
     */
    fun onUrlEnteredInner(
        context: Context,
        urlStr: String,
        isTextInput: Boolean,
        autocompleteResult: InlineAutocompleteEditText.AutocompleteResult?,
        inputLocation: UrlTextInputLocation?
    ) {
        if (TextUtils.isEmpty(urlStr.trim())) {
            return
        }

        val isUrl = UrlUtils.isUrl(urlStr)
        val updatedUrlStr = if (isUrl) UrlUtils.normalize(urlStr) else UrlUtils.createSearchUrl(context, urlStr)

        showBrowserScreenForUrl(updatedUrlStr)

        if (isTextInput) {
            // Non-text input events are handled at the source, e.g. home tile click events.
            if (autocompleteResult == null) {
                throw IllegalArgumentException("Expected non-null autocomplete result for text input")
            }
            if (inputLocation == null) {
                throw IllegalArgumentException("Expected non-null input location for text input")
            }

            TelemetryIntegration.INSTANCE.urlBarEvent(isUrl, autocompleteResult, inputLocation)
        }
    }

    fun showSettingsScreen(settingsScreen: SettingsScreen) {
        val transition = when (settingsScreen) {
            SettingsScreen.DATA_COLLECTION -> Transition.ADD_SETTINGS_DATA
            SettingsScreen.CLEAR_COOKIES -> Transition.ADD_SETTINGS_COOKIES
            SettingsScreen.FXA_PROFILE -> Transition.ADD_FXA_PROFILE
            SettingsScreen.ABOUT -> Transition.ADD_SETTINGS_ABOUT
            else -> Transition.ADD_SETTINGS_DATA
        }
        handleTransitionAndUpdateActiveScreen(transition)
    }

    fun showBrowserScreenForCurrentSession(session: TabSessionState) {
        if (session.content.url != URLs.APP_URL_HOME) {
            handleTransitionAndUpdateActiveScreen(Transition.SHOW_BROWSER)
        }
    }

    fun showBrowserScreenForUrl(url: String) {
        handleTransitionAndUpdateActiveScreen(Transition.SHOW_BROWSER)
        sessionRepo.loadURL(android.net.Uri.parse(url))
    }

    fun showNavigationOverlay(toShow: Boolean) {
        val currentScreen = if (toShow) ActiveScreen.NAVIGATION_OVERLAY else ActiveScreen.WEB_RENDER
        _currentActiveScreen.value = currentScreen
    }

    fun dispatchKeyEvent(
        keyEvent: KeyEvent,
        @VisibleForTesting currentActiveScreen: ActiveScreen? = _currentActiveScreen.value
    ): Boolean {
        if (keyEvent.keyCode == KeyEvent.KEYCODE_MENU) {
            return when (keyEvent.action) {
                KeyEvent.ACTION_DOWN -> handleMenu()
                else -> true // We swallow ACTION_UP to only handle the key event once.
            }
        }
        return false
    }

    fun handleBack(): Boolean {
        val currentScreen = _currentActiveScreen.value
        Log.d("ScreenController", "handleBack: currentScreen=$currentScreen")

        if (currentScreen == ActiveScreen.WEB_RENDER) {
            val browserHandled = sessionRepo.attemptBack()
            Log.d("ScreenController", "handleBack: browserHandled=$browserHandled")
            if (browserHandled) return true
        }
        val canGoBack = canGoBack()
        val transition = ScreenControllerStateMachine.getNewStateBackPress(currentScreen!!, canGoBack)
        Log.d("ScreenController", "handleBack: canGoBack=$canGoBack, transition=$transition")
        val result = handleTransitionAndUpdateActiveScreen(transition)
        Log.d("ScreenController", "handleBack: result=$result")
        return result
    }

    fun handleMenu(): Boolean {
        val transition = ScreenControllerStateMachine.getNewStateMenuPress(_currentActiveScreen.value!!, isOnHomeUrl())

        if (transition == Transition.ADD_OVERLAY) {
            TelemetryIntegration.INSTANCE.menuOpenedFromMenuButton()
        }

        return handleTransitionAndUpdateActiveScreen(transition)
    }

    private fun canGoBack(): Boolean {
        return sessionRepo.currentState()?.backEnabled ?: false
    }

    private fun isOnHomeUrl(): Boolean {
        @Suppress("DEPRECATION")
        return sessionRepo.currentState()?.currentUrl == URLs.APP_URL_HOME
    }

    private fun handleTransitionAndUpdateActiveScreen(transition: Transition): Boolean {
        Log.d("ScreenController", "handleTransitionAndUpdateActiveScreen: transition=$transition")

        when (transition) {
            Transition.ADD_OVERLAY -> {
                Log.d("ScreenController", "Executing ADD_OVERLAY transition")
                _currentActiveScreen.value = ActiveScreen.NAVIGATION_OVERLAY
            }
            Transition.REMOVE_OVERLAY -> {
                _currentActiveScreen.value = ActiveScreen.WEB_RENDER
            }
            Transition.ADD_SETTINGS_DATA,
            Transition.ADD_SETTINGS_COOKIES,
            Transition.ADD_SETTINGS_ABOUT,
            Transition.ADD_FXA_PROFILE -> {
                _currentActiveScreen.value = ActiveScreen.SETTINGS
            }
            Transition.REMOVE_SETTINGS,
            Transition.REMOVE_FXA_PROFILE -> {
                _currentActiveScreen.value = ActiveScreen.NAVIGATION_OVERLAY
            }
            Transition.SHOW_BROWSER -> {
                _currentActiveScreen.value = ActiveScreen.WEB_RENDER
            }
            Transition.EXIT_APP -> {
                Log.d("ScreenController", "Executing EXIT_APP transition - app will exit")
                return false
            }
            Transition.NO_OP -> {
                Log.d("ScreenController", "Executing NO_OP transition")
                return true
            }
        }
        return true
    }
}
