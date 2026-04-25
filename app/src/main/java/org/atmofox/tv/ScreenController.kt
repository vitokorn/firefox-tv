/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv

import android.content.Context
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.selector.findTabOrCustomTab
import org.atmofox.tv.ScreenControllerStateMachine.ActiveScreen
import org.atmofox.tv.ScreenControllerStateMachine.Transition
import org.atmofox.tv.channels.SettingsScreen
import org.atmofox.tv.ext.serviceLocator
import org.atmofox.tv.navigationoverlay.NavigationOverlayFragment
import org.atmofox.tv.session.SessionRepo
import org.atmofox.tv.settings.SettingsFragment
import org.atmofox.tv.telemetry.MenuInteractionMonitor
import org.atmofox.tv.telemetry.TelemetryIntegration
import org.atmofox.tv.telemetry.UrlTextInputLocation
import org.atmofox.tv.utils.URLs
import org.atmofox.tv.utils.UrlUtils
import org.atmofox.tv.webrender.WebRenderFragment
import org.atmofox.tv.widget.InlineAutocompleteEditText

class ScreenController(private val sessionRepo: SessionRepo) {

    private val _currentActiveScreen = BehaviorSubject.createDefault(ActiveScreen.NAVIGATION_OVERLAY)
    /**
     * Observers will be notified just before the fragment transaction is committed
     */
    val currentActiveScreen: Observable<ActiveScreen> = _currentActiveScreen
            .distinctUntilChanged()
            .hide()

    /**
     * Update the active screen state directly for Compose UI transitions.
     * This is used when the screen controller is not managing fragment transactions.
     */
    fun setActiveScreenForCompose(screen: ActiveScreen) {
        _currentActiveScreen.onNext(screen)
    }

    /**
     * To keep things simple, we add all the fragments at start instead of creating them when needed
     * in order to make the assumption that all Fragments exist.
     * To show the correct Fragment, we use Fragment hide/show to make sure the correct Fragment is visible.
     * We DO NOT use the Fragment backstack so that all transitions are controlled in the same manner, and we
     * don't end up mixing backstack actions with show/hide.
     */
    fun setUpFragmentsForNewSession(fragmentManager: FragmentManager, session: TabSessionState) {
        val renderFragment = WebRenderFragment.createForSession(session)
        fragmentManager
            .beginTransaction()
            .add(R.id.container_web_render, renderFragment, WebRenderFragment.FRAGMENT_TAG)
            // We add NavigationOverlayFragment last so that it takes focus
            .add(R.id.container_navigation_overlay, NavigationOverlayFragment(), NavigationOverlayFragment.FRAGMENT_TAG)
            .commitNow()

        _currentActiveScreen.onNext(ActiveScreen.NAVIGATION_OVERLAY)
    }

