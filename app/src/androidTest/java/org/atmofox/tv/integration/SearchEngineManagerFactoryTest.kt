/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.integration

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.atmofox.tv.FirefoxApplication
import org.atmofox.tv.search.SearchEngineManagerFactory
import org.atmofox.tv.utils.UrlUtils

/**
 * Stubbed test: browser-search was removed in mozilla-components 128.x.
 * SearchEngineManagerFactory.create returns null. UrlUtils.createSearchUrl
 * still appends search codes directly.
 */
class SearchEngineManagerFactoryTest {

    lateinit var app: FirefoxApplication

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun searchEngineFactoryIsStubbed() {
        val searchEngineManager = SearchEngineManagerFactory.create(app)
        assertTrue(searchEngineManager == null)
    }

    @Test
    fun searchEngineUrlShouldIncludeFftvSearchCodes() {
        val searchUrl = UrlUtils.createSearchUrl(app, "cats")
        assertTrue(searchUrl.contains(SearchEngineManagerFactory.AMAZON_SEARCH_CODE) ||
                searchUrl.contains(SearchEngineManagerFactory.AMAZON_SEARCH_CODE_US_ONLY)
        )
    }
}
