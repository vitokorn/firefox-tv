/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.hint

import kotlinx.coroutines.flow.Flow

/**
 * Contains backing data for hint bar
 */
interface HintViewModel {
    val isDisplayed: Flow<Boolean>
    val hints: Flow<List<HintContent>>
}
