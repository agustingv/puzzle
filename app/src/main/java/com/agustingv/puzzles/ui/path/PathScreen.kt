package com.agustingv.puzzles.ui.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.game.path.PathGame
import com.agustingv.puzzles.ui.common.GameClock
import com.agustingv.puzzles.ui.common.GameScaffold
import com.agustingv.puzzles.ui.theme.Accent
import com.agustingv.puzzles.ui.theme.Border
import com.agustingv.puzzles.ui.theme.Ink
import com.agustingv.puzzles.ui.theme.Pink

@Composable
fun PathScreen(viewModel: PathViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel::tick)

    val game = viewModel.game

    GameScaffold(
        title = stringResource(R.string.path_title),
        elapsedSeconds = viewModel.elapsedSeconds,
        status = stringResource(R.string.path_progress, game.path.size, game.totalCells),
        onExit = onExit,
        actions = {
            when {
                game.isComplete -> {
                    Text(
                        text = stringResource(R.string.path_solved),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Button(onClick = { viewModel.newGame(game.difficulty) }) {
                        Text(stringResource(R.string.play_again))
                    }
                }
                else -> {
                    if (game.isFullButWrong) {
                        Text(
                            text = stringResource(R.string.path_must_end, game.checkpoints.size),
                            textAlign = TextAlign.Center,
                        )
                    }
                    OutlinedButton(onClick = viewModel::reset) {
                        Text(stringResource(R.string.path_reset))
                    }
                }
            }
        },
        board = { side ->
            Board(game = game, side = side, onPress = viewModel::press, onDrag = viewModel::dragTo)
        },
    )
}

@Composable
private fun Board(game: PathGame, side: Dp, onPress: (Int) -> Unit, onDrag: (Int) -> Unit) {
    val size = game.size
    val cellPx = with(LocalDensity.current) { side.toPx() } / size
    val press by rememberUpdatedState(onPress)
    val drag by rememberUpdatedState(onDrag)
    val lineColor = if (game.isComplete) Accent else Pink
    val description = stringResource(R.string.path_board, size)

    fun cellAt(position: Offset): Int? {
        val row = (position.y / cellPx).toInt()
        val col = (position.x / cellPx).toInt()
        return if (position.x >= 0 && position.y >= 0 && row < size && col < size) row * size + col else null
    }

    BoxWithConstraints(
        modifier = Modifier
            .size(side)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .semantics { contentDescription = description }
            .pointerInput(size, cellPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    cellAt(down.position)?.let { press(it) }

                    var previous = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()

                        // A fast swipe jumps several cells between events: walk the segment so no
                        // cell along the way is skipped.
                        val delta = change.position - previous
                        val steps = (delta.getDistance() / (cellPx / 4f)).toInt().coerceAtLeast(1)
                        for (step in 1..steps) {
                            cellAt(previous + delta * (step / steps.toFloat()))?.let { drag(it) }
                        }
                        previous = change.position
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gap = 1.dp.toPx()
            for (row in 0 until size) {
                for (col in 0 until size) {
                    drawRect(
                        color = Border,
                        topLeft = Offset(col * cellPx + gap, row * cellPx + gap),
                        size = Size(cellPx - 2 * gap, cellPx - 2 * gap),
                    )
                }
            }

            fun center(cell: Int) = Offset(
                (cell % size + 0.5f) * cellPx,
                (cell / size + 0.5f) * cellPx,
            )

            if (game.path.size == 1) {
                drawCircle(lineColor, radius = cellPx * 0.25f, center = center(game.path[0]))
            } else if (game.path.size > 1) {
                val line = Path()
                game.path.forEachIndexed { index, cell ->
                    val point = center(cell)
                    if (index == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y)
                }
                drawPath(
                    path = line,
                    color = lineColor,
                    style = Stroke(cellPx * 0.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }

        val cell = maxWidth / size
        val badge = cell * 0.62f
        for ((position, number) in game.checkpoints) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(
                        x = cell * (position % size) + (cell - badge) / 2,
                        y = cell * (position / size) + (cell - badge) / 2,
                    )
                    .size(badge)
                    .background(Ink, CircleShape),
            ) {
                Text(text = number.toString(), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
