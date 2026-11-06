package com.butaca13.puzzles.ui.menu

import androidx.activity.compose.BackHandler
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
        }
    }
}
