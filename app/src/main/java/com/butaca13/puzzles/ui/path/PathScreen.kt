package com.butaca13.puzzles.ui.path

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
import androidx.compose.foundation.systemGestureExclusion
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
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.path.PathGame
import com.butaca13.puzzles.ui.common.GameActionButton
import com.butaca13.puzzles.ui.common.GameClock
import com.butaca13.puzzles.ui.common.GameResult
import com.butaca13.puzzles.ui.common.GameScaffold
import com.butaca13.puzzles.ui.theme.Accent
import com.butaca13.puzzles.ui.theme.Blocked
import com.butaca13.puzzles.ui.theme.Border
import com.butaca13.puzzles.ui.theme.Card
import com.butaca13.puzzles.ui.theme.Pink

@Composable
fun PathScreen(viewModel: PathViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val game = viewModel.session.game ?: return

    GameScaffold(
        title = stringResource(R.string.path_title),
        session = viewModel.session,
        status = stringResource(R.string.path_progress, game.path.size, game.totalCells),
        onExit = onExit,
        actions = {
            when {
                game.isComplete -> GameResult(
                    message = stringResource(R.string.path_solved),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.difficulty) },
                )
                else -> {
                    if (game.isFullButWrong) {
                        Text(
                            text = stringResource(R.string.path_must_end, game.checkpoints.size),
                            textAlign = TextAlign.Center,
                        )
                    }
                    GameActionButton(stringResource(R.string.path_reset), viewModel::reset)
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
    val cellColor = Border
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
            .background(Card)
            .semantics { contentDescription = description }
            // A drag starting near the screen edge is for the board, not the system's back gesture.
            .systemGestureExclusion()
            .pointerInput(size, cellPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    cellAt(down.position)?.let { press(it) }

                    var previous = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()

                        // A fast swipe jumps several cells between events: walk each segment so no
                        // cell along the way is skipped. Android can batch several moves into one
                        // event, so its intermediate points count too: going straight to the last
                        // one would cut across a turn. That includes the last stretch up to where
                        // the finger lifts, which can be past the last move event.
                        for (point in change.historical.map { it.position } + change.position) {
                            val delta = point - previous
                            val steps = (delta.getDistance() / (cellPx / 4f)).toInt().coerceAtLeast(1)
                            for (step in 1..steps) {
                                cellAt(previous + delta * (step / steps.toFloat()))?.let { drag(it) }
                            }
                            previous = point
                        }

                        if (!change.pressed) break
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gap = 1.dp.toPx()
            for (row in 0 until size) {
                for (col in 0 until size) {
                    drawRect(
                        color = cellColor,
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
                    .background(Blocked, CircleShape),
            ) {
                Text(text = number.toString(), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
