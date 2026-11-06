package com.butaca13.puzzles.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.SavedGame
import com.butaca13.puzzles.ui.common.AppTitle
import com.butaca13.puzzles.ui.common.AvatarButton
import com.butaca13.puzzles.ui.common.CardTitle
import com.butaca13.puzzles.ui.common.SettingsButton
import com.butaca13.puzzles.ui.common.widestTitle
import com.butaca13.puzzles.ui.theme.PuzzlesTheme
import com.butaca13.puzzles.ui.theme.Card as CardColor

private val FavoriteCardWidth = 104.dp
private val FavoritesHeight = 132.dp

/**
 * The app's first screen, all of it in sight with no scrolling, however many games there are: the four
 * groups of games, each opening its own screen, and under them the player's favourites, which scroll
 * sideways.
 */
@Composable
fun HomeScreen(
    saved: (GameInfo) -> SavedGame?,
    onOpenGroup: (GameGroup) -> Unit,
    onOpen: (GameInfo) -> Unit,
    favorites: List<GameInfo>,
    secretSolved: Boolean,
    onSecret: () -> Unit,
    shop: GameShop = GameShop.Free,
) {
    val widest = widestTitle(GameGroup.entries.map { stringResource(it.title) })

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
        ) {
            AppTitle(secretSolved, onSecret, Modifier.weight(1f))
            AvatarButton(modifier = Modifier.padding(start = 4.dp))
            SettingsButton(modifier = Modifier.padding(start = 4.dp))
        }

        // The groups share whatever height the title and the favourites leave.
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GameGroup.entries.chunked(2).forEach { row ->
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { group ->
                        GroupCard(
                            group = group,
                            widestTitle = widest,
                            inProgress = group.games.count { saved(it) != null && shop.unlocked(it) },
                            onClick = { onOpenGroup(group) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }

        // Not inside the side margins: its row scrolls from one edge of the screen to the other.
        FavoritesRow(favorites, saved, onOpen, shop)
    }
}

/**
 * A group as a card that fills its share of the screen: its picture, its name in tiles, and how many
 * games it has, or that they are still to come.
 */
@Composable
private fun GroupCard(group: GameGroup, widestTitle: Float, inProgress: Int, onClick: () -> Unit, modifier: Modifier) {
    val title = stringResource(group.title)
    val count = group.games.size
    val detail = if (count == 0) {
        stringResource(R.string.group_soon)
    } else {
        pluralStringResource(R.plurals.group_games, count, count)
    }
    val progress = if (inProgress > 0) pluralStringResource(R.plurals.group_in_progress, inProgress, inProgress) else null

    Card(
        onClick = onClick,
        enabled = count > 0,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor, disabledContainerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, disabledElevation = 0.dp),
        modifier = modifier
            .alpha(if (count > 0) 1f else 0.55f)
            .semantics { contentDescription = listOfNotNull(title, detail, progress).joinToString(", ") },
    ) {
        Column(modifier = Modifier.fillMaxSize().clearAndSetSemantics {}) {
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().weight(1f).background(Brush.linearGradient(group.gradient)),
            ) {
                // As large as the picture's share of the card allows, which depends on the phone.
                val iconSize = with(LocalDensity.current) { (min(maxHeight, maxWidth) * 0.5f).toSp() }
                Text(text = group.icon, fontSize = iconSize)
                if (progress != null) Badge(progress, Modifier.align(Alignment.TopEnd))
            }
            CardTitle(title, widestTitle, Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp, top = 2.dp),
            )
        }
    }
}

/** A white label on a dark pill, over a card's picture: a game in progress, or locked. */
@Composable
internal fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * The favourite games, in a row that scrolls sideways. Always the same height, so that the groups do
 * not jump when the first one is added; with none, it says how to add them.
 */
@Composable
private fun FavoritesRow(
    favorites: List<GameInfo>,
    saved: (GameInfo) -> SavedGame?,
    onOpen: (GameInfo) -> Unit,
    shop: GameShop,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = stringResource(R.string.favorites_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
        )
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(FavoritesHeight)) {
            if (favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.favorites_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    // Edge to edge, so that a card cut by the side of the screen says there are more.
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(favorites, key = { it.id }) { info ->
                        val locked = !shop.unlocked(info)
                        FavoriteCard(info, inProgress = saved(info) != null && !locked, locked = locked, onClick = { onOpen(info) })
                    }
                }
            }
        }
    }
}

/** A favourite game, smaller than its card in its group: its picture and its name. */
@Composable
private fun FavoriteCard(info: GameInfo, inProgress: Boolean, locked: Boolean, onClick: () -> Unit) {
    val title = stringResource(info.title)
    val status = when {
        locked -> stringResource(R.string.shop_locked)
        inProgress -> stringResource(R.string.game_in_progress)
        else -> null
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .width(FavoriteCardWidth)
            .fillMaxHeight()
            .semantics { contentDescription = listOfNotNull(title, status).joinToString(", ") },
    ) {
        Column(modifier = Modifier.fillMaxSize().clearAndSetSemantics {}) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().weight(1f).background(Brush.linearGradient(info.gradient)),
            ) {
                Text(text = info.icon, fontSize = 34.sp)
                // Small as the card is, "in progress" fits; a padlock says locked.
                if (locked) Badge("🔒", Modifier.align(Alignment.TopEnd)) else if (inProgress) Badge(status!!, Modifier.align(Alignment.TopEnd))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HomePreview() {
    PuzzlesTheme {
        HomeScreen(
            saved = { if (it == GameInfo.HANGMAN) SavedGame("5", 83) else null },
            onOpenGroup = {},
            onOpen = {},
            favorites = listOf(GameInfo.HANGMAN, GameInfo.SUDOKU, GameInfo.GARDEN),
            secretSolved = false,
            onSecret = {},
        )
    }
}
