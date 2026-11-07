package com.butaca13.puzzles.ui.freecell

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
import com.butaca13.puzzles.game.freecell.FreeCellGame
import com.butaca13.puzzles.game.freecell.Place
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
fun FreeCellScreen(viewModel: FreeCellViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val game = viewModel.session.game ?: return
    val possible = remember(game) { game.possibleMoves }
    val left = when {
        game.isWon -> null
        possible > 0 -> pluralStringResource(R.plurals.klondike_possible, possible, possible)
        game.hasFreeCell -> stringResource(R.string.freecell_cell_hint)
        else -> stringResource(R.string.klondike_stuck)
    }
    val status = pluralStringResource(R.plurals.klondike_status, game.moves, game.moves) + (left?.let { " · $it" } ?: "")

    GameScaffold(
        title = stringResource(R.string.freecell_title),
        session = viewModel.session,
        status = status,
        onExit = onExit,
        actions = {
            if (game.isWon) {
                GameResult(
                    message = pluralStringResource(R.plurals.freecell_won, game.moves, game.moves),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.cells.size) },
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

private typealias Spot = CardSpot<Place>

/**
 * The table's measures for the space it has: eight columns across, the free cells on the left of the top
 * row and the foundations on its right, and the columns below, their cards closer together the longer
 * they grow, so that all of them always fit.
 */
private class TableLayout(game: FreeCellGame, width: Float, height: Float) : CardLayout<Place>(width, COLUMNS) {
    val columnsTop = cardHeight + gap * 2.5f
    val cellCount = game.cells.size
    override val spots: List<Spot>

    init {
        val all = mutableListOf<Spot>()
        game.columns.forEachIndexed { column, cards ->
            fan(cards, columnsTop, height) { i, y -> all += Spot(cards[i], Place.Column(column), i, columnX(column), y, all.size) }
        }
        game.cells.forEachIndexed { i, card -> if (card != null) all += Spot(card, Place.Cell(i), 0, columnX(i), 0f, all.size) }
        game.foundations.forEachIndexed { f, cards ->
            cards.forEachIndexed { i, card -> all += Spot(card, Place.Foundation(f), i, columnX(FOUNDATIONS_AT + f), 0f, all.size) }
        }
        spots = all
    }

    /** A cell or a foundation in the top row, else the column below. */
    override fun dropTarget(point: Offset): Place? {
        val column = columnAt(point.x, COLUMNS)
        return if (point.y < columnsTop - gap) {
            when {
                column < cellCount -> Place.Cell(column)
                column >= FOUNDATIONS_AT -> Place.Foundation(column - FOUNDATIONS_AT)
                else -> null
            }
        } else {
            Place.Column(column)
        }
    }

    companion object {
        const val COLUMNS = 8
        const val FOUNDATIONS_AT = 4
    }
}

@Composable
private fun Table(game: FreeCellGame, side: Dp, viewModel: FreeCellViewModel) {
    CardTable(
        side = side,
        layoutFor = { width, height -> TableLayout(game, width, height) },
        layoutKey = game,
        moved = rememberMovedCards(game) { it.positions() },
        canPickUp = game::canPickUp,
        onTap = { spot -> viewModel.send(spot.pile, spot.index) },
        onDrop = { from, index, to -> viewModel.move(from, index, dropPlace(game, from, index, to)) },
        describe = { describe(game, it) },
    ) { layout, cardWidth, cardHeight ->
        layout as TableLayout
        for (i in game.cells.indices) {
            if (game.cells[i] == null) CardSlot(layout.columnX(i), 0f, cardWidth, cardHeight, "", stringResource(R.string.freecell_cell_empty, i + 1))
        }
        for (f in 0 until 4) {
            CardSlot(layout.columnX(TableLayout.FOUNDATIONS_AT + f), 0f, cardWidth, cardHeight, "A", stringResource(R.string.klondike_foundation_empty, f + 1))
        }
        for (column in game.columns.indices) {
            if (game.columns[column].isEmpty()) {
                CardSlot(layout.columnX(column), layout.columnsTop, cardWidth, cardHeight, "", stringResource(R.string.klondike_column_empty, column + 1))
            }
        }
    }
}

/** A card let go on a taken cell goes to a free one, if there is one: the cells are all alike. */
private fun dropPlace(game: FreeCellGame, from: Place, index: Int, to: Place): Place {
    if (to !is Place.Cell || game.cells[to.index] == null) return to
    val cards = game.cards(from).drop(index)
    return game.cells.indices.map { Place.Cell(it) }.firstOrNull { game.canPlace(cards, it) } ?: to
}

/** Every card's place, by its id. */
private fun FreeCellGame.positions(): Map<Int, Any> = buildMap {
    cells.forEachIndexed { i, card -> if (card != null) put(card.id, Place.Cell(i)) }
    foundations.forEachIndexed { i, pile -> pile.forEach { put(it.id, Place.Foundation(i)) } }
    columns.forEachIndexed { i, pile -> pile.forEach { put(it.id, Place.Column(i)) } }
}

/** A card as screen readers hear it, with where it lies. */
@Composable
private fun describe(game: FreeCellGame, spot: Spot): String {
    val name = cardName(spot.card)
    return when (val place = spot.pile) {
        is Place.Cell -> stringResource(R.string.freecell_cell, place.index + 1, name)
        is Place.Foundation -> stringResource(R.string.klondike_foundation, place.index + 1, name)
        is Place.Column -> stringResource(R.string.freecell_column, place.index + 1, name)
    }
}
