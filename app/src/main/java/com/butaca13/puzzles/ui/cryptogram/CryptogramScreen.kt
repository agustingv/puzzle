package com.butaca13.puzzles.ui.cryptogram

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.butaca13.puzzles.game.cryptogram.ALPHABET
import com.butaca13.puzzles.game.cryptogram.CryptogramGame
import com.butaca13.puzzles.ui.common.GameActionButton
import com.butaca13.puzzles.ui.common.GameClock
import com.butaca13.puzzles.ui.common.GameResult
import com.butaca13.puzzles.ui.common.GameScaffold
import com.butaca13.puzzles.ui.common.LetterKeyboard
import com.butaca13.puzzles.ui.theme.Absent
import com.butaca13.puzzles.ui.theme.Accent
import com.butaca13.puzzles.ui.theme.Border
import com.butaca13.puzzles.ui.theme.Card
import com.butaca13.puzzles.ui.theme.Ink
import com.butaca13.puzzles.ui.theme.InkSoft

private val CellGap = 3.dp
private val MaxCell = 30.dp

@Composable
fun CryptogramScreen(viewModel: CryptogramViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val game = viewModel.session.game ?: return
    val status = buildList {
        add(pluralStringResource(R.plurals.cryptogram_left, game.left, game.left))
        if (game.hints > 0) add(pluralStringResource(R.plurals.ladder_hints, game.hints, game.hints))
    }.joinToString(" · ")

    GameScaffold(
        title = stringResource(R.string.cryptogram_title),
        session = viewModel.session,
        status = status,
        onExit = onExit,
        actions = {
            if (game.isWon) {
                GameResult(
                    message = stringResource(R.string.cryptogram_won),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.difficulty) },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameActionButton(stringResource(R.string.cryptogram_clear), viewModel::clear, enabled = viewModel.selected?.let { game.guesses[it] } != null)
                    GameActionButton(stringResource(R.string.ladder_hint), viewModel::hint)
                }
                // The letters already used dimmed: each stands for one code letter only.
                val used = game.codeLetters.mapNotNull(game::reading).toSet()
                LetterKeyboard(
                    onLetter = viewModel::letter,
                    keyColor = { if (it in used) Absent else null },
                    keyEnabled = { viewModel.selected != null },
                    keyHeight = 42.dp,
                )
            }
        },
        board = { side -> Board(game, side, viewModel) },
    )
}

/**
 * The proverb, word by word: each letter's code under the place for its reading. A tap on a letter picks
 * its code letter, lit wherever it is, for the keyboard to read.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Board(game: CryptogramGame, side: Dp, viewModel: CryptogramViewModel) {
    val longest = game.plain.split(" ").maxOf { it.length }
    val cell = min((side - CellGap * 12) / 13, MaxCell).coerceAtMost((side - CellGap * longest) / longest)
    val words = game.phrase.split(" ").let { written ->
        var at = 0
        written.map { word -> (at until at + word.length).also { at += word.length + 1 } }
    }

    Box(modifier = Modifier.width(side), contentAlignment = Alignment.Center) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(cell * 0.7f, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(cell * 0.4f),
        ) {
            for (range in words) {
                Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                    for (i in range) Cell(game, i, cell, viewModel)
                }
            }
        }
    }
}

@Composable
private fun Cell(game: CryptogramGame, index: Int, size: Dp, viewModel: CryptogramViewModel) {
    val code = game.coded[index]
    val fontSize = with(LocalDensity.current) { (size * 0.6f).toSp() }
    val codeSize = with(LocalDensity.current) { (size * 0.42f).toSp() }

    if (code !in ALPHABET) {
        // Punctuation, as it is.
        Box(modifier = Modifier.width(size * 0.5f).size(width = size * 0.5f, height = size * 1.6f), contentAlignment = Alignment.TopCenter) {
            Text(game.phrase[index].toString(), fontSize = fontSize, fontWeight = FontWeight.Bold, color = Ink)
        }
        return
    }
    val reading = game.reading(code)
    val given = code in game.given
    val picked = code == viewModel.selected
    val shape = RoundedCornerShape(5.dp)
    val description = stringResource(R.string.cryptogram_cell, code.uppercase(), reading?.uppercase() ?: "?")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(shape)
            .clickable(enabled = !given && !game.isWon, role = Role.Button) { viewModel.select(code) }
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(
                    when {
                        game.isWon -> Accent
                        picked -> Accent.copy(alpha = 0.35f)
                        else -> Card
                    },
                )
                .border(if (picked) 2.dp else 1.dp, if (picked) Accent else Border, shape),
        ) {
            Text(
                text = reading?.uppercase() ?: "",
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                color = when {
                    game.isWon -> androidx.compose.ui.graphics.Color.White
                    given -> InkSoft
                    else -> Ink
                },
            )
        }
        Text(code.uppercase(), fontSize = codeSize, color = InkSoft, fontWeight = FontWeight.Bold)
    }
}
