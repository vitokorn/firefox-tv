/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.webrender.cursor

import android.annotation.SuppressLint
import android.graphics.PointF
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.annotation.CheckResult
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import org.atmofox.tv.ScreenControllerStateMachine
import org.atmofox.tv.ScreenControllerStateMachine.ActiveScreen.WEB_RENDER
import org.atmofox.tv.ext.isKeyCodeSelect
import org.atmofox.tv.ext.isUriFxaSignIn
import org.atmofox.tv.ext.isUriYouTubeTV
import org.atmofox.tv.ext.toDirection
import org.atmofox.tv.framework.FrameworkRepo
import org.atmofox.tv.session.SessionRepo
import org.atmofox.tv.utils.Direction
import org.atmofox.tv.utils.toObservableMutableSet

// Constants that we expect to be tweaked in order to adjust cursor behavior
private const val INITIAL_VELOCITY = 0f
private const val MAX_VELOCITY = 21f
private const val MS_TO_MAX_VELOCITY = 600
private const val MAX_SCROLL_VELOCITY = 16

// Other constants
private const val VELOCITY_TO_ACCELERATE = MAX_VELOCITY - INITIAL_VELOCITY
private const val ACCELERATION_PER_MS = VELOCITY_TO_ACCELERATE / MS_TO_MAX_VELOCITY
// 60 FPS = 16.6 repeating millis/frame.
// This is only used to draw one frame, so it's alright that it's only an approximation
private const val MS_PER_FRAME = 16
private const val LAST_UPDATE_AT_MS_UNSET = -1L

/**
 * @param [wasKeyEventConsumed] represents whether or not the passed [KeyEvent] was consumed
 * @param [simulatedTouch] a [MotionEvent]. If it is not null, this should be dispatched to an Activity
 */
data class HandleKeyEventResult(val wasKeyEventConsumed: Boolean, val simulatedTouch: MotionEvent?)

sealed class CursorEvent {
    /**
     * This represents the user moving the cursor to the edge of the screen,
     * attempting to scroll, and failing because the website has no more
     * content in that direction.
     */
    data class ScrolledToEdge(val edge: Direction) : CursorEvent() // TODO how often does this happen?  Telemetry would be good
    data class CursorMoved(val direction: Direction) : CursorEvent()
}

/**
 * Handles the cursor state and business logic.
 *
 * PLEASE READ BEFORE MODIFYING THIS CLASS: it operates VERY differently from the rest of
 * the codebase. This was necessary for performance reasons.
 *
 * Because this class is very long, you may want to break it up: we caution against this. Any
 * abstractions we created were unintuitive and inconsistent with the rest of the code, making the
 * class more confusing for unfamiliar developers.
 *
 * This implementation is not thread safe.
 *
 * ## Architecture overview
 * The cursor code is broken up into a model ([CursorModel]) and a view ([CursorView]).
 * - [CursorModel] handles internal cursor state and business logic
 * - [CursorView] handles view state (except for position updates)
 *
 * These classes are untraditional in that [CursorView] triggers position updates in the model,
 * as opposed to the Model pushing updates to the View. This happens in [CursorView.onDraw]:
 * when each frame is drawn, onDraw requests a position update from [CursorModel].  This ensures
 * that we update position exactly once per frame.
 *
 * ## Problem this solved
 * Our original solution pushed a new position to the View every 16 MS (approximately 60 FPS), but
 * the view drew every 15-17 MS.  This discrepancy led to the appearance of dropped frames.
 *
 * Our solution is to have the View drive the interaction, ensuring exactly one update per frame.
 * This is effective, but inconsistent with our architecture.
 */
