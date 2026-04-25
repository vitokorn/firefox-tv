/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.navigationoverlay

import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModel
import io.reactivex.Observable
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

    val pinnedTiles: Observable<ChannelDetails> = channelRepo.getPinnedTiles()
        .map { ChannelDetails(title = channelTitles.pinned, tileList = it) }

    val newsChannel: Observable<ChannelDetails> = channelRepo.getNewsTiles()
        .map { ChannelDetails(title = channelTitles.newsAndPolitics, tileList = it) }

    val sportsChannel: Observable<ChannelDetails> = channelRepo.getSportsTiles()
        .map { ChannelDetails(title = channelTitles.sports, tileList = it) }

    val musicChannel: Observable<ChannelDetails> = channelRepo.getMusicTiles()
        .map { ChannelDetails(title = channelTitles.music, tileList = it) }

    fun shouldBeDisplayed(channelDetails: Observable<ChannelDetails>): Observable<Boolean> =
        channelDetails.map { it.tileList.isNotEmpty() }
            .distinctUntilChanged()

    val focusView: Observable<Int> = screenController.currentActiveScreen
            .buffer(2, 1)
            .filter { (_, currentScreen) -> currentScreen == ActiveScreen.NAVIGATION_OVERLAY }
            .map { (prevScreen, _) ->
                when (prevScreen!!) {
                    ActiveScreen.WEB_RENDER -> R.id.navUrlInput
                    ActiveScreen.SETTINGS -> R.id.settings_tile_telemetry
                    ActiveScreen.NAVIGATION_OVERLAY -> View.NO_ID
                    ActiveScreen.FXA_PROFILE -> R.id.fxaButton
                }
            }

    val leftmostActiveToolBarId: Observable<Int> = toolbarViewModel.state
            .map { state ->
                when {
                    state.backEnabled -> R.id.navButtonBack
                    state.forwardEnabled -> R.id.navButtonForward
                    state.refreshEnabled -> R.id.navButtonReload
                    else -> R.id.turboButton
                }
            }

    fun fxaButtonClicked(fragmentManager: FragmentManager) {
        fun showFxaProfileScreen() {
            screenController.showSettingsScreen(fragmentManager, SettingsScreen.FXA_PROFILE)
            TelemetryIntegration.INSTANCE.fxaShowProfileButtonClickEvent()
        }

        when (fxaRepo.accountState.blockingFirst()) {
            is AccountState.AuthenticatedWithProfile -> showFxaProfileScreen()
            is AccountState.AuthenticatedNoProfile -> {
                // TODO The UI for this error state is not perfect. See #2721
                showFxaProfileScreen()
            }
            is AccountState.NeedsReauthentication -> {
                TelemetryIntegration.INSTANCE.fxaReauthorizeButtonClickEvent()
                fxaLoginUseCase.beginLogin(fragmentManager)
            }
            is AccountState.NotAuthenticated, AccountState.Initial -> {
                TelemetryIntegration.INSTANCE.fxaLoginButtonClickEvent()
                fxaLoginUseCase.beginLogin(fragmentManager)
            }
        }
    }
}
