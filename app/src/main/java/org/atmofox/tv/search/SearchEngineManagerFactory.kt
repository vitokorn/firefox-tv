/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.search

import android.app.Application

// browser-search removed in mozilla-components 128.x
// This is a stub. TODO: Replace with feature-search APIs if needed.

object SearchEngineManagerFactory {

    val AMAZON_SEARCH_CODE = "google-b-amzftv"
    val AMAZON_SEARCH_CODE_US_ONLY = "google-b-1-amzftv"

    fun create(app: Application): Any? = null
}
