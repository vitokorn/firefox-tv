/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.utils

import android.graphics.Color
import org.atmofox.tv.helpers.FirefoxRobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(FirefoxRobolectricTestRunner::class)
class ColorUtilsTest {
    @Test
    fun testGetReadableTextColor() {
        assertEquals(Color.BLACK, ColorUtils.getReadableTextColor(Color.WHITE))
        assertEquals(Color.WHITE, ColorUtils.getReadableTextColor(Color.BLACK))

        // Slack
        assertEquals(Color.BLACK, ColorUtils.getReadableTextColor(0xfff6f4ec.toInt()))

        // Google+
        assertEquals(Color.WHITE, ColorUtils.getReadableTextColor(0xffdb4437.toInt()))

        // Telegram
        assertEquals(Color.WHITE, ColorUtils.getReadableTextColor(0xff527da3.toInt()))

        // IRCCloud
        assertEquals(Color.BLACK, ColorUtils.getReadableTextColor(0xfff2f7fc.toInt()))

        // Yahnac
        assertEquals(Color.WHITE, ColorUtils.getReadableTextColor(0xfff57c00.toInt()))
    }
}
