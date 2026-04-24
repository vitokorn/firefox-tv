/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.telemetry

import android.content.Context
import android.net.http.SslError
import android.view.InputDevice
import android.view.KeyEvent
import androidx.annotation.UiThread
import mozilla.components.concept.sync.DeviceType
import mozilla.components.service.glean.Glean
import org.mozilla.tv.firefox.GleanMetrics.Telemetry as TelemetryMetrics
import org.mozilla.tv.firefox.channels.ChannelTile
import org.mozilla.tv.firefox.channels.SettingsButton
import org.mozilla.tv.firefox.channels.SettingsScreen
import org.mozilla.tv.firefox.channels.SettingsTile
import org.mozilla.tv.firefox.channels.TileSource
import org.mozilla.tv.firefox.ext.serviceLocator
import org.mozilla.tv.firefox.fxa.FxaReceivedTab
import org.mozilla.tv.firefox.navigationoverlay.NavigationEvent
import org.mozilla.tv.firefox.utils.Assert
import org.mozilla.tv.firefox.widget.InlineAutocompleteEditText.AutocompleteResult

private const val SHARED_PREFS_KEY = "telemetryLib" // Don't call it TelemetryWrapper to avoid accidental IDE rename.
private const val KEY_CLICKED_HOME_TILE_IDS_PER_SESSION = "clickedHomeTileIDsPerSession"
private const val KEY_REMOTE_CONTROL_NAME = "remoteControlName"
private const val YOUTUBE_TILE_ID = "youtube"

