/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.helpers

import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.IdlingResource
import mozilla.components.browser.state.selector.selectedTab
import org.mozilla.tv.firefox.FirefoxApplication

/**
 * An IdlingResource implementation that waits until the current session is not loading anymore.
 * Only after loading has completed further actions will be performed.
 */
class SessionLoadedIdlingResource(private val ignoreLoading: Boolean = false) : IdlingResource {
    private var callback: IdlingResource.ResourceCallback? = null

    override fun getName(): String {
        return this::class.java.simpleName
    }

    override fun isIdleNow(): Boolean {
        val context = ApplicationProvider.getApplicationContext<FirefoxApplication>()
        val store = context.components.store

        val tab = store.state.selectedTab

        return if (tab?.content?.loading == true && !ignoreLoading) {
            false
        } else {
            invokeCallback()
            true
        }
    }

    private fun invokeCallback() {
        if (callback != null) {
            callback!!.onTransitionToIdle()
        }
    }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
        this.callback = callback
    }
}
