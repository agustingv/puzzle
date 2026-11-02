package com.agustingv.puzzles.ui.common

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agustingv.puzzles.data.GameEvent
import com.agustingv.puzzles.data.GameFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A phone's motor can not be felt from a test, so this checks what is asked of it. */
@RunWith(AndroidJUnit4::class)
class GameFeedbackTest {

    @get:Rule
    val rule = createComposeRule()

    private val played = mutableListOf<HapticFeedbackType>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            played += hapticFeedbackType
        }
    }
    private val visuals = FeedbackVisuals()
    private var feedback by mutableStateOf<GameFeedback?>(null)

    private fun show() {
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                GameFeedbackEffect(feedback, visuals)
            }
        }
    }

    private fun send(event: GameEvent, id: Int) {
        feedback = GameFeedback(event, id)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
    }

    @Test
    fun eachEventAsksForItsVibration() {
        show()

        send(GameEvent.TICK, 1)
        assertEquals(listOf(HapticFeedbackType.TextHandleMove), played)

        played.clear()
        send(GameEvent.HIT, 2)
        assertEquals(listOf(HapticFeedbackType.Confirm), played)

        played.clear()
        send(GameEvent.MISS, 3)
        assertEquals(listOf(HapticFeedbackType.Reject), played)

        played.clear()
        send(GameEvent.LOSE, 4)
        assertEquals(listOf(HapticFeedbackType.Reject), played)
    }

    @Test
    fun winningVibratesTwiceAndStartsConfetti() {
        show()

        send(GameEvent.WIN, 1)

        assertEquals(listOf(HapticFeedbackType.Confirm, HapticFeedbackType.Confirm), played)
        assertEquals(1, visuals.confettiBursts)
    }

    @Test
    fun onlyWinningStartsConfetti() {
        show()

        send(GameEvent.HIT, 1)
        send(GameEvent.MISS, 2)
        send(GameEvent.LOSE, 3)

        assertEquals(0, visuals.confettiBursts)
    }

    @Test
    fun aMissShakesTheBoardAndLeavesItInPlace() {
        show()
        rule.mainClock.autoAdvance = false

        feedback = GameFeedback(GameEvent.MISS, 1)

        // Sampled along the way: a shake crosses zero, so one instant alone proves nothing.
        val offsets = List(30) {
            rule.mainClock.advanceTimeBy(16)
            visuals.shakeX
        }
        rule.mainClock.advanceTimeBy(1000)

        assertTrue("to the right: $offsets", offsets.max() > 5f)
        assertTrue("to the left: $offsets", offsets.min() < -5f)
        assertEquals(0f, visuals.shakeX)
    }

    @Test
    fun anEventAlreadyThereWhenTheScreenAppearsIsNotReplayed() {
        feedback = GameFeedback(GameEvent.WIN, 7)
        show()
        rule.waitForIdle()

        assertTrue(played.isEmpty())
        assertEquals(0, visuals.confettiBursts)
    }

    @Test
    fun rotatingTheScreenDoesNotReplayTheLastEvent() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                GameFeedbackEffect(feedback, visuals)
            }
        }

        feedback = GameFeedback(GameEvent.WIN, 1)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1000)
        assertEquals(2, played.size)

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1000)

        assertEquals(2, played.size)
        assertEquals(1, visuals.confettiBursts)
    }
}
