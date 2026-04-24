/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.session

import mozilla.components.browser.state.store.BrowserStore

/**
 * A facade to simplify the process of observing [BrowserStore] state changes
 * and sending their information to a [SessionRepo].
 *
 * browser-session Session/SessionManager removed in 128.x - replaced by browser-state.
 */
class SessionObserverHelper private constructor(sessionRepo: SessionRepo) {

    companion object {
        fun attach(sessionRepo: SessionRepo, store: BrowserStore) {
            // Observe BrowserStore state changes directly
            store.observeManually { sessionRepo.update() }
        }
    }
}
