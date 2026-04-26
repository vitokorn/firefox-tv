/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose.browser

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.atmofox.tv.R
import org.atmofox.tv.channels.pinnedtile.PinnedTile
import org.atmofox.tv.compose.theme.Ink80
import org.atmofox.tv.compose.theme.PhotonBlue50
import org.atmofox.tv.compose.theme.PhotonGrey10
import androidx.compose.runtime.collectAsState
import org.atmofox.tv.ext.serviceLocator
import org.atmofox.tv.utils.URLs

/**
 * Top navigation buttons matching the legacy NavigationButton style.
 *
 * Back / Forward / Reload / Pin / Turbo / Desktop / FxA / Exit + Firefox logo.
 * Checkable buttons (Pin, Turbo, Desktop) switch icon based on checked state.
 */
@Composable
fun NavigationControls(
    onOpenMenu: () -> Unit = {},
    observeBrowserState: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val serviceLocator = context.serviceLocator
    val sessionRepo = if (observeBrowserState) serviceLocator.sessionRepo else null
    val pinnedTileRepo = if (observeBrowserState) serviceLocator.pinnedTileRepo else null

    val state by if (sessionRepo != null) {
        sessionRepo.state.collectAsState()
    } else {
        remember {
            mutableStateOf(
                org.atmofox.tv.session.SessionRepo.State(
                    backEnabled = false,
                    forwardEnabled = false,
                    desktopModeActive = false,
                    turboModeActive = false,
                    currentUrl = URLs.APP_URL_HOME,
                    loading = false
                )
            )
        }
    }

    val pinnedTiles by if (pinnedTileRepo != null) {
        pinnedTileRepo.pinnedTiles.collectAsState()
    } else {
        remember { mutableStateOf(emptyMap<String, PinnedTile>()) }
    }
    val isCurrentUrlPinned = pinnedTiles.containsKey(state.currentUrl)
    val isHomepage = state.currentUrl == URLs.APP_URL_HOME ||
            state.currentUrl == "data:text/html,<html></html>" ||
            state.currentUrl.isEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Navigation ──
        NavButton(
            iconRes = R.drawable.mozac_ic_back,
            contentDescription = "Back",
            enabled = sessionRepo != null && state.backEnabled,
            onClick = { sessionRepo?.attemptBack() }
        )
        NavButton(
            iconRes = R.drawable.mozac_ic_forward,
            contentDescription = "Forward",
            enabled = sessionRepo != null && state.forwardEnabled,
            onClick = { sessionRepo?.goForward() }
        )
        NavButton(
            iconRes = R.drawable.mozac_ic_refresh,
            contentDescription = "Reload",
            enabled = sessionRepo != null,
            onClick = { sessionRepo?.reload() }
        )

        // ── Checkable actions ──
        NavCheckableButton(
            iconResUnchecked = R.drawable.mozac_ic_pin,
            iconResChecked = R.drawable.mozac_ic_pin_filled,
            contentDescription = if (isCurrentUrlPinned) "Unpin site" else "Pin site",
            enabled = sessionRepo != null && pinnedTileRepo != null && !isHomepage,
            checked = isCurrentUrlPinned,
            onClick = {
                if (pinnedTileRepo != null) {
                    if (isCurrentUrlPinned) pinnedTileRepo.removePinnedTile(state.currentUrl)
                    else pinnedTileRepo.addPinnedTile(state.currentUrl, null)
                }
            }
        )
        NavCheckableButton(
            iconResUnchecked = R.drawable.mozac_ic_rocket,
            iconResChecked = R.drawable.mozac_ic_rocket_filled,
            contentDescription = if (state.turboModeActive) "Turbo mode on" else "Turbo mode off",
            enabled = sessionRepo != null,
            checked = state.turboModeActive,
            onClick = {
                if (sessionRepo != null) {
                    sessionRepo.setTurboModeEnabled(!state.turboModeActive, skipEngineSettingsUpdate = isHomepage)
                    if (!isHomepage) sessionRepo.reload()
                }
            }
        )
        NavCheckableButton(
            iconResUnchecked = R.drawable.mozac_ic_device_desktop,
            iconResChecked = R.drawable.mozac_ic_device_desktop,
            contentDescription = if (state.desktopModeActive) "Desktop mode on" else "Desktop mode off",
            enabled = sessionRepo != null && !isHomepage,
            checked = state.desktopModeActive,
            onClick = {
                sessionRepo?.setDesktopMode(!state.desktopModeActive)
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        // ── Home / Menu (opens overlay) ──
        NavButton(
            iconRes = R.drawable.ic_home,
            contentDescription = "Home",
            enabled = true,
            onClick = onOpenMenu
        )

        // ── Firefox Account ──
        NavButton(
            iconRes = R.drawable.ic_fxa_login,
            contentDescription = "Firefox Account",
            enabled = true,
            onClick = { serviceLocator.fxaLoginUseCase.beginLogin() }
        )

        // ── Firefox logo ──
        Icon(
            painter = painterResource(id = R.mipmap.ic_launcher),
            contentDescription = "Firefox",
            modifier = Modifier.size(48.dp),
            tint = Color.Unspecified
        )
    }
}

@Composable
private fun NavButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bgColor = when {
        !enabled -> Ink80.copy(alpha = 0.4f)
        isFocused -> PhotonBlue50.copy(alpha = 0.15f)
        else -> Ink80
    }
    val tintAlpha = if (enabled) 0.8f else 0.3f

    Box(modifier = modifier.padding(end = 12.dp)) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(bgColor, RoundedCornerShape(4.dp))
                .border(
                    width = if (isFocused) 4.dp else 0.dp,
                    color = if (isFocused) PhotonBlue50 else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                )
                .clickable(
                    enabled = enabled,
                    onClick = onClick,
                    interactionSource = interactionSource,
                    indication = null
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp).alpha(tintAlpha),
                tint = PhotonGrey10
            )
        }

        if (isFocused) {
            val tooltipOffset = with(LocalDensity.current) { 54.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, tooltipOffset),
                properties = PopupProperties(focusable = false)
            ) {
                Text(
                    text = contentDescription,
                    color = PhotonGrey10,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun NavCheckableButton(
    iconResUnchecked: Int,
    iconResChecked: Int,
    contentDescription: String,
    enabled: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bgColor = when {
        !enabled -> Ink80.copy(alpha = 0.4f)
        checked -> PhotonBlue50.copy(alpha = 0.25f)
        isFocused -> PhotonBlue50.copy(alpha = 0.15f)
        else -> Ink80
    }
    val tintAlpha = if (enabled) 0.8f else 0.3f

    Box(modifier = modifier.padding(end = 12.dp)) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(bgColor, RoundedCornerShape(4.dp))
                .border(
                    width = if (isFocused) 4.dp else 0.dp,
                    color = if (isFocused) PhotonBlue50 else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                )
                .clickable(
                    enabled = enabled,
                    onClick = onClick,
                    interactionSource = interactionSource,
                    indication = null
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = if (checked) iconResChecked else iconResUnchecked),
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp).alpha(tintAlpha),
                tint = PhotonGrey10
            )
        }

        if (isFocused) {
            val tooltipOffset = with(LocalDensity.current) { 54.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, tooltipOffset),
                properties = PopupProperties(focusable = false)
            ) {
                Text(
                    text = contentDescription,
                    color = PhotonGrey10,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
