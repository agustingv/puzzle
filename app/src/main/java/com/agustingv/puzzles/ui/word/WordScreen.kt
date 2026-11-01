package com.agustingv.puzzles.ui.word

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.agustingv.puzzles.R
import com.agustingv.puzzles.game.word.GuessedLetter
import com.agustingv.puzzles.game.word.LetterState
import com.agustingv.puzzles.game.word.WordGuessGame
import com.agustingv.puzzles.ui.common.GameClock
import com.agustingv.puzzles.ui.common.GameScaffold
import com.agustingv.puzzles.ui.theme.Accent
import com.agustingv.puzzles.ui.theme.Border
import com.agustingv.puzzles.ui.theme.Ink
import com.agustingv.puzzles.ui.theme.InkSoft
import com.agustingv.puzzles.ui.theme.Warn

private val CellGap = 4.dp
private val KeyHeight = 48.dp
private val KeyboardRows = listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm")

@Composable
fun WordScreen(viewModel: WordViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel::tick)

    val game = viewModel.game
    val max = WordGuessGame.MAX_ATTEMPTS

    GameScaffold(
        title = stringResource(R.string.word_title),
        elapsedSeconds = viewModel.elapsedSeconds,
        status = stringResource(
            R.string.word_attempt,
            if (game.isOver) game.guesses.size else game.guesses.size + 1,
            max,
        ),
        onExit = onExit,
        actions = {
            if (game.isOver) {
                Text(
                    text = if (game.isWon) {
                        stringResource(R.string.word_won, game.guesses.size, max)
                    } else {
                        stringResource(R.string.word_lost, game.word)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { viewModel.newGame(game.length) }) {
                    Text(stringResource(R.string.play_again))
                }
            } else {
                Keyboard(
                    states = game.keyboardStates(),
                    canSubmit = viewModel.typed.length == game.length,
                    canDelete = viewModel.typed.isNotEmpty(),
                    onLetter = viewModel::type,
                    onDelete = viewModel::delete,
                    onSubmit = viewModel::submit,
                )
            }
        },
        board = { side -> Board(game, viewModel.typed, side) },
    )
}

@Composable
private fun Board(game: WordGuessGame, typed: String, side: Dp) {
    val cell = min(side / game.length, side / WordGuessGame.MAX_ATTEMPTS)

    Column(
        verticalArrangement = Arrangement.spacedBy(CellGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (row in 0 until WordGuessGame.MAX_ATTEMPTS) {
            val guess = game.guesses.getOrNull(row)
            val feedback = guess?.let(game::feedback)
            val isCurrent = row == game.guesses.size && !game.isOver

            Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                for (column in 0 until game.length) {
                    LetterCell(
                        guessed = feedback?.get(column),
                        typed = if (isCurrent) typed.getOrNull(column) else null,
                        size = cell - CellGap,
                    )
                }
            }
        }
    }
}

@Composable
private fun LetterCell(guessed: GuessedLetter?, typed: Char?, size: Dp) {
    val shape = RoundedCornerShape(8.dp)
    val fontSize = with(LocalDensity.current) { (size * 0.5f).toSp() }
    val char = guessed?.char ?: typed
    val label = char?.toString()?.uppercase().orEmpty()

    val description = when (guessed?.state) {
        LetterState.CORRECT -> stringResource(R.string.word_letter_correct, label)
        LetterState.PRESENT -> stringResource(R.string.word_letter_present, label)
        LetterState.ABSENT -> stringResource(R.string.word_letter_absent, label)
        null -> label
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(guessed?.state?.color ?: Color.White)
            .border(2.dp, if (typed != null) InkSoft else Border, shape)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = label,
            color = if (guessed != null) Color.White else Ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
        )
    }
}

@Composable
private fun Keyboard(
    states: Map<Char, LetterState>,
    canSubmit: Boolean,
    canDelete: Boolean,
    onLetter: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KeyboardRows.forEachIndexed { index, letters ->
            Row(
                modifier = Modifier.fillMaxWidth().height(KeyHeight),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (index == KeyboardRows.lastIndex) {
                    Key(
                        label = "OK",
                        description = stringResource(R.string.word_key_submit),
                        enabled = canSubmit,
                        weight = 1.5f,
                        onClick = onSubmit,
                    )
                }
                for (letter in letters) {
                    Key(
                        label = letter.uppercase(),
                        description = letter.uppercase(),
                        state = states[letter],
                        onClick = { onLetter(letter) },
                    )
                }
                if (index == KeyboardRows.lastIndex) {
                    Key(
                        label = "←",
                        description = stringResource(R.string.word_key_delete),
                        enabled = canDelete,
                        weight = 1.5f,
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.Key(
    label: String,
    description: String,
    onClick: () -> Unit,
    state: LetterState? = null,
    enabled: Boolean = true,
    weight: Float = 1f,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(weight)
            .height(KeyHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(state?.color ?: Border)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            color = when {
                state != null -> Color.White
                enabled -> Ink
                else -> InkSoft.copy(alpha = 0.5f)
            },
        )
    }
}

private val LetterState.color: Color
    get() = when (this) {
        LetterState.CORRECT -> Accent
        LetterState.PRESENT -> Warn
        LetterState.ABSENT -> InkSoft
    }
