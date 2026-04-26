package org.atmofox.tv.components.locale

import android.app.Application
import android.content.res.Configuration

open class LocaleAwareApplication : Application() {
    private var inBackground = false

    override fun onCreate() {
        Locales.initializeLocale(this)
        super.onCreate()
    }

    /**
     * We need to do locale work here, because we need to intercept
     * each hit to onConfigurationChanged.
     */
    override fun onConfigurationChanged(config: Configuration) {
        // Do nothing if we're in the background. It'll simply cause a loop
        // (Bug 936756 Comment 11), and it's not necessary.
        if (inBackground) {
            super.onConfigurationChanged(config)
            return
        }

        // Otherwise, correct the locale. This catches some cases that the current Activity
        // doesn't get a chance to.
        try {
            LocaleManager.getInstance().correctLocale(this, resources, config)
        } catch (ex: IllegalStateException) {
            // Activity hasn't started yet, so we have no ContextGetter in LocaleManager.
        }

        super.onConfigurationChanged(config)
    }

    fun onActivityPause() {
        inBackground = true
    }

    fun onActivityResume() {
        inBackground = false
    }
}
