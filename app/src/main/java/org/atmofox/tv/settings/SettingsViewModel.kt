package org.atmofox.tv.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
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
    private val _events = MutableSharedFlow<SettingsAction>()

    val events: SharedFlow<SettingsAction> = _events
    val dataCollectionEnabled = settingsRepo.dataCollectionEnabled

    fun setDataCollectionEnabled(toEnable: Boolean) {
        settingsRepo.setDataCollectionEnabled(toEnable)
    }

    fun clearBrowsingData(engineViewCache: EngineViewCache) {
        TelemetryIntegration.INSTANCE.clearDataEvent()
        sessionRepo.clearBrowsingData(engineViewCache)
        viewModelScope.launch {
            _events.emit(SettingsAction.SESSION_CLEARED)
        }
    }
}
