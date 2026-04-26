/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.webrender

import android.content.Context
import androidx.collection.ArrayMap
import mozilla.components.browser.errorpages.ErrorType
import org.atmofox.tv.R
import org.atmofox.tv.utils.HtmlLoader

object ErrorPage {
    fun loadErrorPage(
        context: Context,
        desiredURL: String,
        errorType: ErrorType
    ): String {
        val cssString = HtmlLoader.loadResourceFile(context, R.raw.errorpage_style, null)

        val substitutionMap = ArrayMap<String, String>()

        val resources = context.resources

        substitutionMap["%page-title%"] = resources.getString(R.string.errorpage_title)
        substitutionMap["%button%"] = resources.getString(R.string.errorpage_refresh)
        substitutionMap["%messageShort%"] = resources.getString(errorType.titleRes)
        substitutionMap["%messageLong%"] = resources.getString(errorType.messageRes, desiredURL)
        substitutionMap["%css%"] = cssString

        return HtmlLoader.loadResourceFile(context, R.raw.errorpage, substitutionMap)
    }
}
