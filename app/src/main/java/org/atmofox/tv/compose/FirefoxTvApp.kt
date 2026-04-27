/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.TabSessionState
import org.atmofox.tv.ScreenControllerStateMachine
import org.atmofox.tv.compose.browser.BrowserScreen
import org.atmofox.tv.compose.menu.MenuOverlay
import org.atmofox.tv.compose.navigation.Screen
import org.atmofox.tv.compose.navigation.SettingsType
import org.atmofox.tv.compose.onboarding.OnboardingScreen
import org.atmofox.tv.compose.settings.SettingsScreen
import androidx.compose.runtime.collectAsState
import org.atmofox.tv.ext.serviceLocator
import org.atmofox.tv.ext.webRenderComponents
import org.atmofox.tv.utils.URLs

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
    val lifecycleOwner = LocalLifecycleOwner.current
    val serviceLocator = context.serviceLocator
    var isBrowserInitialized by rememberSaveable { mutableStateOf(false) }
    var pendingMenuUrlLoad by rememberSaveable { mutableStateOf<String?>(null) }
    val shouldInitializeScreenController = isBrowserInitialized || currentScreen != Screen.MenuOverlay
    val composeActiveScreen = when (currentScreen) {
        Screen.Browser -> ScreenControllerStateMachine.ActiveScreen.WEB_RENDER
        Screen.MenuOverlay -> ScreenControllerStateMachine.ActiveScreen.NAVIGATION_OVERLAY
        Screen.Settings -> ScreenControllerStateMachine.ActiveScreen.SETTINGS
        Screen.Onboarding -> ScreenControllerStateMachine.ActiveScreen.WEB_RENDER
    }
    val screenController = if (shouldInitializeScreenController) {
        remember(serviceLocator) {
            serviceLocator.screenController.apply {
                setActiveScreenForCompose(composeActiveScreen)
            }
        }
    } else {
        null
    }

    LaunchedEffect(currentScreen, isBrowserInitialized) {
        if (currentScreen == Screen.Browser && !isBrowserInitialized) {
            // Initialize browser engine/store/session lazily on first browser entry.
            if (context.webRenderComponents.store.state.selectedTab == null) {
                val newTab = TabSessionState(
                    id = "initial-session",
                    content = ContentState(url = URLs.APP_URL_HOME)
                )
                context.webRenderComponents.store.dispatch(TabListAction.AddTabAction(newTab, select = true))
            }
            serviceLocator.sessionRepo.update()
            lifecycleOwner.lifecycle.addObserver(serviceLocator.engineViewCache)
            isBrowserInitialized = true
        }
    }

    LaunchedEffect(isBrowserInitialized, pendingMenuUrlLoad) {
        val url = pendingMenuUrlLoad
        if (isBrowserInitialized && url != null) {
            serviceLocator.sessionUseCases.loadUrl.invoke(url)
            pendingMenuUrlLoad = null
        }
    }

    // Notify ScreenController of active screen changes so cursor model / telemetry work
    LaunchedEffect(currentScreen, screenController) {
        if (screenController != null) {
            screenController.setActiveScreenForCompose(composeActiveScreen)
        }
    }

    // Observe ScreenController state changes from non-Compose code (e.g., FxA login)
    val controllerScreen = screenController?.currentActiveScreen?.collectAsState()?.value
    LaunchedEffect(controllerScreen) {
        if (controllerScreen != null) {
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
        val handled = serviceLocator.sessionRepo.attemptBack()
        if (!handled) {
            currentScreen = Screen.MenuOverlay
        }
    }

    Column(modifier = modifier) {
        when (currentScreen) {
                Screen.Browser -> BrowserScreen()

                Screen.MenuOverlay -> AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    MenuOverlay(
                        onNavigateToBrowser = { currentScreen = Screen.Browser },
                        onNavigateToSettings = { settingsType ->
                            currentSettingsScreen = settingsType
                            currentScreen = Screen.Settings
                        },
                        onNavigateHome = {
                            pendingMenuUrlLoad = URLs.APP_URL_HOME
                            currentScreen = Screen.Browser
                        },
                        onNavigateToUrl = { url ->
                            pendingMenuUrlLoad = url
                            currentScreen = Screen.Browser
                        }
                    )
                }

                Screen.Settings -> SettingsScreen(
                    settingsType = currentSettingsScreen,
                    onBack = { currentScreen = Screen.MenuOverlay },
                    onNavigateToBrowser = { currentScreen = Screen.Browser },
                    onSessionCleared = { (context as? android.app.Activity)?.recreate() }
                )

                Screen.Onboarding -> OnboardingScreen(
                    onFinish = { currentScreen = Screen.Browser }
                )
            }
    }
}
