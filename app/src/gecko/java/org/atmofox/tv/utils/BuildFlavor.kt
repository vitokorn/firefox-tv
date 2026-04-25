/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.utils

import android.content.Context
import mozilla.components.concept.engine.request.RequestInterceptor
import org.atmofox.tv.BuildConfig

/**
 * Provides build flavor specific tasks
 */
class BuildFlavor {
    fun getDebugLogStr(isDevBuild: Boolean): String? {
        if (isDevBuild) {
            return "DEBUG / " +
                    "FLAVOR: ${BuildConfig.FLAVOR} / " +
                    "VERSION: ${BuildConfig.VERSION_NAME} / " +
                    "GECKO: " + generateGeckoVersionStr()
        }

        return null
    }

    /**
     * SystemWebView requires context to acquire version
     */
    fun getEngineVersion(@Suppress("UNUSED_PARAMETER") context: Context): String {
        return generateGeckoVersionStr()
    }

    /**
     * (This is a workaround for bug 1535131)
     * Intercepted content is loaded through a data URL for internal pages.
     */
    fun getInterceptionResponseContent(localizedContent: String): RequestInterceptor.InterceptionResponse.Content {
        return RequestInterceptor.InterceptionResponse.Content(localizedContent, "text/html")
    }

    private fun generateGeckoVersionStr(): String {
        return "${org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION}-${org.mozilla.geckoview.BuildConfig.MOZ_APP_BUILDID}"
    }
}
