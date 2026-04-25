/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.framework

import android.view.accessibility.AccessibilityManager
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A model to hold state related to the Android framework.
 */
class FrameworkRepo @VisibleForTesting constructor() {

    private var wasInitCalled = false

    private val _isVoiceViewEnabled = MutableStateFlow(false)
    val isVoiceViewEnabled: StateFlow<Boolean> = _isVoiceViewEnabled.asStateFlow()

    /**
     * Initializes this repository.
     * @throws IllegalStateException when called twice.
     */
    fun init(accessibilityManager: AccessibilityManager) {
        if (wasInitCalled) throw IllegalStateException("FrameworkRepo.init unexpectedly called twice")
        wasInitCalled = true

        // We call the listener directly to set the initial state.
        val touchExplorationStateChangeListener = TouchExplorationStateChangeListener()
        accessibilityManager.addTouchExplorationStateChangeListener(touchExplorationStateChangeListener)
        touchExplorationStateChangeListener.onTouchExplorationStateChanged(accessibilityManager.isTouchExplorationEnabled)
    }

    private inner class TouchExplorationStateChangeListener : AccessibilityManager.TouchExplorationStateChangeListener {
        @UiThread // for simplicity: listener should be called from UI thread anyway.
        override fun onTouchExplorationStateChanged(isEnabled: Boolean) {
            _isVoiceViewEnabled.value = isEnabled // Touch exploration state == VoiceView.
        }
    }

    companion object {
        fun newInstanceAndInit(accessibilityManager: AccessibilityManager): FrameworkRepo {
            return FrameworkRepo().apply {
                init(accessibilityManager)
            }
        }
    }
}
