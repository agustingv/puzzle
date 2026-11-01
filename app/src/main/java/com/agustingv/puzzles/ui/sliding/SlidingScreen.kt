package com.agustingv.puzzles.ui.sliding

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.game.sliding.SlidingPuzzleGame
import com.agustingv.puzzles.ui.common.GameClock
import com.agustingv.puzzles.ui.common.GameScaffold
import com.agustingv.puzzles.ui.theme.Accent
import com.agustingv.puzzles.ui.theme.Border
import com.agustingv.puzzles.ui.theme.Pink

private val TileGap = 6.dp

@Composable
fun SlidingScreen(viewModel: SlidingViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel::tick)

    val game = viewModel.game

    GameScaffold(
        title = stringResource(R.string.sliding_title),
        elapsedSeconds = viewModel.elapsedSeconds,
        status = pluralStringResource(R.plurals.moves_value, game.moves, game.moves),
        onExit = onExit,
        actions = {
            if (game.isSolved) {
                Text(
                    text = stringResource(R.string.sliding_solved),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Button(onClick = { viewModel.newGame(game.difficulty) }) {
                    Text(stringResource(R.string.play_again))
                }
            }
        },
        board = { side ->
            Board(game = game, onTileClick = viewModel::move, modifier = Modifier.size(side))
        },
    )
}

@Composable
private fun Board(game: SlidingPuzzleGame, onTileClick: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Border),
    ) {
        val cell = maxWidth / game.size
        val cellPx = with(LocalDensity.current) { cell.roundToPx() }

        // Keyed by tile value, not position, so each tile animates from its old cell to the new one.
        game.tiles.forEachIndexed { index, value ->
            if (value != 0) {
                key(value) {
                    val target = IntOffset(index % game.size * cellPx, index / game.size * cellPx)
                    val offset by animateIntOffsetAsState(target, label = "tile")

                    Tile(
                        value = value,
                        solved = game.isSolved,
                        onClick = { onTileClick(game.tiles.indexOf(value)) },
                        modifier = Modifier
                            .size(cell)
                            .offset { offset }
                            .padding(TileGap / 2),
                        fontSizeSp = if (game.size >= 5) 20 else 28,
                    )
                }
            }
        }
    }
}

@Composable
private fun Tile(
    value: Int,
    solved: Boolean,
    onClick: () -> Unit,
    fontSizeSp: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(if (solved) Accent else Pink)
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
            .clickable(
                enabled = !solved,
                role = Role.Button,
                onClickLabel = stringResource(R.string.sliding_move_tile),
                onClick = onClick,
            ),
    ) {
        Text(
            text = value.toString(),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSizeSp.sp,
        )
    }
}
