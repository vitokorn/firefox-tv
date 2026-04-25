/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.navigationoverlay

import android.view.View
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.atmofox.tv.R
import org.atmofox.tv.ScreenController
import org.atmofox.tv.ScreenControllerStateMachine.ActiveScreen
import org.atmofox.tv.channels.ChannelDetails
import org.atmofox.tv.channels.ChannelRepo
import org.atmofox.tv.channels.SettingsScreen
import org.atmofox.tv.fxa.FxaLoginUseCase
import org.atmofox.tv.fxa.FxaRepo
import org.atmofox.tv.fxa.FxaRepo.AccountState
import org.atmofox.tv.telemetry.TelemetryIntegration

class ChannelTitles(
    val pinned: String,
    val newsAndPolitics: String,
    val sports: String,
    val music: String,
    val food: String
)

class NavigationOverlayViewModel(
    private val screenController: ScreenController,
    channelTitles: ChannelTitles,
    channelRepo: ChannelRepo,
    toolbarViewModel: ToolbarViewModel,
    private val fxaRepo: FxaRepo,
    private val fxaLoginUseCase: FxaLoginUseCase
) : ViewModel() {

    val pinnedTiles: Flow<ChannelDetails> = channelRepo.pinnedTilesFlow
        .map { ChannelDetails(title = channelTitles.pinned, tileList = it) }

    val newsChannel: Flow<ChannelDetails> = channelRepo.newsTilesFlow
        .map { ChannelDetails(title = channelTitles.newsAndPolitics, tileList = it) }

    val sportsChannel: Flow<ChannelDetails> = channelRepo.sportsTilesFlow
        .map { ChannelDetails(title = channelTitles.sports, tileList = it) }

    val musicChannel: Flow<ChannelDetails> = channelRepo.musicTilesFlow
        .map { ChannelDetails(title = channelTitles.music, tileList = it) }

    fun shouldBeDisplayed(channelDetails: Flow<ChannelDetails>): Flow<Boolean> =
        channelDetails.map { it.tileList.isNotEmpty() }

    // focusView tracks screen transitions: emit a view ID to focus whenever we enter the overlay.
    private var _previousScreen = ActiveScreen.NAVIGATION_OVERLAY
    val focusView: Flow<Int> = screenController.currentActiveScreen
            .filter { currentScreen -> currentScreen == ActiveScreen.NAVIGATION_OVERLAY }
            .map { currentScreen ->
                val prevScreen = _previousScreen
                _previousScreen = currentScreen
                when (prevScreen) {
                    ActiveScreen.WEB_RENDER -> R.id.navUrlInput
                    ActiveScreen.SETTINGS -> R.id.settings_tile_telemetry
                    ActiveScreen.NAVIGATION_OVERLAY -> View.NO_ID
                    ActiveScreen.FXA_PROFILE -> R.id.fxaButton
                }
            }

    val leftmostActiveToolBarId: Flow<Int> = toolbarViewModel.state
            .map { state ->
                when {
                    state.backEnabled -> R.id.navButtonBack
                    state.forwardEnabled -> R.id.navButtonForward
                    state.refreshEnabled -> R.id.navButtonReload
                    else -> R.id.turboButton
                }
            }

    fun fxaButtonClicked() {
        fun showFxaProfileScreen() {
            screenController.showSettingsScreen(SettingsScreen.FXA_PROFILE)
            TelemetryIntegration.INSTANCE.fxaShowProfileButtonClickEvent()
        }

        when (fxaRepo.accountState.value) {
            is AccountState.AuthenticatedWithProfile -> showFxaProfileScreen()
            is AccountState.AuthenticatedNoProfile -> {
                // TODO The UI for this error state is not perfect. See #2721
                fxaLoginUseCase.beginLogin()
            }
            is AccountState.NeedsReauthentication -> {
                TelemetryIntegration.INSTANCE.fxaReauthorizeButtonClickEvent()
                fxaLoginUseCase.beginLogin()
            }
            is AccountState.NotAuthenticated, AccountState.Initial -> {
                TelemetryIntegration.INSTANCE.fxaLoginButtonClickEvent()
                fxaLoginUseCase.beginLogin()
            }
        }
    }
}
