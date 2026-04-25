/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Firefox TV Photon dark palette matching the original XML theme.
 *
 * Key colours from the legacy codebase:
 * - tv_ink (#0c0c15)        -> background
 * - photonGrey70 (#52525e)  -> overlay / surfaceVariant
 * - photonGrey90 (#0c0c0d)  -> surface / hint bar
 * - ink80 (#333540)         -> surface tint
 * - photonBlue50 (#0060df)  -> primary accent
 * - photonGrey10 (#f9f9fa)  -> primary text
 * - tv_gray2 (#a0a0a3)      -> secondary text
 */
val PhotonBlue50 = Color(0xFF0060DF)
val PhotonBlue60 = Color(0xFF0250BB)
val PhotonGrey10 = Color(0xFFF9F9FA)
val PhotonGrey40 = Color(0xFFB1B1B3)
val PhotonGrey50 = Color(0xFF8F8F9D)
val PhotonGrey70 = Color(0xFF52525E)
val PhotonGrey90 = Color(0xFF0C0C0D)
val Ink80 = Color(0xFF333540)
val TvInk = Color(0xFF0C0C15)
val TvGray2 = Color(0xFFA0A0A3)
val PocketCoral = Color(0xFFEF4056)

private val TvDarkColorScheme = darkColorScheme(
    primary = PhotonBlue50,
    onPrimary = PhotonGrey10,
    primaryContainer = PhotonBlue60,
    onPrimaryContainer = PhotonGrey10,

    secondary = Ink80,
    onSecondary = PhotonGrey10,
    secondaryContainer = PhotonGrey70,
    onSecondaryContainer = PhotonGrey10,

    tertiary = PhotonBlue50,
    onTertiary = PhotonGrey10,
    tertiaryContainer = PhotonBlue60,
    onTertiaryContainer = PhotonGrey10,

    background = TvInk,
    onBackground = PhotonGrey10,

    surface = PhotonGrey90,
    onSurface = PhotonGrey10,
    surfaceVariant = PhotonGrey70,
    onSurfaceVariant = TvGray2,

    error = PocketCoral,
    onError = PhotonGrey10,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    outline = PhotonGrey70,
    inverseSurface = PhotonGrey10,
    inverseOnSurface = TvInk,
    inversePrimary = PhotonBlue50,
    surfaceTint = PhotonBlue50,
)

@Composable
fun FirefoxTvTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
        typography = FirefoxTvTypography,
        content = content
    )
}
