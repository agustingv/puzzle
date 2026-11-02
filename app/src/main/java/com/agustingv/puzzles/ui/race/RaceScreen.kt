package com.agustingv.puzzles.ui.race

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.agustingv.puzzles.R
import com.agustingv.puzzles.game.race.RaceGame
import com.agustingv.puzzles.game.race.RaceResult
import com.agustingv.puzzles.game.race.RaceRound
import com.agustingv.puzzles.ui.common.GameClock
import com.agustingv.puzzles.ui.common.GameResult
import com.agustingv.puzzles.ui.common.GameScaffold
import com.agustingv.puzzles.ui.common.LetterBox
import com.agustingv.puzzles.ui.common.LetterKeyboard
import com.agustingv.puzzles.ui.common.SubmitKeys
import com.agustingv.puzzles.ui.theme.Absent
import com.agustingv.puzzles.ui.theme.Accent
import com.agustingv.puzzles.ui.theme.InkSoft

private val LetterGap = 4.dp
private val MaxLetterSize = 48.dp

@Composable
fun RaceScreen(viewModel: RaceViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel::tick)

    val game = viewModel.session.game ?: return
    val total = game.rounds.size

    GameScaffold(
        title = stringResource(R.string.race_title),
        session = viewModel.session,
        status = if (game.isFinished) {
            stringResource(R.string.race_score, game.correctCount, total)
        } else {
            stringResource(R.string.race_round, game.index + 1, total)
        },
        onExit = onExit,
        actions = {
            if (game.isFinished) {
                GameResult(
                    message = stringResource(
                        if (game.isWon) R.string.race_finished_perfect else R.string.race_finished,
                    ),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.length) },
                )
            } else {
                LetterKeyboard(
                    onLetter = viewModel::type,
                    submitKeys = SubmitKeys(
                        canSubmit = viewModel.typed.length == game.length,
                        canDelete = viewModel.typed.isNotEmpty(),
                        onSubmit = viewModel::submit,
                        onDelete = viewModel::delete,
                    ),
                )
            }
        },
        board = { side ->
            val round = game.current
            if (round == null) {
                Results(game, side)
            } else {
                Round(game, round, viewModel.typed, viewModel.lastAnswerWrong, side)
            }
        },
    )
}

@Composable
private fun Round(game: RaceGame, round: RaceRound, typed: String, wrong: Boolean, side: Dp) {
    val letter = min(side / game.length, MaxLetterSize) - LetterGap

    Column(
        modifier = Modifier.width(side),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LinearProgressIndicator(
                progress = { game.secondsLeft / RaceGame.TIME_LIMIT.toFloat() },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.race_seconds_left, game.secondsLeft),
                fontWeight = FontWeight.Bold,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(LetterGap)) {
            round.scrambled.forEach { LetterBox(letter = it, size = letter, fill = Absent) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(LetterGap)) {
            for (i in round.word.indices) {
                val char = typed.getOrNull(i)
                LetterBox(letter = char, size = letter, emphasized = char != null)
            }
        }

        if (wrong) {
            Text(
                text = stringResource(R.string.race_wrong),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }

        if (game.correctWords.isNotEmpty()) {
            Text(
                text = stringResource(R.string.race_hits, game.correctWords.joinToString(", ")),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Results(game: RaceGame, side: Dp) {
    Column(
        modifier = Modifier.size(side).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        game.rounds.forEachIndexed { index, round ->
            val correct = game.results[index] == RaceResult.CORRECT

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = round.word, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(
                        if (correct) R.string.race_result_correct else R.string.race_result_timeout,
                    ),
                    color = if (correct) Accent else InkSoft,
                )
            }
        }
    }
}
