/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.ext

import mozilla.components.browser.state.state.TabSessionState

// Extension methods on TabSessionState (replaces removed Session class from browser-session).

val TabSessionState.isYoutubeTV: Boolean
    get() = content.url.isUriYouTubeTV
