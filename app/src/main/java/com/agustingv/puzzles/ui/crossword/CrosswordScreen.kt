package com.agustingv.puzzles.ui.crossword

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.game.crossword.ClueState
import com.agustingv.puzzles.game.crossword.CrosswordDirection
import com.agustingv.puzzles.game.crossword.CrosswordGame
import com.agustingv.puzzles.ui.common.BoardZoomState
import com.agustingv.puzzles.ui.common.GameClock
import com.agustingv.puzzles.ui.common.GameResult
import com.agustingv.puzzles.ui.common.GameScaffold
import com.agustingv.puzzles.ui.common.LetterKeyboard
import com.agustingv.puzzles.ui.common.SubmitKeys
import com.agustingv.puzzles.ui.common.ZoomableBoard
import com.agustingv.puzzles.ui.theme.Accent
import com.agustingv.puzzles.ui.theme.Border
import com.agustingv.puzzles.ui.theme.Ink
import com.agustingv.puzzles.ui.theme.InkSoft
import com.agustingv.puzzles.ui.theme.Pink
import com.agustingv.puzzles.ui.theme.PinkDark

private const val ZOOM_FROM_SIZE = 15
private val ClueListMaxHeight = 220.dp
private val ClueMinHeight = 88.dp
private val LandscapeKeyHeight = 32.dp

@Composable
fun CrosswordScreen(viewModel: CrosswordViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val game = viewModel.session.game ?: return
    var showClues by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }

    GameScaffold(
        title = stringResource(R.string.crossword_title),
        elapsedSeconds = viewModel.session.elapsedSeconds,
        status = stringResource(R.string.grid_solved, game.solved.size, game.words.size),
        onExit = onExit,
        actions = {
            if (game.isOver) {
                GameResult(
                    message = if (game.isComplete) {
                        stringResource(R.string.crossword_won, game.words.size)
                    } else {
                        stringResource(R.string.crossword_lost, game.solved.size, game.words.size)
                    },
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.difficulty) },
                )
                ClueList(game, onSelect = {})
            } else {
                // Landscape has little height: clue, buttons and keyboard must all fit without scrolling.
                val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

                CurrentClue(
                    game = game,
                    minHeight = if (landscape) 0.dp else ClueMinHeight,
                    onMove = viewModel::moveWord,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showClues = !showClues }) {
                        Text(stringResource(if (showClues) R.string.crossword_keyboard else R.string.crossword_clues))
                    }
                    OutlinedButton(onClick = { confirmFinish = true }) {
                        Text(stringResource(R.string.crossword_finish))
                    }
                }
                if (showClues) {
                    ClueList(game, onSelect = {
                        viewModel.selectWord(it)
                        showClues = false
                    })
                } else {
                    LetterKeyboard(
                        onLetter = viewModel::type,
                        submitKeys = SubmitKeys(
                            canSubmit = true,
                            canDelete = true,
                            onSubmit = viewModel::check,
                            onDelete = viewModel::delete,
                        ),
                        keyHeight = if (landscape) LandscapeKeyHeight else 48.dp,
                    )
                }
            }
        },
        board = { side -> Board(game, side, onTap = viewModel::tap) },
    )

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.crossword_finish_title)) },
            text = { Text(stringResource(R.string.crossword_finish_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    viewModel.finish()
                }) { Text(stringResource(R.string.crossword_finish)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.crossword_continue)) }
            },
        )
    }
}

