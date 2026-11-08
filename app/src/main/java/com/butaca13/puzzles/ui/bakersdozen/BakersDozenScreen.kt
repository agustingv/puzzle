package com.butaca13.puzzles.ui.bakersdozen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.bakersdozen.BakersDozenGame
import com.butaca13.puzzles.game.bakersdozen.Pile
import com.butaca13.puzzles.ui.cards.CardLayout
import com.butaca13.puzzles.ui.cards.CardSlot
import com.butaca13.puzzles.ui.cards.CardSpot
import com.butaca13.puzzles.ui.cards.CardTable
import com.butaca13.puzzles.ui.cards.cardName
import com.butaca13.puzzles.ui.cards.rememberMovedCards
import com.butaca13.puzzles.ui.common.GameActionButton
import com.butaca13.puzzles.ui.common.GameClock
import com.butaca13.puzzles.ui.common.GameResult
import com.butaca13.puzzles.ui.common.GameScaffold

@Composable
fun BakersDozenScreen(viewModel: BakersDozenViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val game = viewModel.session.game ?: return
    val possible = remember(game) { game.possibleMoves }
    val left = when {
        game.isWon -> null
        possible > 0 -> pluralStringResource(R.plurals.klondike_possible, possible, possible)
        else -> stringResource(R.string.klondike_stuck)
    }
    val status = pluralStringResource(R.plurals.klondike_status, game.moves, game.moves) + (left?.let { " · $it" } ?: "")

    GameScaffold(
        title = stringResource(R.string.bakersdozen_title),
        session = viewModel.session,
        status = status,
        onExit = onExit,
        actions = {
            if (game.isWon) {
                GameResult(
                    message = pluralStringResource(R.plurals.bakersdozen_won, game.moves, game.moves),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.kingsOnEmpty) },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GameActionButton(stringResource(R.string.klondike_undo), viewModel::undo, enabled = viewModel.canUndo && !viewModel.completing)
                    if (game.canAutoComplete && !viewModel.completing) {
                        GameActionButton(stringResource(R.string.klondike_complete), viewModel::complete)
                    }
                }
            }
        },
        board = { side -> Table(game, side, viewModel) },
    )
}

private typealias Spot = CardSpot<Pile>

/**
 * The table's measures for the space it has. Thirteen columns would make the cards too small to read,
 * so they go in two rows, seven and six, each in half the height left under the foundations, whose four
 * places take the middle of the top row.
 */
private class TableLayout(game: BakersDozenGame, width: Float, height: Float) : CardLayout<Pile>(width, PER_ROW) {
    val firstTop = cardHeight + gap * 2.5f
    val secondTop = firstTop + (height - firstTop) / 2
    override val spots: List<Spot>

    /** Where column [c] lies: its x, and the top of its row. */
    fun columnAt(c: Int): Pair<Float, Float> =
        if (c < PER_ROW) columnX(c) to firstTop else columnX(c - PER_ROW) + (cardWidth + gap) / 2 to secondTop

    fun foundationX(f: Int) = columnX(FOUNDATIONS_AT + f)

    init {
        val all = mutableListOf<Spot>()
        game.columns.forEachIndexed { c, cards ->
            val (x, top) = columnAt(c)
            val bottom = if (c < PER_ROW) secondTop - gap else height
            fan(cards, top, bottom) { i, y -> all += Spot(cards[i], Pile.Column(c), i, x, y, all.size) }
        }
        game.foundations.forEachIndexed { f, cards ->
            cards.forEachIndexed { i, card -> all += Spot(card, Pile.Foundation(f), i, foundationX(f), 0f, all.size) }
        }
        spots = all
    }

    /** A foundation in the top row, else the column under the point in either row. */
    override fun dropTarget(point: Offset): Pile? = when {
        point.y < firstTop - gap -> {
            val c = columnAt(point.x, PER_ROW) - FOUNDATIONS_AT
            if (c in 0 until 4) Pile.Foundation(c) else null
        }
        point.y < secondTop - gap -> Pile.Column(columnAt(point.x, PER_ROW))
        else -> Pile.Column(PER_ROW + columnAt(point.x - (cardWidth + gap) / 2, BakersDozenGame.COLUMNS - PER_ROW))
    }

    companion object {
        const val PER_ROW = 7
        const val FOUNDATIONS_AT = 3
    }
}

@Composable
private fun Table(game: BakersDozenGame, side: Dp, viewModel: BakersDozenViewModel) {
    CardTable(
        side = side,
        layoutFor = { width, height -> TableLayout(game, width, height) },
        layoutKey = game,
        moved = rememberMovedCards(game) { it.positions() },
        canPickUp = game::canPickUp,
        onTap = { spot -> viewModel.send(spot.pile, spot.index) },
        onDrop = viewModel::move,
        describe = { describe(game, it) },
    ) { layout, cardWidth, cardHeight ->
        layout as TableLayout
        for (f in 0 until 4) {
            CardSlot(layout.foundationX(f), 0f, cardWidth, cardHeight, "A", stringResource(R.string.klondike_foundation_empty, f + 1))
        }
        for (c in game.columns.indices) {
            if (game.columns[c].isEmpty()) {
                val (x, y) = layout.columnAt(c)
                CardSlot(x, y, cardWidth, cardHeight, if (game.kingsOnEmpty) "K" else "", stringResource(R.string.klondike_column_empty, c + 1))
            }
        }
    }
}

/** Every card's pile, by its id. */
private fun BakersDozenGame.positions(): Map<Int, Any> = buildMap {
    foundations.forEachIndexed { i, pile -> pile.forEach { put(it.id, Pile.Foundation(i)) } }
    columns.forEachIndexed { i, pile -> pile.forEach { put(it.id, Pile.Column(i)) } }
}

/** A card as screen readers hear it, with where it lies. */
@Composable
private fun describe(game: BakersDozenGame, spot: Spot): String {
    val name = cardName(spot.card)
    return when (val pile = spot.pile) {
        is Pile.Foundation -> stringResource(R.string.klondike_foundation, pile.index + 1, name)
        is Pile.Column -> stringResource(R.string.freecell_column, pile.index + 1, name)
    }
}
