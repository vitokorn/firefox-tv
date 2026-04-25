/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv

import android.view.KeyEvent
import io.mockk.MockKAnnotations
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.atmofox.tv.ScreenControllerStateMachine.ActiveScreen
import org.atmofox.tv.helpers.FirefoxRobolectricTestRunner

@RunWith(FirefoxRobolectricTestRunner::class)
class ScreenControllerTest {

    private lateinit var controller: ScreenController

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        controller = ScreenController(mockk())
    }

    @Test
    fun `GIVEN WEB_RENDER is active WHEN dispatchKeyEvent is called THEN it returns false for non-menu keys`() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
        controller.setActiveScreenForCompose(ActiveScreen.WEB_RENDER)

        val result = controller.dispatchKeyEvent(keyEvent)

        assert(!result) { "Expected dispatchKeyEvent to return false for WEB_RENDER with non-menu key" }
    }

    @Test
    fun `GIVEN NAVIGATION_OVERLAY is active WHEN dispatchKeyEvent is called THEN it returns false`() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
        controller.setActiveScreenForCompose(ActiveScreen.NAVIGATION_OVERLAY)

        val result = controller.dispatchKeyEvent(keyEvent)

        assert(!result) { "Expected dispatchKeyEvent to return false for NAVIGATION_OVERLAY" }
    }

    @Test
    fun `GIVEN handleMenu is called THEN it updates active screen`() {
        controller.setActiveScreenForCompose(ActiveScreen.WEB_RENDER)

        controller.handleMenu()

        // After handleMenu, state should have changed (typically to NAVIGATION_OVERLAY)
        assert(controller.currentActiveScreen.value == ActiveScreen.NAVIGATION_OVERLAY) {
            "Expected active screen to change after handleMenu"
        }
    }
}
