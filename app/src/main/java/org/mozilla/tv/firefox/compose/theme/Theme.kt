/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.compose.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Material3 dark color scheme tuned for TV (10-foot UI) usage.
 *
 * Based on the existing Firefox TV palette:
 * - Dark backgrounds for living-room viewing
 * - High-contrast text for readability at distance
 * - Accent colors from the existing photon palette
 */
private val TvDarkColorScheme = darkColorScheme(
    primary = Color(0xFF0060DF),
    onPrimary = Color(0xFFEEEEEE),
    primaryContainer = Color(0xFF003E9C),
    onPrimaryContainer = Color(0xFFD7E3FF),

    secondary = Color(0xFF6C3B6E),
    onSecondary = Color(0xFFEEEEEE),
    secondaryContainer = Color(0xFF4A254C),
    onSecondaryContainer = Color(0xFFFFD6FA),

    tertiary = Color(0xFF00A4DC),
    onTertiary = Color(0xFFEEEEEE),
    tertiaryContainer = Color(0xFF005E7F),
    onTertiaryContainer = Color(0xFFC0E8FF),

    background = Color(0xFF0C0C15),
    onBackground = Color(0xFFEEEEEE),

    surface = Color(0xFF272727),
    onSurface = Color(0xFFEEEEEE),
    surfaceVariant = Color(0xFF333540),
    onSurfaceVariant = Color(0xFFA0A0A3),

    error = Color(0xFFEF4056),
    onError = Color(0xFFEEEEEE),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    outline = Color(0xFF474959),
    inverseSurface = Color(0xFFEEEEEE),
    inverseOnSurface = Color(0xFF0C0C15),
    inversePrimary = Color(0xFF0060DF),
    surfaceTint = Color(0xFF0060DF),
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