@Suppress(
        // Yes, this a large class with a lot of functions. But it's very simple and still easy to read.
        "TooManyFunctions",
        "LargeClass"
)
open class TelemetryIntegration protected constructor(
    private val sentryIntegration: SentryIntegration = SentryIntegration
) {

    companion object {
        val INSTANCE: TelemetryIntegration by lazy { TelemetryIntegration() }
    }

    private var activeExperimentNames: Set<String> = emptySet()
    private var sessionActive: Boolean = false

    private object Category {
        const val ACTION = "action"
        const val AGGREGATE = "aggregate"
        const val ERROR = "error"
    }

    private object Method {
        const val TYPE_URL = "type_url"
        const val TYPE_QUERY = "type_query"
        const val CLICK = "click"
        const val CLICK_OR_VOICE = "click_or_voice"
        const val CHANGE = "change"
        const val FOREGROUND = "foreground"
        const val BACKGROUND = "background"
        const val USER_SHOW = "user_show"
        const val PAGE = "page"
        const val RESOURCE = "resource"
        const val REMOVE = "remove"
        const val NO_ACTION_TAKEN = "no_action_taken"
        const val YOUTUBE_CAST = "youtube_cast"
        const val VIEW_INTENT = "view_intent"
        const val IMPRESSION = "impression"
        const val PROGRAMMATICALLY_CLOSED = "programmatically_closed"
        const val RECEIVED_TAB = "received_tab"
    }

    private object Object {
        const val SEARCH_BAR = "search_bar"
        const val SETTING = "setting"
        const val APP = "app"
        const val MENU = "menu"
        const val BROWSER = "browser"
        const val HOME_TILE = "home_tile"
        const val TURBO_MODE = "turbo_mode"
        const val PIN_PAGE = "pin_page"
        const val MEDIA_SESSION = "media_session"
        const val DESKTOP_MODE = "desktop_mode"
        const val VIDEO_ID = "video_id"
        const val FULL_SCREEN_VIDEO = "full_screen_video"
        const val FXA = "fxa"
    }

    internal object Value {
        const val URL = "url"
        const val RELOAD = "refresh"
        const val CLEAR_DATA = "clear_data"
        const val BACK = "back"
        const val FORWARD = "forward"
        const val ON = "on"
        const val OFF = "off"
        const val TILE_BUNDLED = "bundled"
        const val TILE_CUSTOM = "custom"
        const val YOUTUBE_TILE = "youtube_tile"
        const val EXIT_FIREFOX = "exit"
        const val SETTINGS_CLEAR_DATA_TILE = "clear_data_tile"
        const val SETTINGS_SEND_DATA_TILE = "send_data_tile"
        const val SETTINGS_ABOUT_TILE = "about_tile"
        const val SETTINGS_PRIVACY_TILE = "privacy_tile"
        const val FXA_LOGIN_BUTTON = "fxa_login_button"
        const val FXA_SHOW_PROFILE_BUTTON = "fxa_show_profile_button"
        const val FXA_GET_TABS_BUTTON = "fxa_get_tabs_button"
        const val FXA_SIGN_OUT_BUTTON = "fxa_sign_out_button"
        const val FXA_REAUTHENTICATE_BUTTON = "fxa_reauthenticate_button"
        const val FXA_NEEDS_REAUTHENTICATION = "fxa_needs_reauthentication"
        const val FXA_SHOW_ONBOARDING = "fxa_show_onboarding"
        const val FXA_PREBOARDING_NOT_NOW = "fxa_preboarding_not_now2"
        const val FXA_PREBOARDING_SIGN_IN = "fxa_preboarding_sign_in"
        const val FXA_LOGGED_IN = "fxa_logged_in"
        const val FXA_LOGGED_OUT = "fxa_logged_out"
    }

    private object Extra {
        const val TOTAL = "total"
        const val AUTOCOMPLETE = "autocomplete"
        const val SOURCE = "source"
        const val ERROR_CODE = "error_code"
        const val DEVICE_TYPE = "device_type"

        // We need this second source key because we use SOURCE when using this key.
        // For the const value, "autocomplete_source" exceeds max extra key length.
        const val AUTOCOMPLETE_SOURCE = "autocompl_src"
        const val TILE_ID = "tile_id"
        const val BOOLEAN = "boolean"
    }

    private fun recordTelemetryEvent(
        name: String,
        source: String? = null,
        autocomplete: String? = null,
        autocomplSrc: String? = null,
        total: String? = null,
        tileId: String? = null,
        enabled: String? = null,
        deviceType: String? = null,
        errorCode: String? = null,
        detail: String? = null
    ) {
        TelemetryMetrics.telemetryEvent.record(
            TelemetryMetrics.TelemetryEventExtra(
                name = name,
                source = source,
                autocomplete = autocomplete,
                autocomplSrc = autocomplSrc,
                total = total,
                tileId = tileId,
                enabled = enabled,
                deviceType = deviceType,
                errorCode = errorCode,
                detail = detail
            )
        )
    }

    fun init(context: Context) {
        // No-op; Glean is initialized by FirefoxApplication.
    }

    val clientId: String
        get() = ""

    @UiThread // via TelemetryHomeTileUniqueClickPerSessionCounter
    fun startSession(context: Context) {
        recordTelemetryEvent("session_start")
        sessionActive = true

        // We call reset in both startSession and stopSession. We call it here to make sure we
        // clean up before a new session if we crashed before stopSession.
        resetSessionMeasurements(context)
    }

    @UiThread // via TelemetryHomeTileUniqueClickPerSessionCounter
    fun stopSession(context: Context) {
        if (!sessionActive) {
            sentryIntegration.capture(IllegalStateException("Telemetry#stopSession called when no session was active"))
        }

        recordTelemetryEvent("session_stop")

        // We call reset in both startSession and stopSession. We call it here to make sure we
        // don't persist the user's visited tile history on disk longer than strictly necessary.
        queueSessionMeasurements(context)
        resetSessionMeasurements(context)
        sessionActive = false
    }

    private fun queueSessionMeasurements(context: Context) {
        TelemetryHomeTileUniqueClickPerSessionCounter.queueEvent(context)
    }

    private fun resetSessionMeasurements(context: Context) {
        TelemetryHomeTileUniqueClickPerSessionCounter.resetSessionData(context)
        TelemetryRemoteControlTracker.resetSessionData(context)
    }

    fun stopMainActivity() {
        // Glean submits the events ping automatically when the application becomes inactive.
    }

    fun urlBarEvent(isUrl: Boolean, autocompleteResult: AutocompleteResult, inputLocation: UrlTextInputLocation) {
        if (isUrl) {
            TelemetryIntegration.INSTANCE.browseEvent(autocompleteResult, inputLocation)
        } else {
            TelemetryIntegration.INSTANCE.searchEnterEvent(inputLocation)
        }
    }

    private fun browseEvent(autocompleteResult: AutocompleteResult, inputLocation: UrlTextInputLocation) {
        recordTelemetryEvent(
            "url_entered",
            source = inputLocation.extra,
            autocomplete = (!autocompleteResult.isEmpty).toString(),
            total = if (!autocompleteResult.isEmpty) autocompleteResult.totalItems.toString() else null,
            autocomplSrc = if (!autocompleteResult.isEmpty) autocompleteResult.source else null
        )
    }

    private fun searchEnterEvent(inputLocation: UrlTextInputLocation) {
        // SearchEngineManager removed in 128.x - search engine identifier unavailable.
        recordTelemetryEvent(
            "search_query",
            source = inputLocation.extra,
            detail = ""
        )
    }

    fun sslErrorEvent(fromPage: Boolean, error: SslError) {
        // SSL Errors from https://developer.android.com/reference/android/net/http/SslError.html
        val primaryErrorMessage = when (error.primaryError) {
            SslError.SSL_DATE_INVALID -> "SSL_DATE_INVALID"
            SslError.SSL_EXPIRED -> "SSL_EXPIRED"
            SslError.SSL_IDMISMATCH -> "SSL_IDMISMATCH"
            SslError.SSL_NOTYETVALID -> "SSL_NOTYETVALID"
            SslError.SSL_UNTRUSTED -> "SSL_UNTRUSTED"
            SslError.SSL_INVALID -> "SSL_INVALID"
            else -> "Undefined SSL Error"
        }
        recordTelemetryEvent(
            if (fromPage) "ssl_error_page" else "ssl_error_resource",
            errorCode = primaryErrorMessage
        )
    }

    fun fullScreenVideoProgrammaticallyClosed() {
        recordTelemetryEvent("full_screen_video_programmatically_closed")
    }

    @UiThread // via TelemetryHomeTileUniqueClickPerSessionCounter
    fun homeTileClickEvent(context: Context, tile: ChannelTile) {
        if (tile.id == YOUTUBE_TILE_ID) {
            recordTelemetryEvent("home_tile_click", detail = Value.YOUTUBE_TILE)
        }

        val tileType = getTileTypeAsStringValue(tile)
        if (tileType == Value.TILE_BUNDLED) {
            recordTelemetryEvent(
                "home_tile_click",
                tileId = tile.id,
                detail = tileType
            )
        } else {
            recordTelemetryEvent("home_tile_click", detail = tileType)
        }
        TelemetryHomeTileUniqueClickPerSessionCounter.countTile(context, tile)
    }

    internal fun homeTileUniqueClickCountPerSessionEvent(uniqueClickCountPerSession: Int) {
        recordTelemetryEvent(
            "home_tile_unique_click_count",
            total = uniqueClickCountPerSession.toString()
        )
    }

    fun clearDataEvent() {
        recordTelemetryEvent("clear_data")
    }

    fun settingsTileClickEvent(tile: SettingsTile) {
        val telemetryValue = when (tile) {
            SettingsScreen.DATA_COLLECTION -> Value.SETTINGS_SEND_DATA_TILE
            SettingsScreen.CLEAR_COOKIES -> Value.SETTINGS_CLEAR_DATA_TILE
            SettingsScreen.ABOUT -> Value.SETTINGS_ABOUT_TILE
            SettingsButton.PRIVACY_POLICY -> Value.SETTINGS_PRIVACY_TILE
            else -> null
        }
        recordTelemetryEvent("settings_tile_click", detail = telemetryValue)
    }

    fun fxaLoginButtonClickEvent() {
        recordTelemetryEvent("fxa_login_button_click", detail = Value.FXA_LOGIN_BUTTON)
    }

    fun fxaReauthorizeButtonClickEvent() {
        recordTelemetryEvent("fxa_reauthorize_button_click", detail = Value.FXA_REAUTHENTICATE_BUTTON)
    }

    fun fxaShowProfileButtonClickEvent() {
        recordTelemetryEvent("fxa_show_profile_button_click", detail = Value.FXA_SHOW_PROFILE_BUTTON)
    }

    fun fxaProfileShowOnboardingButtonClickEvent() {
        recordTelemetryEvent("fxa_profile_get_tabs_button_click", detail = Value.FXA_GET_TABS_BUTTON)
    }

    fun fxaShowOnboardingEvent() {
        recordTelemetryEvent("fxa_show_onboarding", detail = Value.FXA_SHOW_ONBOARDING)
    }

    fun fxaProfileSignOutButtonClickEvent() {
        recordTelemetryEvent("fxa_profile_sign_out_button_click", detail = Value.FXA_SIGN_OUT_BUTTON)
    }

    fun doesFxaNeedReauthenticationEvent(boolean: Boolean) {
        recordTelemetryEvent(
            "fxa_needs_reauthentication",
            enabled = boolean.toString()
        )
    }

    fun fxaPreboardingSignInButtonClickEvent() {
        recordTelemetryEvent("fxa_preboarding_sign_in_button_click", detail = Value.FXA_PREBOARDING_SIGN_IN)
    }

    fun fxaPreboardingDismissButtonClickEvent() {
        recordTelemetryEvent("fxa_preboarding_dismiss_button_click", detail = Value.FXA_PREBOARDING_NOT_NOW)
    }

    fun fxaLoggedInEvent() {
        recordTelemetryEvent("fxa_logged_in")
    }

    fun fxaLoggedOutEvent() {
        recordTelemetryEvent("fxa_logged_out")
    }

    /**
     * User presses menu button to open overlay (i.e. if menu closes overlay, this isn't counted).
     */
    fun menuOpenedFromMenuButton() {
        recordTelemetryEvent("menu_opened_from_button")
    }

    /**
     * This event is sent when a user opens the menu and then closes it without
     * appearing to find what they are looking for.
     *
     * See [MenuInteractionMonitor] kdoc for more information.
     */
    fun menuUnusedEvent() {
        recordTelemetryEvent("menu_unused")
    }

    fun overlayClickEvent(
        event: NavigationEvent,
        isTurboButtonChecked: Boolean,
        isPinButtonChecked: Boolean,
        isDesktopModeButtonChecked: Boolean
    ) {
        when (event) {
            NavigationEvent.BACK -> recordTelemetryEvent("overlay_back_click")
            NavigationEvent.FORWARD -> recordTelemetryEvent("overlay_forward_click")
            NavigationEvent.RELOAD -> recordTelemetryEvent("overlay_reload_click")
            NavigationEvent.EXIT_FIREFOX -> recordTelemetryEvent("overlay_exit_click")

            // For legacy reasons, turbo has different telemetry params so we special case it.
            // Pin has a similar state change so we model it after turbo.
            NavigationEvent.TURBO -> {
                recordTelemetryEvent(
                    "turbo_mode_change",
                    enabled = boolToOnOff(isTurboButtonChecked)
                )
            }
            NavigationEvent.PIN_ACTION -> {
                recordTelemetryEvent(
                    "pin_page_change",
                    enabled = boolToOnOff(isPinButtonChecked),
                    detail = boolToOnOff(isDesktopModeButtonChecked)
                )
            }
            NavigationEvent.DESKTOP_MODE -> {
                recordTelemetryEvent(
                    "desktop_mode_change",
                    enabled = boolToOnOff(isDesktopModeButtonChecked)
                )
            }

            // Settings telemetry handled in a separate event
            NavigationEvent.SETTINGS_DATA_COLLECTION, NavigationEvent.SETTINGS_CLEAR_COOKIES, NavigationEvent.SETTINGS_ABOUT -> return

            // Load is handled in a separate event
            NavigationEvent.LOAD_URL, NavigationEvent.LOAD_TILE -> return

            NavigationEvent.FXA_BUTTON -> return // TODO: #2512 add telemetry for FxA login.
        }
    }

    /** The browser goes back from a controller press. */
    fun browserBackControllerEvent() {
        recordTelemetryEvent("browser_back_controller", source = "controller")
    }

    fun homeTileRemovedEvent(removedTile: ChannelTile) {
        recordTelemetryEvent(
            "home_tile_removed",
            detail = getTileTypeAsStringValue(removedTile)
        )
    }

    fun mediaSessionEvent(eventType: MediaSessionEventType) {
        recordTelemetryEvent("media_session_event", detail = eventType.value)
    }

    private fun boolToOnOff(boolean: Boolean) = if (boolean) Value.ON else Value.OFF

    private fun getTileTypeAsStringValue(tile: ChannelTile) = when (tile.tileSource) {
        TileSource.BUNDLED -> Value.TILE_BUNDLED
        TileSource.CUSTOM -> Value.TILE_CUSTOM
        TileSource.NEWS -> Value.TILE_BUNDLED
        TileSource.SPORTS -> Value.TILE_BUNDLED
        TileSource.MUSIC -> Value.TILE_BUNDLED
    }

    fun youtubeCastEvent() = recordTelemetryEvent("youtube_cast")

    @UiThread
    fun saveRemoteControlInformation(context: Context, keyEvent: KeyEvent) =
            TelemetryRemoteControlTracker.saveRemoteControlInformation(context, keyEvent)

    fun viewIntentEvent() = recordTelemetryEvent("view_intent")

    fun recordActiveExperiments(experimentNames: List<String>) {
        val newActiveExperiments = experimentNames.toSet()

        activeExperimentNames
            .subtract(newActiveExperiments)
            .forEach { experimentName ->
                Glean.setExperimentInactive(experimentName.substringBefore(':'))
            }

        newActiveExperiments.forEach { experimentName ->
            val experimentId = experimentName.substringBefore(':')
            val branch = experimentName.substringAfter(':', "active")
            Glean.setExperimentActive(
                experimentId = experimentId,
                branch = branch,
                extra = emptyMap()
            )
        }

        activeExperimentNames = newActiveExperiments
    }

    fun receivedTabEvent(metadata: FxaReceivedTab.Metadata) {
        val internalDeviceType = when (metadata.deviceType) {
            DeviceType.DESKTOP -> ReceivedTabDeviceType.DESKTOP
            DeviceType.MOBILE -> ReceivedTabDeviceType.MOBILE
            DeviceType.TABLET -> ReceivedTabDeviceType.TABLET
            DeviceType.TV -> ReceivedTabDeviceType.TV
            DeviceType.VR -> ReceivedTabDeviceType.VR

            DeviceType.UNKNOWN -> ReceivedTabDeviceType.UNKNOWN
        }.extra

        recordTelemetryEvent(
            "received_tab",
            deviceType = internalDeviceType,
            total = metadata.receivedUrlCount.toString()
        )
    }
}

