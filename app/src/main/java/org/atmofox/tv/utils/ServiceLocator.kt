/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// We want the Pocket code from a-c: #1976. Unfortunately, the compiler won't let
// us suppress individual lines so we have to suppress the file.
@file:Suppress("DEPRECATION")

package org.atmofox.tv.utils

import android.app.Application
import kotlinx.coroutines.flow.MutableSharedFlow
import org.atmofox.tv.ScreenController
import org.atmofox.tv.ValidatedIntentData
import org.atmofox.tv.architecture.ViewModelFactory
import org.atmofox.tv.channels.ChannelRepo
import org.atmofox.tv.channels.pinnedtile.PinnedTileImageUtilWrapper
import org.atmofox.tv.channels.pinnedtile.PinnedTileRepo
import org.atmofox.tv.experiments.ExperimentsProvider
import org.atmofox.tv.experiments.FretboardProvider
import org.atmofox.tv.ext.getAccessibilityManager
import org.atmofox.tv.ext.webRenderComponents
import org.atmofox.tv.framework.FrameworkRepo
import org.atmofox.tv.fxa.FxaLoginUseCase
import org.atmofox.tv.fxa.FxaRepo
import org.atmofox.tv.session.SessionRepo
import org.atmofox.tv.settings.SettingsRepo
import org.atmofox.tv.webrender.EngineViewCache
import org.atmofox.tv.webrender.cursor.CursorModel

/**
 * Implementation of the Service Locator pattern. Use this class to provide dependencies without
 * making client code aware of their specific implementations (i.e., make it easier to program to
 * an interface).
 *
 * This also makes it easier to mock out dependencies during testing.
 *
 * See: https://en.wikipedia.org/wiki/Service_locator_pattern
 *
 * ### Dependencies can be defined as follows:
 *
 *   #### Lazy, app-wide Singleton:
 *   ```
 *   open val pocket by lazy { Pocket() }
 *   ```
 *
 *   #### Eager, app-wide singleton:
 *   ```
 *   open val pocket = Pocket()
 *   ```
 *
 *   #### New value each time:
 *   ```
 *   open val pocket: Pocket get() = Pocket()
 *   ```
 *
 *   #### Concrete value for interface:
 *   ```
 *   open val telemetry: TelemetryInterface by lazy { SentryWrapper() }
 *   ```
 */
open class ServiceLocator(val app: Application) {
    val intentFlow by lazy { MutableSharedFlow<ValidatedIntentData?>(extraBufferCapacity = 1) }
    val fretboardProvider: FretboardProvider by lazy { FretboardProvider(app) }
    val experimentsProvider by lazy { ExperimentsProvider(fretboardProvider.fretboard, app) }
    val turboMode: TurboMode by lazy { TurboMode(app) }
    val viewModelFactory by lazy { ViewModelFactory(this, app) }
    val screenController by lazy { ScreenController(sessionRepo) }
    val engineViewCache by lazy { EngineViewCache(sessionRepo) }
    val store get() = app.webRenderComponents.store
    val sessionUseCases get() = app.webRenderComponents.sessionUseCases
    val cursorModel by lazy { CursorModel(screenController.currentActiveScreen, frameworkRepo, sessionRepo) }
    val screenshotStoreWrapper by lazy { PinnedTileImageUtilWrapper(app) }
    val formattedDomainWrapper by lazy { FormattedDomainWrapper(app) }
    val channelRepo by lazy { ChannelRepo(app, screenshotStoreWrapper, formattedDomainWrapper, pinnedTileRepo) }
    val fxaRepo by lazy { FxaRepo(app) }
    val fxaLoginUseCase by lazy { FxaLoginUseCase(fxaRepo, sessionRepo, screenController) }
    val deviceInfo by lazy { DeviceInfo() }

    // These open vals are overridden in testing
    open val frameworkRepo by lazy { FrameworkRepo.newInstanceAndInit(app.getAccessibilityManager()) }
    open val pinnedTileRepo by lazy { PinnedTileRepo(app) }
    open val sessionRepo by lazy { SessionRepo(store, sessionUseCases, turboMode).apply { observeSources() } }
    open val settingsRepo by lazy { SettingsRepo(app) }
}
