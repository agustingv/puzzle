package com.butaca13.puzzles.ui.menu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.ui.common.CardTitle
import com.butaca13.puzzles.ui.theme.Card as CardColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.butaca13.puzzles.data.SavedGame
import com.butaca13.puzzles.ui.common.ScreenTopBar
import com.butaca13.puzzles.ui.common.widestTitle

private val MinCardWidth = 150.dp

/** The games of one group, as cards. A card opens that game's page. */
@Composable
fun GroupScreen(
    group: GameGroup,
    saved: (GameInfo) -> SavedGame?,
    onOpen: (GameInfo) -> Unit,
    onBack: () -> Unit,
    shop: GameShop = GameShop.Free,
    /** The secret message's prize, listed here once it is solved; null until then. */
    onStarfall: (() -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    // Measured over every game, not just this group's: a game's title tiles are the same size in all.
    val widest = widestTitle(GameInfo.entries.map { stringResource(it.title) })

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenTopBar(title = stringResource(group.title), onBack = onBack, titleInTiles = true)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(MinCardWidth),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(group.games, key = { it.id }) { info ->
                val locked = !shop.unlocked(info)
                GameCard(
                    info = info,
                    widestTitle = widest,
                    inProgress = saved(info) != null && !locked,
                    lock = if (locked) LockBadge(info.productId?.let(shop.price)) else null,
                    onClick = { onOpen(info) },
                )
            }
            if (onStarfall != null) {
                item(key = "starfall") { StarfallCard(widest, onStarfall) }
            }
        }
    }
}

/** Starfall's card: like a game's, on the night sky it is played in. */
@Composable
private fun StarfallCard(widestTitle: Float, onClick: () -> Unit) {
    val title = stringResource(R.string.starfall_card_title)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = title },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF080A18), Color(0xFF26214A)))),
        ) {
            Text(text = "🚀", fontSize = 42.sp, modifier = Modifier.clearAndSetSemantics {})
            Text(
                text = "👑",
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clearAndSetSemantics {},
            )
        }
        CardTitle(title, widestTitle, Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}
