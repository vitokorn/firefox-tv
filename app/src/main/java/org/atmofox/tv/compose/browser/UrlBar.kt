/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.atmofox.tv.R
import org.atmofox.tv.compose.theme.Ink80
import org.atmofox.tv.compose.theme.PhotonBlue50
import org.atmofox.tv.compose.theme.PhotonGrey10
import org.atmofox.tv.compose.theme.PhotonGrey40
import org.atmofox.tv.compose.theme.TvGray2
import org.atmofox.tv.compose.utils.collectAsState
import org.atmofox.tv.ext.serviceLocator
import org.atmofox.tv.utils.URLs
import org.atmofox.tv.utils.UrlUtils

/**
 * URL bar styled like the legacy nav_urlbar_background.
 *
 * Dark rounded rectangle, search icon on the left, near-white text,
 * 48dp height matching the navigation buttons.
 */
@Composable
fun UrlBar(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionRepo = context.serviceLocator.sessionRepo
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

    val displayUrl = UrlUtils.toUrlBarDisplay(state.currentUrl)
    var isFocused by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf("") }

    // Initialize editing buffer when focus is gained
    LaunchedEffect(isFocused) {
        if (isFocused) {
            editedText = displayUrl
        }
    }

    // Use direct displayUrl when not focused (no async delay), edited buffer when focused
    val text = if (isFocused) editedText else displayUrl

    val serviceLocator = context.serviceLocator

    val urlBarBackground = if (isFocused) {
        Modifier.border(2.dp, PhotonBlue50, RoundedCornerShape(4.dp))
    } else {
        Modifier
    }

    BasicTextField(
        value = text,
        onValueChange = { editedText = it },
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(urlBarBackground)
            .background(Ink80, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
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
                if (text.isNotEmpty() && text != URLs.APP_URL_HOME) {
                    val url = if (UrlUtils.isUrl(text)) {
                        text
                    } else {
                        UrlUtils.createSearchUrl(context, text)
                    }
                    serviceLocator.sessionUseCases.loadUrl.invoke(url)
                }
            }
        ),
        decorationBox = { innerTextField ->
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.mozac_ic_search),
                    contentDescription = null,
                    tint = PhotonGrey40,
                    modifier = Modifier.padding(end = 10.dp)
                )
                innerTextField()
            }
        }
    )
}
