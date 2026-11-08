package com.butaca13.puzzles.ui.ladder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.ladder.LadderGame
import com.butaca13.puzzles.ui.common.GameActionButton
import com.butaca13.puzzles.ui.common.GameClock
import com.butaca13.puzzles.ui.common.GameLoading
import com.butaca13.puzzles.ui.common.GameResult
import com.butaca13.puzzles.ui.common.GameScaffold
import com.butaca13.puzzles.ui.common.LetterBox
import com.butaca13.puzzles.ui.common.LetterKeyboard
import com.butaca13.puzzles.ui.theme.Accent
import com.butaca13.puzzles.ui.theme.InkSoft

private val LetterGap = 6.dp
private val MaxLetterSize = 44.dp

@Composable
fun LadderScreen(viewModel: LadderViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val title = stringResource(R.string.ladder_title)
    val game = viewModel.session.game
    if (game == null) {
        GameLoading(title, onExit)
        return
    }
    val status = buildList {
        add(pluralStringResource(R.plurals.ladder_steps, game.steps, game.steps, game.best))
        if (game.hints > 0) add(pluralStringResource(R.plurals.ladder_hints, game.hints, game.hints))
    }.joinToString(" · ")

    GameScaffold(
        title = title,
        session = viewModel.session,
        status = status,
        onExit = onExit,
        actions = {
            if (game.isWon) {
                GameResult(
                    message = pluralStringResource(R.plurals.ladder_won, game.steps, game.steps),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.difficulty) },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameActionButton(stringResource(R.string.ladder_undo), viewModel::undo, enabled = game.chain.size > 1)
                    GameActionButton(stringResource(R.string.ladder_hint), viewModel::hint)
                }
                LetterKeyboard(
                    onLetter = viewModel::letter,
                    keyEnabled = { viewModel.selected >= 0 },
                    keyHeight = 42.dp,
                )
            }
        },
        board = { side -> Board(game, side, viewModel) },
    )
}

/**
 * The way from the start down to the target: every word taken so far, the current one at the bottom
 * with a letter to pick for changing, and the target under it. A tap on an earlier word goes back to it.
 */
@Composable
private fun Board(game: LadderGame, side: Dp, viewModel: LadderViewModel) {
    val length = game.start.length
    val letter = min((side - LetterGap * (length - 1)) / length, MaxLetterSize)
    val scroll = rememberScrollState()
    val pick = stringResource(R.string.ladder_pick)
    val back = stringResource(R.string.ladder_back_to)
    LaunchedEffect(game.chain.size) { scroll.animateScrollTo(scroll.maxValue) }

    Column(
        modifier = Modifier.width(side).height(side),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LetterGap, Alignment.Bottom),
        ) {
            game.chain.forEachIndexed { index, word ->
                val current = index == game.chain.lastIndex
                val earlier = !current && !game.isWon
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LetterGap),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = earlier, role = Role.Button) { viewModel.backTo(index) },
                ) {
                    word.forEachIndexed { position, char ->
                        val changed = index > 0 && game.chain[index - 1][position] != char
                        val picked = current && position == viewModel.selected
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = current && !game.isWon, role = Role.Button) { viewModel.select(position) },
                        ) {
                            LetterBox(
                                letter = char,
                                size = letter,
                                fill = when {
                                    game.isWon -> Accent
                                    picked -> InkSoft
                                    changed && !current -> Accent.copy(alpha = 0.55f)
                                    else -> null
                                },
                                emphasized = current,
                                description = when {
                                    current -> "${char.uppercase()}, $pick"
                                    earlier && position == 0 -> "${word.uppercase()}, $back"
                                    else -> char.uppercase()
                                },
                            )
                        }
                    }
                }
            }
        }

        if (!game.isWon) {
            Text(stringResource(R.string.ladder_target), style = MaterialTheme.typography.labelMedium, color = InkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(LetterGap)) {
                game.target.forEachIndexed { position, char ->
                    // The target's letters that the current word already has in place are lit.
                    LetterBox(letter = char, size = letter, fill = if (game.current[position] == char) Accent else null)
                }
            }
        }
    }
}
