/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.hint

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.atmofox.tv.session.SessionRepo

/**
 * Contains business logic for, and exposes data to the hint bar.
 *
 * Although the exposed data is the same between this and [WebRenderHintViewModel],
 * the business logic, dependencies, and API are all substantially
 * different. As the exposed data is the most trivial part of the implementation,
 * these were broken into two classes.
 */
class OverlayHintViewModel(
    sessionRepo: SessionRepo,
    closeMenuHint: HintContent
) : HintViewModel {
    // TODO this will require an additional dependency when overlay
    // hint is updated to change contextually according to the currently focused view

    override val isDisplayed: Flow<Boolean> = sessionRepo.state
        .map { it.backEnabled }

    override val hints: Flow<List<HintContent>> = kotlinx.coroutines.flow.flowOf(listOf(closeMenuHint))
}
