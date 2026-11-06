package com.butaca13.puzzles.ui.garden

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.garden.GardenCell
import com.butaca13.puzzles.game.garden.GardenGame
import com.butaca13.puzzles.ui.common.BoardZoomState
import com.butaca13.puzzles.ui.common.GameActionButton
import com.butaca13.puzzles.ui.common.GameClock
import com.butaca13.puzzles.ui.common.GameLoading
import com.butaca13.puzzles.ui.common.GameResult
import com.butaca13.puzzles.ui.common.GameScaffold
import com.butaca13.puzzles.ui.common.ZoomableBoard
import com.butaca13.puzzles.ui.theme.Accent
import com.butaca13.puzzles.ui.theme.BedDim
import com.butaca13.puzzles.ui.theme.Danger
import com.butaca13.puzzles.ui.theme.GardenBeds
import com.butaca13.puzzles.ui.theme.GardenInk

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

    // The cell of the last single tap: a double tap plants only where its first tap fell, and two quick
    // taps on neighbouring cells are two single ones.
    var lastTap by remember { mutableIntStateOf(-1) }

    ZoomableBoard(
        side = side,
        state = zoom,
        zoomEnabled = false,
        onTap = {
            lastTap = cellAt(it)
            viewModel.tap(lastTap)
        },
        onDoubleTap = {
            val cell = cellAt(it)
            if (cell == lastTap) viewModel.toggleFlower(cell) else viewModel.tap(cell)
            lastTap = -1
        },
        onDragStart = { viewModel.mark(cellAt(it)) },
        onDrag = { viewModel.mark(cellAt(it)) },
    ) { boardSide ->
        val cell = boardSide / size
        val colors = remember(game.beds) { bedColors(game.beds, size) }

        Box(modifier = Modifier.size(boardSide)) {
            Column {
                for (row in 0 until size) {
                    Row {
                        for (col in 0 until size) {
                            GardenCellView(
                                game, row * size + col, row, col, cell, colors,
                                onTap = { viewModel.tap(row * size + col) },
                                onToggleFlower = { viewModel.toggleFlower(row * size + col) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GardenCellView(
    game: GardenGame,
    index: Int,
    row: Int,
    col: Int,
    side: Dp,
    /** The pastel of each bed, from [bedColors]. */
    colors: List<Int>,
    onTap: () -> Unit,
    onToggleFlower: () -> Unit,
) {
    val state = game.cells[index]
    val clashing = index in game.conflicts
    val bed = game.beds[index]
    val base = lerp(GardenBeds[colors[bed]], Color.Black, BedDim)
    val markColor = lerp(base, Color.Black, 0.42f)
    val density = LocalDensity.current
    val flowerSize = with(density) { (side * 0.62f).toSp() }

    // Screen readers have no double tap of their own to spare: planting is an action of the cell.
    val markLabel = stringResource(if (state == GardenCell.MARK) R.string.garden_action_unmark else R.string.garden_action_mark)
    val flowerLabel = stringResource(if (state == GardenCell.FLOWER) R.string.garden_action_unplant else R.string.garden_action_plant)
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

    val fill = when {
        game.isComplete && state == GardenCell.FLOWER -> Accent.copy(alpha = 0.55f).compositeOver(base)
        clashing -> Danger.copy(alpha = 0.5f).compositeOver(base)
        else -> base
    }
    val shape = RoundedCornerShape(side * 0.16f)

    // Each cell a raised tile, like the title's, with a little room around it.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(side)
            .padding(side * 0.05f)
            .shadow(side * 0.06f, shape)
            // Lit from above: a touch lighter at the top, a touch darker at the bottom.
            .background(Brush.verticalGradient(listOf(lerp(fill, Color.White, 0.18f), fill, lerp(fill, Color.Black, 0.06f))), shape)
            .then(if (clashing) Modifier.border(2.dp, Danger, shape) else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
                if (state != GardenCell.FLOWER) onClick(label = markLabel) { onTap(); true }
                customActions = listOf(CustomAccessibilityAction(flowerLabel) { onToggleFlower(); true })
            },
    ) {
        when (state) {
            GardenCell.FLOWER -> Text(text = "🌻", fontSize = flowerSize)
            // Drawn, not a "×" glyph, which fills only part of its box: corner to corner over 90% of the
            // tile, in a deeper shade of its own bed, so that it reads without standing out from the pastels.
            GardenCell.MARK -> Canvas(modifier = Modifier.fillMaxSize(0.9f)) {
                val stroke = this.size.width * 0.06f
                val inset = stroke / 2
                val end = this.size.width - inset
                drawLine(markColor, Offset(inset, inset), Offset(end, end), stroke, StrokeCap.Round)
                drawLine(markColor, Offset(end, inset), Offset(inset, end), stroke, StrokeCap.Round)
            }
            GardenCell.EMPTY -> Unit
        }
    }
}
