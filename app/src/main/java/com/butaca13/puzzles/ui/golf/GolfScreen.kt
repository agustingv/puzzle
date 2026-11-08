package com.butaca13.puzzles.ui.golf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.golf.GolfGame
import com.butaca13.puzzles.game.golf.Place
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
fun GolfScreen(viewModel: GolfViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val game = viewModel.session.game ?: return
    val possible = remember(game) { game.possibleMoves }
    val left = when {
        game.isWon -> null
        possible > 0 -> pluralStringResource(R.plurals.klondike_possible, possible, possible)
        game.stock.isNotEmpty() -> stringResource(R.string.klondike_draw_hint)
        // Out of moves, the cards left are golf's score: how close it came.
        else -> pluralStringResource(R.plurals.golf_stuck, game.left, game.left)
    }
    val status = pluralStringResource(R.plurals.klondike_status, game.moves, game.moves) + (left?.let { " · $it" } ?: "")

    GameScaffold(
        title = stringResource(R.string.golf_title),
        session = viewModel.session,
        status = status,
        onExit = onExit,
        actions = {
            if (game.isWon) {
                GameResult(
                    message = pluralStringResource(R.plurals.golf_won, game.moves, game.moves),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.wraps) },
                )
            } else {
                GameActionButton(stringResource(R.string.klondike_undo), viewModel::undo, enabled = viewModel.canUndo)
            }
        },
        board = { side -> Table(game, side, viewModel) },
    )
}

/**
 * The table's measures for the space it has: seven columns across, their cards fanned down as far as
 * they need, and under them the stock and the waste side by side in the middle.
 */
private class TableLayout(game: GolfGame, width: Float) : CardLayout<Place>(width, GolfGame.COLUMNS) {
    private val step = cardHeight * 0.30f
    val pilesTop = step * 4 + cardHeight + cardHeight * 0.4f
    private val apart = cardWidth * 0.15f
    val wasteX = width / 2 + apart
    val stockX = width / 2 - apart - cardWidth
    override val spots: List<CardSpot<Place>>

    init {
        val all = mutableListOf<CardSpot<Place>>()
        fun add(card: Card, place: Place, index: Int, x: Float, y: Float) {
            all += CardSpot(card, place, index, x, y, all.size)
        }
        game.columns.forEachIndexed { c, cards -> cards.forEachIndexed { i, card -> add(card, Place.Column(c), i, columnX(c), i * step) } }
        game.stock.forEachIndexed { i, card -> add(card, Place.Stock, i, stockX, pilesTop) }
        game.waste.forEachIndexed { i, card -> add(card, Place.Waste, i, wasteX, pilesTop) }
        spots = all
    }

    fun onStock(point: Offset) = point.x in stockX..stockX + cardWidth && point.y in pilesTop..pilesTop + cardHeight

    /** Only the waste takes cards, anywhere near it. */
    override fun dropTarget(point: Offset): Place? =
        Place.Waste.takeIf { point.y > pilesTop - cardHeight / 2 && point.x > wasteX - cardWidth / 2 }
}

@Composable
private fun Table(game: GolfGame, side: Dp, viewModel: GolfViewModel) {
    CardTable(
        side = side,
        layoutFor = { width, _ -> TableLayout(game, width) },
        layoutKey = game,
        moved = rememberMovedCards(game) { it.positions() },
        canPickUp = { place, index -> place is Place.Column && index == game.columns[place.index].lastIndex && game.playable(place.index) != null },
        onTap = { spot ->
            when (val place = spot.pile) {
                Place.Stock -> viewModel.draw()
                is Place.Column -> if (spot.index == game.columns[place.index].lastIndex) viewModel.playCard(place.index)
                Place.Waste -> Unit
            }
        },
        onDrop = { from, _, _ -> if (from is Place.Column) viewModel.playCard(from.index) },
        describe = { describe(game, it) },
        onTapEmpty = { layout, point -> if ((layout as TableLayout).onStock(point)) viewModel.draw() },
    ) { layout, cardWidth, cardHeight ->
        layout as TableLayout
        if (game.stock.isEmpty()) CardSlot(layout.stockX, layout.pilesTop, cardWidth, cardHeight, "", stringResource(R.string.klondike_stock_empty))
    }
}

/** Every card's place, by its id. */
private fun GolfGame.positions(): Map<Int, Any> = buildMap {
    columns.forEachIndexed { i, column -> column.forEach { put(it.id, Place.Column(i)) } }
    stock.forEach { put(it.id, Place.Stock) }
    waste.forEach { put(it.id, Place.Waste) }
}

/** A card as screen readers hear it, with where it lies. Only the top of each pile speaks. */
@Composable
private fun describe(game: GolfGame, spot: CardSpot<Place>): String = when (val place = spot.pile) {
    is Place.Column -> stringResource(R.string.freecell_column, place.index + 1, cardName(spot.card))
    Place.Stock -> if (spot.index == game.stock.lastIndex) stringResource(R.string.klondike_stock, game.stock.size) else ""
    Place.Waste -> if (spot.index == game.waste.lastIndex) stringResource(R.string.klondike_waste, cardName(spot.card)) else ""
}
