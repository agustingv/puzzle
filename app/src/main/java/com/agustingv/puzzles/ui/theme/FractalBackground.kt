package com.agustingv.puzzles.ui.theme

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val DEPTH = 10
private const val SWAY_SPEED = 0.00012
private const val SWAY_AMOUNT = 0.16f
private const val BRANCH_ANGLE = 0.45f
private const val BRANCH_SHRINK = 0.72f
private const val MIN_LENGTH_DP = 2

private const val FRAME_MILLIS = 66L

/**
 * The web version's swaying fractal tree. The sway takes almost a minute, so 15 frames per second
 * look as smooth as 60 and save a phone's battery. It stops in the background and stays still when
 * the system's animations are off, as some people need for motion sensitivity.
 */
@Composable
fun FractalBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val alpha = FractalAlpha
    val animate = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }

    // Read only while drawing, so that each frame redraws this canvas and recomposes nothing.
    var timeMillis by remember { mutableLongStateOf(0L) }
    val paths = remember { List(DEPTH + 1) { Path() } }

    LaunchedEffect(lifecycleOwner, animate) {
        if (!animate) return@LaunchedEffect

        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                timeMillis = withFrameMillis { it }
                delay(FRAME_MILLIS)
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val sway = sin(timeMillis * SWAY_SPEED).toFloat() * SWAY_AMOUNT
        val baseLength = min(size.width, size.height) * 0.22f
        val minLength = MIN_LENGTH_DP.dp.toPx()

        paths.forEach { it.rewind() }

        fun branch(x: Float, y: Float, length: Float, angle: Float, depth: Int) {
            if (depth == 0 || length < minLength) return

            val endX = x + cos(angle) * length
            val endY = y + sin(angle) * length
            paths[depth].moveTo(x, y)
            paths[depth].lineTo(endX, endY)

            branch(endX, endY, length * BRANCH_SHRINK, angle - BRANCH_ANGLE - sway, depth - 1)
            branch(endX, endY, length * BRANCH_SHRINK, angle + BRANCH_ANGLE + sway, depth - 1)
        }

        branch(size.width / 2, size.height + 20.dp.toPx(), baseLength, -Math.PI.toFloat() / 2, DEPTH)

        val brush = Brush.linearGradient(
            colors = listOf(Purple, Pink),
            start = Offset(0f, size.height),
            end = Offset(0f, size.height - baseLength * 3),
        )

        // One path per depth, since each has its own line width: thick trunk, fine tips.
        for (depth in 1..DEPTH) {
            drawPath(
                path = paths[depth],
                brush = brush,
                alpha = alpha,
                style = Stroke(width = max(0.6f, depth * 0.55f).dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}
