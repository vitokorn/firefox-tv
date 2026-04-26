/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

@file:Suppress("DEPRECATION")

package org.atmofox.tv.settings

import android.app.Application
import android.content.SharedPreferences
import android.os.StrictMode
import android.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mozilla.components.support.ktx.android.os.resetAfter
import org.atmofox.tv.R

private val PREF_KEY_TELEMETRY = R.string.pref_key_telemetry
const val IS_TELEMETRY_ENABLED_DEFAULT = true

class SettingsRepo(applicationContext: Application) {
    private val _sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    private val resources = applicationContext.resources

    private val _dataCollectionEnabled = MutableStateFlow(true)
    val dataCollectionEnabled: StateFlow<Boolean> = _dataCollectionEnabled

    init {
        loadSettingsFromPreferences()
    }

    private fun loadSettingsFromPreferences() {
        // The first access to shared preferences will require a disk read.
        StrictMode.allowThreadDiskReads().resetAfter {
            _dataCollectionEnabled.value = _sharedPreferences
                    .getBoolean(resources.getString(PREF_KEY_TELEMETRY), IS_TELEMETRY_ENABLED_DEFAULT)
        }
    }

    fun setDataCollectionEnabled(toEnable: Boolean) {
        _sharedPreferences.edit()
                .putBoolean(resources.getString(PREF_KEY_TELEMETRY), toEnable)
                .apply()
        _dataCollectionEnabled.value = toEnable
    }
}
