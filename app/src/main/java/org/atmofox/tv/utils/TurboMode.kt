/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.utils

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.atmofox.tv.ext.webRenderComponents

/**
 * Facade hiding the ceremony needed to setEnabled Turbo Mode.
 *
 * We are trying to keep our setting and the state of the engine synchronized.
 */
class TurboMode(private val app: Application) {

    var isEnabled: Boolean
        get() = Settings.getInstance(app).isBlockingEnabled
        set(enabled: Boolean) {
            setEnabled(enabled, skipEngineSettingsUpdate = false)
        }

    fun setEnabled(enabled: Boolean, skipEngineSettingsUpdate: Boolean) {
        val settings = Settings.getInstance(app)
        settings.isBlockingEnabled = enabled

        // Update TrackingProtectionPolicy via EngineSettings (v72+ handles propagation automatically)
        if (!skipEngineSettingsUpdate) {
            val engineSettings = app.webRenderComponents.engine.settings
            engineSettings.trackingProtectionPolicy = settings.trackingProtectionPolicy
        }
        _observable.postValue(enabled)
    }

    private val _observable = MutableLiveData<Boolean>()
    val observable: LiveData<Boolean> = _observable
}
