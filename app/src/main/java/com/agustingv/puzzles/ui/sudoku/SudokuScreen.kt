package com.agustingv.puzzles.ui.sudoku

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.game.sudoku.SudokuGame
import com.agustingv.puzzles.ui.common.GameClock
import com.agustingv.puzzles.ui.common.GameLoading
import com.agustingv.puzzles.ui.common.GameResult
import com.agustingv.puzzles.ui.common.GameScaffold
import com.agustingv.puzzles.ui.common.NumberPad
import com.agustingv.puzzles.ui.theme.Border
import com.agustingv.puzzles.ui.theme.Accent
import com.agustingv.puzzles.ui.theme.Ink
import com.agustingv.puzzles.ui.theme.InkSoft
import com.agustingv.puzzles.ui.theme.Pink
import com.agustingv.puzzles.ui.theme.PinkDark
import com.agustingv.puzzles.ui.theme.Purple

@Composable
fun SudokuScreen(viewModel: SudokuViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel::tick)

    val title = stringResource(R.string.sudoku_title)
    val game = viewModel.game

    if (game == null) {
        GameLoading(title, onExit)
        return
    }

    GameScaffold(
        title = title,
        elapsedSeconds = viewModel.elapsedSeconds,
        status = stringResource(R.string.grid_filled, game.filledCount, 81),
        onExit = onExit,
        actions = {
            if (game.isComplete) {
                GameResult(
                    message = stringResource(R.string.sudoku_won),
                    onPlayAgain = { viewModel.newGame(game.difficulty) },
                )
            } else {
                OutlinedButton(onClick = viewModel::check) { Text(stringResource(R.string.key_submit)) }
                NumberPad(onDigit = viewModel::type, onClear = viewModel::clear)
            }
        },
        board = { side -> Board(game, side, viewModel::select) },
    )
}

@Composable
private fun Board(game: SudokuGame, side: Dp, onSelect: (Int) -> Unit) {
    val cell = side / 9

    Box(modifier = Modifier.size(side).background(Color.White)) {
        Column {
            for (row in 0 until 9) {
                Row {
                    for (col in 0 until 9) {
                        val index = row * 9 + col
                        Cell(game, index, row, col, cell, onClick = { onSelect(index) })
                    }
                }
            }
        }

        // Drawn over the cells so the box lines stay continuous.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = size.width / 9
            for (i in 0..9) {
                val thick = i % 3 == 0
                val width = if (thick) 2.dp.toPx() else 0.5.dp.toPx()
                val color = if (thick) Ink else InkSoft
                drawLine(color, Offset(i * step, 0f), Offset(i * step, size.height), width)
                drawLine(color, Offset(0f, i * step), Offset(size.width, i * step), width)
            }
        }
    }
}

@Composable
private fun Cell(game: SudokuGame, index: Int, row: Int, col: Int, side: Dp, onClick: () -> Unit) {
    val value = game.valueAt(index)
    val given = index in game.givens
    val wrong = index in game.markedWrong
    val selected = !game.isComplete && index == game.cursor
    val cursorRow = game.cursor / 9
    val cursorCol = game.cursor % 9
    val related = !game.isComplete && (
        row == cursorRow || col == cursorCol || (row / 3 == cursorRow / 3 && col / 3 == cursorCol / 3)
        )

    val fontSize = with(LocalDensity.current) { (side * 0.55f).toSp() }
    val description = stringResource(
        when {
            given -> R.string.digit_cell_given
            wrong -> R.string.digit_cell_wrong
            selected -> R.string.digit_cell_selected
            else -> R.string.digit_cell
        },
        row + 1,
        col + 1,
        if (value == 0) "" else value.toString(),
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(side)
            .background(
                when {
                    game.isComplete -> Accent.copy(alpha = 0.2f)
                    selected -> Pink.copy(alpha = 0.5f)
                    wrong -> Pink.copy(alpha = 0.25f)
                    related -> Border
                    else -> Color.White
                },
            )
            .clickable(enabled = !given && !game.isComplete, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                if (!given) onClick { onClick(); true }
            },
    ) {
        if (value != 0) {
            Text(
                text = value.toString(),
                fontSize = fontSize,
                fontWeight = if (given) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    wrong -> PinkDark
                    given -> Ink
                    else -> Purple
                },
            )
        }
    }
}
