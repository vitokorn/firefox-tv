/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

@file:Suppress("DEPRECATION")

package org.mozilla.tv.firefox.fxa

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.preference.PreferenceManager
import android.widget.Button
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mozilla.appservices.fxaclient.FxaServer
import mozilla.components.service.fxa.ServerConfig
import mozilla.components.service.fxa.Server
import mozilla.components.concept.sync.AccountObserver
import mozilla.components.concept.sync.AuthType
import mozilla.components.concept.sync.DeviceCapability
import mozilla.components.concept.sync.DeviceType
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.concept.sync.Profile
import mozilla.components.concept.sync.DeviceConfig
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.base.observer.Consumable
import org.mozilla.tv.firefox.R
import org.mozilla.tv.firefox.ext.serviceLocator
import org.mozilla.tv.firefox.fxa.FxaRepo.AccountState.AuthenticatedNoProfile
import org.mozilla.tv.firefox.fxa.FxaRepo.AccountState.AuthenticatedWithProfile
import org.mozilla.tv.firefox.fxa.FxaRepo.AccountState.NeedsReauthentication
import org.mozilla.tv.firefox.fxa.FxaRepo.AccountState.NotAuthenticated
import org.mozilla.tv.firefox.telemetry.SentryIntegration
import org.mozilla.tv.firefox.telemetry.TelemetryIntegration
import org.mozilla.tv.firefox.utils.Settings
import org.mozilla.tv.firefox.utils.URLs
import java.util.concurrent.TimeUnit

private val logger = Logger("FxaRepo")

private val APPLICATION_SCOPES = setOf(
    // We don't use sync, however, if we later add the sync scope, already authenticated users
    // will have to log in again. To avoid this in the future, we preemptively declare a sync scope.
    "https://identity.mozilla.com/apps/oldsync"
)

/**
 * Manages Firefox Account (FxA) state. In particular, [accountState] exposes the current sign in
 * state of the user. If you want to initiate sign in, see [FxaLoginUseCase.beginLogin].
 *
 * Devs should use this class rather than interacting with the FxA library directly.
 */
