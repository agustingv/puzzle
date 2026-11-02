package com.agustingv.puzzles.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import com.agustingv.puzzles.R
import com.agustingv.puzzles.ui.theme.Accent
import com.agustingv.puzzles.ui.theme.AccentDark
import com.agustingv.puzzles.ui.theme.Card
import com.agustingv.puzzles.ui.theme.Ink
import com.agustingv.puzzles.ui.theme.InkSoft
import com.agustingv.puzzles.ui.theme.Paper

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

/**
 * Which letter of a title is lit up, counting only letters. It looks picked at random, but comes from
 * the title itself, so a game keeps its letter on every screen and every launch. Never the last
 * letter, which has its own style.
 */
internal fun litLetter(title: String): Int {
    val choices = title.count { it != ' ' } - 1
    return if (choices < 1) 0 else title.hashCode().mod(choices)
}

private enum class TileStyle { RAISED, ON_CARD, LIT, PENDING }

@Composable
private fun LetterTile(letter: Char, size: Dp, style: TileStyle, letterRatio: Float) {
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

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size).then(look)) {
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
                TileStyle.PENDING -> InkSoft
            },
        )
    }
}

/** Lines of tiles, as in the app's title: the [lit] letter stands out and the last one is still to be placed. */
@Composable
private fun TileLines(lines: List<String>, lit: Int, size: Dp, gap: Dp, plain: TileStyle, letterRatio: Float) {
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
                            lastLetter -> TileStyle.PENDING
                            else -> plain
                        }
                        LetterTile(character, size, style, letterRatio)
                        index++
                    }
                }
            }
        }
    }
}

/** The app's name as a row of letter tiles. */
@Composable
internal fun AppTitle(modifier: Modifier = Modifier) {
    val name = stringResource(R.string.app_name)

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = name
                heading()
            },
    ) {
        val gaps = AppTitleTileGap * (name.length - 1)
        val tile = min(AppTitleTileSize, (maxWidth - gaps) / name.length)

        TileLines(listOf(name), APP_TITLE_LIT_LETTER, tile, AppTitleTileGap, TileStyle.RAISED, APP_TITLE_LETTER_RATIO)
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
            TileLines(titleLines(title), litLetter(title), tile, gap, TileStyle.ON_CARD, SMALL_TILE_LETTER_RATIO)
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

        TileLines(lines, litLetter(title), tile, tile * TILE_GAP, TileStyle.RAISED, SMALL_TILE_LETTER_RATIO)
    }
}
