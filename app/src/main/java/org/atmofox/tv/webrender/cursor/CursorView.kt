/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.webrender.cursor

import android.content.Context
import android.graphics.Canvas
import android.graphics.PointF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.atmofox.tv.R
import kotlin.time.Duration.Companion.seconds

private val BITMAP_PRESSED = R.drawable.cursor_full_active
private val BITMAP_UNPRESSED = R.drawable.cursor_full
private const val HIDE_ANIMATION_DURATION_MILLIS = 250L
private val HIDE_AFTER_MILLIS = 3.seconds.inWholeMilliseconds

/**
 * Handles view state (except for position updates).
 *
 * For details on the cursor architecture, see the [CursorModel] kdoc.
 *
 * Any images set must be Bitmaps, not other Drawables.
 */
class CursorView(context: Context, attrs: AttributeSet) : AppCompatImageView(context, attrs) {

    // This is a performance micro-optimization that avoid extra allocations, and should only be
    // called from onDraw. This is safe because it is only called from the UI thread, and so
    // cannot be accessed concurrently
    private val onDrawMutablePositionCache = PointF(x, y)

    private var cursorModel: CursorModel? = null

    init {
        setImageResource(BITMAP_UNPRESSED)
    }

    fun setup(cursorModel: CursorModel, scope: CoroutineScope) {
        this.cursorModel = cursorModel

        cursorModel.isCursorEnabledForAppState
                .onEach { isEnabled ->
                    isVisible = isEnabled
                    if (!isEnabled) {
                        animate().cancel()
                    }
                }
                .launchIn(scope)

        cursorModel.isAnyCursorKeyPressed
                .onEach { cursorIsMovingOrPressed ->
                    if (cursorIsMovingOrPressed) {
                        animate().cancel()
                        alpha = 1f
                        invalidate()
                    } else {
                        animate()
                                .setStartDelay(HIDE_AFTER_MILLIS)
                                .setDuration(HIDE_ANIMATION_DURATION_MILLIS)
                                .alpha(0f)
                                .start()
                    }
                }
                .launchIn(scope)

        cursorModel.isSelectPressed
                .onEach { pressed -> when (pressed) {
                        true -> setImageResource(BITMAP_PRESSED)
                        false -> setImageResource(BITMAP_UNPRESSED)
                    } }
                .launchIn(scope)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // The amount that this view must be offset for it to appear centered
        // (otherwise the PointF we set would be its top left corner)
        val xOffset = width / 2
        val yOffset = height / 2

        onDrawMutablePositionCache.set(x + xOffset, y + yOffset)

        val shouldInvalidate = cursorModel?.mutatePosition(onDrawMutablePositionCache) ?: false
        x = onDrawMutablePositionCache.x - xOffset
        y = onDrawMutablePositionCache.y - yOffset

        // This method will stop being called when the app is backgrounded.
        if (shouldInvalidate) invalidate()
    }
}
