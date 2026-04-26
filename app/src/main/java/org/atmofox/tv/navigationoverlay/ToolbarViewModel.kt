/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.navigationoverlay

import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mozilla.components.support.base.observer.Consumable
import org.atmofox.tv.R
import org.atmofox.tv.channels.pinnedtile.PinnedTileRepo
import org.atmofox.tv.session.SessionRepo
import org.atmofox.tv.telemetry.TelemetryIntegration
import org.atmofox.tv.utils.URLs
import org.atmofox.tv.utils.UrlUtils
import org.atmofox.tv.webrender.EngineViewCache

class ToolbarViewModel(
    private val sessionRepo: SessionRepo,
    private val pinnedTileRepo: PinnedTileRepo,
    private val engineViewCache: EngineViewCache,
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
    private val _events = MutableSharedFlow<Consumable<Action>>(extraBufferCapacity = 2)
    val events: SharedFlow<Consumable<Action>> = _events.asSharedFlow()

    val state: StateFlow<State> = combine(sessionRepo.state, pinnedTileRepo.pinnedTiles) { sessionState, pinnedTiles ->
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
    }.stateIn(viewModelScope, SharingStarted.Lazily, State(
        backEnabled = false,
        forwardEnabled = false,
        refreshEnabled = false,
        pinEnabled = false,
        pinChecked = false,
        turboChecked = false,
        desktopModeEnabled = false,
        desktopModeChecked = false,
        urlBarText = ""
    ))

    @UiThread
    fun backButtonClicked() {
        sendOverlayClickTelemetry()
        sessionRepo.attemptBack(forceYouTubeExit = true)
        hideOverlay()
    }

    @UiThread
    fun forwardButtonClicked() {
        sendOverlayClickTelemetry()
        sessionRepo.goForward()
        hideOverlay()
    }

    @UiThread
    fun reloadButtonClicked() {
        sendOverlayClickTelemetry()
        sessionRepo.reload()
        sessionRepo.pushCurrentValue()
        hideOverlay()
    }

    @UiThread
    fun pinButtonClicked() {
        viewModelScope.launch {
            val pinChecked = state.value.pinChecked
            val url = sessionRepo.state.value.currentUrl

            sendOverlayClickTelemetry()

            if (pinChecked) {
                pinnedTileRepo.removePinnedTile(url)
                _events.tryEmit(Consumable.from(Action.ShowTopToast(R.string.notification_unpinned_site)))
            } else {
                val screenshot = engineViewCache.captureThumbnail()
                pinnedTileRepo.addPinnedTile(url, screenshot)
                _events.tryEmit(Consumable.from(Action.ShowTopToast(R.string.notification_pinned_site)))
            }
            hideOverlay()
        }
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

        sendOverlayClickTelemetry()
        currentUrl.let { if (!it.isEqualToHomepage()) hideOverlay() }
    }

    @UiThread
    fun desktopModeButtonClicked() {
        val desktopModeChecked = state.value.desktopModeChecked

        sendOverlayClickTelemetry()

        sessionRepo.setDesktopMode(!desktopModeChecked)
        val textId = when {
            desktopModeChecked -> R.string.notification_request_non_desktop_site
            else -> R.string.notification_request_desktop_site
        }

        _events.tryEmit(Consumable.from(Action.ShowBottomToast(textId)))
        hideOverlay()
    }

    @UiThread
    fun exitFirefoxButtonClicked() {
        sendOverlayClickTelemetry()
        _events.tryEmit(Consumable.from(Action.ExitFirefox))
    }

    private fun sendOverlayClickTelemetry() {
        // legacyState removed - telemetry disabled pending LiveDataReactiveStreams replacement
    }

    private fun String.isEqualToHomepage() = this == URLs.APP_URL_HOME || this == "data:text/html,<html></html>" || this.isEmpty()

    private fun hideOverlay() {
        _events.tryEmit(Consumable.from(Action.SetOverlayVisible(false)))
    }
}
