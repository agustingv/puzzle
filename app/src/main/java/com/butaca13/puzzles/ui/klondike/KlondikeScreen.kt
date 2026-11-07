package com.butaca13.puzzles.ui.klondike

import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.klondike.Card
import com.butaca13.puzzles.game.klondike.KlondikeGame
import com.butaca13.puzzles.game.klondike.Pile
import com.butaca13.puzzles.game.klondike.Suit
import com.butaca13.puzzles.ui.common.FeltChalk
import com.butaca13.puzzles.ui.common.FeltTable
import com.butaca13.puzzles.ui.common.GameActionButton
import com.butaca13.puzzles.ui.common.GameClock
import com.butaca13.puzzles.ui.common.GameResult
import com.butaca13.puzzles.ui.common.GameScaffold
import com.butaca13.puzzles.ui.theme.GardenInk
import kotlin.math.roundToInt

/** Cards stay light in both themes, like real ones and like the garden's beds. */
private val CardFace = Color(0xFFFFFDF8)
private val CardRed = Color(0xFFD7263D)
private val CardEdge = Color(0x22000000)

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

/** Where a card lies on the table, in pixels, and the pile and place it holds there. */
private class Spot(val card: Card, val pile: Pile, val index: Int, val x: Float, val y: Float, val order: Int)

/**
 * The table's measures for the space it has: seven columns of cards across, the stock, the waste and
 * the four foundations on top, and the columns below, their cards closer together the longer they grow,
 * so that all of them always fit.
 */
private class TableLayout(game: KlondikeGame, width: Float, height: Float) {
    val gap = width * 0.018f
    val cardWidth = (width - gap * 6) / 7
    val cardHeight = cardWidth * 1.42f
    val tableauTop = cardHeight + gap * 2.5f
    val spots: List<Spot>

    fun columnX(column: Int) = column * (cardWidth + gap)

