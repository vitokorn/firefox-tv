/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose.browser

import android.graphics.PointF
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.atmofox.tv.MainActivity
import org.atmofox.tv.ScreenControllerStateMachine
import org.atmofox.tv.compose.engine.EngineViewCompose
import org.atmofox.tv.compose.theme.PhotonGrey70
import org.atmofox.tv.compose.theme.TvGray2
import org.atmofox.tv.ext.addSubmitListenerToInputElements
import org.atmofox.tv.ext.couldScrollInDirection
import org.atmofox.tv.ext.focusedDOMElement
import org.atmofox.tv.ext.isUriYouTubeTV
import org.atmofox.tv.ext.isUrlWhitelistedForSubmitInputHack
import org.atmofox.tv.ext.maybeGoBackBeforeFxaSignIn
import org.atmofox.tv.ext.observeScrollPosition
import org.atmofox.tv.ext.pauseAllVideoPlaybacks
import org.atmofox.tv.ext.scrollByClamped
import org.atmofox.tv.ext.serviceLocator
import org.atmofox.tv.ext.setupForApp
import org.atmofox.tv.hint.InactiveHintViewModel
import org.atmofox.tv.session.SessionRepo
import org.atmofox.tv.webrender.WebRenderViewModel
import org.atmofox.tv.webrender.YouTubeBackHandler
import org.atmofox.tv.webrender.YoutubeGreyScreenWorkaround
import org.atmofox.tv.webrender.cursor.CursorView

/**
 * Main browser screen matching the legacy layout:
 * top nav buttons + URL bar, then the engine view filling the rest.
 */
