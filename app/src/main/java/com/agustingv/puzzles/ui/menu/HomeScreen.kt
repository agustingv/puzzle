package com.agustingv.puzzles.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.data.SavedGame
import com.agustingv.puzzles.ui.common.AvatarButton
import com.agustingv.puzzles.ui.common.AppTitle
import com.agustingv.puzzles.ui.common.CardTitle
import com.agustingv.puzzles.ui.common.SettingsButton
import com.agustingv.puzzles.ui.common.widestTitle
import com.agustingv.puzzles.ui.theme.Card as CardColor
import com.agustingv.puzzles.ui.theme.PuzzlesTheme

private val MinCardWidth = 150.dp

/** The app's first screen: every game as a card. A card opens that game's page. */
@Composable
fun HomeScreen(
    saved: (GameInfo) -> SavedGame?,
    onOpen: (GameInfo) -> Unit,
    secretSolved: Boolean,
    onSecret: () -> Unit,
    shop: GameShop = GameShop.Free,
) {
    val widest = widestTitle(GameInfo.entries.map { stringResource(it.title) })

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
                AppTitle(secretSolved, onSecret, Modifier.weight(1f))
                AvatarButton(modifier = Modifier.padding(start = 4.dp))
                SettingsButton(modifier = Modifier.padding(start = 4.dp))
            }
        }

        items(GameInfo.entries, key = { it.id }) { info ->
            val locked = !shop.unlocked(info)
            GameCard(
                info = info,
                widestTitle = widest,
                inProgress = saved(info) != null && !locked,
                lock = if (locked) LockBadge(info.productId?.let(shop.price)) else null,
                onClick = { onOpen(info) },
            )
        }
    }
}

/** A paid game not bought yet, with its price when Google Play has said it. */
private class LockBadge(val price: String?)

@Composable
private fun GameCard(info: GameInfo, widestTitle: Float, inProgress: Boolean, lock: LockBadge?, onClick: () -> Unit) {
    val title = stringResource(info.title)
    val status = stringResource(R.string.game_in_progress)
    val lockLabel = lock?.price?.let { stringResource(R.string.shop_locked_price, it) } ?: stringResource(R.string.shop_locked)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = when {
                    lock != null -> "$title, $lockLabel"
                    inProgress -> "$title, $status"
                    else -> title
                }
            },
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
            if (lock != null) {
                Text(
                    text = listOfNotNull("🔒", lock.price).joinToString(" "),
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
        CardTitle(title, widestTitle, Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HomePreview() {
    PuzzlesTheme {
        HomeScreen(
            saved = { if (it == GameInfo.HANGMAN) SavedGame("5", 83) else null },
            onOpen = {},
            secretSolved = false,
            onSecret = {},
        )
    }
}
