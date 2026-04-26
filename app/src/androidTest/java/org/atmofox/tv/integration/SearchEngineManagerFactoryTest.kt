/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.integration

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.atmofox.tv.FirefoxApplication
import org.atmofox.tv.utils.UrlUtils

/**
 * feature-search replaced browser-search in mozilla-components 128.x.
 * Search engines are now initialized in BrowserStore via WebRenderComponents.
 */
class SearchEngineManagerFactoryTest {

    lateinit var app: FirefoxApplication

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun searchEngineUrlShouldUseGoogle() {
        val searchUrl = UrlUtils.createSearchUrl(app, "cats")
        assertTrue(searchUrl.contains("google.com/search?q=cats"))
    }
}
