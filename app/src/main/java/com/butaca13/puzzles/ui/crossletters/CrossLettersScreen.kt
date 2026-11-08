package com.butaca13.puzzles.ui.crossletters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.crossletters.CrossLettersGame
import com.butaca13.puzzles.ui.common.GameActionButton
import com.butaca13.puzzles.ui.common.GameClock
import com.butaca13.puzzles.ui.common.GameLoading
import com.butaca13.puzzles.ui.common.GameResult
import com.butaca13.puzzles.ui.common.GameScaffold
import com.butaca13.puzzles.ui.common.LetterBox
import com.butaca13.puzzles.ui.theme.Accent
import com.butaca13.puzzles.ui.theme.Border
import com.butaca13.puzzles.ui.theme.Card
import com.butaca13.puzzles.ui.theme.Ink
import com.butaca13.puzzles.ui.theme.InkSoft

private val Gap = 6.dp

@Composable
fun CrossLettersScreen(viewModel: CrossLettersViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val title = stringResource(R.string.crossletters_title)
    val game = viewModel.session.game
    if (game == null) {
        GameLoading(title, onExit)
        return
    }
    val status = buildList {
        add(pluralStringResource(R.plurals.cryptogram_left, game.tray.size, game.tray.size))
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
                    message = stringResource(R.string.crossletters_won),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.difficulty) },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameActionButton(stringResource(R.string.panal_shuffle), viewModel::shuffle, enabled = game.tray.size > 1)
                    GameActionButton(stringResource(R.string.ladder_hint), viewModel::hint)
                }
            }
        },
        board = { side -> Board(game, side, viewModel) },
    )
}

/**
 * The square, every row and column a word to make, and the tray of letters under it. A row or column
 * that already spells a word lights up. A tap on a place picks it, sending back its letter if it has
 * one; a tap on a tray letter puts it in the picked place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Board(game: CrossLettersGame, side: Dp, viewModel: CrossLettersViewModel) {
    val n = game.size
    val cell = min((side * 0.7f - Gap * (n - 1)) / n, 72.dp)
    val trayLetter = min((side - Gap * 7) / 8, 44.dp)
    val words = (0 until n).map { game.row(it) } to (0 until n).map { game.column(it) }
    val pick = stringResource(R.string.ladder_pick)

    Column(
        modifier = Modifier.width(side),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
            for (r in 0 until n) {
                Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    for (c in 0 until n) {
                        val i = r * n + c
                        val letter = game.cells[i]
                        val fixed = i in game.given
                        // Lit when its row or its column is complete: whether it is a word is the game's
                        // to say at the end, so that the square is not given away line by line.
                        val inLine = words.first[r] != null || words.second[c] != null
                        val shape = RoundedCornerShape(10.dp)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(cell)
                                .clip(shape)
                                .background(
                                    when {
                                        game.isWon -> Accent
                                        i == viewModel.selected -> Accent.copy(alpha = 0.3f)
                                        inLine -> Accent.copy(alpha = 0.12f)
                                        else -> Card
                                    },
                                )
                                .border(if (i == viewModel.selected) 2.dp else 1.dp, if (i == viewModel.selected) Accent else Border, shape)
                                .clickable(enabled = !fixed && !game.isWon, role = Role.Button) { viewModel.tapCell(i) }
                                .clearAndSetSemantics { contentDescription = "${r + 1}, ${c + 1}: ${letter?.uppercase() ?: "—"}" + if (fixed) "" else ", $pick" },
                        ) {
                            Text(
                                text = letter?.uppercase() ?: "",
                                fontSize = with(LocalDensity.current) { (cell * 0.5f).toSp() },
                                fontWeight = FontWeight.ExtraBold,
                                color = when {
                                    game.isWon -> Color.White
                                    fixed -> InkSoft
                                    else -> Ink
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(cell * 0.5f))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Gap)) {
            game.tray.forEachIndexed { index, letter ->
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { viewModel.tapTray(index) }) {
                    LetterBox(letter = letter, size = trayLetter)
                }
            }
        }
    }
}
