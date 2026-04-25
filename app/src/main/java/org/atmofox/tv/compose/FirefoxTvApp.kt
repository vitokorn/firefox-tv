/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.atmofox.tv.ScreenControllerStateMachine
import org.atmofox.tv.compose.browser.BrowserScreen
import org.atmofox.tv.compose.menu.MenuOverlay
import org.atmofox.tv.compose.navigation.Screen
import org.atmofox.tv.compose.navigation.SettingsType
import org.atmofox.tv.compose.onboarding.OnboardingScreen
import org.atmofox.tv.compose.settings.SettingsScreen
import androidx.compose.runtime.collectAsState
import org.atmofox.tv.ext.serviceLocator
import org.atmofox.tv.utils.URLs
import org.atmofox.tv.utils.UrlUtils

/**
 * Root composable for the Firefox TV application.
 *
 * Hosts multi-screen navigation for the Compose migration:
 * - Browser (primary screen with URL bar, engine view, navigation)
 * - MenuOverlay (channel tiles, settings shortcut)
 * - Settings (telemetry, clear data, about)
 * - Onboarding (first-run experience)
 */
@Composable
fun FirefoxTvApp(
    modifier: Modifier = Modifier
) {
    var currentScreen by rememberSaveable { mutableStateOf(Screen.MenuOverlay) }
    var currentSettingsScreen by rememberSaveable { mutableStateOf(SettingsType.DATA_COLLECTION) }
    val context = LocalContext.current
    val serviceLocator = context.serviceLocator
    val sessionRepo = serviceLocator.sessionRepo
    val screenController = serviceLocator.screenController

    val state by sessionRepo.state.collectAsState()
    var previousUrl by remember { mutableStateOf(state.currentUrl) }

    // Auto-switch from Browser to MenuOverlay when back navigation lands on internal URL
    LaunchedEffect(state.currentUrl) {
        if (currentScreen == Screen.Browser &&
            previousUrl.isNotEmpty() &&
            !UrlUtils.isInternalBrowserUrl(previousUrl) &&
            UrlUtils.isInternalBrowserUrl(state.currentUrl)
        ) {
            currentScreen = Screen.MenuOverlay
        }
        previousUrl = state.currentUrl
    }

    // Polling fallback: ensure SessionRepo emits state updates even if
    // store.observeManually callback is not invoked (e.g. due to lifecycle issues).
    // update() uses setIfNew so this is cheap when nothing changed.
    LaunchedEffect(Unit) {
        while (true) {
            sessionRepo.update()
            delay(5000)
        }
    }

    // Notify ScreenController of active screen changes so cursor model / telemetry work
    LaunchedEffect(currentScreen) {
        val activeScreen = when (currentScreen) {
            Screen.Browser -> ScreenControllerStateMachine.ActiveScreen.WEB_RENDER
            Screen.MenuOverlay -> ScreenControllerStateMachine.ActiveScreen.NAVIGATION_OVERLAY
            Screen.Settings -> ScreenControllerStateMachine.ActiveScreen.SETTINGS
            Screen.Onboarding -> ScreenControllerStateMachine.ActiveScreen.WEB_RENDER
        }
        screenController.setActiveScreenForCompose(activeScreen)
    }

    // Observe ScreenController state changes from non-Compose code (e.g., FxA login)
    val controllerScreen by screenController.currentActiveScreen.collectAsState()
    LaunchedEffect(controllerScreen) {
        val targetScreen = when (controllerScreen) {
            ScreenControllerStateMachine.ActiveScreen.WEB_RENDER -> Screen.Browser
            ScreenControllerStateMachine.ActiveScreen.NAVIGATION_OVERLAY -> Screen.MenuOverlay
            ScreenControllerStateMachine.ActiveScreen.SETTINGS -> Screen.Settings
            ScreenControllerStateMachine.ActiveScreen.FXA_PROFILE -> Screen.Settings
            else -> currentScreen
        }
        if (targetScreen != currentScreen) {
            currentScreen = targetScreen
        }
    }

    // Handle hardware back button for screen navigation (TV remote BACK / DPad)
    BackHandler(enabled = currentScreen != Screen.Browser) {
        currentScreen = when (currentScreen) {
            Screen.Settings -> Screen.MenuOverlay
            Screen.Onboarding -> Screen.Browser
            else -> Screen.Browser
        }
    }

    // On Browser screen, intercept hardware BACK to navigate browser history first
    BackHandler(enabled = currentScreen == Screen.Browser) {
        val handled = sessionRepo.attemptBack()
        if (!handled) {
            currentScreen = Screen.MenuOverlay
        }
    }

    Column(modifier = modifier) {
        when (currentScreen) {
                Screen.Browser -> BrowserScreen(
                    onOpenMenu = { currentScreen = Screen.MenuOverlay }
                )

                Screen.MenuOverlay -> MenuOverlay(
                    onNavigateToBrowser = { currentScreen = Screen.Browser },
                    onNavigateToSettings = { settingsType ->
                        currentSettingsScreen = settingsType
                        currentScreen = Screen.Settings
                    },
                    onNavigateHome = {
                        serviceLocator.sessionUseCases.loadUrl.invoke(URLs.APP_URL_HOME)
                        currentScreen = Screen.Browser
                    }
                )

                Screen.Settings -> SettingsScreen(
                    settingsType = currentSettingsScreen,
                    onBack = { currentScreen = Screen.MenuOverlay }
                )

                Screen.Onboarding -> OnboardingScreen(
                    onFinish = { currentScreen = Screen.Browser }
                )
            }
    }
}
