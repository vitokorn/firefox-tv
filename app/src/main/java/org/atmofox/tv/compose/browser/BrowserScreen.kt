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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.atmofox.tv.compose.engine.EngineViewCompose
import org.atmofox.tv.ext.couldScrollInDirection
import org.atmofox.tv.ext.scrollByClamped
import org.atmofox.tv.ext.serviceLocator
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
        val cursorModel = context.serviceLocator.cursorModel
        val cursorScope = rememberCoroutineScope()
        val engineView = remember(context) {
            context.serviceLocator.engineViewCache.getEngineView(context)
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
