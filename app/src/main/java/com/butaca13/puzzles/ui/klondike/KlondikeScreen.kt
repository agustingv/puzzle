package com.butaca13.puzzles.ui.klondike

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
import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.klondike.KlondikeGame
import com.butaca13.puzzles.game.klondike.Pile
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
fun KlondikeScreen(viewModel: KlondikeViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val game = viewModel.session.game ?: return
    // Counted once per move, and the stock gone through only when nothing else is left.
    val possible = remember(game) { game.possibleMoves }
    val drawingHelps = remember(game) { possible == 0 && game.drawingHelps }
    val left = when {
        game.isWon -> null
        possible > 0 -> pluralStringResource(R.plurals.klondike_possible, possible, possible)
        drawingHelps -> stringResource(R.string.klondike_draw_hint)
        else -> stringResource(R.string.klondike_stuck)
    }
    val status = pluralStringResource(R.plurals.klondike_status, game.moves, game.moves) + (left?.let { " · $it" } ?: "")

    GameScaffold(
        title = stringResource(R.string.klondike_title),
        session = viewModel.session,
        status = status,
        onExit = onExit,
        actions = {
            if (game.isWon) {
                GameResult(
                    message = pluralStringResource(R.plurals.klondike_won, game.moves, game.moves),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.drawCount) },
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
 * The table's measures for the space it has: seven columns of cards across, the stock, the waste and
 * the four foundations on top, and the columns below, their cards closer together the longer they grow,
 * so that all of them always fit.
 */
private class TableLayout(game: KlondikeGame, width: Float, height: Float) : CardLayout<Pile>(width, 7) {
    val tableauTop = cardHeight + gap * 2.5f
    override val spots: List<Spot>

    init {
        val all = mutableListOf<Spot>()
        fun add(card: Card, pile: Pile, index: Int, x: Float, y: Float) {
            all += Spot(card, pile, index, x, y, all.size)
        }

        game.tableau.forEachIndexed { column, cards ->
            fan(cards, tableauTop, height) { i, y -> add(cards[i], Pile.Tableau(column), i, columnX(column), y) }
        }
        game.stock.forEachIndexed { i, card -> add(card, Pile.Stock, i, columnX(0), 0f) }
        // With three a draw, the last three of the waste fan out to the right, the top one last.
        val fanned = if (game.drawCount == 3) 3 else 1
        game.waste.forEachIndexed { i, card ->
            val fromTop = game.waste.lastIndex - i
            val shift = (fanned - 1 - fromTop).coerceAtLeast(0) * cardWidth * 0.46f
            add(card, Pile.Waste, i, columnX(1) + shift, 0f)
        }
        game.foundations.forEachIndexed { f, cards ->
            cards.forEachIndexed { i, card -> add(card, Pile.Foundation(f), i, columnX(3 + f), 0f) }
        }
        spots = all
    }

    fun onStock(point: Offset) = point.x in columnX(0)..columnX(0) + cardWidth && point.y in 0f..cardHeight

    /** A foundation in the top row, else the column below. */
    override fun dropTarget(point: Offset): Pile? {
        val column = columnAt(point.x, 7)
        return if (point.y < tableauTop - gap) {
            if (column >= 3) Pile.Foundation(column - 3) else null
        } else {
            Pile.Tableau(column)
        }
    }
}

@Composable
private fun Table(game: KlondikeGame, side: Dp, viewModel: KlondikeViewModel) {
    CardTable(
        side = side,
        layoutFor = { width, height -> TableLayout(game, width, height) },
        layoutKey = game,
        moved = rememberMovedCards(game) { it.positions() },
        canPickUp = game::canPickUp,
        onTap = { spot -> if (spot.pile == Pile.Stock) viewModel.draw() else viewModel.send(spot.pile, spot.index) },
        onDrop = viewModel::move,
        describe = { describe(game, it) },
        onTapEmpty = { layout, point -> if ((layout as TableLayout).onStock(point)) viewModel.draw() },
    ) { layout, cardWidth, cardHeight ->
        Slots(game, layout as TableLayout, cardWidth, cardHeight, viewModel)
    }
}

/** Every card's pile, by its id. */
private fun KlondikeGame.positions(): Map<Int, Any> = buildMap {
    stock.forEach { put(it.id, Pile.Stock) }
    waste.forEach { put(it.id, Pile.Waste) }
    foundations.forEachIndexed { i, pile -> pile.forEach { put(it.id, Pile.Foundation(i)) } }
    tableau.forEachIndexed { i, pile -> pile.forEach { put(it.id, Pile.Tableau(i)) } }
}

/** The empty places: the stock to turn over, the foundations to start, the columns waiting for a king. */
@Composable
private fun Slots(game: KlondikeGame, layout: TableLayout, cardWidth: Dp, cardHeight: Dp, viewModel: KlondikeViewModel) {
    CardSlot(
        layout.columnX(0), 0f, cardWidth, cardHeight,
        if (game.stock.isEmpty() && game.waste.isNotEmpty()) "↻" else "",
        stringResource(if (game.waste.isNotEmpty()) R.string.klondike_stock_recycle else R.string.klondike_stock_empty),
        if (game.waste.isNotEmpty()) viewModel::draw else null,
    )
    for (f in 0 until 4) {
        CardSlot(layout.columnX(3 + f), 0f, cardWidth, cardHeight, "A", stringResource(R.string.klondike_foundation_empty, f + 1))
    }
    for (column in 0 until 7) {
        if (game.tableau[column].isEmpty()) {
            CardSlot(layout.columnX(column), layout.tableauTop, cardWidth, cardHeight, "K", stringResource(R.string.klondike_column_empty, column + 1))
        }
    }
}

/** A card as screen readers hear it, with where it lies. */
@Composable
private fun describe(game: KlondikeGame, spot: Spot): String {
    val name = cardName(spot.card)
    return when (val pile = spot.pile) {
        Pile.Stock -> stringResource(R.string.klondike_stock, game.stock.size)
        Pile.Waste -> stringResource(R.string.klondike_waste, name)
        is Pile.Foundation -> stringResource(R.string.klondike_foundation, pile.index + 1, name)
        is Pile.Tableau -> {
            val hidden = game.tableau[pile.index].take(spot.index).count { !it.faceUp }
            if (spot.card.faceUp) stringResource(R.string.klondike_column, pile.index + 1, hidden, name) else ""
        }
    }
}
