package com.butaca13.puzzles.ui.cards

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.Suit
import com.butaca13.puzzles.ui.common.FeltChalk
import com.butaca13.puzzles.ui.common.FeltTable
import com.butaca13.puzzles.ui.theme.GardenInk
import kotlin.math.roundToInt

/** Cards stay light in both themes, like real ones and like the garden's beds. */
private val CardFace = Color(0xFFFFFDF8)
private val CardRed = Color(0xFFD7263D)
private val CardEdge = Color(0x22000000)
private val BackRed = Color(0xFFB71C2C)
private val BackRedDark = Color(0xFF8E1422)

/** Where a card lies on the table, in pixels, and the pile of type [P] and place it holds there. */
class CardSpot<P>(val card: Card, val pile: P, val index: Int, val x: Float, val y: Float, val order: Int)

/** A card game's table measured for the space it has: where every card lies, and where cards let go fall. */
abstract class CardLayout<P>(width: Float, columns: Int) {
    val gap = width * 0.018f
    val cardWidth = (width - gap * (columns - 1)) / columns
    val cardHeight = cardWidth * 1.42f

    abstract val spots: List<CardSpot<P>>

    fun columnX(column: Int) = column * (cardWidth + gap)

    /** The column a point falls in, of [columns] across. */
    fun columnAt(x: Float, columns: Int) = (x / (cardWidth + gap)).toInt().coerceIn(0, columns - 1)

    /** Where cards let go with their middle at [point] would go, if anywhere. */
    abstract fun dropTarget(point: Offset): P?

    /** The card under a point, the one on top where several overlap. */
    fun spotAt(point: Offset): CardSpot<P>? = spots.lastOrNull {
        point.x in it.x..it.x + cardWidth && point.y in it.y..it.y + cardHeight
    }

    /** Lays [cards] out down from [top], closer together where they would not fit above [bottom]. */
    protected fun fan(cards: List<Card>, top: Float, bottom: Float, place: (index: Int, y: Float) -> Unit) {
        val down = cards.count { !it.faceUp }
        val up = cards.size - down
        var downStep = cardHeight * 0.13f
        var upStep = cardHeight * 0.30f
        val spread = down * downStep + (up - 1).coerceAtLeast(0) * upStep
        val room = bottom - top - cardHeight
        if (spread > room && spread > 0) {
            val squeeze = (room / spread).coerceAtLeast(0.2f)
            downStep *= squeeze
            upStep *= squeeze
        }
        var y = top
        cards.forEachIndexed { i, card ->
            place(i, y)
            y += if (card.faceUp) upStep else downStep
        }
    }
}

/** What is being dragged: the cards from [index] up in [pile], and how far the finger has taken them. */
private data class Drag<P>(val pile: P, val index: Int, val offset: Offset)

/**
 * The felt with the cards on it, laid out by [layout] for the space given: a card can be tapped, which
 * [onTap] handles, or dragged with those on top of it, when [canPickUp] lets it, and let go where
 * [CardLayout.dropTarget] says, for [onDrop]. A tap on no card goes to [onTapEmpty]. [moved] cards fly
 * over the rest on their way. [slots] draws the empty places under the cards.
 */
