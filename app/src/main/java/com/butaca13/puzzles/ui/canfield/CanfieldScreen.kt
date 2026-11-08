package com.butaca13.puzzles.ui.canfield

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
import com.butaca13.puzzles.game.canfield.CanfieldGame
import com.butaca13.puzzles.game.canfield.Pile
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
fun CanfieldScreen(viewModel: CanfieldViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val game = viewModel.session.game ?: return
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
        title = stringResource(R.string.canfield_title),
        session = viewModel.session,
        status = status,
        onExit = onExit,
        actions = {
            if (game.isWon) {
                GameResult(
                    message = pluralStringResource(R.plurals.canfield_won, game.moves, game.moves),
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
 * The table's measures for the space it has, seven cards across: on top, the stock, the waste and the
 * four foundations, as in the patience; below, the reserve on the left, a little of each of its cards
 * showing, and the four columns under the foundations, their cards closer together the longer they grow.
 */
private class TableLayout(game: CanfieldGame, width: Float, height: Float) : CardLayout<Pile>(width, 7) {
    val rowTop = cardHeight + gap * 2.5f
    override val spots: List<Spot>

    init {
        val all = mutableListOf<Spot>()
        game.columns.forEachIndexed { c, cards ->
            fan(cards, rowTop, height) { i, y -> all += Spot(cards[i], Pile.Column(c), i, columnX(COLUMNS_AT + c), y, all.size) }
        }
        // The reserve squared up but for a sliver of each card, so that its depth shows.
        val sliver = cardHeight * 0.11f
        game.reserve.forEachIndexed { i, card -> all += Spot(card, Pile.Reserve, i, columnX(0), rowTop + i * sliver, all.size) }
        game.stock.forEachIndexed { i, card -> all += Spot(card, Pile.Stock, i, columnX(0), 0f, all.size) }
        val fanned = if (game.drawCount == 3) 3 else 1
        game.waste.forEachIndexed { i, card ->
            val fromTop = game.waste.lastIndex - i
            val shift = (fanned - 1 - fromTop).coerceAtLeast(0) * cardWidth * 0.46f
            all += Spot(card, Pile.Waste, i, columnX(1) + shift, 0f, all.size)
        }
        game.foundations.forEachIndexed { f, cards ->
            cards.forEachIndexed { i, card -> all += Spot(card, Pile.Foundation(f), i, columnX(COLUMNS_AT + f), 0f, all.size) }
        }
        spots = all
    }

    fun onStock(point: Offset) = point.x in columnX(0)..columnX(0) + cardWidth && point.y in 0f..cardHeight

    /** A foundation in the top row, else a column below. */
    override fun dropTarget(point: Offset): Pile? {
        val c = columnAt(point.x, 7) - COLUMNS_AT
        if (c !in 0 until 4) return null
        return if (point.y < rowTop - gap) Pile.Foundation(c) else Pile.Column(c)
    }

    companion object {
        const val COLUMNS_AT = 3
    }
}

@Composable
private fun Table(game: CanfieldGame, side: Dp, viewModel: CanfieldViewModel) {
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
            CardSlot(
                layout.columnX(0), 0f, cardWidth, cardHeight, if (game.waste.isNotEmpty()) "↻" else "",
                stringResource(if (game.waste.isNotEmpty()) R.string.klondike_stock_recycle else R.string.klondike_stock_empty),
                if (game.waste.isNotEmpty()) viewModel::draw else null,
            )
        }
        if (game.reserve.isEmpty()) CardSlot(layout.columnX(0), layout.rowTop, cardWidth, cardHeight, "", stringResource(R.string.canfield_reserve_empty))
        val base = rankLabel(game.base)
        for (f in 0 until 4) {
            if (game.foundations[f].isEmpty()) {
                CardSlot(layout.columnX(TableLayout.COLUMNS_AT + f), 0f, cardWidth, cardHeight, base, stringResource(R.string.canfield_foundation_empty, f + 1, base))
            }
        }
        for (c in 0 until 4) {
            if (game.columns[c].isEmpty()) {
                CardSlot(layout.columnX(TableLayout.COLUMNS_AT + c), layout.rowTop, cardWidth, cardHeight, "", stringResource(R.string.klondike_column_empty, c + 1))
            }
        }
    }
}

private fun rankLabel(rank: Int) = when (rank) {
    1 -> "A"
    11 -> "J"
    12 -> "Q"
    13 -> "K"
    else -> rank.toString()
}

/** Every card's pile, by its id. */
private fun CanfieldGame.positions(): Map<Int, Any> = buildMap {
    stock.forEach { put(it.id, Pile.Stock) }
    waste.forEach { put(it.id, Pile.Waste) }
    reserve.forEach { put(it.id, Pile.Reserve) }
    foundations.forEachIndexed { i, pile -> pile.forEach { put(it.id, Pile.Foundation(i)) } }
    columns.forEachIndexed { i, pile -> pile.forEach { put(it.id, Pile.Column(i)) } }
}

/** A card as screen readers hear it, with where it lies. Only the top of the piles speaks. */
@Composable
private fun describe(game: CanfieldGame, spot: Spot): String {
    val name = cardName(spot.card)
    return when (val pile = spot.pile) {
        Pile.Stock -> if (spot.index == game.stock.lastIndex) stringResource(R.string.klondike_stock, game.stock.size) else ""
        Pile.Waste -> if (spot.index == game.waste.lastIndex) stringResource(R.string.klondike_waste, name) else ""
        Pile.Reserve -> if (spot.index == game.reserve.lastIndex) stringResource(R.string.canfield_reserve, game.reserve.size, name) else ""
        is Pile.Foundation -> stringResource(R.string.klondike_foundation, pile.index + 1, name)
        is Pile.Column -> stringResource(R.string.freecell_column, pile.index + 1, name)
    }
}
