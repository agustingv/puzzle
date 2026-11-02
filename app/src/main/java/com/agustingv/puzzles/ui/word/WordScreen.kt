package com.agustingv.puzzles.ui.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.agustingv.puzzles.R
import com.agustingv.puzzles.game.word.GuessedLetter
import com.agustingv.puzzles.game.word.LetterState
import com.agustingv.puzzles.game.word.WordGuessGame
import com.agustingv.puzzles.ui.common.GameClock
import com.agustingv.puzzles.ui.common.GameResult
import com.agustingv.puzzles.ui.common.GameScaffold
import com.agustingv.puzzles.ui.common.LetterBox
import com.agustingv.puzzles.ui.common.LetterKeyboard
import com.agustingv.puzzles.ui.common.SubmitKeys
import com.agustingv.puzzles.ui.theme.Absent
import com.agustingv.puzzles.ui.theme.Accent
import com.agustingv.puzzles.ui.theme.Warn

private val CellGap = 4.dp

@Composable
fun WordScreen(viewModel: WordViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val game = viewModel.session.game ?: return
    val max = WordGuessGame.MAX_ATTEMPTS

    GameScaffold(
        title = stringResource(R.string.word_title),
        elapsedSeconds = viewModel.session.elapsedSeconds,
        status = stringResource(
            R.string.word_attempt,
            if (game.isOver) game.guesses.size else game.guesses.size + 1,
            max,
        ),
        onExit = onExit,
        actions = {
            if (game.isOver) {
                GameResult(
                    message = if (game.isWon) {
                        stringResource(R.string.word_won, game.guesses.size, max)
                    } else {
                        stringResource(R.string.game_lost_word, game.word)
                    },
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.length) },
                )
            } else {
                val states = game.keyboardStates()
                LetterKeyboard(
                    onLetter = viewModel::type,
                    keyColor = { states[it]?.color },
                    submitKeys = SubmitKeys(
                        canSubmit = viewModel.typed.length == game.length,
                        canDelete = viewModel.typed.isNotEmpty(),
                        onSubmit = viewModel::submit,
                        onDelete = viewModel::delete,
                    ),
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
            val feedback = game.guesses.getOrNull(row)?.let(game::feedback)
            val isCurrent = row == game.guesses.size && !game.isOver

            Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                for (column in 0 until game.length) {
                    val guessed = feedback?.get(column)
                    val pending = if (isCurrent) typed.getOrNull(column) else null

                    LetterBox(
                        letter = guessed?.char ?: pending,
                        size = cell - CellGap,
                        fill = guessed?.state?.color,
                        emphasized = pending != null,
                        description = guessed?.let { describe(it) }
                            ?: pending?.uppercase().orEmpty(),
                    )
                }
            }
        }
    }
}

@Composable
private fun describe(letter: GuessedLetter): String {
    val label = letter.char.uppercase()

    return when (letter.state) {
        LetterState.CORRECT -> stringResource(R.string.word_letter_correct, label)
        LetterState.PRESENT -> stringResource(R.string.word_letter_present, label)
        LetterState.ABSENT -> stringResource(R.string.word_letter_absent, label)
    }
}

private val LetterState.color: Color
    get() = when (this) {
        LetterState.CORRECT -> Accent
        LetterState.PRESENT -> Warn
        LetterState.ABSENT -> Absent
    }
