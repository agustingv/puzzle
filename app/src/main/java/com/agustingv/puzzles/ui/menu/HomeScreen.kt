package com.agustingv.puzzles.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.data.SavedGame
import com.agustingv.puzzles.ui.common.SettingsButton
import com.agustingv.puzzles.ui.theme.Accent
import com.agustingv.puzzles.ui.theme.AccentDark
import com.agustingv.puzzles.ui.theme.Card as CardColor
import com.agustingv.puzzles.ui.theme.Ink
import com.agustingv.puzzles.ui.theme.InkSoft
import com.agustingv.puzzles.ui.theme.PuzzlesTheme

private val MinCardWidth = 150.dp
private val TitleTileSize = 44.dp
private val TitleTileGap = 8.dp

/** The app's first screen: every game as a card. A card opens that game's page. */
@Composable
fun HomeScreen(
    saved: (GameInfo) -> SavedGame?,
    onOpen: (GameInfo) -> Unit,
    onPrivacy: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MinCardWidth),
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            ) {
                AppTitle(modifier = Modifier.weight(1f))
                SettingsButton(modifier = Modifier.padding(start = 4.dp))
            }
        }

        items(GameInfo.entries, key = { it.id }) { info ->
            GameCard(info, inProgress = saved(info) != null, onClick = { onOpen(info) })
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onPrivacy) { Text(stringResource(R.string.privacy_title)) }
            }
        }
    }
}

/** The app's name as a row of letter tiles: one lit up and the last still to be placed. */
@Composable
private fun AppTitle(modifier: Modifier = Modifier) {
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
        val gaps = TitleTileGap * (name.length - 1)
        val tile = min(TitleTileSize, (maxWidth - gaps) / name.length)

        Row(horizontalArrangement = Arrangement.spacedBy(TitleTileGap)) {
            name.uppercase().forEachIndexed { index, letter ->
                TitleTile(
                    letter = letter,
                    size = tile,
                    style = when (index) {
                        1 -> TitleTileStyle.LIT
                        name.lastIndex -> TitleTileStyle.PENDING
                        else -> TitleTileStyle.PLAIN
                    },
                )
            }
        }
    }
}

private enum class TitleTileStyle { PLAIN, LIT, PENDING }

@Composable
private fun TitleTile(letter: Char, size: Dp, style: TitleTileStyle) {
    val shape = RoundedCornerShape(8.dp)
    val pendingBorder = InkSoft.copy(alpha = 0.35f)
    val fontSize = with(LocalDensity.current) { (size * 0.44f).toSp() }

    val look = when (style) {
        TitleTileStyle.PLAIN -> Modifier
            .shadow(2.dp, shape)
            .background(CardColor)
        TitleTileStyle.LIT -> Modifier
            .shadow(6.dp, shape, ambientColor = Accent, spotColor = Accent)
            .background(Brush.linearGradient(listOf(Accent, AccentDark)))
        TitleTileStyle.PENDING -> Modifier.drawBehind {
            val dash = 5.dp.toPx()
            val corner = 8.dp.toPx()
            drawRoundRect(
                color = pendingBorder,
                cornerRadius = CornerRadius(corner),
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
            )
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size).then(look)) {
        Text(
            text = letter.toString(),
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            color = when (style) {
                TitleTileStyle.PLAIN -> Ink
                TitleTileStyle.LIT -> Color.White
                TitleTileStyle.PENDING -> InkSoft
            },
        )
    }
}

@Composable
private fun GameCard(info: GameInfo, inProgress: Boolean, onClick: () -> Unit) {
    val title = stringResource(info.title)
    val status = stringResource(R.string.game_in_progress)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = if (inProgress) "$title, $status" else title },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(Brush.linearGradient(info.gradient)),
        ) {
            Text(
                text = info.icon,
                fontSize = 42.sp,
                modifier = Modifier.clearAndSetSemantics {},
            )
            if (inProgress) {
                Text(
                    text = status,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HomePreview() {
    PuzzlesTheme {
        HomeScreen(
            saved = { if (it == GameInfo.HANGMAN) SavedGame("5", 83) else null },
            onOpen = {},
            onPrivacy = {},
        )
    }
}
