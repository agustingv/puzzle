package com.butaca13.puzzles.ui.yukon

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
import com.butaca13.puzzles.game.yukon.Pile
import com.butaca13.puzzles.game.yukon.YukonGame
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
fun YukonScreen(viewModel: YukonViewModel, onExit: () -> Unit) {
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
        title = stringResource(R.string.yukon_title),
        session = viewModel.session,
        status = status,
        onExit = onExit,
        actions = {
            if (game.isWon) {
                GameResult(
                    message = pluralStringResource(R.plurals.yukon_won, game.moves, game.moves),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.sameSuit) },
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
 * The table's measures for the space it has: seven columns across, the four foundations on the right of
 * the top row, and the columns below, their cards closer together the longer they grow, so that all of
 * them always fit.
 */
private class TableLayout(game: YukonGame, width: Float, height: Float) : CardLayout<Pile>(width, 7) {
    val columnsTop = cardHeight + gap * 2.5f
    override val spots: List<Spot>

    init {
        val all = mutableListOf<Spot>()
        game.columns.forEachIndexed { column, cards ->
            fan(cards, columnsTop, height) { i, y -> all += Spot(cards[i], Pile.Column(column), i, columnX(column), y, all.size) }
        }
        game.foundations.forEachIndexed { f, cards ->
            cards.forEachIndexed { i, card -> all += Spot(card, Pile.Foundation(f), i, columnX(FOUNDATIONS_AT + f), 0f, all.size) }
        }
        spots = all
    }

    /** A foundation in the top row, else the column below. */
    override fun dropTarget(point: Offset): Pile? {
        val column = columnAt(point.x, 7)
        return if (point.y < columnsTop - gap) {
            if (column >= FOUNDATIONS_AT) Pile.Foundation(column - FOUNDATIONS_AT) else null
        } else {
            Pile.Column(column)
        }
    }

    companion object {
        const val FOUNDATIONS_AT = 3
    }
}

@Composable
private fun Table(game: YukonGame, side: Dp, viewModel: YukonViewModel) {
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
            CardSlot(layout.columnX(TableLayout.FOUNDATIONS_AT + f), 0f, cardWidth, cardHeight, "A", stringResource(R.string.klondike_foundation_empty, f + 1))
        }
        for (column in game.columns.indices) {
            if (game.columns[column].isEmpty()) {
                CardSlot(layout.columnX(column), layout.columnsTop, cardWidth, cardHeight, "K", stringResource(R.string.klondike_column_empty, column + 1))
            }
        }
    }
}

/** Every card's pile, by its id. */
private fun YukonGame.positions(): Map<Int, Any> = buildMap {
    foundations.forEachIndexed { i, pile -> pile.forEach { put(it.id, Pile.Foundation(i)) } }
    columns.forEachIndexed { i, pile -> pile.forEach { put(it.id, Pile.Column(i)) } }
}

/** A card as screen readers hear it, with where it lies. */
@Composable
private fun describe(game: YukonGame, spot: Spot): String {
    val name = cardName(spot.card)
    return when (val pile = spot.pile) {
        is Pile.Foundation -> stringResource(R.string.klondike_foundation, pile.index + 1, name)
        is Pile.Column -> {
            val hidden = game.columns[pile.index].take(spot.index).count { !it.faceUp }
            if (spot.card.faceUp) stringResource(R.string.klondike_column, pile.index + 1, hidden, name) else ""
        }
    }
}
