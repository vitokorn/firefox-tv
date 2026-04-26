/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.utils

import android.content.Context
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.atmofox.tv.helpers.FirefoxRobolectricTestRunner
import org.atmofox.tv.ext.application

@RunWith(FirefoxRobolectricTestRunner::class)
class TurboModeTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var turboMode: TurboMode

    @Before
    fun setUp() {
        // Avoid [Settings] from keeping a references to a shared preference instance from a previous test run.
        Settings.reset()

        // Clear all settings to always start fresh
        PreferenceManager
            .getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .apply()

        turboMode = TurboMode(context.application)
    }

    @Test
    fun `Turbo Mode should be enabled by default`() {
        assertTrue(turboMode.isEnabled)
        assertTrue(Settings.getInstance(context).isBlockingEnabled)
    }

    @Test
    fun `Turbo Mode should be disabled after toggling`() {
        turboMode.isEnabled = false

        assertFalse(turboMode.isEnabled)
        assertFalse(Settings.getInstance(context).isBlockingEnabled)
    }

    @Test
    fun `Turbo Mode should be enabled after toggling again`() {
        turboMode.isEnabled = false
        turboMode.isEnabled = true

        assertTrue(turboMode.isEnabled)
        assertTrue(Settings.getInstance(context).isBlockingEnabled)
    }
}
