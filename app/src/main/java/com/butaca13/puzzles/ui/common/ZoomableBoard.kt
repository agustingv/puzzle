package com.butaca13.puzzles.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.butaca13.puzzles.R
import kotlin.math.abs

private const val MAX_SCALE = 3f
private const val BUTTON_ZOOM_STEP = 1.5f
private val ControlsHeight = 48.dp
private val ControlsGap = 4.dp

/** Current zoom of a board. [offset] is in pixels of the viewport, always keeping it covered. */
@Stable
class BoardZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** Side of the visible area; changes when the layout gives the board a different size. */
    internal var viewport = 0f
        set(value) {
            field = value
            offset = clamped(offset)
        }

    /** Zooms by [factor] keeping the content under [focus], a viewport position, where it is. */
    fun zoomBy(factor: Float, focus: Offset = Offset(viewport / 2, viewport / 2)) {
        val newScale = (scale * factor).coerceIn(1f, MAX_SCALE)
        val content = toContent(focus)
        scale = newScale
        offset = clamped(focus - content * newScale)
    }

    fun panBy(delta: Offset) {
        offset = clamped(offset + delta)
    }

    /** Pans just enough to bring a cell, given as fractions of the board, into view. */
    fun reveal(left: Float, top: Float, cellFraction: Float) {
        val cell = cellFraction * viewport * scale

        fun adjust(current: Float, start: Float): Float {
            val position = start * viewport * scale + current
            return when {
                position < 0f -> current - position
                position + cell > viewport -> current - (position + cell - viewport)
                else -> current
            }
        }

        offset = clamped(Offset(adjust(offset.x, left), adjust(offset.y, top)))
    }

    /** Board position under a viewport position, as fractions of the board (0..1 when on it). */
    fun toBoardFraction(position: Offset): Offset = toContent(position) / viewport.coerceAtLeast(1f)

    private fun toContent(position: Offset) = (position - offset) / scale

    private fun clamped(value: Offset): Offset {
        val min = viewport * (1f - scale)
        return Offset(value.x.coerceIn(min, 0f), value.y.coerceIn(min, 0f))
    }
}

/**
 * Square board that can optionally zoom: two fingers pinch and pan, as do the +/− buttons, which
 * keep zoom reachable for anyone who cannot pinch. What one finger does depends on the game: with
 * [singleFingerPans] a drag moves the board (only tapping selects), otherwise the drag is reported.
 * With [onDoubleTap], a second tap soon after one and near it is reported as such, instead of as a
 * tap: the first one has been reported already, at once, so that a single tap never waits. Callbacks
 * receive positions as fractions of the board, so games can stay unaware of the zoom.
 */
