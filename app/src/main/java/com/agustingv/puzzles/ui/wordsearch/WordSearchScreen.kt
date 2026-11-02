package com.agustingv.puzzles.ui.wordsearch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.game.wordsearch.PlacedWord
import com.agustingv.puzzles.game.wordsearch.WordSearchGame
import com.agustingv.puzzles.ui.common.BoardZoomState
import com.agustingv.puzzles.ui.common.GameClock
import com.agustingv.puzzles.ui.common.GameResult
import com.agustingv.puzzles.ui.common.GameScaffold
import com.agustingv.puzzles.ui.common.ZoomableBoard
import com.agustingv.puzzles.ui.theme.Accent
import com.agustingv.puzzles.ui.theme.Border
import com.agustingv.puzzles.ui.theme.Card
import com.agustingv.puzzles.ui.theme.Ink
import com.agustingv.puzzles.ui.theme.InkSoft
import com.agustingv.puzzles.ui.theme.Pink

private const val ZOOM_FROM_SIZE = 15

@Composable
fun WordSearchScreen(viewModel: WordSearchViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val game = viewModel.session.game ?: return

    GameScaffold(
        title = stringResource(R.string.wordsearch_title),
        elapsedSeconds = viewModel.session.elapsedSeconds,
        status = stringResource(R.string.grid_found, game.foundCount, game.words.size),
        onExit = onExit,
        actions = {
            if (viewModel.lastSelectionMissed) {
                Text(
                    text = stringResource(R.string.wordsearch_miss),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            WordList(game.words)
            if (game.isComplete) {
                GameResult(
                    message = stringResource(R.string.wordsearch_won, game.words.size),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.difficulty) },
                )
            }
        },
        board = { side -> Board(game, viewModel, side) },
    )
}

@Composable
private fun Board(game: WordSearchGame, viewModel: WordSearchViewModel, side: Dp) {
    val zoom = remember(game.size) { BoardZoomState() }
    val size = game.size

    fun coordinate(fraction: Float) = (fraction * size).toInt().coerceIn(0, size - 1)
    fun cellAt(position: Offset) = coordinate(position.y) * size + coordinate(position.x)

    ZoomableBoard(
        side = side,
        state = zoom,
        zoomEnabled = size >= ZOOM_FROM_SIZE,
        onTap = { viewModel.tap(cellAt(it)) },
        onDragStart = { viewModel.startDrag(cellAt(it)) },
        onDrag = { viewModel.dragTo(coordinate(it.y), coordinate(it.x)) },
        onDragEnd = viewModel::endDrag,
        onDragCancel = viewModel::cancelDrag,
    ) { boardSide ->
        val cell = boardSide / size
        val fontSize = with(LocalDensity.current) { (cell * 0.55f).toSp() }

        Box(modifier = Modifier.size(boardSide).background(Card)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                game.words.filter { it.found }.forEach { word ->
                    capsule(word.cells.first(), word.cells.last(), size, Accent.copy(alpha = 0.4f))
                }
                game.pendingStart?.let { capsule(it, it, size, Pink.copy(alpha = 0.5f)) }
                viewModel.dragSelection?.let { (start, end) ->
                    capsule(start, end, size, Pink.copy(alpha = 0.4f))
                }
            }

            Column {
                for (row in 0 until size) {
                    Row {
                        for (col in 0 until size) {
                            Box(modifier = Modifier.size(cell), contentAlignment = Alignment.Center) {
                                Text(
                                    text = game.grid[row * size + col].uppercase(),
                                    fontSize = fontSize,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Ink,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.capsule(start: Int, end: Int, boardCells: Int, color: Color) {
    val cell = size.width / boardCells

    fun center(index: Int) = Offset(
        (index % boardCells + 0.5f) * cell,
        (index / boardCells + 0.5f) * cell,
    )

    drawLine(
        color = color,
        start = center(start),
        end = center(end),
        strokeWidth = cell * 0.8f,
        cap = StrokeCap.Round,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordList(words: List<PlacedWord>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        words.forEach { word ->
            val label = if (word.found) {
                stringResource(R.string.wordsearch_word_found, word.word)
            } else {
                word.word
            }

            Text(
                text = word.word,
                style = MaterialTheme.typography.bodyMedium,
                color = if (word.found) InkSoft else Ink,
                fontWeight = if (word.found) FontWeight.Normal else FontWeight.SemiBold,
                textDecoration = if (word.found) TextDecoration.LineThrough else null,
                modifier = Modifier
                    .background(if (word.found) Accent.copy(alpha = 0.2f) else Border, MaterialTheme.shapes.small)
                    .clearAndSetSemantics { contentDescription = label }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}
