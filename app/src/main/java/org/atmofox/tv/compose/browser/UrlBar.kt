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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.browser.domains.autocomplete.ShippedDomainsProvider
import org.atmofox.tv.R
import org.atmofox.tv.compose.theme.Ink80
import org.atmofox.tv.compose.theme.PhotonBlue50
import org.atmofox.tv.compose.theme.PhotonGrey10
import org.atmofox.tv.compose.theme.PhotonGrey40
import org.atmofox.tv.compose.theme.TvGray2
import androidx.compose.runtime.collectAsState
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
    onSubmit: () -> Unit = {},
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

    val domainsProvider = remember { ShippedDomainsProvider() }
    var autocompleteResult by remember { mutableStateOf<mozilla.components.concept.toolbar.AutocompleteResult?>(null) }

    LaunchedEffect(Unit) {
        domainsProvider.initialize(context)
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            editedText = displayUrl
            autocompleteResult = null
        }
    }

    LaunchedEffect(editedText, isFocused) {
        if (!isFocused || editedText.isBlank() || UrlUtils.isUrl(editedText)) {
            autocompleteResult = null
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.IO) {
            domainsProvider.getAutocompleteSuggestion(editedText)
        }
        autocompleteResult = result?.takeIf { it.text.startsWith(editedText, ignoreCase = true) }
    }

    // Use direct displayUrl when not focused (no async delay), edited buffer when focused
    val text = if (isFocused) editedText else displayUrl
    val autocompleteSuffix = if (isFocused && autocompleteResult != null) {
        autocompleteResult!!.text.removePrefix(editedText)
    } else ""

    val autocompleteTransformation = remember(autocompleteSuffix) {
        VisualTransformation { original ->
            if (autocompleteSuffix.isEmpty()) {
                TransformedText(buildAnnotatedString { append(original.text) }, OffsetMapping.Identity)
            } else {
                val annotated = buildAnnotatedString {
                    append(original.text)
                    withStyle(SpanStyle(color = PhotonGrey40)) {
                        append(autocompleteSuffix)
                    }
                }
                val mapping = object : OffsetMapping {
                    override fun originalToTransformed(offset: Int): Int = offset.coerceIn(0, original.text.length)
                    override fun transformedToOriginal(offset: Int): Int = offset.coerceIn(0, original.text.length)
                }
                TransformedText(annotated, mapping)
            }
        }
    }

    val serviceLocator = context.serviceLocator

    val urlBarBackground = if (isFocused) {
        Modifier.border(2.dp, PhotonBlue50, RoundedCornerShape(4.dp))
    } else {
        Modifier
    }

    BasicTextField(
        value = text,
        onValueChange = {
            editedText = it
            autocompleteResult = null
        },
        visualTransformation = autocompleteTransformation,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(urlBarBackground)
            .background(Ink80, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .onPreviewKeyEvent { keyEvent ->
                if (isFocused && keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionRight)
                ) {
                    autocompleteResult?.let {
                        editedText = it.text
                        autocompleteResult = null
                    }
                    true
                } else if (isFocused && keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionLeft)
                ) {
                    true
                } else if (isFocused && keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                ) {
                    val submitText = autocompleteResult?.text ?: text
                    if (submitText.isNotEmpty() && submitText != URLs.APP_URL_HOME) {
                        val url = if (UrlUtils.isUrl(submitText)) {
                            submitText
                        } else {
                            UrlUtils.createSearchUrl(context, submitText)
                        }
                        serviceLocator.sessionUseCases.loadUrl.invoke(url)
                        onSubmit()
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
                if (text.isNotEmpty() && text != URLs.APP_URL_HOME) {
                    val url = if (UrlUtils.isUrl(text)) {
                        text
                    } else {
                        UrlUtils.createSearchUrl(context, text)
                    }
                    serviceLocator.sessionUseCases.loadUrl.invoke(url)
                    onSubmit()
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
