/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose.browser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.atmofox.tv.compose.theme.PhotonBlue50
import org.atmofox.tv.compose.theme.TvGray2
import androidx.compose.runtime.collectAsState
import org.atmofox.tv.ext.serviceLocator
import org.atmofox.tv.utils.URLs
import org.atmofox.tv.utils.UrlUtils

/**
 * Pill-style progress bar matching the legacy FirefoxProgressBar.
 *
 * Positioned at the bottom-left of the browser, showing a loading
 * spinner + current URL text on a semi-transparent rounded background.
 */
@Composable
fun BrowserProgressBar(
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

    if (state.loading) {
        val displayUrl = UrlUtils.stripUserInfo(state.currentUrl) ?: state.currentUrl

        Row(
            modifier = modifier
                .padding(15.dp)
                .background(
                    color = Color(0xD9E3E3E5),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Loading spinner
            val infiniteTransition = rememberInfiniteTransition(label = "progress")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .alpha(alpha)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = PhotonBlue50,
                    strokeWidth = 2.dp
                )
            }

            // URL text
            Text(
                text = displayUrl,
                color = TvGray2,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .width(200.dp)
            )
        }
    }
}