@Composable
fun ZoomableBoard(
    side: Dp,
    state: BoardZoomState,
    zoomEnabled: Boolean,
    onTap: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    singleFingerPans: Boolean = false,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDoubleTap: ((Offset) -> Unit)? = null,
    content: @Composable (boardSide: Dp) -> Unit,
) {
    val boardSide = if (zoomEnabled) side - ControlsHeight - ControlsGap else side
    val tap by rememberUpdatedState(onTap)
    val dragStart by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val dragCancel by rememberUpdatedState(onDragCancel)
    val zooms by rememberUpdatedState(zoomEnabled)
    val pans by rememberUpdatedState(singleFingerPans)
    val doubleTap by rememberUpdatedState(onDoubleTap)

    Column(
        modifier = modifier.width(side),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ControlsGap),
    ) {
        Box(
            modifier = Modifier
                .size(boardSide)
                .clipToBounds()
                // A drag starting near the screen edge is for the board, not the system's back gesture.
                .systemGestureExclusion()
                .onSizeChanged { state.viewport = it.width.toFloat() }
                .pointerInput(state) {
                    // The last single tap: where it was, and when its finger lifted.
                    var lastTap: Offset? = null
                    var lastTapUp = 0L

                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()

                        var mode = GestureMode.UNDECIDED
                        var previous = down.position

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            event.changes.forEach { it.consume() }

                            if (pressed.isEmpty()) {
                                // The lift has its own position, which can be past the last move
                                // event: a selection must end where the finger really left.
                                val lift = event.changes.firstOrNull { it.id == down.id }?.position ?: previous

                                if (mode == GestureMode.UNDECIDED &&
                                    (lift - down.position).getDistance() > viewConfiguration.touchSlop
                                ) {
                                    mode = if (pans) GestureMode.PAN else GestureMode.DRAG
                                    if (mode == GestureMode.DRAG) dragStart(state.toBoardFraction(down.position))
                                }

                                when (mode) {
                                    GestureMode.UNDECIDED -> {
                                        val second = doubleTap
                                        val previousTap = lastTap
                                        val isDouble = second != null && previousTap != null &&
                                            down.uptimeMillis - lastTapUp <= viewConfiguration.doubleTapTimeoutMillis &&
                                            (down.position - previousTap).getDistance() <= viewConfiguration.touchSlop * 4
                                        if (isDouble) {
                                            second(state.toBoardFraction(down.position))
                                            // A third tap starts over, as a single one.
                                            lastTap = null
                                        } else {
                                            tap(state.toBoardFraction(down.position))
                                            lastTap = down.position
                                            lastTapUp = event.changes.firstOrNull { it.id == down.id }?.uptimeMillis ?: down.uptimeMillis
                                        }
                                    }
                                    GestureMode.DRAG -> {
                                        drag(state.toBoardFraction(lift))
                                        dragEnd()
                                    }
                                    GestureMode.PAN -> state.panBy(lift - previous)
                                    GestureMode.TRANSFORM -> Unit
                                }
                                break
                            }

                            if (zooms && pressed.size >= 2) {
                                if (mode == GestureMode.DRAG) dragCancel()
                                mode = GestureMode.TRANSFORM
                                state.zoomBy(event.calculateZoom(), event.calculateCentroid())
                                state.panBy(event.calculatePan())
                                continue
                            }

                            // Lifting one of two fingers must not turn the rest into a tap or drag.
                            if (mode == GestureMode.TRANSFORM) continue

                            val position = event.changes.firstOrNull { it.id == down.id }?.position ?: continue

                            if (mode == GestureMode.UNDECIDED &&
                                (position - down.position).getDistance() > viewConfiguration.touchSlop
                            ) {
                                mode = if (pans) GestureMode.PAN else GestureMode.DRAG
                                if (mode == GestureMode.DRAG) dragStart(state.toBoardFraction(down.position))
                            }

                            when (mode) {
                                GestureMode.PAN -> state.panBy(position - previous)
                                GestureMode.DRAG -> drag(state.toBoardFraction(position))
                                GestureMode.UNDECIDED, GestureMode.TRANSFORM -> Unit
                            }
                            previous = position
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = state.scale
                        scaleY = state.scale
                        translationX = state.offset.x
                        translationY = state.offset.y
                    },
            ) {
                content(boardSide)
            }
        }

        if (zoomEnabled) {
            val zoomOut = stringResource(R.string.zoom_out)
            val zoomIn = stringResource(R.string.zoom_in)

            Row(
                modifier = Modifier.height(ControlsHeight),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { state.zoomBy(1 / BUTTON_ZOOM_STEP) },
                    enabled = state.scale > 1f,
                    modifier = Modifier.semantics { contentDescription = zoomOut },
                ) { Text("−") }
                OutlinedButton(
                    onClick = { state.zoomBy(BUTTON_ZOOM_STEP) },
                    enabled = abs(state.scale - MAX_SCALE) > 0.01f,
                    modifier = Modifier.semantics { contentDescription = zoomIn },
                ) { Text("+") }
            }
        }
    }
}

private enum class GestureMode { UNDECIDED, DRAG, PAN, TRANSFORM }