    /**
     * Loads the given url. If isTextInput is true, there should be no null parameters.
     */
    fun onUrlEnteredInner(
        context: Context,
        fragmentManager: FragmentManager,
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

        showBrowserScreenForUrl(fragmentManager, updatedUrlStr)

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

    fun showSettingsScreen(fragmentManager: FragmentManager, settingsScreen: SettingsScreen) {
        val transition = when (settingsScreen) {
            SettingsScreen.DATA_COLLECTION -> Transition.ADD_SETTINGS_DATA
            SettingsScreen.CLEAR_COOKIES -> Transition.ADD_SETTINGS_COOKIES
            SettingsScreen.FXA_PROFILE -> Transition.ADD_FXA_PROFILE
            SettingsScreen.ABOUT -> Transition.ADD_SETTINGS_ABOUT
            else -> Transition.ADD_SETTINGS_DATA
        }
        handleTransitionAndUpdateActiveScreen(fragmentManager, transition)
    }

    fun showBrowserScreenForCurrentSession(fragmentManager: FragmentManager, session: TabSessionState) {
        if (session.content.url != URLs.APP_URL_HOME) {
            handleTransitionAndUpdateActiveScreen(fragmentManager, Transition.SHOW_BROWSER)
        }
    }

    fun showBrowserScreenForUrl(fragmentManager: FragmentManager, url: String) {
        handleTransitionAndUpdateActiveScreen(fragmentManager, Transition.SHOW_BROWSER)
        val webRenderFragment = fragmentManager.webRenderFragment()
        webRenderFragment.loadUrl(url)
    }

    fun showNavigationOverlay(fragmentManager: FragmentManager?, toShow: Boolean) {
        fragmentManager ?: return
        fragmentManagerShowNavigationOverlay(fragmentManager, toShow)

        val currentScreen = if (toShow) ActiveScreen.NAVIGATION_OVERLAY else ActiveScreen.WEB_RENDER
        _currentActiveScreen.onNext(currentScreen)
    }

    private fun fragmentManagerShowNavigationOverlay(fragmentManager: FragmentManager, toShow: Boolean) {
        val transaction = fragmentManager.beginTransaction()
        val overlayFragment = fragmentManager.navigationOverlayFragment()

        if (toShow) {
            // If a user navigates to YouTube while a video is fullscreened, it will cause YouTube
            // to display oddly (see #1719). Exiting fullscreen is asynchronous, so handling it
            // here is safer than just before navigation. Most browsers don't show the URL
            // bar while fullscreen is active and so we are aligning with that strategy and exiting
            // fullscreen before any navigation options on the overlay are made available to the user
            val fullScreenExited = overlayFragment.context?.serviceLocator?.sessionRepo?.exitFullScreenIfPossible()
            if (fullScreenExited == true) {
                TelemetryIntegration.INSTANCE.fullScreenVideoProgrammaticallyClosed()
            }

            transaction.show(overlayFragment)
            MenuInteractionMonitor.menuOpened()
            // TODO: Disabled until Overlay refactor is complete #1666
            // overlayFragment.navOverlayScrollView.updateOverlayForHomescreen(isOnHomeUrl(fragmentManager))
        } else {
            transaction.hide(overlayFragment)
            MenuInteractionMonitor.menuClosed()
        }

        transaction.commit()
    }

    fun dispatchKeyEvent(
        keyEvent: KeyEvent,
        fragmentManager: FragmentManager,
        @VisibleForTesting currentActiveScreen: ActiveScreen? = _currentActiveScreen.value
    ): Boolean {
        if (keyEvent.keyCode == KeyEvent.KEYCODE_MENU) {
            return when (keyEvent.action) {
                KeyEvent.ACTION_DOWN -> handleMenu(fragmentManager)
                else -> true // We swallow ACTION_UP to only handle the key event once.
            }
        }

        return when (currentActiveScreen) {
            ScreenControllerStateMachine.ActiveScreen.WEB_RENDER ->
                fragmentManager.webRenderFragment().dispatchKeyEvent(keyEvent)
            ScreenControllerStateMachine.ActiveScreen.NAVIGATION_OVERLAY ->
                fragmentManager.navigationOverlayFragment().dispatchKeyEvent(keyEvent)

            else -> false
        }
    }

    fun handleBack(fragmentManager: FragmentManager): Boolean {
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
        val result = handleTransitionAndUpdateActiveScreen(fragmentManager, transition)
        Log.d("ScreenController", "handleBack: result=$result")
        return result
    }

    fun handleMenu(fragmentManager: FragmentManager): Boolean {
        val transition = ScreenControllerStateMachine.getNewStateMenuPress(_currentActiveScreen.value!!, isOnHomeUrl())

        if (transition == Transition.ADD_OVERLAY) {
            TelemetryIntegration.INSTANCE.menuOpenedFromMenuButton()
        }

        return handleTransitionAndUpdateActiveScreen(fragmentManager, transition)
    }

    private fun canGoBack(): Boolean {
        return sessionRepo.currentState()?.backEnabled ?: false
    }

    private fun isOnHomeUrl(): Boolean {
        @Suppress("DEPRECATION")
        return sessionRepo.currentState()?.currentUrl == URLs.APP_URL_HOME
    }

    private fun handleTransitionAndUpdateActiveScreen(fragmentManager: FragmentManager, transition: Transition): Boolean {
        Log.d("ScreenController", "handleTransitionAndUpdateActiveScreen: transition=$transition")
        // Call show() before hide() so that focus moves correctly to the shown fragment once others are hidden

        when (transition) {
            Transition.ADD_OVERLAY -> {
                Log.d("ScreenController", "Executing ADD_OVERLAY transition")
                // We always update the currentActiveScreen value before beginning the fragment transaction
                _currentActiveScreen.onNext(ActiveScreen.NAVIGATION_OVERLAY)
                fragmentManagerShowNavigationOverlay(fragmentManager, true)
            }
            Transition.REMOVE_OVERLAY -> {
                _currentActiveScreen.onNext(ActiveScreen.WEB_RENDER)
                showNavigationOverlay(fragmentManager, false)
            }
            Transition.ADD_SETTINGS_DATA -> {
                _currentActiveScreen.onNext(ActiveScreen.SETTINGS)
                fragmentManager.beginTransaction()
                    .hide(fragmentManager.navigationOverlayFragment())
                    .add(R.id.container_settings, SettingsFragment.newInstance(SettingsScreen.DATA_COLLECTION),
                            SettingsFragment.FRAGMENT_TAG)
                    .commit()
            }
            Transition.ADD_SETTINGS_COOKIES -> {
                _currentActiveScreen.onNext(ActiveScreen.SETTINGS)
                fragmentManager.beginTransaction()
                    .hide(fragmentManager.navigationOverlayFragment())
                    .add(R.id.container_settings, SettingsFragment.newInstance(SettingsScreen.CLEAR_COOKIES),
                            SettingsFragment.FRAGMENT_TAG)
                    .commit()
            }
            Transition.ADD_SETTINGS_ABOUT -> {
                _currentActiveScreen.onNext(ActiveScreen.SETTINGS)
                fragmentManager.beginTransaction()
                    .hide(fragmentManager.navigationOverlayFragment())
                    .add(R.id.container_settings, SettingsFragment.newInstance(SettingsScreen.ABOUT),
                            SettingsFragment.FRAGMENT_TAG)
                    .commit()
            }
            Transition.REMOVE_SETTINGS -> {
                _currentActiveScreen.onNext(ActiveScreen.NAVIGATION_OVERLAY)
                fragmentManager.findFragmentByTag(SettingsFragment.FRAGMENT_TAG).let {
                    fragmentManager.beginTransaction()
                        .remove(it!!)
                        .show(fragmentManager.navigationOverlayFragment())
                        .commit()
                }
            }
            Transition.ADD_FXA_PROFILE -> {
                _currentActiveScreen.onNext(ActiveScreen.FXA_PROFILE)
                fragmentManager.beginTransaction()
                    .hide(fragmentManager.navigationOverlayFragment())
                    .add(R.id.container_settings, SettingsFragment.newInstance(SettingsScreen.FXA_PROFILE),
                        SettingsFragment.FRAGMENT_TAG)
                    .commit()
            }
            Transition.REMOVE_FXA_PROFILE -> {
                _currentActiveScreen.onNext(ActiveScreen.NAVIGATION_OVERLAY)
                fragmentManager.findFragmentByTag(SettingsFragment.FRAGMENT_TAG).let {
                    fragmentManager.beginTransaction()
                        .remove(it!!)
                        .show(fragmentManager.navigationOverlayFragment())
                        .commit()
                }
            }
            Transition.SHOW_BROWSER -> {
                _currentActiveScreen.onNext(ActiveScreen.WEB_RENDER)
                fragmentManager.beginTransaction()
                    .maybeRemoveSettingsScreen(fragmentManager)
                    .hide(fragmentManager.navigationOverlayFragment())
                    .commitNow()
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

private fun FragmentManager.webRenderFragment(): WebRenderFragment =
    this.findFragmentByTag(WebRenderFragment.FRAGMENT_TAG) as WebRenderFragment

private fun FragmentManager.navigationOverlayFragment(): NavigationOverlayFragment =
    this.findFragmentByTag(NavigationOverlayFragment.FRAGMENT_TAG) as NavigationOverlayFragment

private fun FragmentTransaction.maybeRemoveSettingsScreen(
    fragmentManager: FragmentManager
): FragmentTransaction {
    val settingsScreen = fragmentManager.findFragmentByTag(SettingsFragment.FRAGMENT_TAG)

    return if (settingsScreen != null) {
        this.remove(settingsScreen)
    } else {
        this
    }
}