    init {
        val all = mutableListOf<Spot>()
        var order = 0
        fun add(card: Card, pile: Pile, index: Int, x: Float, y: Float) {
            all += Spot(card, pile, index, x, y, order++)
        }

        game.tableau.forEachIndexed { column, cards ->
            val down = cards.count { !it.faceUp }
            val up = cards.size - down
            var downStep = cardHeight * 0.13f
            var upStep = cardHeight * 0.30f
            val spread = down * downStep + (up - 1).coerceAtLeast(0) * upStep
            val room = height - tableauTop - cardHeight
            if (spread > room && spread > 0) {
                val squeeze = (room / spread).coerceAtLeast(0.2f)
                downStep *= squeeze
                upStep *= squeeze
            }
            var y = tableauTop
            cards.forEachIndexed { i, card ->
                add(card, Pile.Tableau(column), i, columnX(column), y)
                y += if (card.faceUp) upStep else downStep
            }
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

    /** The card under a point, the one on top where several overlap. */
    fun spotAt(point: Offset): Spot? = spots.lastOrNull {
        point.x in it.x..it.x + cardWidth && point.y in it.y..it.y + cardHeight
    }

    fun onStock(point: Offset) = point.x in columnX(0)..columnX(0) + cardWidth && point.y in 0f..cardHeight

    /** Where cards let go at [point] would go: a foundation in the top row, else the column below. */
    fun dropTarget(point: Offset): Pile? {
        val column = (point.x / (cardWidth + gap)).toInt().coerceIn(0, 6)
        return if (point.y < tableauTop - gap) {
            if (column >= 3) Pile.Foundation(column - 3) else null
        } else {
            Pile.Tableau(column)
        }
    }
}

/** What is being dragged: the cards from [index] up in [pile], and how far the finger has taken them. */
private data class Drag(val pile: Pile, val index: Int, val offset: Offset)

@Composable
private fun Table(game: KlondikeGame, side: Dp, viewModel: KlondikeViewModel) = FeltTable(Modifier.width(side).fillMaxHeight()) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val layout = remember(game, width, height) { TableLayout(game, width, height) }
        var drag by remember { mutableStateOf<Drag?>(null) }

        // The cards that just changed pile fly over the rest on their way.
        // A plain holder, not state: the game before this one, only to compare with.
        val previous = remember { arrayOf(game) }
        val moved = remember(game) {
            val before = previous[0].positions()
            game.positions().filter { (id, pile) -> before[id] != pile }.keys.also { previous[0] = game }
        }

        val currentLayout by rememberUpdatedState(layout)
        val currentGame by rememberUpdatedState(game)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = down.position
                        val spot = currentLayout.spotAt(start)
                        val pickable = spot != null && currentGame.canPickUp(spot.pile, spot.index)
                        var dragging = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                val current = drag
                                if (dragging && current != null) {
                                    val first = currentLayout.spots.first { it.pile == current.pile && it.index == current.index }
                                    val center = Offset(first.x, first.y) + current.offset +
                                        Offset(currentLayout.cardWidth / 2, currentLayout.cardHeight / 2)
                                    currentLayout.dropTarget(center)?.let { viewModel.move(current.pile, current.index, it) }
                                    drag = null
                                } else if (!dragging) {
                                    when {
                                        currentLayout.onStock(start) -> viewModel.draw()
                                        spot != null && pickable -> viewModel.send(spot.pile, spot.index)
                                    }
                                }
                                change.consume()
                                break
                            }
                            val moved = change.position - start
                            if (!dragging && pickable && moved.getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                            }
                            if (dragging && spot != null) {
                                drag = Drag(spot.pile, spot.index, moved)
                                change.consume()
                            }
                        }
                        // A cancelled gesture puts the cards back.
                        if (drag != null) drag = null
                    }
                },
        ) {
            val cardWidth = with(density) { layout.cardWidth.toDp() }
            val cardHeight = with(density) { layout.cardHeight.toDp() }

            Slots(game, layout, cardWidth, cardHeight, viewModel)

            for (spot in layout.spots) {
                key(spot.card.id) {
                    val current = drag
                    val lifted = current != null && spot.pile == current.pile && spot.index >= current.index
                    val target = IntOffset(
                        (spot.x + if (lifted) current!!.offset.x else 0f).roundToInt(),
                        (spot.y + if (lifted) current!!.offset.y else 0f).roundToInt(),
                    )
                    val position by animateIntOffsetAsState(
                        targetValue = target,
                        animationSpec = if (lifted) snap() else spring(stiffness = Spring.StiffnessMediumLow),
                        label = "card",
                    )
                    PlayingCard(
                        card = spot.card,
                        width = cardWidth,
                        height = cardHeight,
                        description = describe(game, spot),
                        onClick = {
                            when (spot.pile) {
                                Pile.Stock -> viewModel.draw()
                                else -> viewModel.send(spot.pile, spot.index)
                            }
                        },
                        modifier = Modifier
                            .offset { position }
                            .zIndex(
                                when {
                                    lifted -> 2000f + spot.index
                                    spot.card.id in moved -> 1000f + spot.order
                                    else -> spot.order.toFloat()
                                },
                            ),
                    )
                }
            }
        }
    }
}

/** Every card's pile, by its id. */
private fun KlondikeGame.positions(): Map<Int, Pile> = buildMap {
    stock.forEach { put(it.id, Pile.Stock) }
    waste.forEach { put(it.id, Pile.Waste) }
    foundations.forEachIndexed { i, pile -> pile.forEach { put(it.id, Pile.Foundation(i)) } }
    tableau.forEachIndexed { i, pile -> pile.forEach { put(it.id, Pile.Tableau(i)) } }
}