@Suppress("LargeClass") // See kdoc above for details.
class CursorModel(
    activeScreen: Flow<ScreenControllerStateMachine.ActiveScreen>,
    frameworkRepo: FrameworkRepo,
    sessionRepo: SessionRepo
) {
    // This is set early in the Fragment lifecycle. Most methods short if it is not available
    var screenBounds: PointF? = null

    private var lastKnownCursorPos = PointF(0f, 0f)
    private var isInitialCursorPositionSet = false
    private var lastVelocity = INITIAL_VELOCITY
    private var lastUpdatedAtMS = LAST_UPDATE_AT_MS_UNSET
    private val scrollDistanceMutableCache = PointF(0f, 0f)

    private val directionKeysPressed = mutableSetOf<Direction>().toObservableMutableSet().apply {
        attachObserver { _isAnyCursorKeyPressed.value = this.isNotEmpty() }
    }
    private val _isAnyCursorKeyPressed = MutableStateFlow(false)
    val isAnyCursorKeyPressed: StateFlow<Boolean> = _isAnyCursorKeyPressed.asStateFlow()

    private val _cursorMovedEvents = MutableSharedFlow<CursorEvent>(extraBufferCapacity = 1)
    val cursorMovedEvents: SharedFlow<CursorEvent> = _cursorMovedEvents.asSharedFlow()

    private val _scrollRequests = MutableSharedFlow<PointF>(extraBufferCapacity = 1)
    val scrollRequests: SharedFlow<PointF> = _scrollRequests.asSharedFlow()

    private val _isCursorMoving = MutableStateFlow(false)
    val isCursorMoving: StateFlow<Boolean> = _isCursorMoving.asStateFlow()

    private val _isSelectPressed = MutableStateFlow(false)
    val isSelectPressed: StateFlow<Boolean> = _isSelectPressed.asStateFlow()

    var webViewCouldScrollInDirectionProvider: (Direction) -> Boolean = { false }

    var simulateTouchEvent: (MotionEvent) -> Unit = { throw NotImplementedError("You must set simulateTouchEvent") }

    // Note that we only redraw the cursor when it's enabled. For a theoretical optimization, we could
    // defer cursor drawing when we scroll, but this would require keeping track of when a scroll ends
    // and this is complex. As such, we just redraw at 60FPS instead.
    val isCursorEnabledForAppState = combine(
        activeScreen,
        frameworkRepo.isVoiceViewEnabled,
        sessionRepo.state
    ) { activeScreen, isVoiceViewEnabled, sessionRepoState ->
        activeScreen == WEB_RENDER && !isVoiceViewEnabled && sessionRepoState?.loading == false &&
                !sessionRepoState.currentUrl.isUriFxaSignIn && !sessionRepoState.currentUrl.isUriYouTubeTV
    }.stateIn(GlobalScope, SharingStarted.Eagerly, false)

    init { attachResetStateObserver() }

    private fun attachResetStateObserver() {
        isCursorEnabledForAppState
                .onEach { enabled ->
                    if (!enabled) {
                        directionKeysPressed.clear()
                        _isCursorMoving.value = false
                        _isSelectPressed.value = false
                        lastKnownCursorPos = PointF(0f, 0f)
                        isInitialCursorPositionSet = false
                    }
                }
                .launchIn(GlobalScope)
    }

    @CheckResult(suggest = "#filterMapToDirection or handle in a switch statement")
    fun handleKeyEvent(event: KeyEvent): HandleKeyEventResult {
        return when {
            event.isKeyCodeSelect -> handleSelectKeyEvent(event)
            Direction.KEY_CODES.contains(event.keyCode) -> handleDirectionKeyEvent(event)
            else -> HandleKeyEventResult(wasKeyEventConsumed = false, simulatedTouch = null)
        }
    }

    private fun handleDirectionKeyEvent(event: KeyEvent): HandleKeyEventResult {
        fun getResult(wasKeyEventConsumed: Boolean) =
            HandleKeyEventResult(wasKeyEventConsumed, simulatedTouch = null)

        if (!isCursorEnabledForAppState.value) {
            return getResult(false)
        }
        require(Direction.KEY_CODES.contains(event.keyCode)) {
            "Invalid key event passed to CursorController#handleDirectionKeyEvent: $event"
        }

        val direction = event.toDirection() ?: return getResult(false)
        when (event.action) {
            KeyEvent.ACTION_UP -> directionKeysPressed -= direction
            KeyEvent.ACTION_DOWN -> {
                directionKeysPressed += direction
                pushCursorEvent(direction)
            }
            else -> return getResult(false)
        }

        return getResult(true)
    }

    private fun pushCursorEvent(direction: Direction) {
        val edgeNearCursor = getEdgeOfScreenNearCursor()
        val couldScroll = edgeNearCursor?.let { webViewCouldScrollInDirectionProvider?.invoke(it) }

        val cursorMovedToEdgeOfScreen = edgeNearCursor == direction
        val endOfDomContentReached = couldScroll == false

        val event = if (cursorMovedToEdgeOfScreen && endOfDomContentReached) {
            CursorEvent.ScrolledToEdge(direction)
        } else {
            CursorEvent.CursorMoved(direction)
        }

        _cursorMovedEvents.tryEmit(event)
    }

    @SuppressLint("Recycle") // Caller is expected to recycle the MotionEvent.
    @CheckResult(suggest = "Recycle MotionEvent after use")
    private fun handleSelectKeyEvent(event: KeyEvent): HandleKeyEventResult {
        fun getResult(motionEvent: MotionEvent?) =
            HandleKeyEventResult(wasKeyEventConsumed = motionEvent != null, simulatedTouch = motionEvent)

        if (!isCursorEnabledForAppState.value) {
            return getResult(null)
        }

        require(event.isKeyCodeSelect) {
            "Expected DPAD_CENTER or ENTER. Instead: $event"
        }

        fun buildMotionEvent() = MotionEvent.obtain(event.downTime, event.eventTime, event.action,
                lastKnownCursorPos.x, lastKnownCursorPos.y, 0)

        val motionEvent = when (event.action) {
            KeyEvent.ACTION_UP -> {
                _isSelectPressed.value = false
                buildMotionEvent()
            }
            KeyEvent.ACTION_DOWN -> {
                _isSelectPressed.value = true
                buildMotionEvent()
            }
            else -> null
        }

        return getResult(motionEvent)
    }

    /**
     * See [CursorModel] kdoc for details.
     *
     * @return whether or not the view should continue to invalidate itself
     * Note that in addition to returning a value, this function also mutates [oldPosAndReturnedPos]
     */
    fun mutatePosition(oldPosAndReturnedPos: PointF): Boolean {
        lastKnownCursorPos = oldPosAndReturnedPos
        when {
            !isInitialCursorPositionSet -> {
                if (screenBounds != null) {
                    oldPosAndReturnedPos.x = screenBounds!!.x / 2
                    oldPosAndReturnedPos.y = screenBounds!!.y / 2
                    isInitialCursorPositionSet = true
                }
                return true
            }
            directionKeysPressed.isEmpty() -> {
                resetCursorState()
                return false
            }
            else -> {
                val currTime = System.currentTimeMillis()
                // If we're starting a new update loop, we don't have a previous update value. Instead,
                // we assume an average amount of time has passed (one frame).
                if (lastUpdatedAtMS == LAST_UPDATE_AT_MS_UNSET) lastUpdatedAtMS = currTime - MS_PER_FRAME
                val millisSinceLastMutation = currTime - lastUpdatedAtMS

                lastVelocity = internalMutatePositionAndReturnVelocity(
                        oldPosAndReturnedPos,
                        millisSinceLastMutation,
                        lastVelocity,
                        directionKeysPressed
                )

                calculateAndSendScrollEvent(millisSinceLastMutation, lastVelocity, oldPosAndReturnedPos)
                lastUpdatedAtMS = currTime
                return true
            }
        }
    }

    private fun calculateAndSendScrollEvent(millisPassed: Long, velocity: Float, newPos: PointF) {
        val approxFramesPassed = (millisPassed / MS_PER_FRAME).toInt()
        getScrollDistance(scrollDistanceMutableCache, velocity, newPos, approxFramesPassed) // mutates scrollDistance...
        if (scrollDistanceMutableCache.x != 0f || scrollDistanceMutableCache.y != 0f) {
            _scrollRequests.tryEmit(scrollDistanceMutableCache)
        }
    }

    /**
     * Mutates [oldPos] to its new position. This position is calculated based on the time passed
     * since the last update, in order to avoid miscalculations when dropping frames.
     *
     * Calculates necessary scrolling, if any, and sends out a request to handle it.
     *
     * @returns the new cursor velocity
     */
    private fun internalMutatePositionAndReturnVelocity(
        oldPos: PointF,
        millisSinceLastMutation: Long,
        oldVelocity: Float,
        directionsPressed: Set<Direction>
    ): Float {
        // directionsPressed empty case is handled in `mutatePosition`
        require(directionsPressed.isNotEmpty())
        val screenBounds = screenBounds ?: return 0f

        val accelerateBy = ACCELERATION_PER_MS * millisSinceLastMutation
        val velocity = (oldVelocity + accelerateBy).coerceIn(0f, MAX_VELOCITY)

        fun updatePosition() {
            var verticalVelocity = 0f
            if (directionKeysPressed.contains(Direction.UP)) verticalVelocity -= velocity
            if (directionKeysPressed.contains(Direction.DOWN)) verticalVelocity += velocity
            var horizontalVelocity = 0f
            if (directionKeysPressed.contains(Direction.LEFT)) horizontalVelocity -= velocity
            if (directionKeysPressed.contains(Direction.RIGHT)) horizontalVelocity += velocity
            oldPos.x += horizontalVelocity
            oldPos.y += verticalVelocity
            oldPos.x = oldPos.x.coerceIn(0f, screenBounds.x)
            oldPos.y = oldPos.y.coerceIn(0f, screenBounds.y)
        }

        updatePosition()

        return velocity
    }

    /**
     * If the cursor is near the edge of the screen, this will return that
     * direction.  If not, it will return null.
     *
     * When in a corner, only UP or DOWN will be returned.
     */
    private fun getEdgeOfScreenNearCursor(): Direction? {
        val screenBounds = screenBounds ?: return null
        return when {
            lastKnownCursorPos.y <= 0 -> Direction.UP
            lastKnownCursorPos.y >= screenBounds.y -> Direction.DOWN
            lastKnownCursorPos.x <= 0 -> Direction.LEFT
            lastKnownCursorPos.x >= screenBounds.x -> Direction.RIGHT
            else -> null
        }
    }

    private fun resetCursorState() {
        lastVelocity = INITIAL_VELOCITY
        lastUpdatedAtMS = LAST_UPDATE_AT_MS_UNSET
        directionKeysPressed.clear()
    }

    // This is taken from older code.  Crufty, but it works
    private fun getScrollDistance(scrollDistanceReturnValue: PointF, vel: Float, pos: PointF, framesPassed: Int) {
        val screenBounds = screenBounds
        if (screenBounds == null) {
            scrollDistanceReturnValue.x = 0f
            scrollDistanceReturnValue.y = 0f
            return
        }

        var scrollVelX = 0f
        var scrollVelY = 0f
        if (vel > 0f) {
            val percentMaxVel = vel / MAX_VELOCITY
            if (pos.x == 0f && directionKeysPressed.contains(Direction.LEFT)) {
                scrollVelX = -percentMaxVel
            } else if (pos.x == screenBounds.x && directionKeysPressed.contains(Direction.RIGHT)) {
                scrollVelX = percentMaxVel
            }

            if (pos.y == 0f && directionKeysPressed.contains(Direction.UP)) {
                scrollVelY = -percentMaxVel
            } else if (pos.y == screenBounds.y && directionKeysPressed.contains(Direction.DOWN)) {
                scrollVelY = percentMaxVel
            }
        }

        scrollDistanceReturnValue.x = (scrollVelX * MAX_SCROLL_VELOCITY * framesPassed)
        scrollDistanceReturnValue.y = (scrollVelY * MAX_SCROLL_VELOCITY * framesPassed)
    }

}
