package org.atmofox.tv.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import mozilla.components.support.base.observer.Consumable
import org.atmofox.tv.session.SessionRepo
import org.atmofox.tv.telemetry.TelemetryIntegration
import org.atmofox.tv.webrender.EngineViewCache

enum class SettingsAction {
    SESSION_CLEARED
}

class SettingsViewModel(
    private val settingsRepo: SettingsRepo,
    private val sessionRepo: SessionRepo
) : ViewModel() {
    private var _events = MutableLiveData<Consumable<SettingsAction>>()

    val events: LiveData<Consumable<SettingsAction>> = _events
    val dataCollectionEnabled = settingsRepo.dataCollectionEnabled

    fun setDataCollectionEnabled(toEnable: Boolean) {
        settingsRepo.setDataCollectionEnabled(toEnable)
    }

    fun clearBrowsingData(engineViewCache: EngineViewCache) {
        TelemetryIntegration.INSTANCE.clearDataEvent()
        sessionRepo.clearBrowsingData(engineViewCache)
        _events.value = Consumable.from(SettingsAction.SESSION_CLEARED)
    }
}
