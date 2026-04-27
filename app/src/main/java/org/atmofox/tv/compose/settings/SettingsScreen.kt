/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose.settings

import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.atmofox.tv.R
import org.atmofox.tv.compose.navigation.SettingsType
import org.atmofox.tv.compose.theme.PhotonBlue50
import org.atmofox.tv.compose.theme.PhotonGrey10
import org.atmofox.tv.compose.theme.PhotonGrey70
import org.atmofox.tv.compose.theme.TvGray2
import org.atmofox.tv.ext.serviceLocator
import mozilla.components.browser.state.state.searchEngines
import org.atmofox.tv.telemetry.TelemetryIntegration
import org.atmofox.tv.utils.BuildConstants
import org.atmofox.tv.utils.URLs

/**
 * Settings screen ported to Compose.
 *
 * Provides TV-optimized settings items with DPad navigation.
 * Currently simplified: data collection toggle, clear cookies, about.
 */
@Composable
fun SettingsScreen(
    settingsType: SettingsType,
    onBack: () -> Unit,
    onNavigateToBrowser: () -> Unit = {},
    onSessionCleared: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val serviceLocator = context.serviceLocator

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(57.dp)
    ) {
        // Title based on settings type
        val title = when (settingsType) {
            SettingsType.COMMON -> "Settings"
            SettingsType.DATA_COLLECTION -> "Data Collection"
            SettingsType.CLEAR_COOKIES -> "Clear Cookies"
            SettingsType.ABOUT -> "About"
            SettingsType.PRIVACY_POLICY -> "Privacy Notice"
            SettingsType.FXA -> "Firefox Account"
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val backInteractionSource = remember { MutableInteractionSource() }
            val isBackFocused by backInteractionSource.collectIsFocusedAsState()
            Image(
                painter = painterResource(R.drawable.ic_tv_back),
                contentDescription = context.getString(R.string.content_description_back),
                modifier = Modifier
                    .width(48.dp)
                    .height(48.dp)
                    .alpha(if (isBackFocused) 1f else 0.85f)
                    .focusable(interactionSource = backInteractionSource)
                    .clickable(
                        interactionSource = backInteractionSource,
                        indication = null,
                        onClick = onBack
                    )
                    .then(
                        if (isBackFocused) {
                            Modifier.border(
                                BorderStroke(2.dp, SolidColor(PhotonBlue50)),
                                MaterialTheme.shapes.small
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(12.dp)
            )
            Spacer(modifier = Modifier.width(32.dp))
            Text(
                text = title,
                fontSize = 32.sp,
                color = PhotonGrey10
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            when (settingsType) {
                SettingsType.COMMON -> {
                    val commonFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { commonFocus.requestFocus() }
                    val isEnabled by serviceLocator.settingsRepo.dataCollectionEnabled.collectAsState()
                    ToggleItem(
                        modifier = Modifier.focusRequester(commonFocus),
                        title = "Send usage data",
                        subtitle = context.getString(R.string.settings_telemetry_description, context.getString(R.string.firefox_tv_brand_name)),
                        checked = isEnabled,
                        onCheckedChange = {
                            serviceLocator.settingsRepo.setDataCollectionEnabled(it)
                            TelemetryIntegration.INSTANCE.settingsTileClickEvent(
                                org.atmofox.tv.channels.SettingsScreen.DATA_COLLECTION
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val selectedSearchId by serviceLocator.settingsRepo.selectedSearchEngineId.collectAsState()
                    val engines = serviceLocator.store.state.search.searchEngines
                    val currentEngine = engines.find { it.id == selectedSearchId }
                    var showSearchEngineDialog by remember { mutableStateOf(false) }

                    SettingItem(
                        title = "Default search engine",
                        subtitle = currentEngine?.name ?: "Google",
                        onClick = { showSearchEngineDialog = true }
                    )

                    if (showSearchEngineDialog) {
                        val dialogFocus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { dialogFocus.requestFocus() }
                        AlertDialog(
                            onDismissRequest = { showSearchEngineDialog = false },
                            title = { Text("Select search engine") },
                            text = {
                                Column {
                                    engines.forEach { engine ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .selectable(
                                                    selected = engine.id == selectedSearchId,
                                                    onClick = {
                                                        serviceLocator.settingsRepo.setSelectedSearchEngineId(engine.id)
                                                        serviceLocator.store.dispatch(
                                                            mozilla.components.browser.state.action.SearchAction.SelectSearchEngineAction(
                                                                engine.id,
                                                                engine.name
                                                            )
                                                        )
                                                        showSearchEngineDialog = false
                                                    }
                                                )
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = engine.id == selectedSearchId,
                                                onClick = null
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(engine.name, fontSize = 18.sp)
                                        }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                DialogButton(
                                    title = context.getString(R.string.action_cancel),
                                    modifier = Modifier.focusRequester(dialogFocus),
                                    onClick = { showSearchEngineDialog = false }
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    var showClearDialog by remember { mutableStateOf(false) }
                    SettingItem(
                        title = "Clear all cookies and site data",
                        subtitle = context.getString(R.string.settings_cookies_dialog_content2),
                        onClick = { showClearDialog = true }
                    )

                    if (showClearDialog) {
                        val confirmFocusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { confirmFocusRequester.requestFocus() }
                        AlertDialog(
                            onDismissRequest = { showClearDialog = false },
                            title = { Text(context.getString(R.string.settings_cookies_dialog_title)) },
                            text = { Text(context.getString(R.string.settings_cookies_dialog_content2)) },
                            confirmButton = {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    DialogButton(
                                        title = context.getString(R.string.action_cancel),
                                        modifier = Modifier.weight(1f),
                                        onClick = { showClearDialog = false }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    DialogButton(
                                        title = context.getString(R.string.settings_cookies_confirm),
                                        modifier = Modifier.focusRequester(confirmFocusRequester).weight(1f),
                                        onClick = {
                                            serviceLocator.sessionRepo.clearBrowsingData(
                                                serviceLocator.engineViewCache
                                            )
                                            showClearDialog = false
                                            onBack()
                                            onSessionCleared()
                                        }
                                    )
                                }
                            },
                            dismissButton = {}
                        )
                    }
                }
                SettingsType.DATA_COLLECTION -> {
                    val dataFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { dataFocus.requestFocus() }
                    val isEnabled by serviceLocator.settingsRepo.dataCollectionEnabled.collectAsState()
                    SettingItem(
                        modifier = Modifier.focusRequester(dataFocus),
                        title = "Send usage data",
                        subtitle = if (isEnabled) "Currently enabled" else "Currently disabled",
                        onClick = {
                            serviceLocator.settingsRepo.setDataCollectionEnabled(!isEnabled)
                            TelemetryIntegration.INSTANCE.settingsTileClickEvent(
                                org.atmofox.tv.channels.SettingsScreen.DATA_COLLECTION
                            )
                        }
                    )
                }
                SettingsType.CLEAR_COOKIES -> {
                    val clearFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { clearFocus.requestFocus() }
                    var showClearDialog by remember { mutableStateOf(false) }
                    SettingItem(
                        modifier = Modifier.focusRequester(clearFocus),
                        title = "Clear all cookies",
                        subtitle = "Removes site data and cookies",
                        onClick = { showClearDialog = true }
                    )

                    if (showClearDialog) {
                        val confirmFocusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { confirmFocusRequester.requestFocus() }
                        AlertDialog(
                            onDismissRequest = { showClearDialog = false },
                            title = { Text(context.getString(R.string.settings_cookies_dialog_title)) },
                            text = { Text(context.getString(R.string.settings_cookies_dialog_content2)) },
                            confirmButton = {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    DialogButton(
                                        title = context.getString(R.string.action_cancel),
                                        modifier = Modifier.weight(1f),
                                        onClick = { showClearDialog = false }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    DialogButton(
                                        title = context.getString(R.string.settings_cookies_confirm),
                                        modifier = Modifier.focusRequester(confirmFocusRequester).weight(1f),
                                        onClick = {
                                            serviceLocator.sessionRepo.clearBrowsingData(
                                                serviceLocator.engineViewCache
                                            )
                                            showClearDialog = false
                                            onBack()
                                            onSessionCleared()
                                        }
                                    )
                                }
                            },
                            dismissButton = {}
                        )
                    }
                }
                SettingsType.ABOUT -> {
                    val aboutFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { aboutFocus.requestFocus() }
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val versionName = packageInfo?.versionName ?: ""

                    // Version row (legacy style)
                    val engineVersion = BuildConstants.getEngineVersion(context)
                    SettingItem(
                        modifier = Modifier.focusRequester(aboutFocus),
                        title = context.getString(R.string.firefox_tv_brand_name),
                        subtitle = "Version $versionName (Build #$engineVersion)",
                        onClick = {}
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Open source licenses row (legacy style)
                    SettingItem(
                        title = "Open source licenses",
                        subtitle = "View license information",
                        onClick = {
                            serviceLocator.sessionUseCases.loadUrl.invoke(URLs.URL_LICENSES)
                            onNavigateToBrowser()
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Your Rights section from old about.html
                    val brand = context.getString(R.string.firefox_tv_brand_name)
                    val rights1 = context.getString(R.string.your_rights_content1, brand)
                    val rights2 = context.getString(R.string.your_rights_content2, brand, "https://www.mozilla.org/MPL/")
                    val rights3 = context.getString(R.string.your_rights_content3, brand, "https://www.mozilla.org/foundation/trademarks/policy/")
                    val rights4 = context.getString(R.string.your_rights_content4, brand, URLs.URL_LICENSES)
                    val rights5 = context.getString(R.string.your_rights_content5, brand, "https://www.gnu.org/licenses/gpl-3.0.html", "https://wiki.mozilla.org/Security/Tracking_protection#Lists")

                    Text(
                        text = context.getString(R.string.your_rights),
                        fontSize = 18.sp,
                        color = PhotonGrey10,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    HtmlLinkText(html = rights1, modifier = Modifier.padding(bottom = 12.dp))
                    HtmlLinkText(html = rights2, modifier = Modifier.padding(bottom = 12.dp))
                    HtmlLinkText(html = rights3, modifier = Modifier.padding(bottom = 12.dp))
                    HtmlLinkText(html = rights4, modifier = Modifier.padding(bottom = 12.dp))
                    HtmlLinkText(html = rights5)
                }
                SettingsType.PRIVACY_POLICY -> {
                    val privacyFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { privacyFocus.requestFocus() }
                    SettingItem(
                        modifier = Modifier.focusRequester(privacyFocus),
                        title = "Privacy Notice",
                        subtitle = "Opens at mozilla.org/privacy",
                        onClick = {
                            serviceLocator.sessionUseCases.loadUrl.invoke(URLs.PRIVACY_NOTICE_URL)
                            onNavigateToBrowser()
                        }
                    )
                }
                SettingsType.FXA -> {
                    val fxaFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { fxaFocus.requestFocus() }
                    val accountState by serviceLocator.fxaRepo.accountState.collectAsState()
                    when (accountState) {
                        is org.atmofox.tv.fxa.FxaRepo.AccountState.AuthenticatedWithProfile -> {
                            val profile = (accountState as org.atmofox.tv.fxa.FxaRepo.AccountState.AuthenticatedWithProfile).profile
                            SettingItem(
                                modifier = Modifier.focusRequester(fxaFocus),
                                title = profile.displayName,
                                subtitle = "Manage Firefox Account",
                                onClick = { /* no-op */ }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingItem(
                                title = "Sign out",
                                subtitle = "Disconnect from Firefox Account",
                                onClick = {
                                    serviceLocator.fxaRepo.logout()
                                    TelemetryIntegration.INSTANCE.fxaProfileSignOutButtonClickEvent()
                                    onBack()
                                }
                            )
                        }
                        is org.atmofox.tv.fxa.FxaRepo.AccountState.AuthenticatedNoProfile -> {
                            SettingItem(
                                modifier = Modifier.focusRequester(fxaFocus),
                                title = "Signed in",
                                subtitle = "Manage Firefox Account",
                                onClick = { /* no-op */ }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingItem(
                                title = "Sign out",
                                subtitle = "Disconnect from Firefox Account",
                                onClick = {
                                    serviceLocator.fxaRepo.logout()
                                    TelemetryIntegration.INSTANCE.fxaProfileSignOutButtonClickEvent()
                                    onBack()
                                }
                            )
                        }
                        is org.atmofox.tv.fxa.FxaRepo.AccountState.NeedsReauthentication -> {
                            SettingItem(
                                modifier = Modifier.focusRequester(fxaFocus),
                                title = "Sign in again",
                                subtitle = "Your account needs re-authentication",
                                onClick = {
                                    serviceLocator.fxaLoginUseCase.beginLogin()
                                    TelemetryIntegration.INSTANCE.fxaReauthorizeButtonClickEvent()
                                }
                            )
                        }
                        is org.atmofox.tv.fxa.FxaRepo.AccountState.NotAuthenticated,
                        is org.atmofox.tv.fxa.FxaRepo.AccountState.Initial -> {
                            SettingItem(
                                modifier = Modifier.focusRequester(fxaFocus),
                                title = "Sign in",
                                subtitle = "Connect to Firefox Account",
                                onClick = {
                                    serviceLocator.fxaLoginUseCase.beginLogin()
                                    TelemetryIntegration.INSTANCE.fxaLoginButtonClickEvent()
                                }
                            )
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun SettingItem(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val alpha = if (isFocused) 1f else 0.85f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .background(PhotonGrey70)
            .then(
                if (isFocused) {
                    Modifier.border(
                        BorderStroke(2.dp, SolidColor(PhotonBlue50)),
                        MaterialTheme.shapes.small
                    )
                } else {
                    Modifier
                }
            )
            .padding(16.dp)
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            color = PhotonGrey10
        )
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = TvGray2
        )
    }
}

@Composable
private fun ToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val alpha = if (isFocused) 1f else 0.85f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .background(PhotonGrey70)
            .then(
                if (isFocused) {
                    Modifier.border(
                        BorderStroke(2.dp, SolidColor(PhotonBlue50)),
                        MaterialTheme.shapes.small
                    )
                } else {
                    Modifier
                }
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 18.sp,
                color = PhotonGrey10
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = TvGray2
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun HtmlLinkText(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(0xFFA0A0A3.toInt())
                textSize = 14f
                setLineSpacing(0f, 1.5f)
            }
        },
        update = { it.text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY) },
        modifier = modifier
    )
}

@Composable
private fun DialogButton(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val alpha = if (isFocused) 1f else 0.85f

    Column(
        modifier = modifier
            .alpha(alpha)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .background(PhotonGrey70)
            .then(
                if (isFocused) {
                    Modifier.border(
                        BorderStroke(2.dp, SolidColor(PhotonBlue50)),
                        MaterialTheme.shapes.small
                    )
                } else {
                    Modifier
                }
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            color = PhotonGrey10
        )
    }
}
