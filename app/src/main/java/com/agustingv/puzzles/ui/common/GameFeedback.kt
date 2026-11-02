package com.agustingv.puzzles.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.agustingv.puzzles.data.GameEvent
import com.agustingv.puzzles.data.GameFeedback
import com.agustingv.puzzles.ui.theme.Accent
import com.agustingv.puzzles.ui.theme.Danger
import com.agustingv.puzzles.ui.theme.Pink
import com.agustingv.puzzles.ui.theme.Purple
import com.agustingv.puzzles.ui.theme.Teal
import com.agustingv.puzzles.ui.theme.Warn
import com.agustingv.puzzles.ui.theme.animationsEnabled
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val CONFETTI_MILLIS = 2200
private const val CONFETTI_PIECES = 90
private const val WIN_DOUBLE_TAP_MILLIS = 130L

/** What the screen shows in response to events: filled in by [GameFeedbackEffect]. */
@Stable
class FeedbackVisuals {
    /** Sideways offset of the board, in pixels: it shakes on a miss. */
    var shakeX by mutableFloatStateOf(0f)
        internal set

    /** Changes on each win, which sets off a new burst of confetti. */
    var confettiBursts by mutableIntStateOf(0)
        internal set
}

/**
 * Turns a session's events into vibration and motion. An event is handled once: rotating the screen
 * recreates this effect without replaying the last one. Vibration uses the system's haptics, which
 * need no permission and follow the user's setting; motion follows the system's animation setting.
 */
@Composable
fun GameFeedbackEffect(feedback: GameFeedback?, visuals: FeedbackVisuals) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val animate = remember(context) { animationsEnabled(context) }
    val shakeDistance = with(LocalDensity.current) { 10.dp.toPx() }
    var handledId by rememberSaveable { mutableIntStateOf(feedback?.id ?: -1) }

    LaunchedEffect(feedback) {
        if (feedback == null || feedback.id == handledId) return@LaunchedEffect
        handledId = feedback.id

        if (animate && feedback.event == GameEvent.WIN) visuals.confettiBursts++

        vibrate(haptics, feedback.event)

        if (animate && feedback.event == GameEvent.MISS) {
            val shake = Animatable(0f)
            shake.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 320
                    shakeDistance at 50
                    -shakeDistance at 110
                    shakeDistance * 0.6f at 170
                    -shakeDistance * 0.6f at 230
                    shakeDistance * 0.25f at 280
                },
            ) { visuals.shakeX = value }
            visuals.shakeX = 0f
        }
    }
}

private suspend fun vibrate(haptics: HapticFeedback, event: GameEvent) {
    when (event) {
        GameEvent.TICK -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        GameEvent.HIT -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        GameEvent.MISS, GameEvent.LOSE -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
        GameEvent.WIN -> {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            delay(WIN_DOUBLE_TAP_MILLIS)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
}

private class ConfettiPiece(random: Random, val color: androidx.compose.ui.graphics.Color) {
    val startX = random.nextFloat()
    val delay = random.nextFloat() * 0.25f
    val fall = 0.8f + random.nextFloat() * 0.5f
    val sway = 0.02f + random.nextFloat() * 0.05f
    val swaySpeed = 6f + random.nextFloat() * 8f
    val spin = 200f + random.nextFloat() * 700f
    val phase = random.nextFloat() * 6.28f
    val width = 6f + random.nextFloat() * 5f
    val height = 10f + random.nextFloat() * 8f
}

/** Falls once per burst, drawn over the whole screen. It takes no touches: play goes on under it. */
@Composable
fun Confetti(bursts: Int, modifier: Modifier = Modifier) {
    if (bursts == 0) return

    val progress = remember(bursts) { Animatable(0f) }
    val pieces = remember(bursts) {
        val random = Random(bursts)
        val colors = listOf(Pink, Purple, Accent, Warn, Danger, Teal)
        List(CONFETTI_PIECES) { ConfettiPiece(random, colors[it % colors.size]) }
    }

    LaunchedEffect(bursts) {
        progress.animateTo(1f, tween(CONFETTI_MILLIS, easing = LinearEasing))
    }

    if (progress.value >= 1f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val scale = 1.dp.toPx()

        for (piece in pieces) {
            val t = ((progress.value - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
            if (t <= 0f) continue

            val x = (piece.startX + sin(t * piece.swaySpeed + piece.phase) * piece.sway) * size.width
            val y = (-0.05f + t * t * piece.fall * 1.15f + t * 0.1f) * size.height
            val fade = if (t > 0.75f) (1f - t) / 0.25f else 1f
            // Turning the width with a cosine reads as a flat piece of paper tumbling in the air.
            val tumble = 0.35f + 0.65f * kotlin.math.abs(cos(t * piece.swaySpeed * 1.4f + piece.phase))
            val pieceWidth = piece.width * scale * tumble
            val pieceHeight = piece.height * scale

            rotate(degrees = t * piece.spin, pivot = Offset(x, y)) {
                drawRect(
                    color = piece.color,
                    topLeft = Offset(x - pieceWidth / 2, y - pieceHeight / 2),
                    size = Size(pieceWidth, pieceHeight),
                    alpha = fade,
                )
            }
        }
    }
}
