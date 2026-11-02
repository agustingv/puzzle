package com.agustingv.puzzles.ui.memorama

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.agustingv.puzzles.R
import com.agustingv.puzzles.game.memorama.MemoramaGame
import com.agustingv.puzzles.ui.common.GameClock
import com.agustingv.puzzles.ui.common.GameResult
import com.agustingv.puzzles.ui.common.GameScaffold
import com.agustingv.puzzles.ui.theme.Accent
import com.agustingv.puzzles.ui.theme.Border
import com.agustingv.puzzles.ui.theme.Purple

private val CardGap = 6.dp

@Composable
fun MemoramaScreen(viewModel: MemoramaViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val game = viewModel.session.game ?: return

    GameScaffold(
        title = stringResource(R.string.memorama_title),
        session = viewModel.session,
        status = stringResource(R.string.memorama_status, game.matchedPairs, game.pairs, game.moves),
        onExit = onExit,
        actions = {
            if (game.isWon) {
                GameResult(
                    message = stringResource(R.string.memorama_won, game.pairs),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.difficulty) },
                )
            }
        },
        board = { side -> Board(game, side, viewModel::flip) },
    )
}

@Composable
private fun Board(game: MemoramaGame, side: Dp, onFlip: (Int) -> Unit) {
    val card = min(side / game.columns, side / game.rows) - CardGap

    Column(verticalArrangement = Arrangement.spacedBy(CardGap)) {
        for (row in 0 until game.rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(CardGap)) {
                for (col in 0 until game.columns) {
                    val index = row * game.columns + col
                    Card(
                        number = index + 1,
                        symbol = game.cards[index].takeIf { game.isFaceUp(index) },
                        matched = index in game.matched,
                        size = card,
                        onClick = { onFlip(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Card(number: Int, symbol: String?, matched: Boolean, size: Dp, onClick: () -> Unit) {
    val fontSize = with(LocalDensity.current) { (size * 0.55f).toSp() }
    val description = when {
        symbol == null -> stringResource(R.string.memorama_card_hidden, number)
        matched -> stringResource(R.string.memorama_card_matched, number, symbol)
        else -> stringResource(R.string.memorama_card_up, number, symbol)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    matched -> Accent.copy(alpha = 0.3f)
                    symbol != null -> Border
                    else -> Purple
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                onClick { onClick(); true }
            },
    ) {
        if (symbol != null) Text(text = symbol, fontSize = fontSize, color = Color.Unspecified)
    }
}
