package com.agustingv.puzzles.ui.garden

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.game.garden.GardenCell
import com.agustingv.puzzles.game.garden.GardenGame
import com.agustingv.puzzles.ui.common.BoardZoomState
import com.agustingv.puzzles.ui.common.GameActionButton
import com.agustingv.puzzles.ui.common.GameClock
import com.agustingv.puzzles.ui.common.GameLoading
import com.agustingv.puzzles.ui.common.GameResult
import com.agustingv.puzzles.ui.common.GameScaffold
import com.agustingv.puzzles.ui.common.ZoomableBoard
import com.agustingv.puzzles.ui.theme.Accent
import com.agustingv.puzzles.ui.theme.BedDim
import com.agustingv.puzzles.ui.theme.Danger
import com.agustingv.puzzles.ui.theme.GardenBeds
import com.agustingv.puzzles.ui.theme.GardenInk

@Composable
fun GardenScreen(viewModel: GardenViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val title = stringResource(R.string.garden_title)
    val game = viewModel.session.game

    if (game == null) {
        GameLoading(title, onExit)
        return
    }

    GameScaffold(
        title = title,
        session = viewModel.session,
        status = stringResource(R.string.garden_status, game.flowers.size, game.size),
        onExit = onExit,
        actions = {
            if (game.isComplete) {
                GameResult(
                    message = stringResource(R.string.garden_won),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.difficulty) },
                )
            } else {
                GameActionButton(stringResource(R.string.path_reset), viewModel::reset)
            }
        },
        board = { side -> Board(game, side, viewModel) },
    )
}

@Composable
private fun Board(game: GardenGame, side: Dp, viewModel: GardenViewModel) {
    val size = game.size
    val zoom = remember(size) { BoardZoomState() }

    fun cellAt(position: Offset): Int {
        val row = (position.y * size).toInt().coerceIn(0, size - 1)
        val col = (position.x * size).toInt().coerceIn(0, size - 1)
        return row * size + col
    }

    ZoomableBoard(
        side = side,
        state = zoom,
        zoomEnabled = false,
        onTap = { viewModel.tap(cellAt(it)) },
        onDragStart = { viewModel.mark(cellAt(it)) },
        onDrag = { viewModel.mark(cellAt(it)) },
    ) { boardSide ->
        val cell = boardSide / size

        Box(modifier = Modifier.size(boardSide)) {
            Column {
                for (row in 0 until size) {
                    Row {
                        for (col in 0 until size) {
                            GardenCellView(game, row * size + col, row, col, cell) {
                                viewModel.tap(row * size + col)
                            }
                        }
                    }
                }
            }

            // Bold lines between beds: the shape of each one must not depend on telling colours apart.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = this.size.width / size
                val fine = 0.5.dp.toPx()
                val bold = 2.5.dp.toPx()

                fun line(from: Offset, to: Offset, strong: Boolean) {
                    drawLine(
                        color = if (strong) GardenInk else GardenInk.copy(alpha = 0.18f),
                        start = from,
                        end = to,
                        strokeWidth = if (strong) bold else fine,
                    )
                }

                for (row in 0 until size) {
                    for (col in 0 until size) {
                        val here = row * size + col
                        val x = col * step
                        val y = row * step
                        if (col + 1 < size) {
                            line(Offset(x + step, y), Offset(x + step, y + step), game.beds[here] != game.beds[here + 1])
                        }
                        if (row + 1 < size) {
                            line(Offset(x, y + step), Offset(x + step, y + step), game.beds[here] != game.beds[here + size])
                        }
                    }
                }

                val end = this.size.width
                line(Offset(0f, 0f), Offset(end, 0f), true)
                line(Offset(0f, end), Offset(end, end), true)
                line(Offset(0f, 0f), Offset(0f, end), true)
                line(Offset(end, 0f), Offset(end, end), true)
            }
        }
    }
}

@Composable
private fun GardenCellView(game: GardenGame, index: Int, row: Int, col: Int, side: Dp, onTap: () -> Unit) {
    val state = game.cells[index]
    val clashing = index in game.conflicts
    val bed = game.beds[index]
    val base = lerp(GardenBeds[bed % GardenBeds.size], Color.Black, BedDim)
    val density = LocalDensity.current
    val flowerSize = with(density) { (side * 0.62f).toSp() }
    val markSize = with(density) { (side * 0.42f).toSp() }

    val description = stringResource(
        when {
            clashing -> R.string.garden_cell_clash
            state == GardenCell.FLOWER -> R.string.garden_cell_flower
            state == GardenCell.MARK -> R.string.garden_cell_mark
            else -> R.string.garden_cell_empty
        },
        row + 1,
        col + 1,
        bed + 1,
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(side)
            .background(
                when {
                    game.isComplete && state == GardenCell.FLOWER -> Accent.copy(alpha = 0.55f).compositeOver(base)
                    clashing -> Danger.copy(alpha = 0.5f).compositeOver(base)
                    else -> base
                },
            )
            .then(if (clashing) Modifier.border(2.dp, Danger) else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
                onClick { onTap(); true }
            },
    ) {
        when (state) {
            GardenCell.FLOWER -> Text(text = "🌻", fontSize = flowerSize)
            GardenCell.MARK -> Text(text = "×", fontSize = markSize, fontWeight = FontWeight.Bold, color = GardenInk)
            GardenCell.EMPTY -> Unit
        }
    }
}
