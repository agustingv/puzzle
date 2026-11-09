package com.butaca13.puzzles.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.SavedGame
import com.butaca13.puzzles.ui.common.AppTitle
import com.butaca13.puzzles.ui.common.AvatarButton
import com.butaca13.puzzles.ui.common.formatTime
import com.butaca13.puzzles.ui.theme.Card
import com.butaca13.puzzles.ui.theme.Hero
import com.butaca13.puzzles.ui.theme.OnHero
import com.butaca13.puzzles.ui.theme.OnHeroSoft
import com.butaca13.puzzles.ui.theme.PuzzlesTheme

private val TileSize = 76.dp
private val TileWidth = 84.dp
private val SidePadding = 20.dp

/**
 * The app's first screen, which scrolls: a greeting, the games left halfway to pick up again, the
 * favourites, and every group's games in a row that scrolls sideways.
 */
@Composable
fun HomeScreen(
    playerName: String,
    saved: (GameInfo) -> SavedGame?,
    onOpen: (GameInfo) -> Unit,
    favorites: List<GameInfo>,
    secretSolved: Boolean,
    onSecret: () -> Unit,
    shop: GameShop = GameShop.Free,
    onStarfall: () -> Unit = {},
) {
    // The games waiting for the player, as GameInfo orders them. One no longer owned is not offered.
    val inProgress = GameInfo.entries.mapNotNull { info -> saved(info)?.takeIf { shop.unlocked(info) }?.let { info to it } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
                ) {
                    AppTitle(secretSolved, onSecret, Modifier.weight(1f), onSolvedTile = onStarfall)
                    AvatarButton(modifier = Modifier.padding(start = 8.dp))
                }
                Column(modifier = Modifier.padding(horizontal = SidePadding), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.home_greeting, playerName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.home_question),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
        }

        if (inProgress.isNotEmpty()) {
            item(key = "continue") {
                Column(modifier = Modifier.padding(horizontal = SidePadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.home_continue_title))
                    val (first, firstSaved) = inProgress.first()
                    ContinueHero(first, firstSaved, onClick = { onOpen(first) })
                    inProgress.drop(1).forEach { (info, game) -> ContinueRow(info, game, onClick = { onOpen(info) }) }
                }
            }
        }

        if (favorites.isNotEmpty()) {
            item(key = "favorites") {
                GameRow(
                    title = stringResource(R.string.favorites_title),
                    games = favorites,
                    saved = saved,
                    shop = shop,
                    onOpen = onOpen,
                )
            }
        }

        GameGroup.entries.filter { it.games.isNotEmpty() }.forEach { group ->
            item(key = "group-${group.name}") {
                GameRow(
                    title = stringResource(group.title),
                    games = group.games,
                    saved = saved,
                    shop = shop,
                    onOpen = onOpen,
                    // The secret's prize closes the group it lives in.
                    onStarfall = onStarfall.takeIf { secretSolved && group == GameGroup.VARIOUS },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() },
    )
}

/** Where a saved game stands, in a few words: its level and the time it has taken so far. */
@Composable
private fun savedSummary(info: GameInfo, game: SavedGame): String {
    val level = if (info.usesWordLengths) {
        val letters = game.variant.toIntOrNull() ?: 0
        pluralStringResource(R.plurals.home_letters, letters, letters)
    } else {
        variantShortLabel(game.variant)
    }
    return "$level · ${formatTime(game.elapsedSeconds)}"
}

/** The game to pick up first, large and deep in colour: what the screen is mostly for. */
@Composable
private fun ContinueHero(info: GameInfo, game: SavedGame, onClick: () -> Unit) {
    val title = stringResource(info.title)
    val summary = savedSummary(info, game)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Hero)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GameIcon(info, size = 56.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = OnHero)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = OnHeroSoft)
            }
        }
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = OnHero, contentColor = Hero),
            modifier = Modifier.height(48.dp),
        ) {
            Text(stringResource(R.string.home_continue), fontWeight = FontWeight.Bold)
        }
    }
}

/** Any other game left halfway, smaller: its icon, name and where it stands. */
@Composable
private fun ContinueRow(info: GameInfo, game: SavedGame, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        GameIcon(info, size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(info.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(savedSummary(info, game), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = stringResource(R.string.home_continue),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * A titled row of games that scrolls sideways, from one edge of the screen to the other, so that a game
 * cut by the edge says there are more.
 */
@Composable
private fun GameRow(
    title: String,
    games: List<GameInfo>,
    saved: (GameInfo) -> SavedGame?,
    shop: GameShop,
    onOpen: (GameInfo) -> Unit,
    onStarfall: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = SidePadding).height(40.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = SidePadding),
        ) {
            items(games, key = { it.id }) { info ->
                val locked = !shop.unlocked(info)
                GameTile(info, inProgress = saved(info) != null && !locked, locked = locked, onClick = { onOpen(info) })
            }
            if (onStarfall != null) {
                item(key = "starfall") { StarfallTile(onStarfall) }
            }
        }
    }
}

/** A game in a row: its picture, a mark when it is halfway or locked, and its name in full. */
@Composable
private fun GameTile(info: GameInfo, inProgress: Boolean, locked: Boolean, onClick: () -> Unit) {
    val title = stringResource(info.title)
    val status = when {
        locked -> stringResource(R.string.shop_locked)
        inProgress -> stringResource(R.string.game_in_progress)
        else -> null
    }

    Tile(
        label = title,
        description = listOfNotNull(title, status).joinToString(", "),
        onClick = onClick,
    ) {
        GameIcon(info, TileSize)
        when {
            locked -> Text("🔒", fontSize = 14.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            inProgress -> Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Hero)
                    .border(2.dp, Color.White, CircleShape),
            )
        }
    }
}

/** Starfall in the Various row: like a game, on the night sky it is played in. */
@Composable
private fun StarfallTile(onClick: () -> Unit) {
    val title = stringResource(R.string.starfall_card_title)
    Tile(
        label = title,
        description = title,
        onClick = onClick,
    ) {
        StarfallIcon(TileSize)
        Text("👑", fontSize = 13.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}

@Composable
private fun Tile(
    label: String,
    description: String,
    onClick: () -> Unit,
    picture: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .width(TileWidth)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(TileSize),
            content = picture,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HomePreview() {
    PuzzlesTheme {
        HomeScreen(
            playerName = "Liebre Atenta 1",
            saved = { if (it == GameInfo.HANGMAN || it == GameInfo.SUDOKU) SavedGame(if (it == GameInfo.HANGMAN) "5" else "MEDIUM", 83) else null },
            onOpen = {},
            favorites = listOf(GameInfo.HANGMAN, GameInfo.SUDOKU, GameInfo.GARDEN),
            secretSolved = false,
            onSecret = {},
        )
    }
}
