package com.butaca13.puzzles.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The gestures adb cannot produce: real two-finger pinch and pan, through the real input path. */
@RunWith(AndroidJUnit4::class)
class ZoomableBoardTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var state: BoardZoomState
    private val taps = mutableListOf<Offset>()
    private val drags = mutableListOf<Offset>()
    private var dragEnds = 0
    private var dragCancels = 0

    private fun show(zoomEnabled: Boolean = true, singleFingerPans: Boolean = false) {
        rule.setContent {
            state = remember { BoardZoomState() }
            ZoomableBoard(
                side = 300.dp,
                state = state,
                zoomEnabled = zoomEnabled,
                singleFingerPans = singleFingerPans,
                onTap = { taps += it },
                onDragStart = { drags += it },
                onDrag = { drags += it },
                onDragEnd = { dragEnds++ },
                onDragCancel = { dragCancels++ },
                modifier = Modifier.testTag("board"),
            ) { side -> Box(Modifier.size(side)) }
        }
    }

    private fun pinchOut() {
        rule.onNodeWithTag("board").performTouchInput {
            pinch(
                start0 = center - Offset(20f, 0f),
                end0 = center - Offset(180f, 0f),
                start1 = center + Offset(20f, 0f),
                end1 = center + Offset(180f, 0f),
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun pinchZooms_withoutReportingTapOrDrag() {
        show()
        pinchOut()

        assertTrue("scale ${state.scale}", state.scale > 1.5f)
        assertTrue(taps.isEmpty())
        assertTrue(drags.isEmpty())
    }

    @Test
    fun zoomIsDisabledOnSmallBoards() {
        show(zoomEnabled = false)
        pinchOut()

        assertEquals(1f, state.scale)
    }

    @Test
    fun twoFingersPanTheZoomedBoard() {
        show()
        pinchOut()
        val before = state.offset
        val scaleBefore = state.scale

        rule.onNodeWithTag("board").performTouchInput {
            down(0, center - Offset(30f, 0f))
            down(1, center + Offset(30f, 0f))
            // Both fingers in the same event, as in a real two-finger drag: moving them
            // one at a time would change the distance between them, which reads as a pinch.
            repeat(4) {
                updatePointerBy(0, Offset(-30f, -30f))
                updatePointerBy(1, Offset(-30f, -30f))
                move()
            }
            up(0)
            up(1)
        }
        rule.waitForIdle()

        assertEquals("a parallel drag must not zoom", scaleBefore, state.scale, 0.01f)
        assertTrue("x ${before.x} -> ${state.offset.x}", state.offset.x < before.x)
        assertTrue("y ${before.y} -> ${state.offset.y}", state.offset.y < before.y)
    }

    @Test
    fun oneFingerDragIsReportedAndDoesNotPan_inSelectionMode() {
        show()
        pinchOut()
        val before = state.offset

        rule.onNodeWithTag("board").performTouchInput {
            down(center)
            moveBy(Offset(100f, 0f))
            moveBy(Offset(100f, 0f))
            up()
        }
        rule.waitForIdle()

        assertEquals(before, state.offset)
        assertTrue(drags.isNotEmpty())
        assertEquals(1, dragEnds)
    }

    @Test
    fun oneFingerDragPansAndTapStillWorks_inPanMode() {
        show(singleFingerPans = true)
        pinchOut()
        val before = state.offset

        rule.onNodeWithTag("board").performTouchInput {
            down(center)
            moveBy(Offset(-100f, 0f))
            moveBy(Offset(-100f, 0f))
            up()
        }
        rule.waitForIdle()

        assertTrue(state.offset.x < before.x)
        assertTrue("a pan is not a tap", taps.isEmpty())
        assertTrue(drags.isEmpty())

        rule.onNodeWithTag("board").performTouchInput {
            down(center)
            up()
        }
        rule.waitForIdle()
        assertEquals(1, taps.size)
    }

    /** Regression: selections in word search sometimes ended one move short of the finger. */
    @Test
    fun dragEndsWhereTheFingerLifts_evenPastTheLastMoveEvent() {
        show(zoomEnabled = false)

        rule.onNodeWithTag("board").performTouchInput {
            down(Offset(10f, 10f))
            moveBy(Offset(100f, 0f))
            updatePointerBy(0, Offset(200f, 0f))
            up()
        }
        rule.waitForIdle()

        val expected = rule.onNodeWithTag("board").fetchSemanticsNode().size.width.toFloat()
        assertEquals(310f / expected, drags.last().x, 0.01f)
        assertEquals(1, dragEnds)
    }

    @Test
    fun quickFlickWithNoMoveEventIsADragNotATap() {
        show(zoomEnabled = false)

        rule.onNodeWithTag("board").performTouchInput {
            down(Offset(10f, 10f))
            updatePointerBy(0, Offset(300f, 0f))
            up()
        }
        rule.waitForIdle()

        assertTrue("a flick is not a tap", taps.isEmpty())
        assertEquals(1, dragEnds)
    }

    @Test
    fun tapPositionAccountsForZoom() {
        show(zoomEnabled = false)

        rule.onNodeWithTag("board").performTouchInput {
            down(center)
            up()
        }
        rule.waitForIdle()

        assertEquals(0.5f, taps.single().x, 0.02f)
        assertEquals(0.5f, taps.single().y, 0.02f)
    }

    @Test
    fun addingASecondFingerCancelsADragInProgress() {
        show()

        rule.onNodeWithTag("board").performTouchInput {
            down(0, center)
            moveBy(0, Offset(80f, 0f))
            moveBy(0, Offset(80f, 0f))
            down(1, center - Offset(100f, 100f))
            moveBy(1, Offset(-10f, -10f))
            up(0)
            up(1)
        }
        rule.waitForIdle()

        assertEquals(1, dragCancels)
        assertEquals(0, dragEnds)
    }
}