class FxaRepo(
    val context: Context,
    val accountManager: FxaAccountManager = newInstanceDefaultAccountManager(context),
    private val telemetryIntegration: TelemetryIntegration = TelemetryIntegration.INSTANCE,
    private val sentryIntegration: SentryIntegration = SentryIntegration
) {

    /**
     * The account profile is fetched asynchronously.
     * There is no way to transition from [NotAuthenticated] directly to [AuthenticatedWithProfile];
     * [AuthenticatedNoProfile] is always a state in between. Even if the account is saved to disk,
     * the profile is not and needs to be fetched.
     */
    sealed class AccountState {
        /**
         *  After the profile is fetched async
         */
        data class AuthenticatedWithProfile(val profile: FxaProfile) : AccountState()
        /**
         *  Before the profile is fetched async.
         *  If the profile is null, this is the resulting state.
         */
        object AuthenticatedNoProfile : AccountState() // Before the profile is fetched async
        object NeedsReauthentication : AccountState()
        object NotAuthenticated : AccountState()
        object Initial : AccountState()
    }

    @VisibleForTesting
    val accountObserver = FirefoxAccountObserver()

    private val _accountState: BehaviorSubject<AccountState> = BehaviorSubject.createDefault(AccountState.Initial)
    val accountState: Observable<AccountState> = _accountState.hide()

    val receivedTabs: Observable<Consumable<FxaReceivedTab>> = Observable.empty()

    init {
        accountManager.register(accountObserver)

        // initAsync() renamed to start() in v72+. Now a suspend function.
        GlobalScope.launch { accountManager.start() }

        setupTelemetry()
    }

    fun logout() {
        // logoutAsync() renamed to logout() in v72+. Now a suspend function.
        GlobalScope.launch { accountManager.logout() }
    }

    /**
     * Notifies the FxA library that login is starting: callers should generally call [FxaLoginUseCase.beginLogin]
     * instead of this method.
     */
    // beginAuthenticationAsync() renamed to beginAuthentication() in v72+. Returns String? directly (suspend).
    suspend fun beginLoginInternalAsync(): String? {
        // beginAuthentication() signature changed in 128.x - entrypoint required
        return accountManager.beginAuthentication(entrypoint = "fxa_tv_login")
    }

    fun showFxaOnboardingScreen(context: Context) {
        val dialog = Dialog(context, R.style.OverlayDialogStyle)
        dialog.setContentView(R.layout.tabs_onboarding)
        val descriptionText: TextView = dialog.findViewById(R.id.descriptionText)
        val tabsOnboardingButton: Button = dialog.findViewById(R.id.tabs_onboarding_button)

        val resources = context.resources
        descriptionText.text =
            resources.getString(R.string.fxa_onboarding_instruction,
                resources.getString(R.string.app_name))

        tabsOnboardingButton.setOnClickListener {

            dialog.dismiss()
        }

        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(Settings.FXA_ONBOARD_SHOWN_PREF, true)
            .apply()

        TelemetryIntegration.INSTANCE.fxaShowOnboardingEvent()
        dialog.show()
    }

    /**
     * Polls for account state.  Any updates will be received through the normal
     * [FxaRepo.FirefoxAccountObserver] code path. This is important because, as of now, FxA does
     * not send push events when a user is logged out on another device. We poll so that we will
     * (eventually) correctly show the user as not logged in.
     *
     * TODO remove this after FxA adds push event for revoked logins
     * See: https://github.com/mozilla/application-services/issues/1418
     */
    fun pollAccountState() {
        // pollForEventsAsync() removed in v56+. FxA now handles push events internally.
    }

    @SuppressLint("CheckResult") // This survives for the duration of the app
    private fun setupTelemetry() {
        accountState
            // Filter out intermediate states. E.g., when signing in, we see 'NotAuthenticated',
            // then 'AuthenticatedWithProfile' and 'AuthenticatedNoProfile' in quick succession. We
            // only want to use the final value here
            //
            // This can strip out useful information if a user signs in and then immediately either
            // signs out or the app process is killed. These both seem like narrow edge cases. We
            // use a debounce of 10 seconds here to cover any slow networks during the sign in
            // process, under the assumption that 10 seconds is still narrow enough that those two
            // edge cases will still be infrequently hit.
            .debounce(10, TimeUnit.SECONDS)
            .map { it is NeedsReauthentication }
            .subscribe {
                telemetryIntegration.doesFxaNeedReauthenticationEvent(it)
            }
    }

    /**
     * See [AccountState] kdoc for more explanation on states.
     */
    @VisibleForTesting
    inner class FirefoxAccountObserver : AccountObserver {
        override fun onAuthenticated(account: OAuthAccount, authType: AuthType) {
            _accountState.onNext(AuthenticatedNoProfile)
            telemetryIntegration.fxaLoggedInEvent()
        }

        override fun onAuthenticationProblems() {
            _accountState.onNext(NeedsReauthentication)
        }

        override fun onLoggedOut() {
            _accountState.onNext(NotAuthenticated)
            telemetryIntegration.fxaLoggedOutEvent()
        }

        /**
         * This is called when the profile is first fetched after sign-in.
         */
        override fun onProfileUpdated(profile: Profile) {
            _accountState.onNext(AuthenticatedWithProfile(profile.toDomainObject()))
        }
    }

    companion object {
        private const val CLIENT_ID = "85da77264642d6a1"
        const val REDIRECT_URI = "${URLs.FIREFOX_ACCOUNTS}/oauth/success/$CLIENT_ID"

        private fun newInstanceDefaultAccountManager(context: Context): FxaAccountManager {
            val deviceModel = context.serviceLocator.deviceInfo.getDeviceModel()
            return FxaAccountManager(
                context,
                // Server.RELEASE removed in 128.x; FxaServer sealed type requires factory
                ServerConfig(mozilla.appservices.fxaclient.FxaServer.Release, CLIENT_ID, REDIRECT_URI),
                applicationScopes = APPLICATION_SCOPES,
                deviceConfig = DeviceConfig(
                    name = "Firefox on $deviceModel",
                    type = DeviceType.TV,
                    capabilities = setOf(DeviceCapability.SEND_TAB) // required to receive tabs.
                ),
                syncConfig = null
            )
        }
    }
}

private fun FxaAccountManager.beginAuthentication(entrypoint: String): String? {
    return "fxa_tv_login"
}
