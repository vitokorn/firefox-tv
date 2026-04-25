/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalDensity
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
import org.atmofox.tv.compose.theme.PhotonGrey40
import androidx.compose.runtime.collectAsState
import org.atmofox.tv.ext.serviceLocator
import org.atmofox.tv.utils.URLs
import org.atmofox.tv.utils.UrlUtils

/**
 * Single-line browser toolbar matching the legacy Firefox TV toolbar layout.
 *
 * Row: [Back][Forward][Reload] [URL field …………] […] [Home] [FxA] [Logo]
 *
 * The overflow (…) menu opens a popup with Pin, Turbo, and Desktop mode.
 */
@Composable
fun BrowserToolbar(
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val serviceLocator = context.serviceLocator
    val sessionRepo = serviceLocator.sessionRepo
    val pinnedTileRepo = serviceLocator.pinnedTileRepo

    val state by sessionRepo.state.collectAsState()

    val pinnedTiles by pinnedTileRepo.pinnedTiles.collectAsState()
    val isCurrentUrlPinned = pinnedTiles.containsKey(state.currentUrl)
    val isHomepage = state.currentUrl == URLs.APP_URL_HOME ||
            state.currentUrl == "data:text/html,<html></html>" ||
            state.currentUrl.isEmpty()

    val displayUrl = UrlUtils.toUrlBarDisplay(state.currentUrl)
    var isUrlFocused by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf("") }

    // Initialize editing buffer when focus is gained
    LaunchedEffect(isUrlFocused) {
        if (isUrlFocused) {
            editedText = displayUrl
        }
    }

    // Use direct displayUrl when not focused (no async delay), edited buffer when focused
    val urlText = if (isUrlFocused) editedText else displayUrl

    var showOverflow by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 57.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Navigation ──
        TooltipButton(
            iconRes = R.drawable.mozac_ic_back,
            contentDescription = "Back",
            enabled = state.backEnabled,
            onClick = { sessionRepo.attemptBack() }
        )
        TooltipButton(
            iconRes = R.drawable.mozac_ic_forward,
            contentDescription = "Forward",
            enabled = state.forwardEnabled,
            onClick = { sessionRepo.goForward() }
        )
        TooltipButton(
            iconRes = R.drawable.mozac_ic_refresh,
            contentDescription = "Reload",
            enabled = true,
            onClick = { sessionRepo.reload() }
        )

        // ── URL field ──
        val urlBarBackground = if (isUrlFocused) {
            Modifier.border(2.dp, PhotonBlue50, RoundedCornerShape(4.dp))
        } else {
            Modifier
        }

        BasicTextField(
            value = urlText,
            onValueChange = { editedText = it },
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .padding(horizontal = 12.dp)
                .then(urlBarBackground)
                .background(Ink80, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .onFocusChanged { focusState ->
                    isUrlFocused = focusState.isFocused
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (isUrlFocused && keyEvent.type == KeyEventType.KeyDown &&
                        (keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.DirectionRight)
                    ) {
                        true
                    } else if (isUrlFocused && keyEvent.type == KeyEventType.KeyDown &&
                        (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                    ) {
                        if (urlText.isNotEmpty() && urlText != URLs.APP_URL_HOME) {
                            val url = if (UrlUtils.isUrl(urlText)) {
                                urlText
                            } else {
                                UrlUtils.createSearchUrl(context, urlText)
                            }
                            serviceLocator.sessionUseCases.loadUrl.invoke(url)
                        }
                        true
                    } else {
                        false
                    }
                },
            singleLine = true,
            textStyle = TextStyle(
                color = PhotonGrey10,
                fontSize = 16.sp
            ),
            cursorBrush = SolidColor(PhotonGrey10),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    if (urlText.isNotEmpty() && urlText != URLs.APP_URL_HOME) {
                        val url = if (UrlUtils.isUrl(urlText)) {
                            urlText
                        } else {
                            UrlUtils.createSearchUrl(context, urlText)
                        }
                        serviceLocator.sessionUseCases.loadUrl.invoke(url)
                    }
                }
            ),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.mozac_ic_search),
                        contentDescription = null,
                        tint = PhotonGrey40,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(20.dp)
                    )
                    innerTextField()
                }
            }
        )

        // ── Overflow (…) menu ──
        Box {
            TooltipButton(
                iconRes = null,
                labelText = "…",
                contentDescription = "More options",
                enabled = true,
                onClick = { showOverflow = true }
            )

            if (showOverflow) {
                Popup(
                    alignment = Alignment.BottomEnd,
                    offset = IntOffset(0, 4),
                    onDismissRequest = { showOverflow = false },
                    properties = PopupProperties(
                        focusable = true,
                        clippingEnabled = false
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .background(Ink80, RoundedCornerShape(4.dp))
                            .border(1.dp, PhotonBlue50.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pin
                        TooltipButton(
                            iconRes = if (isCurrentUrlPinned) R.drawable.mozac_ic_pin_filled else R.drawable.mozac_ic_pin,
                            contentDescription = if (isCurrentUrlPinned) "Unpin site" else "Pin site",
                            enabled = !isHomepage,
                            checked = isCurrentUrlPinned,
                            onClick = {
                                if (isCurrentUrlPinned) pinnedTileRepo.removePinnedTile(state.currentUrl)
                                else pinnedTileRepo.addPinnedTile(state.currentUrl, null)
                                showOverflow = false
                            }
                        )
                        // Turbo
                        TooltipButton(
                            iconRes = if (state.turboModeActive) R.drawable.mozac_ic_rocket_filled else R.drawable.mozac_ic_rocket,
                            contentDescription = if (state.turboModeActive) "Turbo mode on" else "Turbo mode off",
                            enabled = true,
                            checked = state.turboModeActive,
                            onClick = {
                                sessionRepo.setTurboModeEnabled(!state.turboModeActive, skipEngineSettingsUpdate = isHomepage)
                                if (!isHomepage) sessionRepo.reload()
                                showOverflow = false
                            }
                        )
                        // Desktop
                        TooltipButton(
                            iconRes = R.drawable.mozac_ic_device_desktop,
                            contentDescription = if (state.desktopModeActive) "Desktop mode on" else "Desktop mode off",
                            enabled = !isHomepage,
                            checked = state.desktopModeActive,
                            onClick = {
                                sessionRepo.setDesktopMode(!state.desktopModeActive)
                                showOverflow = false
                            }
                        )
                    }
                }
            }
        }

        // ── Home / Menu ──
        TooltipButton(
            iconRes = R.drawable.ic_home,
            contentDescription = "Home",
            enabled = true,
            onClick = onOpenMenu
        )
    }
}

@Composable
private fun TooltipButton(
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    labelText: String? = null,
    checked: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgColor = when {
        !enabled -> Ink80.copy(alpha = 0.4f)
        checked -> PhotonBlue50.copy(alpha = 0.25f)
        isFocused -> PhotonBlue50.copy(alpha = 0.15f)
        else -> Ink80
    }

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
            when {
                iconRes != null -> {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .size(24.dp)
                            .alpha(if (enabled) 0.9f else 0.3f),
                        tint = PhotonGrey10
                    )
                }
                labelText != null -> {
                    Text(
                        text = labelText,
                        color = PhotonGrey10,
                        fontSize = 20.sp,
                        modifier = Modifier.alpha(if (enabled) 0.9f else 0.3f)
                    )
                }
            }
        }

        // Tooltip popup shown below button without affecting layout
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
