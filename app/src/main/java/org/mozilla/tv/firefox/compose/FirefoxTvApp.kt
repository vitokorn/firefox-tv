/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.mozilla.tv.firefox.compose.browser.BrowserScreen

/**
 * Root composable for the Firefox TV application.
 *
 * Hosts navigation and global UI chrome. For now, routes directly to
 * the browser screen while the rest of the navigation graph is built out.
 */
@Composable
fun FirefoxTvApp(
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            BrowserScreen()
        }
    }
}
