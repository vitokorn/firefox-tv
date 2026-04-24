/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.navigationoverlay

import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModel
import io.reactivex.BackpressureStrategy
import io.reactivex.Observable
import io.reactivex.rxkotlin.Observables
import io.reactivex.subjects.BehaviorSubject
import mozilla.components.support.base.observer.Consumable
import org.mozilla.tv.firefox.R
import org.mozilla.tv.firefox.channels.pinnedtile.PinnedTileRepo
import org.mozilla.tv.firefox.session.SessionRepo
import org.mozilla.tv.firefox.telemetry.TelemetryIntegration
import org.mozilla.tv.firefox.utils.URLs
import org.mozilla.tv.firefox.utils.UrlUtils

class ToolbarViewModel(
    private val sessionRepo: SessionRepo,
    private val pinnedTileRepo: PinnedTileRepo,
    private val telemetryIntegration: TelemetryIntegration = TelemetryIntegration.INSTANCE
) : ViewModel() {

    data class State(
        val backEnabled: Boolean,
        val forwardEnabled: Boolean,
        val refreshEnabled: Boolean,
        val pinEnabled: Boolean,
        val pinChecked: Boolean,
        val turboChecked: Boolean,
        val desktopModeEnabled: Boolean,
        val desktopModeChecked: Boolean,
        val urlBarText: String
    )

    sealed class Action {
        data class ShowTopToast(@StringRes val textId: Int) : Action()
        data class ShowBottomToast(@StringRes val textId: Int) : Action()
        data class SetOverlayVisible(val visible: Boolean) : Action()
        object ExitFirefox : Action()
    }

    // We use events in order to decouple the ViewModel from holding a reference to a context
    private val _events = BehaviorSubject.create<Consumable<Action>>()
    val events = _events.hide()

    val state: Observable<State> = Observables.combineLatest(sessionRepo.state, pinnedTileRepo.pinnedTiles) { sessionState, pinnedTiles ->
        fun isCurrentURLPinned() = pinnedTiles.containsKey(sessionState.currentUrl)

        ToolbarViewModel.State(
            backEnabled = sessionState.backEnabled,
            forwardEnabled = sessionState.forwardEnabled,
            refreshEnabled = !sessionState.currentUrl.isEqualToHomepage(),
            pinEnabled = !sessionState.currentUrl.isEqualToHomepage(),
            pinChecked = isCurrentURLPinned(),
            turboChecked = sessionState.turboModeActive,
            desktopModeEnabled = !sessionState.currentUrl.isEqualToHomepage(),
            desktopModeChecked = sessionState.desktopModeActive,
            urlBarText = UrlUtils.toUrlBarDisplay(sessionState.currentUrl)
        )
    }

    @UiThread
    fun backButtonClicked() {
        sendOverlayClickTelemetry(NavigationEvent.BACK)
        sessionRepo.attemptBack(forceYouTubeExit = true)
        hideOverlay()
    }

    @UiThread
    fun forwardButtonClicked() {
        sendOverlayClickTelemetry(NavigationEvent.FORWARD)
        sessionRepo.goForward()
        hideOverlay()
    }

    @UiThread
    fun reloadButtonClicked() {
        sendOverlayClickTelemetry(NavigationEvent.RELOAD)
        sessionRepo.reload()
        sessionRepo.pushCurrentValue()
        hideOverlay()
    }

    @UiThread
    fun pinButtonClicked() {
        val pinChecked = state.blockingFirst().pinChecked
        val url = sessionRepo.state.blockingFirst().currentUrl

        sendOverlayClickTelemetry(NavigationEvent.PIN_ACTION, pinChecked = !pinChecked)

        if (pinChecked) {
            pinnedTileRepo.removePinnedTile(url)
            _events.onNext(Consumable.from(Action.ShowTopToast(R.string.notification_unpinned_site)))
        } else {
            pinnedTileRepo.addPinnedTile(url, sessionRepo.currentURLScreenshot())
            _events.onNext(Consumable.from(Action.ShowTopToast(R.string.notification_pinned_site)))
        }
        hideOverlay()
    }

    @UiThread
    fun turboButtonClicked() {
        val currentState = sessionRepo.currentState()
        val currentUrl = currentState?.currentUrl ?: URLs.APP_URL_HOME
        val turboModeActive = currentState?.turboModeActive ?: true
        val isHomepage = currentUrl.isEqualToHomepage()

        sessionRepo.setTurboModeEnabled(!turboModeActive, skipEngineSettingsUpdate = isHomepage)
        if (!isHomepage) {
            sessionRepo.reload()
        }

        sendOverlayClickTelemetry(NavigationEvent.TURBO, turboChecked = !turboModeActive)
        currentUrl.let { if (!it.isEqualToHomepage()) hideOverlay() }
    }

    @UiThread
    fun desktopModeButtonClicked() {
        val desktopModeChecked = state.blockingFirst().desktopModeChecked

        sendOverlayClickTelemetry(NavigationEvent.DESKTOP_MODE, desktopModeChecked = !desktopModeChecked)

        sessionRepo.setDesktopMode(!desktopModeChecked)
        val textId = when {
            desktopModeChecked -> R.string.notification_request_non_desktop_site
            else -> R.string.notification_request_desktop_site
        }

        _events.onNext(Consumable.from(Action.ShowBottomToast(textId)))
        hideOverlay()
    }

    @UiThread
    fun exitFirefoxButtonClicked() {
        sendOverlayClickTelemetry(NavigationEvent.EXIT_FIREFOX)
        _events.onNext(Consumable.from(Action.ExitFirefox))
    }

    private fun sendOverlayClickTelemetry(
        event: NavigationEvent,
        turboChecked: Boolean? = null,
        pinChecked: Boolean? = null,
        desktopModeChecked: Boolean? = null
    ) {
        // legacyState removed - telemetry disabled pending LiveDataReactiveStreams replacement
    }

    private fun sendOverlayClickTelemetry(event: NavigationEvent) {
        // legacyState removed - telemetry disabled pending LiveDataReactiveStreams replacement
    }

    private fun String.isEqualToHomepage() = this == URLs.APP_URL_HOME || this == "data:text/html,<html></html>" || this.isEmpty()

    private fun hideOverlay() {
        _events.onNext(Consumable.from(Action.SetOverlayVisible(false)))
    }
}
