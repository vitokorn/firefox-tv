/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose.engine

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.session.SessionFeature
import org.atmofox.tv.ext.onPauseIfNotNull
import org.atmofox.tv.ext.onResumeIfNotNull
import org.atmofox.tv.ext.serviceLocator

/**
 * Compose wrapper around [EngineView] using AndroidView interop.
 *
 * Handles EngineView lifecycle via [SessionFeature], matching the behavior
 * of [EngineViewLifecycleFragment] but adapted for Compose composition.
 */
@Composable
fun EngineViewCompose(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val serviceLocator = context.serviceLocator

    // Reuse cached EngineView so web page state and native memory survive
    // across BrowserScreen ↔ MenuOverlay composition switches.
    val engineView = remember(context) {
        serviceLocator.engineViewCache.getEngineView(context)
    }

    AndroidView(
        factory = { _ ->
            // Remove from previous parent before re-adding.
            (engineView.asView().parent as? ViewGroup)?.removeView(engineView.asView())
            engineView.asView()
        },
        modifier = modifier
    )

    DisposableEffect(engineView) {
        val sessionFeature = SessionFeature(
            store = serviceLocator.store,
            goBackUseCase = serviceLocator.sessionUseCases.goBack,
            goForwardUseCase = serviceLocator.sessionUseCases.goForward,
            engineView = engineView
        )
        sessionFeature.start()
        engineView.onResumeIfNotNull()

        // Wire up Gecko-specific delegates if needed (no-op on system flavor).
        serviceLocator.engineViewCache.setupSessionDelegateIfNeeded()

        onDispose {
            sessionFeature.stop()
            engineView.onPauseIfNotNull()
        }
    }
}
