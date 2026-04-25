/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.webrender

import androidx.lifecycle.ViewModel
import io.reactivex.Observable
import org.atmofox.tv.R
import org.atmofox.tv.ScreenController
import org.atmofox.tv.ScreenControllerStateMachine.ActiveScreen
import org.atmofox.tv.fxa.FxaLoginUseCase

class WebRenderViewModel(
    screenController: ScreenController,
    fxaLoginUseCase: FxaLoginUseCase
) : ViewModel() {

    val onFxaLoginSuccess = fxaLoginUseCase.onLoginSuccess

    val focusRequests: Observable<Int> = screenController.currentActiveScreen
            .filter { currentScreen -> currentScreen == ActiveScreen.WEB_RENDER }
            .map { R.id.engineView }
}