@Composable
private fun Board(game: CrosswordGame, side: Dp, onTap: (Int) -> Unit) {
    val zoom = remember(game.size) { BoardZoomState() }
    val size = game.size

    // Typing moves the cursor: keep it on screen while the board is zoomed in.
    LaunchedEffect(game.cursorCell, zoom.scale) {
        zoom.reveal(
            left = (game.cursorCell % size) / size.toFloat(),
            top = (game.cursorCell / size) / size.toFloat(),
            cellFraction = 1f / size,
        )
    }

    val selected = game.words[game.cursorWord].cells.toSet()
    val solvedCells = game.solved.flatMap { game.words[it].cells }.toSet()

    ZoomableBoard(
        side = side,
        state = zoom,
        zoomEnabled = size >= ZOOM_FROM_SIZE,
        singleFingerPans = true,
        onTap = { position ->
            val row = (position.y * size).toInt().coerceIn(0, size - 1)
            val col = (position.x * size).toInt().coerceIn(0, size - 1)
            onTap(row * size + col)
        },
    ) { boardSide ->
        val cellSide = boardSide / size

        Column(modifier = Modifier.size(boardSide).background(Ink)) {
            for (row in 0 until size) {
                Row {
                    for (col in 0 until size) {
                        val cell = row * size + col
                        if (game.isBlocked(cell)) {
                            Box(modifier = Modifier.size(cellSide))
                        } else {
                            Cell(
                                letter = game.input[cell].takeIf { it != CrosswordGame.EMPTY },
                                number = game.numbers[cell],
                                background = when {
                                    !game.isOver && cell == game.cursorCell -> Pink.copy(alpha = 0.55f)
                                    !game.isOver && cell in selected -> Pink.copy(alpha = 0.2f)
                                    cell in solvedCells -> Accent.copy(alpha = 0.35f)
                                    else -> Color.White
                                },
                                side = cellSide,
                                description = stringResource(
                                    if (!game.isOver && cell == game.cursorCell) {
                                        R.string.crossword_cell_selected
                                    } else {
                                        R.string.crossword_cell
                                    },
                                    row + 1,
                                    col + 1,
                                    game.input[cell].uppercase().trim(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(letter: Char?, number: Int?, background: Color, side: Dp, description: String) {
    val density = LocalDensity.current
    val letterSize = with(density) { (side * 0.6f).toSp() }
    val numberSize = with(density) { (side * 0.28f).toSp() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(side)
            .background(Color.White)
            .background(background)
            .border(0.5.dp, InkSoft)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (number != null) {
            Text(
                text = number.toString(),
                fontSize = numberSize,
                lineHeight = numberSize,
                color = Ink,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 1.dp),
            )
        }
        if (letter != null) {
            Text(
                text = letter.uppercase(),
                fontSize = letterSize,
                lineHeight = letterSize,
                fontWeight = FontWeight.Bold,
                color = Ink,
            )
        }
    }
}

@Composable
private fun directionLabel(direction: CrosswordDirection) = stringResource(
    if (direction == CrosswordDirection.ACROSS) R.string.crossword_across else R.string.crossword_down,
)

@Composable
private fun CurrentClue(game: CrosswordGame, minHeight: Dp, onMove: (Int) -> Unit) {
    val word = game.words[game.cursorWord]
    val previous = stringResource(R.string.crossword_previous)
    val next = stringResource(R.string.crossword_next)

    Row(
        // Fixed room for a long clue: otherwise the board would resize from one word to the next.
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(Border, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { onMove(-1) },
            modifier = Modifier.clearAndSetSemantics { contentDescription = previous },
        ) { Text("◀") }
        Column(
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    if (word.direction == CrosswordDirection.ACROSS) {
                        R.string.crossword_clue_across
                    } else {
                        R.string.crossword_clue_down
                    },
                    word.number,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = InkSoft,
            )
            Text(text = word.clue, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            if (game.clueState(game.cursorWord) == ClueState.WRONG) {
                Text(
                    text = stringResource(R.string.crossword_wrong),
                    style = MaterialTheme.typography.labelMedium,
                    color = PinkDark,
                )
            }
        }
        TextButton(
            onClick = { onMove(1) },
            modifier = Modifier.clearAndSetSemantics { contentDescription = next },
        ) { Text("▶") }
    }
}

@Composable
private fun ClueList(game: CrosswordGame, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ClueListMaxHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CrosswordDirection.entries.forEach { direction ->
            Text(
                text = directionLabel(direction),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
            game.words.forEachIndexed { index, word ->
                if (word.direction == direction) {
                    val state = game.clueState(index)
                    val text = if (state == ClueState.SOLVED) word.word else word.clue
                    val suffix = if (state == ClueState.WRONG) " (${stringResource(R.string.crossword_wrong)})" else ""

                    Text(
                        text = "${word.number}. $text$suffix",
                        color = when (state) {
                            ClueState.SOLVED -> InkSoft
                            ClueState.WRONG -> PinkDark
                            ClueState.PENDING -> Ink
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !game.isOver) { onSelect(index) }
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
