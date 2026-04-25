/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.channels.pinnedtile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.ContextCompat
import android.util.TypedValue
import org.atmofox.tv.R
import org.atmofox.tv.utils.UrlUtils

class PinnedTilePlaceholderGenerator {

    companion object {
        private val TEXT_SIZE_DP = 36f
        private val DEFAULT_ICON_CHAR = '?'
        private val ROTATION_MAX_DEG = 6f

        fun generate(context: Context, url: String?): Bitmap {
            val startingChar = getRepresentativeCharacter(url)
            val dimen = context.resources.getDimensionPixelSize(R.dimen.home_tile_placeholder_icon_size)
            val bitmap = Bitmap.createBitmap(dimen, dimen, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(generateColorForUrl(url))
            return drawCharacterOnBitmap(context, startingChar, bitmap, rotationForUrl(url))
        }

        /** Deterministic pastel-ish color based on URL hash. */
        fun generateColorForUrl(url: String?): Int {
            val hash = url?.hashCode() ?: 0
            val hue = (Math.abs(hash) % 360).toFloat()
            val sat = 0.55f + (Math.abs(hash shr 8) % 20) / 100f  // 0.55 – 0.74
            val value = 0.65f + (Math.abs(hash shr 16) % 15) / 100f // 0.65 – 0.79
            return android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        }

        /** Slight left rotation for style. */
        fun rotationForUrl(url: String?): Float {
            val hash = url?.hashCode() ?: 0
            val step = Math.abs(hash shr 4) % 3  // 0..2
            return -8f + step * 2f  // -8, -6, -4 degrees (left tilt)
        }

        private fun drawCharacterOnBitmap(
            context: Context,
            character: Char,
            bitmap: Bitmap,
            rotationDeg: Float
        ): Bitmap {
            val desiredTextSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DP, context.resources.displayMetrics)
            val paint = Paint().apply {
                color = ContextCompat.getColor(context, R.color.tv_white)
                textAlign = Paint.Align.CENTER
                textSize = desiredTextSize
                isAntiAlias = true
                isFakeBoldText = true
            }

            val canvas = Canvas(bitmap)
            canvas.save()
            canvas.rotate(rotationDeg, canvas.width / 2.0f, canvas.height / 2.0f)
            canvas.drawText(character.toString(),
                    canvas.width / 2.0f,
                    canvas.height / 2.0f - (paint.descent() + paint.ascent()) / 2.0f + canvas.height * 0.08f,
                    paint)
            canvas.restore()

            return bitmap
        }

        /**
         * Get a representative character for the given URL.
         *
         * For example this method will return "f" for "http://m.facebook.com/foobar".
         */
        @JvmStatic
        fun getRepresentativeCharacter(url: String?): Char {
            val firstChar = getRepresentativeSnippet(url)?.find { it.isLetterOrDigit() }?.uppercaseChar()
            return (firstChar ?: DEFAULT_ICON_CHAR)
        }

        /**
         * Get the representative part of the URL. Usually this is the host (without common prefixes).
         *
         * @return the representative snippet or null if one could not be found.
         */
        private fun getRepresentativeSnippet(url: String?): String? {
            if (url == null || url.isEmpty()) return null

            val uri = Uri.parse(url)
            val snippet = if (!uri.host.isNullOrEmpty()) {
                uri.host // cached by Uri class.
            } else if (!uri.path.isNullOrEmpty()) { // The uri may not have a host for e.g. file:// uri
                uri.path // cached by Uri class.
            } else {
                return null
            }

            // Strip common prefixes that we do not want to use to determine the representative characters
            return UrlUtils.stripCommonSubdomains(snippet)
        }
    }
}
