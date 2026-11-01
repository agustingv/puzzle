package com.agustingv.puzzles.ui.hangman

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.agustingv.puzzles.R
import com.agustingv.puzzles.game.hangman.HangmanGame
import com.agustingv.puzzles.game.hangman.HangmanLetterState
import com.agustingv.puzzles.ui.common.GameClock
import com.agustingv.puzzles.ui.common.GameResult
import com.agustingv.puzzles.ui.common.GameScaffold
import com.agustingv.puzzles.ui.common.LetterBox
import com.agustingv.puzzles.ui.common.LetterKeyboard
import com.agustingv.puzzles.ui.theme.Accent
import com.agustingv.puzzles.ui.theme.Ink
import com.agustingv.puzzles.ui.theme.InkSoft
import com.agustingv.puzzles.ui.theme.Pink

private val LetterGap = 4.dp
private val MaxLetterSize = 48.dp

@Composable
fun HangmanScreen(viewModel: HangmanViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel::tick)

    val game = viewModel.game

    GameScaffold(
        title = stringResource(R.string.hangman_title),
        elapsedSeconds = viewModel.elapsedSeconds,
        status = stringResource(R.string.hangman_misses, game.wrongCount, HangmanGame.MAX_WRONG),
        onExit = onExit,
        actions = {
            if (game.isOver) {
                GameResult(
                    message = if (game.isWon) {
                        stringResource(R.string.hangman_won)
                    } else {
                        stringResource(R.string.game_lost_word, game.word)
                    },
                    onPlayAgain = { viewModel.newGame(game.word.length) },
                )
            } else {
                LetterKeyboard(
                    onLetter = viewModel::guess,
                    keyColor = {
                        when (game.letterState(it)) {
                            HangmanLetterState.UNUSED -> null
                            HangmanLetterState.CORRECT -> Accent
                            HangmanLetterState.INCORRECT -> InkSoft
                        }
                    },
                    keyEnabled = { game.letterState(it) == HangmanLetterState.UNUSED },
                )
            }
        },
        board = { side -> Board(game, side) },
    )
}

@Composable
private fun Board(game: HangmanGame, side: Dp) {
    val letter = min(side / game.word.length, MaxLetterSize) - LetterGap
    val description = stringResource(R.string.hangman_drawing, game.wrongCount, HangmanGame.MAX_WRONG)

    Column(
        modifier = Modifier.width(side),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The whole slot is a square: the drawing gets what the row of letters leaves over.
        val drawingHeight = side - letter - 12.dp

        Canvas(
            modifier = Modifier
                .size(width = drawingHeight * (VIEW_WIDTH / VIEW_HEIGHT), height = drawingHeight)
                .semantics { contentDescription = description },
        ) {
            drawGallows(game.wrongCount)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(LetterGap)) {
            game.revealed.forEach { char -> LetterBox(letter = char, size = letter) }
        }
    }
}

private const val VIEW_WIDTH = 200f
private const val VIEW_HEIGHT = 250f

/** Same drawing and coordinates as the web version's 200x250 SVG. */
private fun DrawScope.drawGallows(misses: Int) {
    val scale = size.height / VIEW_HEIGHT

    fun line(color: Color, x1: Int, y1: Int, x2: Int, y2: Int) = drawLine(
        color = color,
        start = Offset(x1 * scale, y1 * scale),
        end = Offset(x2 * scale, y2 * scale),
        strokeWidth = 5 * scale,
        cap = StrokeCap.Round,
    )

    line(Ink, 20, 230, 100, 230)
    line(Ink, 40, 230, 40, 20)
    line(Ink, 40, 20, 140, 20)
    line(Ink, 140, 20, 140, 40)

    if (misses >= 1) {
        drawCircle(Pink, radius = 15 * scale, center = Offset(140 * scale, 55 * scale), style = Stroke(5 * scale))
    }
    if (misses >= 2) line(Pink, 140, 70, 140, 130)
    if (misses >= 3) line(Pink, 140, 85, 115, 110)
    if (misses >= 4) line(Pink, 140, 85, 165, 110)
    if (misses >= 5) line(Pink, 140, 130, 115, 160)
    if (misses >= 6) line(Pink, 140, 130, 165, 160)
}