/** The empty places: the stock to turn over, the foundations to start, the columns waiting for a king. */
@Composable
private fun Slots(game: KlondikeGame, layout: TableLayout, cardWidth: Dp, cardHeight: Dp, viewModel: KlondikeViewModel) {
    val density = LocalDensity.current

    @Composable
    fun slot(x: Float, y: Float, label: String, description: String, onClick: (() -> Unit)?) {
        val shape = RoundedCornerShape(cardWidth * 0.12f)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(cardWidth, cardHeight)
                .background(Color.Black.copy(alpha = 0.12f), shape)
                .border(1.5.dp, FeltChalk.copy(alpha = 0.35f), shape)
                .clearAndSetSemantics {
                    contentDescription = description
                    if (onClick != null) onClick { onClick(); true }
                },
        ) {
            Text(
                text = label,
                color = FeltChalk.copy(alpha = 0.4f),
                fontSize = with(density) { (cardWidth * 0.45f).toSp() },
                fontWeight = FontWeight.Bold,
            )
        }
    }

    val stockLabel = when {
        game.stock.isNotEmpty() -> ""
        game.waste.isNotEmpty() -> "↻"
        else -> ""
    }
    slot(
        layout.columnX(0), 0f, stockLabel,
        stringResource(if (game.waste.isNotEmpty()) R.string.klondike_stock_recycle else R.string.klondike_stock_empty),
        if (game.waste.isNotEmpty()) viewModel::draw else null,
    )
    for (f in 0 until 4) {
        slot(layout.columnX(3 + f), 0f, "A", stringResource(R.string.klondike_foundation_empty, f + 1), null)
    }
    for (column in 0 until 7) {
        if (game.tableau[column].isEmpty()) {
            slot(layout.columnX(column), layout.tableauTop, "K", stringResource(R.string.klondike_column_empty, column + 1), null)
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

@Composable
private fun cardName(card: Card): String {
    val rank = when (card.rank) {
        1 -> stringResource(R.string.klondike_rank_1)
        11 -> stringResource(R.string.klondike_rank_11)
        12 -> stringResource(R.string.klondike_rank_12)
        13 -> stringResource(R.string.klondike_rank_13)
        else -> card.rank.toString()
    }
    val suit = stringResource(
        when (card.suit) {
            Suit.SPADES -> R.string.klondike_spades
            Suit.HEARTS -> R.string.klondike_hearts
            Suit.DIAMONDS -> R.string.klondike_diamonds
            Suit.CLUBS -> R.string.klondike_clubs
        },
    )
    return stringResource(R.string.klondike_card, rank, suit)
}

private val Card.rankLabel: String
    get() = when (rank) {
        1 -> "A"
        11 -> "J"
        12 -> "Q"
        13 -> "K"
        else -> rank.toString()
    }

/** The suit as a plain symbol, in the card's ink: U+FE0E asks for text, not the coloured emoji. */
private val Suit.symbol: String
    get() = when (this) {
        Suit.SPADES -> "♠\uFE0E"
        Suit.HEARTS -> "♥\uFE0E"
        Suit.DIAMONDS -> "♦\uFE0E"
        Suit.CLUBS -> "♣\uFE0E"
    }

/**
 * One card: face up, its rank and suit in the corner, where it shows when fanned out, and its suit
 * large in the middle; face down, a pastel back.
 */
@Composable
private fun PlayingCard(
    card: Card,
    width: Dp,
    height: Dp,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(width * 0.12f)
    val ink = if (card.suit.red) CardRed else GardenInk

    Box(
        modifier = modifier
            .size(width, height)
            .shadow(1.5.dp, shape)
            .background(CardFace, shape)
            .border(0.5.dp, CardEdge, shape)
            .clearAndSetSemantics {
                if (description.isNotEmpty()) {
                    contentDescription = description
                    onClick { onClick(); true }
                }
            },
    ) {
        if (card.faceUp) {
            Text(
                text = card.rankLabel + card.suit.symbol,
                color = ink,
                fontSize = with(density) { (width * 0.30f).toSp() },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = width * 0.07f, top = height * 0.01f),
            )
            Text(
                text = card.suit.symbol,
                color = ink,
                fontSize = with(density) { (width * 0.58f).toSp() },
                modifier = Modifier.align(Alignment.Center).offset(y = height * 0.1f),
            )
        } else {
            CardBack(Modifier.fillMaxSize().padding(width * 0.08f), RoundedCornerShape(width * 0.08f))
        }
    }
}

private val BackRed = Color(0xFFB71C2C)
private val BackRedDark = Color(0xFF8E1422)

/**
 * The back of a card as on a poker deck: inside the white margin, a red field crossed by a fine
 * diamond lattice, and a thin white line framing it.
 */
@Composable
private fun CardBack(modifier: Modifier, shape: RoundedCornerShape) {
    Canvas(modifier = modifier.clip(shape).background(BackRed)) {
        val step = size.width / 5
        val line = size.width * 0.035f
        val pattern = BackRedDark
        // Diagonals both ways, far enough to cover the whole field whatever its height.
        var start = -size.height
        while (start < size.width + size.height) {
            drawLine(pattern, Offset(start, 0f), Offset(start + size.height, size.height), line)
            drawLine(pattern, Offset(start, size.height), Offset(start + size.height, 0f), line)
            start += step
        }
        val inset = size.width * 0.1f
        val corner = CornerRadius(size.width * 0.06f)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = corner,
            style = Stroke(width = size.width * 0.03f),
        )
    }
}
