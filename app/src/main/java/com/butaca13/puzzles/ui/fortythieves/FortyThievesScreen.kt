package com.butaca13.puzzles.ui.fortythieves

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
import com.butaca13.puzzles.game.fortythieves.FortyThievesGame
import com.butaca13.puzzles.game.fortythieves.Pile
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
fun FortyThievesScreen(viewModel: FortyThievesViewModel, onExit: () -> Unit) {
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
        title = stringResource(R.string.fortythieves_title),
        session = viewModel.session,
        status = status,
        onExit = onExit,
        actions = {
            if (game.isWon) {
                GameResult(
                    message = pluralStringResource(R.plurals.fortythieves_won, game.moves, game.moves),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.passes) },
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
 * The table's measures for the space it has: ten columns across; on top, the stock and the waste on the
 * left and the eight foundations; the columns below, their cards closer together the longer they grow,
 * so that all of them always fit.
 */
private class TableLayout(game: FortyThievesGame, width: Float, height: Float) : CardLayout<Pile>(width, FortyThievesGame.COLUMNS) {
    val columnsTop = cardHeight + gap * 2.5f
    override val spots: List<Spot>

    init {
        val all = mutableListOf<Spot>()
        game.columns.forEachIndexed { column, cards ->
            fan(cards, columnsTop, height) { i, y -> all += Spot(cards[i], Pile.Column(column), i, columnX(column), y, all.size) }
        }
        game.stock.forEachIndexed { i, card -> all += Spot(card, Pile.Stock, i, columnX(0), 0f, all.size) }
        game.waste.forEachIndexed { i, card -> all += Spot(card, Pile.Waste, i, columnX(1), 0f, all.size) }
        game.foundations.forEachIndexed { f, cards ->
            cards.forEachIndexed { i, card -> all += Spot(card, Pile.Foundation(f), i, columnX(FOUNDATIONS_AT + f), 0f, all.size) }
        }
        spots = all
    }

    fun onStock(point: Offset) = point.x in columnX(0)..columnX(0) + cardWidth && point.y in 0f..cardHeight

    /** A foundation in the top row, else the column below. */
    override fun dropTarget(point: Offset): Pile? {
        val column = columnAt(point.x, FortyThievesGame.COLUMNS)
        return if (point.y < columnsTop - gap) {
            if (column >= FOUNDATIONS_AT) Pile.Foundation(column - FOUNDATIONS_AT) else null
        } else {
            Pile.Column(column)
        }
    }

    companion object {
        const val FOUNDATIONS_AT = 2
    }
}

@Composable
private fun Table(game: FortyThievesGame, side: Dp, viewModel: FortyThievesViewModel) {
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
        layout as TableLayout
        if (game.stock.isEmpty()) {
            val recycle = game.canDraw
            CardSlot(
                layout.columnX(0), 0f, cardWidth, cardHeight, if (recycle) "↻" else "",
                if (recycle) stringResource(R.string.klondike_stock_recycle) else stringResource(R.string.klondike_stock_empty),
                if (recycle) viewModel::draw else null,
            )
        }
        if (game.waste.isEmpty()) CardSlot(layout.columnX(1), 0f, cardWidth, cardHeight, "", stringResource(R.string.klondike_waste_empty))
        for (f in 0 until FortyThievesGame.FOUNDATIONS) {
            if (game.foundations[f].isEmpty()) {
                CardSlot(layout.columnX(TableLayout.FOUNDATIONS_AT + f), 0f, cardWidth, cardHeight, "A", stringResource(R.string.klondike_foundation_empty, f + 1))
            }
        }
        for (column in game.columns.indices) {
            if (game.columns[column].isEmpty()) {
                CardSlot(layout.columnX(column), layout.columnsTop, cardWidth, cardHeight, "", stringResource(R.string.klondike_column_empty, column + 1))
            }
        }
    }
}

/** Every card's pile, by its id. */
private fun FortyThievesGame.positions(): Map<Int, Any> = buildMap {
    stock.forEach { put(it.id, Pile.Stock) }
    waste.forEach { put(it.id, Pile.Waste) }
    foundations.forEachIndexed { i, pile -> pile.forEach { put(it.id, Pile.Foundation(i)) } }
    columns.forEachIndexed { i, pile -> pile.forEach { put(it.id, Pile.Column(i)) } }
}

/** A card as screen readers hear it, with where it lies. Only the top of the stock and the waste speak. */
@Composable
private fun describe(game: FortyThievesGame, spot: Spot): String {
    val name = cardName(spot.card)
    return when (val pile = spot.pile) {
        Pile.Stock -> if (spot.index == game.stock.lastIndex) stringResource(R.string.klondike_stock, game.stock.size) else ""
        Pile.Waste -> if (spot.index == game.waste.lastIndex) stringResource(R.string.klondike_waste, name) else ""
        is Pile.Foundation -> stringResource(R.string.klondike_foundation, pile.index + 1, name)
        is Pile.Column -> stringResource(R.string.freecell_column, pile.index + 1, name)
    }
}
