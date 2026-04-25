/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

@file:Suppress("TestFunctionName")

package org.atmofox.tv.ui

import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.atmofox.tv.ext.serviceLocator
import org.atmofox.tv.helpers.MainActivityTestRule
import org.atmofox.tv.settings.IS_TELEMETRY_ENABLED_DEFAULT
import org.atmofox.tv.settings.SettingsRepo
import org.atmofox.tv.ui.robots.navigationOverlay

class ToggleDataCollectionTest {
    @get:Rule val activityTestRule = MainActivityTestRule()

    private lateinit var settingsRepo: SettingsRepo

    @Before
    fun setUp() {
        settingsRepo = activityTestRule.activity.application.serviceLocator.settingsRepo
    }

    @Test
    fun WHEN_data_collection_button_is_toggled_THEN_data_collection_matches_button_state() {
        navigationOverlay {
        }.linearNavigateToTelemtryTileAndOpen {
            assertDataCollectionButtonState(IS_TELEMETRY_ENABLED_DEFAULT)

            toggleDataCollectionButton()
            assertNotNull(settingsRepo.dataCollectionEnabled.value)
            assertDataCollectionButtonState(settingsRepo.dataCollectionEnabled.value ?: false)

            toggleDataCollectionButton()
            assertNotNull(settingsRepo.dataCollectionEnabled.value)
            assertDataCollectionButtonState(settingsRepo.dataCollectionEnabled.value ?: false)
        }
    }

    @Test
    fun WHEN_data_collection_button_is_toggled_THEN_button_state_persists_when_returning_to_settings() {
        var cachedDataCollectionButtonIsChecked = IS_TELEMETRY_ENABLED_DEFAULT

        navigationOverlay {
        }.linearNavigateToTelemtryTileAndOpen {
            assertDataCollectionButtonState(cachedDataCollectionButtonIsChecked)
            toggleDataCollectionButton()
            cachedDataCollectionButtonIsChecked = !cachedDataCollectionButtonIsChecked
        }.exitToOverlay {
        }.linearNavigateToTelemtryTileAndOpen {
            assertDataCollectionButtonState(cachedDataCollectionButtonIsChecked)
        }
    }
}
