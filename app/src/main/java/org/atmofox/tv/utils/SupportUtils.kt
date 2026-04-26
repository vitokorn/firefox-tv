/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.utils

import android.content.Context
import android.content.pm.PackageManager
import org.atmofox.tv.components.locale.Locales
import java.net.URLEncoder
import java.util.Locale

object SupportUtils {

    @JvmStatic
    fun getSumoURLForTopic(context: Context, topic: String): String {
        val escapedTopic = URLEncoder.encode(topic, "UTF-8")

        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            // This should be impossible - we should always be able to get information about ourselves:
            throw IllegalStateException("Unable find package details for Focus", e)
        }

        val osTarget = "Android"
        val langTag = Locales.getLanguageTag(Locale.getDefault())

        return "https://support.mozilla.org/1/mobile/$appVersion/$osTarget/$langTag/$escapedTopic"
    }

    @JvmStatic
    fun getManifestoURL(): String {
        val langTag = Locales.getLanguageTag(Locale.getDefault())
        return "https://www.mozilla.org/$langTag/about/manifesto/"
    }
}
