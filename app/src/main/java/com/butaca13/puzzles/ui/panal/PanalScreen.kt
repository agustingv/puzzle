package com.butaca13.puzzles.ui.panal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.panal.PanalGame
import com.butaca13.puzzles.ui.common.GameActionButton
import com.butaca13.puzzles.ui.common.GameClock
import com.butaca13.puzzles.ui.common.GameLoading
import com.butaca13.puzzles.ui.common.GameResult
import com.butaca13.puzzles.ui.common.GameScaffold
import com.butaca13.puzzles.ui.theme.Accent
import com.butaca13.puzzles.ui.theme.Card
import com.butaca13.puzzles.ui.theme.Danger
import com.butaca13.puzzles.ui.theme.Ink
import com.butaca13.puzzles.ui.theme.InkSoft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun PanalScreen(viewModel: PanalViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val title = stringResource(R.string.panal_title)
    val game = viewModel.session.game
    if (game == null) {
        GameLoading(title, onExit)
        return
    }
    val status = stringResource(R.string.panal_status, game.score, game.target) + " · " +
        pluralStringResource(R.plurals.panal_words, game.found.size, game.found.size)

    GameScaffold(
        title = title,
        session = viewModel.session,
        status = status,
        onExit = onExit,
        actions = {
            if (game.isWon) {
                GameResult(
                    message = pluralStringResource(R.plurals.panal_won, game.found.size, game.found.size, game.score),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.difficulty) },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameActionButton(stringResource(R.string.panal_delete), viewModel::delete, enabled = viewModel.typed.isNotEmpty())
                    GameActionButton(stringResource(R.string.panal_shuffle), viewModel::shuffle)
                    GameActionButton(stringResource(R.string.panal_submit), viewModel::submit, enabled = viewModel.typed.isNotEmpty())
                }
            }
        },
        board = { side -> Board(game, side, viewModel) },
    )
}

/** The word being typed and what the last one got, the honeycomb, and the words found under it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Board(game: PanalGame, side: Dp, viewModel: PanalViewModel) {
    Column(
        modifier = Modifier.width(side),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Typed(game, viewModel.typed)
        FeedbackLine(viewModel.feedback)
        Honeycomb(game, side * 0.78f, onLetter = viewModel::letter)
        // Always there, even empty, so that the honeycomb never moves under the finger.
        val found = game.found.sorted()
        run {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().height(side * 0.22f).verticalScroll(rememberScrollState()),
            ) {
                for (word in found) {
                    Text(
                        text = word.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (game.isPangram(word)) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (game.isPangram(word)) Accent else Ink,
                    )
                }
            }
        }
    }
}

/** The word as typed, its centre letters lit. */
@Composable
private fun Typed(game: PanalGame, typed: String) {
    val text = typed.uppercase().ifEmpty { " " }
    Row {
        for (c in text) {
            Text(
                text = c.toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (c.lowercaseChar() == game.center) Accent else Ink,
            )
        }
    }
}

@Composable
private fun FeedbackLine(feedback: PanalViewModel.Feedback?) {
    val (text, color) = when (feedback) {
        null -> " " to InkSoft
        is PanalViewModel.Feedback.Found ->
            (if (feedback.pangram) stringResource(R.string.panal_pangram, feedback.points) else "+${feedback.points}") to Accent
        is PanalViewModel.Feedback.Rejected -> stringResource(
            when (feedback.reason) {
                PanalGame.Rejection.TOO_SHORT -> R.string.panal_too_short
                PanalGame.Rejection.WRONG_LETTER -> R.string.panal_wrong_letter
                PanalGame.Rejection.NO_CENTER -> R.string.panal_no_center
                PanalGame.Rejection.ALREADY_FOUND -> R.string.panal_already_found
                PanalGame.Rejection.NOT_A_WORD -> R.string.panal_not_a_word
            },
        ) to Danger
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
}

/**
 * Seven hexagons: the centre letter in the middle, lit, and the other six round it. A tap on one types
 * its letter. Screen readers get each letter as a button of its own.
 */
@Composable
private fun Honeycomb(game: PanalGame, size: Dp, onLetter: (Char) -> Unit) {
    val measurer = rememberTextMeasurer()
    val letters = listOf(game.center) + game.outer
    val currentLetters by rememberUpdatedState(letters)
    val currentOnLetter by rememberUpdatedState(onLetter)
    val typeLabel = stringResource(R.string.panal_type)
    // The theme's colours, read here: drawing is not composition.
    val lit = Accent
    val plain = Card
    val edge = InkSoft.copy(alpha = 0.35f)
    val ink = Ink

    Canvas(
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = letters.joinToString(", ") { it.uppercase() } + ". $typeLabel" }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val r = this.size.width / 6.4f
                    val centers = hexCenters(Offset(this.size.width / 2f, this.size.height / 2f), r)
                    val nearest = centers.indices.minBy { (centers[it] - tap).getDistance() }
                    if ((centers[nearest] - tap).getDistance() <= r) currentOnLetter(currentLetters[nearest])
                }
            },
    ) {
        val r = this.size.width / 6.4f
        val centers = hexCenters(center, r)
        letters.forEachIndexed { i, letter ->
            val path = hexagon(centers[i], r * 0.94f)
            drawPath(path, if (i == 0) lit else plain)
            drawPath(path, if (i == 0) lit else edge, style = Stroke(2.dp.toPx()))
            val layout = measurer.measure(
                letter.uppercase(),
                TextStyle(fontSize = (r * 0.8f).toSp(), fontWeight = FontWeight.ExtraBold, color = if (i == 0) Color.White else ink),
            )
            drawText(layout, topLeft = centers[i] - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }
    }
}

/** The centre and the six round it, flat-topped hexagons of radius [r] side by side. */
private fun hexCenters(center: Offset, r: Float): List<Offset> {
    val d = r * sqrt(3f)
    return listOf(center) + (0 until 6).map { k ->
        val a = (PI / 6 + k * PI / 3).toFloat()
        center + Offset(cos(a) * d, sin(a) * d)
    }
}

private fun hexagon(center: Offset, r: Float) = Path().apply {
    for (k in 0 until 6) {
        val a = (k * PI / 3).toFloat()
        val p = center + Offset(cos(a) * r, sin(a) * r)
        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}
