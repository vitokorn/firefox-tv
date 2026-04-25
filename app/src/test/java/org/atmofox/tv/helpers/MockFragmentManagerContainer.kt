/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.helpers

import androidx.fragment.app.FragmentManager
import io.mockk.every
import io.mockk.mockk
import org.atmofox.tv.navigationoverlay.NavigationOverlayFragment
import org.atmofox.tv.webrender.WebRenderFragment

/**
 * A data container for a mocked [FragmentManager] which returns fragments specific to this app,
 * such as the [NavigationOverlayFragment].
 */
class MockFragmentManagerContainer {

    val navigationOverlayFragment: NavigationOverlayFragment = mockk(relaxed = true)
    val webRenderFragment: WebRenderFragment = mockk(relaxed = true)

    val fragmentManager: FragmentManager = mockk<FragmentManager>().apply {
        initFindFragmentByTag()
    }

    private fun FragmentManager.initFindFragmentByTag() {
        every { findFragmentByTag(NavigationOverlayFragment.FRAGMENT_TAG) } returns navigationOverlayFragment
        every { findFragmentByTag(WebRenderFragment.FRAGMENT_TAG) } returns webRenderFragment
    }
}