enum class MediaSessionEventType(internal val value: String) {
    PLAY("play"), PAUSE("pause"),
    NEXT("next"), PREV("prev"),
    SEEK("seek"),

    PLAY_PAUSE_BUTTON("play_pause_btn")
}

enum class UrlTextInputLocation(internal val extra: String) {
    // We hardcode the Strings so we can change the enum without changing the sent telemetry values.
    HOME("home"),
    MENU("menu"),
}

private enum class ReceivedTabDeviceType(val extra: String) {
    // We hardcode the Strings so we can change the enum without changing the sent telemetry values.
    DESKTOP("desktop"),
    MOBILE("mobile"),
    TABLET("tablet"),
    TV("tv"),
    VR("vr"),

    UNKNOWN("unknown")
}

/** Counts the number of unique home tiles per session the user has clicked on. */
@UiThread // We get-and-set over SharedPreferences in countTile so we need resource protection.
private object TelemetryHomeTileUniqueClickPerSessionCounter {

    fun countTile(context: Context, tile: ChannelTile) {
        Assert.isUiThread()
        if (context.serviceLocator.settingsRepo.dataCollectionEnabled.value != true) { return }

        val sharedPrefs = getSharedPrefs(context)
        val clickedTileIDs = (sharedPrefs.getStringSet(KEY_CLICKED_HOME_TILE_IDS_PER_SESSION, null)
                ?: setOf()).toMutableSet() // create a copy: we can't modify a SharedPref StringSet.
        val wasNewTileAdded = clickedTileIDs.add(tile.id)

        if (wasNewTileAdded) {
            sharedPrefs.edit()
                    .putStringSet(KEY_CLICKED_HOME_TILE_IDS_PER_SESSION, clickedTileIDs)
                    .apply()
        }
    }

