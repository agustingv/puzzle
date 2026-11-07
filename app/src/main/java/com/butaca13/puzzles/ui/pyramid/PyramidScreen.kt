package com.butaca13.puzzles.ui.pyramid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.pyramid.PyramidGame
import com.butaca13.puzzles.game.pyramid.Spot
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
fun PyramidScreen(viewModel: PyramidViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val game = viewModel.session.game ?: return
    val possible = remember(game) { game.possibleMoves }
    val left = when {
        game.isWon -> null
        possible > 0 -> pluralStringResource(R.plurals.klondike_possible, possible, possible)
        game.canDraw -> stringResource(R.string.klondike_draw_hint)
        else -> stringResource(R.string.klondike_stuck)
    }
    val status = pluralStringResource(R.plurals.klondike_status, game.moves, game.moves) + (left?.let { " · $it" } ?: "")

    GameScaffold(
        title = stringResource(R.string.pyramid_title),
        session = viewModel.session,
        status = status,
        onExit = onExit,
        actions = {
            if (game.isWon) {
                GameResult(
                    message = pluralStringResource(R.plurals.pyramid_won, game.moves, game.moves),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.passes) },
                )
            } else {
                GameActionButton(stringResource(R.string.klondike_undo), viewModel::undo, enabled = viewModel.canUndo)
            }
        },
        board = { side -> Table(game, side, viewModel) },
    )
}

/**
 * The table's measures for the space it has, seven cards across: on top, the stock, the waste beside it
 * and the cards taken away on the right; below, the pyramid, each row half over the one above.
 */
private class TableLayout(game: PyramidGame, width: Float) : CardLayout<Spot>(width, 7) {
    val pyramidTop = cardHeight + gap * 4f
    // Each row shows only the corner of the one above, and the cards of a row touch, so that nothing of
    // the covered cards' middles peeks out between them.
    private val rowStep = cardHeight * 0.34f
    private val step = cardWidth
    private val left = (width - step * PyramidGame.ROWS) / 2
    override val spots: List<CardSpot<Spot>>

    fun pyramidX(index: Int): Float {
        val (row, col) = PyramidGame.rowCol(index)
        return left + (PyramidGame.ROWS - 1 - row) * step / 2 + col * step
    }

    fun pyramidY(index: Int) = pyramidTop + PyramidGame.rowCol(index).first * rowStep

    init {
        val all = mutableListOf<CardSpot<Spot>>()
        fun add(card: Card, spot: Spot, index: Int, x: Float, y: Float) {
            all += CardSpot(card, spot, index, x, y, all.size)
        }
        game.pyramid.forEachIndexed { i, card -> if (card != null) add(card, Spot.Pyramid(i), i, pyramidX(i), pyramidY(i)) }
        game.stock.forEachIndexed { i, card -> add(card, Spot.Stock, i, columnX(0), 0f) }
        game.waste.forEachIndexed { i, card -> add(card, Spot.Waste, i, columnX(1), 0f) }
        game.discard.forEachIndexed { i, card -> add(card, Spot.Discard, i, columnX(6), 0f) }
        spots = all
    }

    fun onStock(point: Offset) = point.x in columnX(0)..columnX(0) + cardWidth && point.y in 0f..cardHeight

    /** The card a card let go on would pair with: the one under the dragged card's middle. */
    override fun dropTarget(point: Offset): Spot? =
        spotAt(point)?.pile?.takeIf { it is Spot.Pyramid || it == Spot.Waste }
}

@Composable
private fun Table(game: PyramidGame, side: Dp, viewModel: PyramidViewModel) {
    val selectedCard = viewModel.selected?.let { game.playable(it) }
    CardTable(
        side = side,
        layoutFor = { width, _ -> TableLayout(game, width) },
        layoutKey = game,
        moved = rememberMovedCards(game) { it.positions() },
        canPickUp = { spot, index ->
            game.playable(spot) != null && (spot != Spot.Waste || index == game.waste.lastIndex)
        },
        onTap = { spot ->
            when (spot.pile) {
                Spot.Stock -> viewModel.draw()
                Spot.Discard -> Unit
                else -> viewModel.tap(spot.pile)
            }
        },
        onDrop = { from, _, to -> viewModel.drop(from, to) },
        describe = { describe(game, it) },
        onTapEmpty = { layout, point -> if ((layout as TableLayout).onStock(point)) viewModel.draw() },
        selected = setOfNotNull(selectedCard?.id),
    ) { layout, cardWidth, cardHeight ->
        layout as TableLayout
        if (game.stock.isEmpty()) {
            val recycle = game.canDraw
            CardSlot(
                layout.columnX(0), 0f, cardWidth, cardHeight, if (recycle) "↻" else "",
                if (recycle) {
                    pluralStringResource(R.plurals.pyramid_recycle, game.passesLeft, game.passesLeft)
                } else {
                    stringResource(R.string.klondike_stock_empty)
                },
                if (recycle) viewModel::draw else null,
            )
        }
        if (game.waste.isEmpty()) CardSlot(layout.columnX(1), 0f, cardWidth, cardHeight, "", stringResource(R.string.klondike_waste_empty))
        if (game.discard.isEmpty()) CardSlot(layout.columnX(6), 0f, cardWidth, cardHeight, "", stringResource(R.string.pyramid_discard_empty))
    }
}

/** Every card's place, by its id. */
private fun PyramidGame.positions(): Map<Int, Any> = buildMap {
    pyramid.forEachIndexed { i, card -> if (card != null) put(card.id, Spot.Pyramid(i)) }
    stock.forEach { put(it.id, Spot.Stock) }
    waste.forEach { put(it.id, Spot.Waste) }
    discard.forEach { put(it.id, Spot.Discard) }
}

/** A card as screen readers hear it, with where it lies. Only the top of each pile speaks. */
@Composable
private fun describe(game: PyramidGame, spot: CardSpot<Spot>): String {
    val name = cardName(spot.card)
    return when (val place = spot.pile) {
        is Spot.Pyramid -> {
            val row = PyramidGame.rowCol(place.index).first + 1
            stringResource(if (game.uncovered(place.index)) R.string.pyramid_card else R.string.pyramid_covered, row, name)
        }
        Spot.Stock -> if (spot.index == game.stock.lastIndex) stringResource(R.string.klondike_stock, game.stock.size) else ""
        Spot.Waste -> if (spot.index == game.waste.lastIndex) stringResource(R.string.klondike_waste, name) else ""
        Spot.Discard -> if (spot.index == game.discard.lastIndex) stringResource(R.string.pyramid_discard, game.discard.size) else ""
    }
}
