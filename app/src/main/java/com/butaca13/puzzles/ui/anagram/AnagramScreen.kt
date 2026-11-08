package com.butaca13.puzzles.ui.anagram

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.anagram.AnagramGame
import com.butaca13.puzzles.ui.common.GameActionButton
import com.butaca13.puzzles.ui.common.GameClock
import com.butaca13.puzzles.ui.common.GameResult
import com.butaca13.puzzles.ui.common.GameScaffold
import com.butaca13.puzzles.ui.common.LetterBox
import com.butaca13.puzzles.ui.theme.Accent
import com.butaca13.puzzles.ui.theme.InkSoft

private val LetterGap = 6.dp
private val MaxLetterSize = 52.dp

@Composable
fun AnagramScreen(viewModel: AnagramViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val game = viewModel.session.game ?: return
    val status = buildList {
        add(stringResource(R.string.anagram_status, game.length))
        if (game.misses > 0) add(pluralStringResource(R.plurals.anagram_misses, game.misses, game.misses))
        if (game.clueShown) add(stringResource(R.string.anagram_clue_used))
    }.joinToString(" · ")

    GameScaffold(
        title = stringResource(R.string.anagram_title),
        session = viewModel.session,
        status = status,
        onExit = onExit,
        actions = {
            if (game.isWon) {
                GameResult(
                    message = stringResource(R.string.anagram_won, game.word.uppercase()),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.length) },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameActionButton(stringResource(R.string.anagram_clue), viewModel::showClue, enabled = !game.clueShown && viewModel.clue != null)
                    GameActionButton(stringResource(R.string.anagram_shuffle), viewModel::shuffle, enabled = game.pool.size > 1)
                    GameActionButton(stringResource(R.string.anagram_clear), viewModel::clear, enabled = game.answer.isNotEmpty())
                }
            }
        },
        board = { side -> Board(game, side, viewModel) },
    )
}

/**
 * The answer on top, a place for each letter, and the shuffled letters under it: a tap on a letter
 * below puts it in the next place, and a tap on one above takes it back, with those after it.
 */
@Composable
private fun Board(game: AnagramGame, side: Dp, viewModel: AnagramViewModel) {
    val letter = min((side - LetterGap * (game.length - 1)) / game.length, MaxLetterSize)
    val full = game.answer.size == game.length
    val takeBack = stringResource(R.string.anagram_take_back)
    val put = stringResource(R.string.anagram_put)

    Column(
        modifier = Modifier.width(side),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The answer: green once solved, the misses' colour while a full answer is wrong.
        Row(horizontalArrangement = Arrangement.spacedBy(LetterGap)) {
            for (position in 0 until game.length) {
                val tile = game.answer.getOrNull(position)
                val char = tile?.let { game.tiles[it] }
                Tappable(enabled = tile != null && !game.isWon, onClick = { viewModel.takeBack(position) }) {
                    LetterBox(
                        letter = char,
                        size = letter,
                        fill = when {
                            game.isWon -> Accent
                            full -> InkSoft
                            else -> null
                        },
                        emphasized = position == game.answer.size,
                        description = char?.let { "${it.uppercase()}, $takeBack" } ?: "",
                    )
                }
            }
        }

        Spacer(Modifier.height(letter * 0.8f))

        // The letters to place: the empty places keep their spot, so that the rest do not move.
        Row(horizontalArrangement = Arrangement.spacedBy(LetterGap)) {
            game.tiles.forEachIndexed { index, char ->
                val inPool = index !in game.answer
                Tappable(enabled = inPool && !game.isWon, onClick = { viewModel.place(index) }) {
                    if (inPool) {
                        LetterBox(letter = char, size = letter, description = "${char.uppercase()}, $put")
                    } else {
                        Box(Modifier.width(letter).height(letter))
                    }
                }
            }
        }

        if (game.clueShown) {
            viewModel.clue?.let { clue ->
                Spacer(Modifier.height(letter * 0.6f))
                Text(
                    text = "💡 $clue",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Tappable(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)) {
        content()
    }
}