@Composable
fun BrowserScreen(
    onOpenMenu: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        // Unified single-line toolbar: nav + URL + overflow + home + FxA + logo
        BrowserToolbar(
            onOpenMenu = onOpenMenu,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        // Engine view with floating progress pill at bottom-left
        val context = LocalContext.current
        val serviceLocator = context.serviceLocator
        val cursorModel = serviceLocator.cursorModel
        val cursorScope = rememberCoroutineScope()
        val engineView = remember(context) {
            serviceLocator.engineViewCache.getEngineView(context)
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        val activity = context as? MainActivity

        // Restore DOM-element cache / focus helpers that the legacy fragment wired via setupForApp().
        DisposableEffect(engineView) {
            engineView.setupForApp()
            onDispose { }
        }

        // Replicate legacy focusRequests: cache focused DOM element and request focus when
        // the browser screen becomes the active screen (#1830, #1850).
        val webRenderViewModel = remember(serviceLocator) {
            WebRenderViewModel(serviceLocator.screenController, serviceLocator.fxaLoginUseCase)
        }
        LaunchedEffect(webRenderViewModel.focusRequests) {
            webRenderViewModel.focusRequests.collect {
                engineView.focusedDOMElement.cache()
                engineView.asView().requestFocus()
            }
        }

        // YouTube back handling: SessionRepo emits YouTubeBack / ExitYouTube events which
        // the legacy fragment routed to YouTubeBackHandler.
        val youtubeBackHandler = remember(engineView, activity) {
            if (activity != null) YouTubeBackHandler(engineView, activity) else null
        }
        DisposableEffect(youtubeBackHandler) {
            val job = serviceLocator.sessionRepo.events.onEach { event ->
                when (event) {
                    SessionRepo.Event.YouTubeBack -> youtubeBackHandler?.onBackPressed()
                    SessionRepo.Event.ExitYouTube -> youtubeBackHandler?.goBackBeforeYouTube()
                }
            }.launchIn(cursorScope)
            onDispose { job.cancel() }
        }

        // YouTube grey-screen workaround (#1865): dispatch dpad keys on resume when on YouTube.
        DisposableEffect(lifecycleOwner, engineView) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val currentUrl = serviceLocator.sessionRepo.currentState().currentUrl
                    if (currentUrl.isUriYouTubeTV) {
                        YoutubeGreyScreenWorkaround.invoke(activity)
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // FxA login success → navigate back past FxA sign-in history (#2053).
        LaunchedEffect(engineView, webRenderViewModel) {
            webRenderViewModel.onFxaLoginSuccess.collect {
                engineView.maybeGoBackBeforeFxaSignIn()
            }
        }

        // Pause all video playbacks when leaving the browser screen (#1720).
        DisposableEffect(engineView) {
            val job = serviceLocator.screenController.currentActiveScreen.onEach { screen ->
                if (screen != ScreenControllerStateMachine.ActiveScreen.WEB_RENDER) {
                    engineView.pauseAllVideoPlaybacks()
                }
            }.launchIn(cursorScope)
            onDispose { job.cancel() }
        }

        // Inject page-load JS workarounds after loading completes.
        val sessionState by serviceLocator.sessionRepo.state.collectAsState()
        var wasLoading by remember { mutableStateOf(false) }
        LaunchedEffect(sessionState.loading, sessionState.currentUrl) {
            if (wasLoading && !sessionState.loading) {
                engineView.observeScrollPosition()
                if (sessionState.currentUrl.isUrlWhitelistedForSubmitInputHack) {
                    engineView.addSubmitListenerToInputElements()
                }
            }
            wasLoading = sessionState.loading
        }

        DisposableEffect(engineView, cursorModel) {
            cursorModel.webViewCouldScrollInDirectionProvider = { direction ->
                engineView.couldScrollInDirection(direction)
            }
            onDispose {
                cursorModel.webViewCouldScrollInDirectionProvider = { false }
            }
        }

        DisposableEffect(engineView, cursorModel, cursorScope) {
            val job = cursorModel.scrollRequests
                .onEach { scrollDist ->
                    engineView.scrollByClamped(scrollDist.x.toInt(), scrollDist.y.toInt())
                }
                .launchIn(cursorScope)
            onDispose { job.cancel() }
        }

        Box(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .onGloballyPositioned { coordinates ->
                cursorModel.screenBounds = PointF(
                    coordinates.size.width.toFloat(),
                    coordinates.size.height.toFloat()
                )
            }
        ) {
            EngineViewCompose(modifier = Modifier.fillMaxSize())
            BrowserProgressBar(
                modifier = Modifier.align(Alignment.BottomStart)
            )

            // Hint bar placeholder (WebRenderHintViewModel was removed pre-migration).
            val hintViewModel = remember { InactiveHintViewModel() }
            val isHintDisplayed by hintViewModel.isDisplayed.collectAsState(initial = false)
            val hints by hintViewModel.hints.collectAsState(initial = emptyList())
            androidx.compose.animation.AnimatedVisibility(
                visible = isHintDisplayed,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(PhotonGrey70)
                        .padding(start = 48.dp, end = 48.dp, top = 16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    val hint = hints.firstOrNull()
                    if (hint != null) {
                        Text(
                            text = hint.text,
                            fontSize = 18.sp,
                            color = TvGray2
                        )
                    }
                }
            }

            // TV Cursor overlay for D-pad navigation
            AndroidView(
                factory = { ctx ->
                    // Inflate CursorView from dedicated layout to get proper XML attributes
                    val layout = LayoutInflater.from(ctx).inflate(org.atmofox.tv.R.layout.cursor_view_layout, null, false)
                    val cursorView = layout.findViewById<org.atmofox.tv.webrender.cursor.CursorView>(org.atmofox.tv.R.id.cursorView)
                    // Remove from the temporary parent and set up
                    (cursorView.parent as? ViewGroup)?.removeView(cursorView)
                    // CursorView must fill the whole composable area so its canvas covers
                    // the full screenBounds used for positioning and scrolling.
                    cursorView.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    cursorView.apply {
                        setup(cursorModel, cursorScope)
                    }
                },
                modifier = Modifier.fillMaxSize()
                // CursorView.setup() already subscribes to isCursorEnabledForAppState
                // and toggles visibility internally. No update lambda needed.
            )
        }
    }
}
