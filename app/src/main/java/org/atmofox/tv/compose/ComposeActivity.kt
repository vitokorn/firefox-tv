/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import org.atmofox.tv.components.locale.LocaleAwareAppCompatActivity
import org.atmofox.tv.compose.theme.FirefoxTvTheme

/**
 * Compose-first entry point for Firefox TV.
 *
 * This Activity exists alongside [org.atmofox.tv.MainActivity] during the migration.
 * Once full feature parity is reached, this will become the primary launcher Activity.
 */
@Deprecated("MainActivity now uses Compose directly. Remove when migration is complete.")
class ComposeActivity : LocaleAwareAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FirefoxTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FirefoxTvApp()
                }
            }
        }
    }

    override fun applyLocale() {
        // Compose recomposes automatically when locale changes via Configuration
    }
}
