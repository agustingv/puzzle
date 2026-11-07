package com.butaca13.puzzles.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.SecretClue
import com.butaca13.puzzles.data.SecretMessage
import com.butaca13.puzzles.ui.theme.Accent
import com.butaca13.puzzles.ui.theme.AccentDark
import com.butaca13.puzzles.ui.theme.Card
import com.butaca13.puzzles.ui.theme.Ink
import com.butaca13.puzzles.ui.theme.InkSoft
import com.butaca13.puzzles.ui.theme.Paper

private val AppTitleTileSize = 44.dp
private val AppTitleTileGap = 8.dp
private const val APP_TITLE_LETTER_RATIO = 0.44f

/** The U, as on the web. */
private const val APP_TITLE_LIT_LETTER = 1

private val CardTitleMaxTile = 20.dp
private const val SMALL_TILE_LETTER_RATIO = 0.6f

private val BarTitleMaxTile = 26.dp

/** Two lines of these still fit the height of the top bar. */
private val BarTitleMaxTileOnTwoLines = 20.dp

/** Space between tiles, as a share of a tile. */
private const val TILE_GAP = 0.18f

/** Space between words, as a share of a tile. */
private const val WORD_GAP = 0.5f

private const val CARD_TITLE_MAX_LINES = 2

/** A title on at most two lines, breaking between words so that the longest line is the shortest possible. */
internal fun titleLines(title: String): List<String> {
    val words = title.split(" ")
    if (words.size == 1) return words

    return (1 until words.size)
        .map { listOf(words.take(it).joinToString(" "), words.drop(it).joinToString(" ")) }
        .minBy { lines -> lines.maxOf(::lineWidth) }
}

/** How wide a line is, measured in tiles. */
internal fun lineWidth(line: String): Float =
    line.sumOf { (if (it == ' ') WORD_GAP else 1f).toDouble() }.toFloat() + (line.length - 1) * TILE_GAP

/** How many tiles wide the widest of these titles is: all cards share one tile size. */
internal fun widestTitle(titles: List<String>): Float = titles.flatMap(::titleLines).maxOf(::lineWidth)

private enum class TileStyle { RAISED, ON_CARD, LIT, PENDING, GOLD }

/** The secret message's tile once solved: gold, with dark letters that read on it. */
private val GoldLight = Color(0xFFFFE07A)
private val Gold = Color(0xFFF2B630)
private val GoldDark = Color(0xFFC98B12)
private val GoldInk = Color(0xFF5C3B00)

@Composable
private fun LetterTile(
    letter: Char,
    size: Dp,
    style: TileStyle,
    letterRatio: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size * 0.18f)
    val cardBorder = InkSoft.copy(alpha = 0.25f)
    val pendingBorder = InkSoft.copy(alpha = 0.35f)
    val fontSize = with(LocalDensity.current) { (size * letterRatio).toSp() }

    val look = when (style) {
        TileStyle.RAISED -> Modifier
            .shadow(size / 22, shape)
            .background(Card)
        TileStyle.ON_CARD -> Modifier
            .background(Paper, shape)
            .border(1.dp, cardBorder, shape)
        TileStyle.LIT -> Modifier
            .shadow(size / 7, shape, ambientColor = Accent, spotColor = Accent)
            .background(Brush.linearGradient(listOf(Accent, AccentDark)))
        TileStyle.GOLD -> Modifier
            .shadow(size / 7, shape, ambientColor = Gold, spotColor = Gold)
            .background(Brush.linearGradient(listOf(GoldLight, Gold, GoldDark)))
        TileStyle.PENDING -> Modifier.drawBehind {
            val dash = size.toPx() * 0.11f
            drawRoundRect(
                color = pendingBorder,
                cornerRadius = CornerRadius(size.toPx() * 0.18f),
                style = Stroke(
                    width = max(1.dp, size / 22).toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                ),
            )
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size).then(look)) {
        Text(
            text = letter.toString(),
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            // The theme's line height is taller than a small tile, which would cut the letter off.
            style = LocalTextStyle.current.merge(
                lineHeight = fontSize,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            color = when (style) {
                TileStyle.RAISED, TileStyle.ON_CARD -> Ink
                TileStyle.LIT -> Color.White
                TileStyle.GOLD -> GoldInk
                TileStyle.PENDING -> InkSoft
            },
        )
    }
}