@Composable
fun <P> CardTable(
    side: Dp,
    layoutFor: (width: Float, height: Float) -> CardLayout<P>,
    layoutKey: Any,
    moved: Set<Int>,
    canPickUp: (P, Int) -> Boolean,
    onTap: (CardSpot<P>) -> Unit,
    onDrop: (from: P, index: Int, to: P) -> Unit,
    describe: @Composable (CardSpot<P>) -> String,
    onTapEmpty: (CardLayout<P>, Offset) -> Unit = { _, _ -> },
    slots: @Composable BoxScope.(CardLayout<P>, cardWidth: Dp, cardHeight: Dp) -> Unit,
) = FeltTable(Modifier.width(side).fillMaxHeight()) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val layout = remember(layoutKey, width, height) { layoutFor(width, height) }
        var drag by remember { mutableStateOf<Drag<P>?>(null) }

        val currentLayout by rememberUpdatedState(layout)
        val currentCanPickUp by rememberUpdatedState(canPickUp)
        val currentOnTap by rememberUpdatedState(onTap)
        val currentOnDrop by rememberUpdatedState(onDrop)
        val currentOnTapEmpty by rememberUpdatedState(onTapEmpty)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = down.position
                        val spot = currentLayout.spotAt(start)
                        val pickable = spot != null && currentCanPickUp(spot.pile, spot.index)
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
                                    currentLayout.dropTarget(center)?.let { currentOnDrop(current.pile, current.index, it) }
                                    drag = null
                                } else if (!dragging) {
                                    if (spot != null) currentOnTap(spot) else currentOnTapEmpty(currentLayout, start)
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

            slots(layout, cardWidth, cardHeight)

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
                        description = describe(spot),
                        onClick = { onTap(spot) },
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

/**
 * The cards whose place changed from the game before, to fly over the rest on their way: [positions]
 * says where each card lies, by its id.
 */
@Composable
fun <G> rememberMovedCards(game: G, positions: (G) -> Map<Int, Any>): Set<Int> {
    // A plain holder, not state: the game before this one, only to compare with.
    val previous = remember { arrayOf<Any?>(game) }
    return remember(game) {
        @Suppress("UNCHECKED_CAST")
        val before = positions(previous[0] as G)
        positions(game).filter { (id, pile) -> before[id] != pile }.keys.also { previous[0] = game }
    }
}

/** An empty place on the felt, outlined in chalk, with a faint [label]: an ace, a king, an arrow. */
@Composable
fun CardSlot(x: Float, y: Float, cardWidth: Dp, cardHeight: Dp, label: String, description: String, onClick: (() -> Unit)? = null) {
    val density = LocalDensity.current
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

/** A card's name as screen readers say it: "Reina de corazones". */
@Composable
fun cardName(card: Card): String {
    val rank = when (card.rank) {
        1 -> stringResource(R.string.card_rank_1)
        11 -> stringResource(R.string.card_rank_11)
        12 -> stringResource(R.string.card_rank_12)
        13 -> stringResource(R.string.card_rank_13)
        else -> card.rank.toString()
    }
    val suit = stringResource(
        when (card.suit) {
            Suit.SPADES -> R.string.card_spades
            Suit.HEARTS -> R.string.card_hearts
            Suit.DIAMONDS -> R.string.card_diamonds
            Suit.CLUBS -> R.string.card_clubs
        },
    )
    return stringResource(R.string.card_name, rank, suit)
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
        Suit.SPADES -> "♠︎"
        Suit.HEARTS -> "♥︎"
        Suit.DIAMONDS -> "♦︎"
        Suit.CLUBS -> "♣︎"
    }

/**
 * One card: face up, its rank and suit in the corner, where it shows when fanned out, and its suit
 * large in the middle; face down, the back of a poker deck.
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
            val cornerSize = with(density) { (width * 0.30f).toSp() }
            Text(
                text = card.rankLabel + card.suit.symbol,
                color = ink,
                fontSize = cornerSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                // Trimmed to the letters themselves, so that the corner shows whole on a card fanned
                // under another, however narrow the cards.
                style = LocalTextStyle.current.merge(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeight = cornerSize,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
                modifier = Modifier.padding(start = width * 0.07f, top = height * 0.05f),
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

/**
 * The back of a card as on a poker deck: inside the white margin, a red field crossed by a fine
 * diamond lattice, and a thin white line framing it.
 */
@Composable
private fun CardBack(modifier: Modifier, shape: RoundedCornerShape) {
    Canvas(modifier = modifier.clip(shape).background(BackRed)) {
        val step = size.width / 5
        val line = size.width * 0.035f
        // Diagonals both ways, far enough to cover the whole field whatever its height.
        var start = -size.height
        while (start < size.width + size.height) {
            drawLine(BackRedDark, Offset(start, 0f), Offset(start + size.height, size.height), line)
            drawLine(BackRedDark, Offset(start, size.height), Offset(start + size.height, 0f), line)
            start += step
        }
        val inset = size.width * 0.1f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(size.width * 0.06f),
            style = Stroke(width = size.width * 0.03f),
        )
    }
}
