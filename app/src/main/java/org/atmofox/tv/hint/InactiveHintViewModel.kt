/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.hint

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Used when a user is part of an experiment that does not show the hint bar
 */
class InactiveHintViewModel : HintViewModel {
    override val isDisplayed: Flow<Boolean> = flowOf(false)
    override val hints: Flow<List<HintContent>> = flowOf(listOf())
}