/** Lines of tiles, as in the app's title: the [lit] letter stands out and the last one is still to be placed. */
@Composable
private fun TileLines(
    lines: List<String>,
    lit: Int,
    size: Dp,
    gap: Dp,
    plain: TileStyle,
    letterRatio: Float,
    last: TileStyle = TileStyle.PENDING,
    lastModifier: Modifier = Modifier,
) {
    val lastLetter = lines.sumOf { line -> line.count { it != ' ' } } - 1
    var index = 0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (line in lines) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (character in line.uppercase()) {
                    if (character == ' ') {
                        Spacer(modifier = Modifier.width(size * WORD_GAP))
                    } else {
                        val style = when (index) {
                            lit -> TileStyle.LIT
                            lastLetter -> last
                            else -> plain
                        }
                        val tile = if (index == lastLetter) lastModifier else Modifier
                        LetterTile(character, size, style, letterRatio, tile)
                        index++
                    }
                }
            }
        }
    }
}

/**
 * The app's name as a row of letter tiles. Its last tile is the piece still to be placed: tapping it
 * leads to the secret message, with no visible hint that it can be tapped, and [solved] fills it in.
 * Filled in, the tile opens the prize the message hides, [onSolvedTile].
 */
@Composable
internal fun AppTitle(solved: Boolean, onLastTile: () -> Unit, modifier: Modifier = Modifier, onSolvedTile: (() -> Unit)? = null) {
    val name = stringResource(R.string.app_name)
    val solveLabel = stringResource(R.string.secret_title)
    val prizeLabel = stringResource(R.string.starfall_open)

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = name
                heading()
                if (!solved) {
                    onClick(label = solveLabel) {
                        onLastTile()
                        true
                    }
                } else if (onSolvedTile != null) {
                    onClick(label = prizeLabel) {
                        onSolvedTile()
                        true
                    }
                }
            },
    ) {
        val gaps = AppTitleTileGap * (name.length - 1)
        val tile = min(AppTitleTileSize, (maxWidth - gaps) / name.length)

        TileLines(
            lines = listOf(name),
            lit = APP_TITLE_LIT_LETTER,
            size = tile,
            gap = AppTitleTileGap,
            plain = TileStyle.RAISED,
            letterRatio = APP_TITLE_LETTER_RATIO,
            last = if (solved) TileStyle.GOLD else TileStyle.PENDING,
            lastModifier = if (solved) {
                if (onSolvedTile == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSolvedTile,
                    )
                }
            } else {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLastTile,
                )
            },
        )
    }
}

/**
 * A game's title on its card. Every card keeps room for two lines and centres what it has, so that
 * cards in a row stay the same height. [widest] comes from [widestTitle]. The card itself tells
 * screen readers its title, so the tiles say nothing.
 */
@Composable
internal fun CardTitle(title: String, widest: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
    ) {
        val tile = min(CardTitleMaxTile, maxWidth / widest)
        val gap = tile * TILE_GAP

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.height(tile * CARD_TITLE_MAX_LINES + gap),
        ) {
            TileLines(titleLines(title), SecretMessage.letterIndexOf(title), tile, gap, TileStyle.ON_CARD, SMALL_TILE_LETTER_RATIO)
        }
    }
}

/**
 * A title in the top bar. Only one is on screen at a time, so each takes the largest tiles that it
 * has room for, instead of sharing a size like the cards do.
 */
@Composable
internal fun BarTitle(title: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = title
            heading()
        },
    ) {
        val lines = titleLines(title)
        val largest = if (lines.size > 1) BarTitleMaxTileOnTwoLines else BarTitleMaxTile
        val tile = min(largest, maxWidth / lines.maxOf(::lineWidth))

        TileLines(lines, SecretMessage.letterIndexOf(title), tile, tile * TILE_GAP, TileStyle.RAISED, SMALL_TILE_LETTER_RATIO)
    }
}

/** The message as far as it is known: each clue found in its place, and a tile to place for the rest. */
@Composable
fun SecretBoard(found: List<SecretClue>) {
    val cells = SecretMessage.clues.size

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val tile = min(CardTitleMaxTile, maxWidth / (cells + (cells - 1) * TILE_GAP))

        Row(horizontalArrangement = Arrangement.spacedBy(tile * TILE_GAP)) {
            for (clue in SecretMessage.clues) {
                val known = clue in found
                LetterTile(
                    letter = if (known) clue.letter else ' ',
                    size = tile,
                    style = if (known) TileStyle.LIT else TileStyle.PENDING,
                    letterRatio = SMALL_TILE_LETTER_RATIO,
                )
            }
        }
    }
}

/** What a first win reveals, under the game that was just won. */
@Composable
fun SecretClueNote(clue: SecretClue, completes: Boolean = false) {
    val shape = RoundedCornerShape(12.dp)

    Text(
        text = if (completes) {
            stringResource(R.string.secret_clue_last, clue.letter.toString(), clue.title)
        } else {
            stringResource(R.string.secret_clue, clue.letter.toString(), clue.title, clue.position, SecretMessage.clues.size)
        },
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(Card, shape)
            .border(1.dp, Accent, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
