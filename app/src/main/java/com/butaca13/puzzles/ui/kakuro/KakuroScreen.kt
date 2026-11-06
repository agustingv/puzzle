package com.butaca13.puzzles.ui.kakuro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.kakuro.KakuroGame
import com.butaca13.puzzles.ui.common.GameActionButton
import com.butaca13.puzzles.ui.common.GameClock
import com.butaca13.puzzles.ui.common.GameLoading
import com.butaca13.puzzles.ui.common.GameResult
import com.butaca13.puzzles.ui.common.GameScaffold
import com.butaca13.puzzles.ui.common.NumberPad
import com.butaca13.puzzles.ui.theme.Accent
import com.butaca13.puzzles.ui.theme.Alert
import com.butaca13.puzzles.ui.theme.Blocked
import com.butaca13.puzzles.ui.theme.Card
import com.butaca13.puzzles.ui.theme.InkSoft
import com.butaca13.puzzles.ui.theme.Pink
import com.butaca13.puzzles.ui.theme.PlayerDigit

@Composable
fun KakuroScreen(viewModel: KakuroViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val title = stringResource(R.string.kakuro_title)
    val game = viewModel.session.game

    if (game == null) {
        GameLoading(title, onExit)
        return
    }

    GameScaffold(
        title = title,
        session = viewModel.session,
        status = stringResource(R.string.grid_filled, game.filledCount, game.activeCells),
        onExit = onExit,
        actions = {
            if (game.isComplete) {
                GameResult(
                    message = stringResource(R.string.kakuro_won),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.difficulty) },
                )
            } else {
                GameActionButton(stringResource(R.string.key_submit), viewModel::check)
                NumberPad(onDigit = viewModel::type, onClear = viewModel::clear)
            }
        },
        board = { side -> Board(game, side, viewModel::select) },
    )
}

@Composable
private fun Board(game: KakuroGame, side: Dp, onSelect: (Int) -> Unit) {
    val cell = side / game.size

    Column(modifier = Modifier.size(side).background(Blocked)) {
        for (row in 0 until game.size) {
            Row {
                for (col in 0 until game.size) {
                    val index = row * game.size + col
                    if (game.blocked[index]) {
                        ClueCell(game.across[index], game.down[index], row, col, cell)
                    } else {
                        DigitCell(game, index, row, col, cell, onClick = { onSelect(index) })
                    }
                }
            }
        }
    }
}

/** A blocked cell: down sum in the lower-left corner, across sum in the upper-right one. */
@Composable
private fun ClueCell(across: Int?, down: Int?, row: Int, col: Int, side: Dp) {
    val clueSize = with(LocalDensity.current) { (side * 0.26f).toSp() }
    val description = when {
        across != null && down != null -> stringResource(R.string.kakuro_clue_both, row + 1, col + 1, across, down)
        across != null -> stringResource(R.string.kakuro_clue_across, row + 1, col + 1, across)
        down != null -> stringResource(R.string.kakuro_clue_down, row + 1, col + 1, down)
        else -> ""
    }

    val lineColor = InkSoft

    Box(
        modifier = Modifier
            .size(side)
            .border(0.5.dp, lineColor)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (across != null || down != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(lineColor, Offset.Zero, Offset(size.width, size.height), 1.dp.toPx())
            }
        }
        across?.let {
            Text(
                text = it.toString(),
                color = Color.White,
                fontSize = clueSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 5.dp),
            )
        }
        down?.let {
            Text(
                text = it.toString(),
                color = Color.White,
                fontSize = clueSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 3.dp, start = 5.dp),
            )
        }
    }
}

@Composable
private fun DigitCell(game: KakuroGame, index: Int, row: Int, col: Int, side: Dp, onClick: () -> Unit) {
    val value = game.input[index]
    val wrong = index in game.markedWrong
    val selected = !game.isComplete && index == game.cursor
    val fontSize = with(LocalDensity.current) { (side * 0.5f).toSp() }
    val description = stringResource(
        when {
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
                    game.isComplete -> Accent.copy(alpha = 0.35f).compositeOver(Card)
                    selected -> Pink.copy(alpha = 0.22f).compositeOver(Card)
                    wrong -> Pink.copy(alpha = 0.25f).compositeOver(Card)
                    else -> Card
                },
            )
            .border(if (selected) 2.dp else 0.5.dp, if (selected) Pink else InkSoft)
            .clickable(enabled = !game.isComplete, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                onClick { onClick(); true }
            },
    ) {
        if (value != 0) {
            Text(
                text = value.toString(),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = if (wrong) Alert else PlayerDigit,
            )
        }
    }
}
