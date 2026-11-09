package com.butaca13.puzzles.ui.scorpion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.scorpion.Heap
import com.butaca13.puzzles.game.scorpion.ScorpionGame
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
fun ScorpionScreen(viewModel: ScorpionViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val game = viewModel.session.game ?: return
    val possible = remember(game) { game.possibleMoves }
    val left = when {
        game.isWon -> null
        possible > 0 -> pluralStringResource(R.plurals.klondike_possible, possible, possible)
        game.canDeal -> stringResource(R.string.scorpion_deal_hint)
        else -> stringResource(R.string.klondike_stuck)
    }
    val status = pluralStringResource(R.plurals.klondike_status, game.moves, game.moves) + (left?.let { " · $it" } ?: "")

    GameScaffold(
        title = stringResource(R.string.scorpion_title),
        session = viewModel.session,
        status = status,
        onExit = onExit,
        actions = {
            if (game.isWon) {
                GameResult(
                    message = pluralStringResource(R.plurals.scorpion_won, game.moves, game.moves),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.anyOnEmpty) },
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
 * The table's measures for the space it has: seven columns across; on top, the reserve on the left and
 * the runs done on the right, each as its king; the columns below, their cards closer together the
 * longer they grow, so that all of them always fit.
 */
private class TableLayout(game: ScorpionGame, width: Float, height: Float) : CardLayout<Heap>(width, 7) {
    val columnsTop = cardHeight + gap * 2.5f

    /** Where the runs done go: the last four places of the top row. */
    fun doneX(run: Int) = columnX(7 - ScorpionGame.RUNS + run)

    /** How far apart the reserve's cards show. */
    private val reserveShift = cardWidth * 0.12f

    val reserveWidth = cardWidth + reserveShift * 2
    override val spots: List<Spot>

    init {
        val all = mutableListOf<Spot>()
        game.columns.forEachIndexed { column, cards ->
            fan(cards, columnsTop, height) { i, y -> all += Spot(cards[i], Heap.Column(column), i, columnX(column), y, all.size) }
        }
        game.reserve.forEachIndexed { i, card -> all += Spot(card, Heap.Reserve, i, i * reserveShift, 0f, all.size) }
        game.done.forEachIndexed { run, cards ->
            cards.reversed().forEachIndexed { i, card -> all += Spot(card, Heap.Done(run), i, doneX(run), 0f, all.size) }
        }
        spots = all
    }

    fun onReserve(point: Offset) = point.x in 0f..reserveWidth && point.y in 0f..cardHeight

    /** Only the columns take cards. */
    override fun dropTarget(point: Offset): Heap? =
        if (point.y < columnsTop - gap) null else Heap.Column(columnAt(point.x, 7))
}

@Composable
private fun Table(game: ScorpionGame, side: Dp, viewModel: ScorpionViewModel) {
    CardTable(
        side = side,
        layoutFor = { width, height -> TableLayout(game, width, height) },
        layoutKey = game,
        moved = rememberMovedCards(game) { it.positions() },
        canPickUp = game::canPickUp,
        onTap = { spot ->
            when (spot.pile) {
                Heap.Reserve -> viewModel.deal()
                is Heap.Column -> viewModel.send(spot.pile, spot.index)
                is Heap.Done -> Unit
            }
        },
        onDrop = viewModel::move,
        describe = { describe(game, it) },
        onTapEmpty = { layout, point -> if ((layout as TableLayout).onReserve(point)) viewModel.deal() },
    ) { layout, cardWidth, cardHeight ->
        layout as TableLayout
        if (game.reserve.isEmpty()) CardSlot(0f, 0f, cardWidth, cardHeight, "", stringResource(R.string.scorpion_reserve_empty))
        for (run in game.done.size until ScorpionGame.RUNS) {
            CardSlot(layout.doneX(run), 0f, cardWidth, cardHeight, "K", stringResource(R.string.scorpion_done_empty))
        }
        for (column in game.columns.indices) {
            if (game.columns[column].isEmpty()) {
                val label = if (game.anyOnEmpty) "" else "K"
                CardSlot(layout.columnX(column), layout.columnsTop, cardWidth, cardHeight, label, stringResource(R.string.klondike_column_empty, column + 1))
            }
        }
    }
}

/** Every card's heap, by its id. */
private fun ScorpionGame.positions(): Map<Int, Any> = buildMap {
    reserve.forEach { put(it.id, Heap.Reserve) }
    done.forEachIndexed { i, run -> run.forEach { put(it.id, Heap.Done(i)) } }
    columns.forEachIndexed { i, column -> column.forEach { put(it.id, Heap.Column(i)) } }
}

/** A card as screen readers hear it, with where it lies. */
@Composable
private fun describe(game: ScorpionGame, spot: Spot): String = when (val heap = spot.pile) {
    // The reserve speaks once, by its top card; the runs done, by their king.
    Heap.Reserve -> if (spot.index == game.reserve.lastIndex) stringResource(R.string.scorpion_reserve) else ""
    is Heap.Done -> if (spot.index == 12) stringResource(R.string.scorpion_done, heap.index + 1, cardName(spot.card)) else ""
    is Heap.Column -> {
        val hidden = game.columns[heap.index].take(spot.index).count { !it.faceUp }
        if (spot.card.faceUp) stringResource(R.string.klondike_column, heap.index + 1, hidden, cardName(spot.card)) else ""
    }
}
