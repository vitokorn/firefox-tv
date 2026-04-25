/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.components.locale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import androidx.annotation.NonNull
import io.sentry.Sentry
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.atmofox.tv.R
import org.atmofox.tv.generated.LocaleList
import java.util.Locale

/**
 * This class manages persistence, application, and otherwise handling of
 * user-specified locales.
 *
 * Of note:
 *
 * * It's a singleton, because its scope extends to that of the application,
 *   and definitionally all changes to the locale of the app must go through
 *   this.
 * * It's lazy.
 * * It relies on using the SharedPreferences file owned by the app for performance.
 */
class LocaleManager private constructor() {

    private val LOG_TAG = "GeckoLocales"

    @Volatile
    private var currentLocale: Locale? = null

    @Volatile
    private var systemLocale: Locale = Locale.getDefault()

    private val inited = AtomicBoolean(false)
    private var systemLocaleDidChange = false
    private var receiver: BroadcastReceiver? = null

    fun initialize(context: Context) {
        if (!inited.compareAndSet(false, true)) {
            return
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val current = systemLocale

                // We don't trust Locale.getDefault() here, because we make a
                // habit of mutating it! Use the one Android supplies, because
                // that gets regularly reset.
                // The default value of systemLocale is fine, because we haven't
                // yet swizzled Locale during static initialization.
                systemLocale = context.resources.configuration.locale
                systemLocaleDidChange = true

                Log.d(LOG_TAG, "System locale changed from $current to $systemLocale")
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_LOCALE_CHANGED))
    }

    fun systemLocaleDidChange(): Boolean {
        return systemLocaleDidChange
    }

    /**
     * Every time the system gives us a new configuration, it
     * carries the external locale. Fix it.
     */
    fun correctLocale(context: Context, res: Resources, config: Configuration) {
        val current = getCurrentLocale(context)
        if (current == null) {
            Log.d(LOG_TAG, "No selected locale. No correction needed.")
            return
        }

        config.locale = current

        Locale.setDefault(current)

        @Suppress("DEPRECATION")
        res.updateConfiguration(config, null)
    }

    /**
     * We can be in one of two states.
     *
     * If the user has not explicitly chosen a Firefox-specific locale, we say
     * we are "mirroring" the system locale.
     *
     * When we are not mirroring, system locale changes do not impact Firefox
     * and are essentially ignored; the user's locale selection is the only
     * thing we care about, and we actively correct incoming configuration
     * changes to reflect the user's chosen locale.
     *
     * By contrast, when we are mirroring, system locale changes cause Firefox
     * to reflect the new system locale, as if the user picked the new locale.
     *
     * If we're currently mirroring the system locale, this method returns the
     * supplied configuration's locale, unless the current activity locale is
     * correct. If we're not currently mirroring, this method updates the
     * configuration object to match the user's currently selected locale, and
     * returns that, unless the current activity locale is correct.
     *
     * If the current activity locale is correct, returns null.
     *
     * The caller is expected to redisplay themselves accordingly.
     *
     * This method is intended to be called from inside
     * `onConfigurationChanged(Configuration)` as part of a strategy
     * to detect and either apply or undo system locale changes.
     */
    fun onSystemConfigurationChanged(context: Context, resources: Resources, configuration: Configuration, currentActivityLocale: Locale): Locale? {
        if (!isMirroringSystemLocale(context)) {
            correctLocale(context, resources, configuration)
        }

        val changed = configuration.locale
        return if (changed == currentActivityLocale) {
            null
        } else {
            changed
        }
    }

    fun getAndApplyPersistedLocale(context: Context): String? {
        initialize(context)

        val t1 = android.os.SystemClock.uptimeMillis()
        val localeCode = getPersistedLocale(context)
        if (localeCode == null) {
            return null
        }

        val resultant = updateLocale(context, localeCode)

        if (resultant == null) {
            updateConfiguration(context, currentLocale)
        }

        val t2 = android.os.SystemClock.uptimeMillis()
        Log.i(LOG_TAG, "Locale read and update took: ${t2 - t1}ms.")
        return resultant
    }

    /**
     * Returns the set locale if it changed.
     *
     * Always persists and notifies Gecko.
     */
    fun setSelectedLocale(context: Context, localeCode: String): String? {
        val resultant = updateLocale(context, localeCode)
        persistLocale(context, localeCode)
        return resultant
    }

    fun resetLocaleIfChanged(context: Context) {
        if (currentLocale != systemLocale) {
            resetToSystemLocale(context)
        }
    }

    fun resetToSystemLocale(context: Context) {
        val settings = getSharedPreferences(context)
        settings.edit().remove(PREF_LOCALE).apply()
        updateLocale(context, systemLocale)
    }

    /**
     * This is public to allow for an activity to force the
     * current locale to be applied if necessary (e.g., when
     * a new activity launches).
     */
    fun updateConfiguration(context: Context, locale: Locale?) {
        val res = context.resources
        val config = res.configuration

        config.locale = locale

        config.setLayoutDirection(locale)

        @Suppress("DEPRECATION")
        res.updateConfiguration(config, null)
    }

    private fun getSharedPreferences(context: Context): SharedPreferences {
        if (PREF_LOCALE == null) {
            PREF_LOCALE = context.getString(R.string.pref_key_locale)
        }

        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    /**
     * @return the persisted locale in Java format: "en_US".
     */
    private fun getPersistedLocale(context: Context): String? {
        val settings = getSharedPreferences(context)
        val locale = settings.getString(PREF_LOCALE, "")

        return if ("" == locale) {
            null
        } else {
            locale
        }
    }

    private fun persistLocale(context: Context, localeCode: String) {
        val settings = getSharedPreferences(context)
        settings.edit().putString(PREF_LOCALE, localeCode).apply()
    }

    /**
     *  Note: If getCurrentLocale is used prior to a locale selection
     *  through an Intent (ie. as a commandline argument),
     *  there could be odd behaviour with differing
     *  locale information.
     */
    @NonNull
    fun getCurrentLocale(@NonNull context: Context): Locale {
        if (currentLocale != null) {
            return currentLocale!!
        }

        val current = getPersistedLocale(context)
        if (current != null) {
            currentLocale = Locales.parseLocaleCode(current)
        }

        if (currentLocale == null) {
            currentLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale
            }
        }

        if (currentLocale == null) {
            Sentry.capture(AssertionError("Selected locale not available. Falling back to EN"))
            currentLocale = Locale.US
        }

        return currentLocale!!
    }

    @NonNull
    fun currentLanguageIsEnglish(@NonNull context: Context): Boolean {
        return getCurrentLocale(context).language == "en"
    }

    /**
     * Updates the Java locale and the Android configuration.
     *
     * Returns the persisted locale if it differed.
     *
     * Does not notify Gecko.
     *
     * @param localeCode a locale string in Java format: "en_US".
     * @return if it differed, a locale string in Java format: "en_US".
     */
    private fun updateLocale(context: Context, localeCode: String): String? {
        val defaultLocale = Locale.getDefault()
        Log.d("LOCALE", "Trying to check locale")
        if (defaultLocale.toString() == localeCode) {
            Log.d("LOCALE", "Early return")
            return null
        }

        val locale = Locales.parseLocaleCode(localeCode)

        return updateLocale(context, locale)
    }

    /**
     * @return the Java locale string: e.g., "en_US".
     */
    private fun updateLocale(context: Context, locale: Locale): String? {
        if (Locale.getDefault() == locale) {
            return null
        }

        Locale.setDefault(locale)
        currentLocale = locale

        updateConfiguration(context, locale)

        return locale.toString()
    }

    fun isMirroringSystemLocale(context: Context): Boolean {
        return getPersistedLocale(context) == null
    }

    companion object {
        private var PREF_LOCALE: String? = null
        private const val FALLBACK_LOCALE_TAG = "en-US"

        private val instance = AtomicReference<LocaleManager?>(null)

        @JvmStatic
        fun getInstance(): LocaleManager {
            var localeManager = instance.get()
            if (localeManager != null) {
                return localeManager
            }

            localeManager = LocaleManager()
            if (instance.compareAndSet(null, localeManager)) {
                return localeManager
            } else {
                return instance.get()!!
            }
        }

        @JvmStatic
        fun getPackagedLocaleTags(context: Context): Collection<String> {
            return LocaleList.BUNDLED_LOCALES
        }

        @JvmStatic
        fun getFallbackLocaleTag(): String {
            return FALLBACK_LOCALE_TAG
        }
    }
}
