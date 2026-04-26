/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.components.locale

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.os.StrictMode
import android.text.TextUtils
import java.util.LinkedHashSet
import java.util.Locale

/**
 * This is a helper class to do typical locale switching operations without
 * hitting StrictMode errors or adding boilerplate to common activity
 * subclasses.
 *
 * Either call [Locales.initializeLocale] in your
 * `onCreate` method, or inherit from
 * `LocaleAwareFragmentActivity` or `LocaleAwareActivity`.
 */
object Locales {

    /**
     * Is only required by locale aware activities, AND Application. In most cases you should be
     * using LocaleAwareAppCompatActivity or friends.
     * @param context
     */
    @JvmStatic
    fun initializeLocale(context: Context) {
        val localeManager = LocaleManager.getInstance()
        val savedPolicy = StrictMode.allowThreadDiskReads()
        StrictMode.allowThreadDiskWrites()
        try {
            localeManager.getAndApplyPersistedLocale(context)
        } finally {
            StrictMode.setThreadPolicy(savedPolicy)
        }
    }

    /**
     * Sometimes we want just the language for a locale, not the entire language
     * tag. But Java's .getLanguage method is wrong.
     *
     * This method is equivalent to the first part of
     * [Locales.getLanguageTag].
     *
     * @return a language string, such as "he" for the Hebrew locales.
     */
    @JvmStatic
    fun getLanguage(locale: Locale): String {
        // Can, but should never be, an empty string.
        val language = locale.language

        // Modernize certain language codes.
        return when (language) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            else -> language
        }
    }

    /**
     * Gecko uses locale codes like "es-ES", whereas a Java [Locale]
     * stringifies as "es_ES".
     *
     * This method approximates the Java 7 method
     * `Locale#toLanguageTag()`.
     *
     * @return a locale string suitable for passing to Gecko.
     */
    @JvmStatic
    fun getLanguageTag(locale: Locale): String {
        // If this were Java 7:
        // return locale.toLanguageTag();

        val language = getLanguage(locale)
        val country = locale.country // Can be an empty string.
        return if (country == "") {
            language
        } else {
            "$language-$country"
        }
    }

    @JvmStatic
    fun parseLocaleCode(localeCode: String): Locale {
        val index = localeCode.indexOf('-').takeIf { it != -1 }
            ?: localeCode.indexOf('_').takeIf { it != -1 }
        return if (index != null) {
            val langCode = localeCode.substring(0, index)
            val countryCode = localeCode.substring(index + 1)
            Locale(langCode, countryCode)
        } else {
            Locale(localeCode)
        }
    }

    /**
     * Get a set of all countries (lowercase) in the list of default locales for this device.
     */
    @JvmStatic
    fun getCountriesInDefaultLocaleList(): Set<String> {
        val countries = LinkedHashSet<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val list = LocaleList.getDefault()
            for (i in 0 until list.size()) {
                val country = list[i].country
                if (!TextUtils.isEmpty(country)) {
                    countries.add(country.toLowerCase(Locale.US))
                }
            }
        } else {
            val country = Locale.getDefault().country
            if (!TextUtils.isEmpty(country)) {
                countries.add(country.toLowerCase(Locale.US))
            }
        }

        return countries
    }

    /**
     * Get a Resources instance with the currently selected locale applied.
     */
    @JvmStatic
    fun getLocalizedResources(context: Context): Resources {
        val currentResources = context.resources

        val currentLocale = LocaleManager.getInstance().getCurrentLocale(context)
        @Suppress("DEPRECATION")
        val viewLocale = currentResources.configuration.locale

        if (currentLocale == null || viewLocale == null) {
            return currentResources
        }

        if (currentLocale.toLanguageTag() == viewLocale.toLanguageTag()) {
            return currentResources
        }

        val configuration = Configuration(currentResources.configuration)
        configuration.setLocale(currentLocale)

        return context.createConfigurationContext(configuration).resources
    }
}