    fun queueEvent(context: Context) {
        Assert.isUiThread()

        val uniqueClickCount = getSharedPrefs(context)
                .getStringSet(KEY_CLICKED_HOME_TILE_IDS_PER_SESSION, null)?.size ?: 0
        TelemetryIntegration.INSTANCE.homeTileUniqueClickCountPerSessionEvent(uniqueClickCount)
    }

    fun resetSessionData(context: Context) {
        Assert.isUiThread()
        getSharedPrefs(context).edit()
                .remove(KEY_CLICKED_HOME_TILE_IDS_PER_SESSION)
                .apply()
    }
}

/** Tracks the name of the remote control used. Only the first remote used per session is reported.
 * If the remote name returns as null, we save as "null". This way, we can also determine how many
 * users use remotes that are not recognized. */
@UiThread // We get-and-set over SharedPreferences so we need resource protection.
private object TelemetryRemoteControlTracker {

    fun saveRemoteControlInformation(context: Context, keyEvent: KeyEvent) {
        Assert.isUiThread()
        if (context.serviceLocator.settingsRepo.dataCollectionEnabled.value != true) { return }

        val remoteName = InputDevice.getDevice(keyEvent.deviceId)?.name ?: "null"
        val sharedPrefs = getSharedPrefs(context)

        if (!sharedPrefs.contains(KEY_REMOTE_CONTROL_NAME)) {
            sharedPrefs.edit()
                    .putString(KEY_REMOTE_CONTROL_NAME, remoteName)
                    .apply()
        }
    }

    fun resetSessionData(context: Context) {
        Assert.isUiThread()
        getSharedPrefs(context).edit()
                .remove(KEY_REMOTE_CONTROL_NAME)
                .apply()
    }
}

private fun getSharedPrefs(context: Context) = context.getSharedPreferences(SHARED_PREFS_KEY, 0)
