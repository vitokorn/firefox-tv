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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.atmofox.tv.R
import org.atmofox.tv.channels.pinnedtile.PinnedTile
import org.atmofox.tv.compose.theme.Ink80
import org.atmofox.tv.compose.theme.PhotonBlue50
import org.atmofox.tv.compose.theme.PhotonGrey10
import org.atmofox.tv.compose.utils.collectAsState
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val serviceLocator = context.serviceLocator
    val sessionRepo = serviceLocator.sessionRepo
    val pinnedTileRepo = serviceLocator.pinnedTileRepo

    val currentState = sessionRepo.currentState()
    val state by sessionRepo.state.collectAsState(
        initial = currentState
            ?: org.atmofox.tv.session.SessionRepo.State(
                backEnabled = false,
                forwardEnabled = false,
                desktopModeActive = false,
                turboModeActive = false,
                currentUrl = URLs.APP_URL_HOME,
                loading = false
            )
    )

    val pinnedTiles by pinnedTileRepo.pinnedTiles.collectAsState(initial = linkedMapOf<String, PinnedTile>())
    val isCurrentUrlPinned = pinnedTiles.containsKey(state.currentUrl)
    val isHomepage = state.currentUrl == URLs.APP_URL_HOME ||
            state.currentUrl == "data:text/html,<html></html>" ||
            state.currentUrl.isEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 57.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Navigation ──
        NavButton(
            iconRes = R.drawable.mozac_ic_back,
            contentDescription = "Back",
            enabled = state.backEnabled,
            onClick = { sessionRepo.attemptBack() }
        )
        NavButton(
            iconRes = R.drawable.mozac_ic_forward,
            contentDescription = "Forward",
            enabled = state.forwardEnabled,
            onClick = { sessionRepo.goForward() }
        )
        NavButton(
            iconRes = R.drawable.mozac_ic_refresh,
            contentDescription = "Reload",
            enabled = true,
            onClick = { sessionRepo.reload() }
        )

        // ── Checkable actions ──
        NavCheckableButton(
            iconResUnchecked = R.drawable.mozac_ic_pin,
            iconResChecked = R.drawable.mozac_ic_pin_filled,
            contentDescription = if (isCurrentUrlPinned) "Unpin site" else "Pin site",
            enabled = !isHomepage,
            checked = isCurrentUrlPinned,
            onClick = {
                if (isCurrentUrlPinned) pinnedTileRepo.removePinnedTile(state.currentUrl)
                else pinnedTileRepo.addPinnedTile(state.currentUrl, null)
            }
        )
        NavCheckableButton(
            iconResUnchecked = R.drawable.mozac_ic_rocket,
            iconResChecked = R.drawable.mozac_ic_rocket_filled,
            contentDescription = if (state.turboModeActive) "Turbo mode on" else "Turbo mode off",
            enabled = true,
            checked = state.turboModeActive,
            onClick = {
                sessionRepo.setTurboModeEnabled(!state.turboModeActive, skipEngineSettingsUpdate = isHomepage)
                if (!isHomepage) sessionRepo.reload()
            }
        )
        NavCheckableButton(
            iconResUnchecked = R.drawable.mozac_ic_device_desktop,
            iconResChecked = R.drawable.mozac_ic_device_desktop,
            contentDescription = if (state.desktopModeActive) "Desktop mode on" else "Desktop mode off",
            enabled = !isHomepage,
            checked = state.desktopModeActive,
            onClick = {
                sessionRepo.setDesktopMode(!state.desktopModeActive)
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

        // ── FxA / Send to Fire TV ──
        NavButton(
            iconRes = R.drawable.ic_fxa_login,
            contentDescription = "Send to Fire TV",
            enabled = true,
            onClick = { /* FxA integration placeholder */ }
        )

        // ── Firefox logo ──
        Icon(
            painter = painterResource(id = R.drawable.ic_atmofox_and_wordmark),
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
            Popup(
                alignment = Alignment.BottomCenter,
                properties = PopupProperties(focusable = false)
            ) {
                Text(
                    text = contentDescription,
                    color = PhotonGrey10,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .padding(top = 52.dp)
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
            Popup(
                alignment = Alignment.BottomCenter,
                properties = PopupProperties(focusable = false)
            ) {
                Text(
                    text = contentDescription,
                    color = PhotonGrey10,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .padding(top = 52.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
