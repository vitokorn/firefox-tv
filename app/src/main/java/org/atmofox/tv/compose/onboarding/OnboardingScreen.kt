/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.atmofox.tv.compose.theme.PhotonBlue50
import org.atmofox.tv.compose.theme.PhotonGrey10
import org.atmofox.tv.compose.theme.TvGray2

/**
 * Onboarding screen for first-run experience.
 *
 * Simplified TV-optimized version with DPad focus support.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Firefox TV",
            fontSize = 32.sp,
            color = PhotonGrey10
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Browse the web safely and privately on your TV.",
            fontSize = 18.sp,
            color = TvGray2
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Get Started",
            fontSize = 18.sp,
            color = PhotonGrey10,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(PhotonBlue50)
                .focusable()
                .clickable(onClick = onFinish)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}
