/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

        when (settingsType) {
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
                SettingItem(
                    title = "Firefox TV",
                    subtitle = "Version $versionName",
                    onClick = {}
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingItem(
                    title = "Open source licenses",
                    subtitle = "View license information",
                    onClick = {}
                )
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
