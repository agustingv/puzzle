package com.butaca13.puzzles.ui.spider

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.spider.Heap
import com.butaca13.puzzles.game.spider.SpiderGame
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
fun SpiderScreen(viewModel: SpiderViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val game = viewModel.session.game ?: return
    val possible = remember(game) { game.possibleMoves }
    val left = when {
        game.isWon -> null
        possible > 0 -> pluralStringResource(R.plurals.klondike_possible, possible, possible)
        game.canDeal -> stringResource(R.string.spider_deal_hint)
        game.stock.isNotEmpty() -> stringResource(R.string.spider_fill_hint)
        else -> stringResource(R.string.klondike_stuck)
    }
    val status = pluralStringResource(R.plurals.klondike_status, game.moves, game.moves) + (left?.let { " · $it" } ?: "")

    GameScaffold(
        title = stringResource(R.string.spider_title),
        session = viewModel.session,
        status = status,
        onExit = onExit,
        actions = {
            if (game.isWon) {
                GameResult(
                    message = pluralStringResource(R.plurals.spider_won, game.moves, game.moves),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.suits) },
                )
            } else {
                GameActionButton(stringResource(R.string.klondike_undo), viewModel::undo, enabled = viewModel.canUndo)
            }
        },
        board = { side -> Table(game, side, viewModel) },
    )
}

private typealias Spot = CardSpot<Heap>

/**
 * The table's measures for the space it has: ten columns across; on top, the stock on the left, one
 * card showing for each deal it has left, and the runs done on the right, each as its king; the columns
 * below, their cards closer together the longer they grow, so that all of them always fit.
 */
private class TableLayout(game: SpiderGame, width: Float, height: Float) : CardLayout<Heap>(width, SpiderGame.COLUMNS) {
    val columnsTop = cardHeight + gap * 2.5f

    /** Where the runs done start, from the right: the last of them in the last column. */
    fun doneX(run: Int) = columnX(SpiderGame.COLUMNS - SpiderGame.RUNS + run)

    /** How far apart the stock's deals show. */
    private val dealShift = cardWidth * 0.18f

    val stockWidth = cardWidth + dealShift * (SpiderGame.DEALS - 1)

    override val spots: List<Spot>

    init {
        val all = mutableListOf<Spot>()
        game.columns.forEachIndexed { column, cards ->
            fan(cards, columnsTop, height) { i, y -> all += Spot(cards[i], Heap.Column(column), i, columnX(column), y, all.size) }
        }
        // The stock in piles of one deal each, fanned a little, the next deal on the right.
        game.stock.forEachIndexed { i, card ->
            all += Spot(card, Heap.Stock, i, (i / SpiderGame.COLUMNS) * dealShift, 0f, all.size)
        }
        // Each run done squares up on its own place, its king on top.
        game.done.forEachIndexed { run, cards ->
            cards.reversed().forEachIndexed { i, card -> all += Spot(card, Heap.Done(run), i, doneX(run), 0f, all.size) }
        }
        spots = all
    }

    fun onStock(point: Offset) = point.x in 0f..stockWidth && point.y in 0f..cardHeight

    /** Only the columns take cards. */
    override fun dropTarget(point: Offset): Heap? =
        if (point.y < columnsTop - gap) null else Heap.Column(columnAt(point.x, SpiderGame.COLUMNS))
}

@Composable
private fun Table(game: SpiderGame, side: Dp, viewModel: SpiderViewModel) {
    CardTable(
        side = side,
        layoutFor = { width, height -> TableLayout(game, width, height) },
        layoutKey = game,
        moved = rememberMovedCards(game) { it.positions() },
        canPickUp = game::canPickUp,
        onTap = { spot ->
            when (spot.pile) {
                Heap.Stock -> viewModel.deal()
                is Heap.Column -> viewModel.send(spot.pile, spot.index)
                is Heap.Done -> Unit
            }
        },
        onDrop = viewModel::move,
        describe = { describe(game, it) },
        onTapEmpty = { layout, point -> if ((layout as TableLayout).onStock(point)) viewModel.deal() },
    ) { layout, cardWidth, cardHeight ->
        layout as TableLayout
        if (game.stock.isEmpty()) {
            CardSlot(0f, 0f, cardWidth, cardHeight, "", stringResource(R.string.klondike_stock_empty))
        }
        for (run in game.done.size until SpiderGame.RUNS) {
            CardSlot(layout.doneX(run), 0f, cardWidth, cardHeight, "K", stringResource(R.string.spider_done_empty))
        }
        for (column in game.columns.indices) {
            if (game.columns[column].isEmpty()) {
                CardSlot(layout.columnX(column), layout.columnsTop, cardWidth, cardHeight, "", stringResource(R.string.klondike_column_empty, column + 1))
            }
        }
    }
}

/** Every card's heap, by its id. */
private fun SpiderGame.positions(): Map<Int, Any> = buildMap {
    stock.forEach { put(it.id, Heap.Stock) }
    done.forEachIndexed { i, run -> run.forEach { put(it.id, Heap.Done(i)) } }
    columns.forEachIndexed { i, column -> column.forEach { put(it.id, Heap.Column(i)) } }
}

/** A card as screen readers hear it, with where it lies. */
@Composable
private fun describe(game: SpiderGame, spot: Spot): String {
    return when (val heap = spot.pile) {
        // The stock speaks once, by its top card; the runs done, by their king.
        Heap.Stock -> if (spot.index == game.stock.lastIndex) pluralStringResource(R.plurals.spider_stock, game.dealsLeft, game.dealsLeft) else ""
        is Heap.Done -> if (spot.index == 12) stringResource(R.string.spider_done, heap.index + 1, cardName(spot.card)) else ""
        is Heap.Column -> {
            val hidden = game.columns[heap.index].take(spot.index).count { !it.faceUp }
            if (spot.card.faceUp) stringResource(R.string.klondike_column, heap.index + 1, hidden, cardName(spot.card)) else ""
        }
    }
}
