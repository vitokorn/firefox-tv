/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.experiments

import android.content.Context
import org.mozilla.tv.firefox.telemetry.TelemetryIntegration

// service-fretboard removed in mozilla-components 128.x
// This is a stub implementation to keep compilation working.
// TODO: Replace with Nimbus experiments framework if needed.

class FretboardProvider(private val applicationContext: Context) {
    val fretboard: Any? = null

    // A list of the experiment names that the user is a part of.
    private val activeExperimentNames: List<String> = emptyList()

    /**
     * Asynchronously requests new experiments from the server and
     * saves them to local storage. STUB - service-fretboard removed in 128.x.
     */
    fun updateExperiments() { }

    /**
     * Synchronously loads experiments from local storage. STUB - service-fretboard removed in 128.x.
     */
    fun loadExperiments() {
        TelemetryIntegration.INSTANCE.recordActiveExperiments(activeExperimentNames)
    }
}
