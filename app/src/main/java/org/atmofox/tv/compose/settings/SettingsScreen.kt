/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose.settings

import android.text.Html
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.atmofox.tv.R
import org.atmofox.tv.compose.navigation.SettingsType
import org.atmofox.tv.compose.theme.PhotonBlue50
import org.atmofox.tv.compose.theme.PhotonGrey10
import org.atmofox.tv.compose.theme.PhotonGrey70
import org.atmofox.tv.compose.theme.TvGray2
import org.atmofox.tv.ext.serviceLocator
import org.atmofox.tv.telemetry.TelemetryIntegration
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
        }

        Text(
            text = title,
            fontSize = 32.sp,
            color = PhotonGrey10
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            when (settingsType) {
                SettingsType.COMMON -> {
                    val isEnabled = serviceLocator.settingsRepo.dataCollectionEnabled.value ?: true
                    ToggleItem(
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
                    SettingItem(
                        title = "Clear all cookies and site data",
                        subtitle = context.getString(R.string.settings_cookies_dialog_content2),
                        onClick = {
                            serviceLocator.sessionRepo.clearBrowsingData(
                                serviceLocator.engineViewCache
                            )
                            onBack()
                        }
                    )
                }
                SettingsType.DATA_COLLECTION -> {
                    val isEnabled = serviceLocator.settingsRepo.dataCollectionEnabled.value ?: true
                    SettingItem(
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
                    SettingItem(
                        title = "Clear all cookies",
                        subtitle = "Removes site data and cookies",
                        onClick = {
                            serviceLocator.sessionRepo.clearBrowsingData(
                                serviceLocator.engineViewCache
                            )
                            onBack()
                        }
                    )
                }
                SettingsType.ABOUT -> {
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val versionName = packageInfo?.versionName ?: ""

                    // Version row (legacy style)
                    SettingItem(
                        title = context.getString(R.string.firefox_tv_brand_name),
                        subtitle = "Version $versionName",
                        onClick = {}
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Open source licenses row (legacy style)
                    SettingItem(
                        title = "Open source licenses",
                        subtitle = "View license information",
                        onClick = {
                            serviceLocator.sessionUseCases.loadUrl.invoke(URLs.URL_LICENSES)
                            onBack()
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Your Rights section from old about.html
                    val brand = context.getString(R.string.firefox_tv_brand_name)
                    val rights1 = remember { Html.fromHtml(context.getString(R.string.your_rights_content1, brand), Html.FROM_HTML_MODE_LEGACY).toString() }
                    val rights2 = remember { Html.fromHtml(context.getString(R.string.your_rights_content2, brand, "https://www.mozilla.org/MPL/"), Html.FROM_HTML_MODE_LEGACY).toString() }
                    val rights3 = remember { Html.fromHtml(context.getString(R.string.your_rights_content3, brand, "https://www.mozilla.org/foundation/trademarks/policy/"), Html.FROM_HTML_MODE_LEGACY).toString() }
                    val rights4 = remember { Html.fromHtml(context.getString(R.string.your_rights_content4, brand, URLs.URL_LICENSES), Html.FROM_HTML_MODE_LEGACY).toString() }
                    val rights5 = remember { Html.fromHtml(context.getString(R.string.your_rights_content5, brand, "https://www.gnu.org/licenses/gpl-3.0.html", "https://wiki.mozilla.org/Security/Tracking_protection#Lists"), Html.FROM_HTML_MODE_LEGACY).toString() }

                    Text(
                        text = context.getString(R.string.your_rights),
                        fontSize = 18.sp,
                        color = PhotonGrey10,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(text = rights1, fontSize = 14.sp, color = TvGray2, modifier = Modifier.padding(bottom = 12.dp))
                    Text(text = rights2, fontSize = 14.sp, color = TvGray2, modifier = Modifier.padding(bottom = 12.dp))
                    Text(text = rights3, fontSize = 14.sp, color = TvGray2, modifier = Modifier.padding(bottom = 12.dp))
                    Text(text = rights4, fontSize = 14.sp, color = TvGray2, modifier = Modifier.padding(bottom = 12.dp))
                    Text(text = rights5, fontSize = 14.sp, color = TvGray2)
                }
                SettingsType.PRIVACY_POLICY -> {
                    SettingItem(
                        title = "Privacy Notice",
                        subtitle = "Opens at mozilla.org/privacy",
                        onClick = {
                            serviceLocator.sessionUseCases.loadUrl.invoke(URLs.PRIVACY_NOTICE_URL)
                            onBack()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Press Back to return",
            fontSize = 14.sp,
            color = TvGray2
        )
    }
}

@Composable
private fun SettingItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusable()
            .clickable(onClick = onClick)
            .background(PhotonGrey70)
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
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusable()
            .clickable { onCheckedChange(!checked) }
            .background(PhotonGrey70)
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
