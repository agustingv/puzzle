package com.butaca13.puzzles.ui.menu

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.Avatar
import com.butaca13.puzzles.data.GlobalRankings
import com.butaca13.puzzles.data.KeyValueStore
import com.butaca13.puzzles.data.PlayerAccount
import com.butaca13.puzzles.data.Products
import com.butaca13.puzzles.data.Ranking
import com.butaca13.puzzles.data.RecordEntry
import com.butaca13.puzzles.data.Records
import com.butaca13.puzzles.data.ScoreUploads
import com.butaca13.puzzles.ui.common.AvatarTile
import com.butaca13.puzzles.ui.common.formatTime
import com.butaca13.puzzles.ui.theme.Accent
import com.butaca13.puzzles.ui.theme.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val LAST_VARIANT_KEY = "last-variant"

/**
 * A game's page, in a sheet that rises over the screen it was opened from: what the game is, which level
 * to play, the player's best times on that level and the best of everyone, and how to start. Swiping it
 * down, or back, closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailSheet(
    info: GameInfo,
    records: Records,
    rankings: GlobalRankings,
    account: PlayerAccount,
    storage: KeyValueStore,
    onPlay: (variant: String) -> Unit,
    onDismiss: () -> Unit,
    shop: GameShop = GameShop.Free,
    favorite: Boolean = false,
    /** Adds the game to the favourites or takes it away; null where there are none, as in previews. */
    onFavorite: (() -> Unit)? = null,
    /**
     * True once the game has started: the sheet slides away, uncovering it, and then [onLeft] is
     * called. Being its own window, the sheet would otherwise vanish at once, before the game is drawn,
     * and the home screen would flash in between.
     */
    leaving: Boolean = false,
    onLeft: () -> Unit = {},
) {
    // Opens on the last level played, so a repeat game takes one tap.
    val lastKey = "$LAST_VARIANT_KEY/${info.id}"
    var selected by rememberSaveable(info) {
        mutableStateOf(storage.get(lastKey)?.takeIf { it in info.variants } ?: info.variants.first())
    }

    // The global ranking shows as last downloaded, at once, and is brought up to date behind it, and
    // again whenever a time sent in the background reaches the server: a win just before opening this
    // page is usually still on its way. While it is on its way, and if it never comes, the card says so.
    var global by remember(info, selected) { mutableStateOf(rankings.cached(info.id, selected)) }
    val online = rankings.canRefresh(account)
    var loading by remember(info, selected) { mutableStateOf(online) }
    var failed by remember(info, selected) { mutableStateOf(false) }
    var attempt by remember(info, selected) { mutableIntStateOf(0) }
    LaunchedEffect(info, selected, account.registered, account.offline, ScoreUploads.filed, attempt) {
        if (!rankings.canRefresh(account)) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val fresh = withContext(Dispatchers.IO) { rankings.refresh(account, info.id, selected) }
        if (fresh != null) global = fresh
        failed = fresh == null
        loading = false
    }

    fun play() {
        storage.put(lastKey, selected)
        onPlay(selected)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(leaving) {
        if (leaving) {
            sheetState.hide()
            onLeft()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Card,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    GameIcon(info, 72.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(info.group.title).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(info.title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    if (onFavorite != null) FavoriteButton(favorite, onFavorite)
                }

                Text(text = stringResource(info.description), color = MaterialTheme.colorScheme.onSurfaceVariant)

                VariantSelector(info, selected, onSelect = { selected = it })
                // Both lists open folded on each game and level: five rows are enough to see where one stands.
                var allMine by rememberSaveable(info, selected) { mutableStateOf(false) }
                var allGlobal by rememberSaveable(info, selected) { mutableStateOf(false) }
                PersonalRecords(records.entries(info.id, selected), allMine, onToggle = { allMine = !allMine })
                // Not once the player is off the server, as after deleting it: the copy kept is the old one.
                if (account.registered) {
                    GlobalRanking(
                        ranking = global,
                        ownPlayer = account.id,
                        expanded = allGlobal,
                        onToggle = { allGlobal = !allGlobal },
                        // Without a connection by choice there is nothing to wait for, nor to fail.
                        loading = online && loading,
                        failed = online && failed,
                        onRetry = { attempt++ },
                    )
                }
            }

            val productId = info.productId
            if (productId != null && !shop.unlocked(info)) {
                UnlockButtons(productId, shop)
            } else {
                Button(
                    onClick = ::play,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(56.dp),
                ) {
                    Text(stringResource(R.string.play), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/**
 * In place of the play button for a paid game not bought yet: this game alone, or all the paid ones.
 * The page itself stays open to see, with its levels and rankings.
 */
@Composable
private fun UnlockButtons(productId: String, shop: GameShop) {
    val price = shop.price(productId)
    val allPrice = shop.allPrice

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            shop.pending(productId) || shop.pending(Products.ALL_GAMES) -> Text(
                text = stringResource(R.string.shop_pending),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            price == null && allPrice == null -> Text(
                text = stringResource(R.string.shop_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Button(
            onClick = { shop.buy(productId) },
            enabled = price != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                text = price?.let { stringResource(R.string.shop_unlock, it) } ?: stringResource(R.string.shop_locked),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (allPrice != null) {
            TextButton(onClick = { shop.buy(Products.ALL_GAMES) }) {
                Text(stringResource(R.string.shop_unlock_all, allPrice), textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariantSelector(info: GameInfo, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(info.variantsTitle),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            info.variants.forEachIndexed { index, variant ->
                SegmentedButton(
                    selected = variant == selected,
                    onClick = { onSelect(variant) },
                    shape = SegmentedButtonDefaults.itemShape(index, info.variants.size),
                    colors = SegmentedButtonDefaults.colors(inactiveContainerColor = Card),
                    icon = {},
                    label = {
                        Text(
                            text = variantShortLabel(variant),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun RecordsCard(title: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .animateContentSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        content()
    }
}

/** How many rows a list shows folded. */
private const val FOLDED_ROWS = 5

/**
 * Unfolds a list beyond its first rows, and folds it back: an arrow that points down, and turns up
 * once unfolded. Screen readers hear what it does. [hint], if any, is said beside it, such as where the
 * player stands among the rows folded away. Nothing when there is nothing more to show.
 */
@Composable
private fun ShowMoreButton(total: Int, expanded: Boolean, onToggle: () -> Unit, hint: String? = null) {
    if (total <= FOLDED_ROWS) return

    val hidden = total - FOLDED_ROWS
    val action = if (expanded) {
        stringResource(R.string.records_show_less)
    } else {
        pluralStringResource(R.plurals.records_show_more, hidden, hidden)
    }
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val color = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = action, role = Role.Button, onClick = onToggle)
            .padding(vertical = 4.dp)
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(action, hint.takeIf { !expanded }).joinToString(", ")
                role = Role.Button
            },
    ) {
        if (hint != null && !expanded) {
            Text(text = hint, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(end = 4.dp))
        }
        Canvas(modifier = Modifier.size(28.dp).rotate(turn)) {
            val stroke = 2.5.dp.toPx()
            val path = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.4f)
                lineTo(size.width * 0.5f, size.height * 0.62f)
                lineTo(size.width * 0.72f, size.height * 0.4f)
            }
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Gold, silver and bronze for the first three places. */
private val Medals = listOf(Color(0xFFF2C230), Color(0xFFC3CAD3), Color(0xFFD38B4F))

/** A place in a ranking: a medal for the first three, a plain number for the rest. */
@Composable
private fun RankLabel(rank: Int) {
    val medal = Medals.getOrNull(rank - 1)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.width(28.dp)) {
        if (medal == null) {
            Text("$rank.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp).clip(CircleShape).background(medal),
            ) {
                // Dark on every medal, in either theme: the medals keep their colours.
                Text("$rank", color = Color(0xFF2B2B2B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

/** This phone's ten best times, with the day each was won: there with or without a connection. */
@Composable
private fun PersonalRecords(entries: List<RecordEntry>, expanded: Boolean, onToggle: () -> Unit) {
    RecordsCard(stringResource(R.string.records_mine)) {
        if (entries.isEmpty()) {
            Text(text = stringResource(R.string.records_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Day first, as in the app's language, whatever the phone's.
        val dates = remember { DateFormat.getDateInstance(DateFormat.SHORT, Locale.forLanguageTag("es-ES")) }
        entries.take(if (expanded) entries.size else FOLDED_ROWS).forEachIndexed { index, entry ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
                Text(
                    text = formatTime(entry.seconds),
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                entry.playedAt?.let {
                    Text(
                        text = dates.format(Date(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ShowMoreButton(entries.size, expanded, onToggle)
    }
}

/**
 * The ten best times of everyone, as last downloaded, with who won each. The player's own places
 * stand out. Without a connection it is the copy kept, and says how old it is. Never downloaded, it
 * keeps the room of its rows with a spinner while it [loading], or says it [failed], with a way to try
 * again; nothing at all when neither, as without a connection by choice.
 */
@Composable
private fun GlobalRanking(
    ranking: Ranking?,
    ownPlayer: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
) {
    if (ranking == null && !loading && !failed) return

    RecordsCard(stringResource(R.string.ranking_global)) {
        if (ranking == null) {
            if (loading) RankingLoading() else RankingFailed(onRetry)
            return@RecordsCard
        }

        if (ranking.entries.isEmpty()) {
            Text(text = stringResource(R.string.ranking_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val you = stringResource(R.string.ranking_you)
        ranking.entries.take(if (expanded) ranking.entries.size else FOLDED_ROWS).forEach { entry ->
            val own = entry.playerId == ownPlayer
            val avatar = entry.playerAvatar?.let(Avatar::decode) ?: Avatar.initial(entry.playerName, entry.playerId)
            val name = if (own) "${entry.playerName} ($you)" else entry.playerName
            val description = "${entry.rank}. $name, ${formatTime(entry.seconds)}"

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (own) Accent.copy(alpha = 0.16f) else Color.Transparent)
                    .padding(horizontal = 4.dp, vertical = 3.dp)
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                RankLabel(entry.rank)
                AvatarTile(avatar, 26.dp)
                Text(
                    text = name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (own) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                Text(formatTime(entry.seconds), fontWeight = if (entry.rank == 1) FontWeight.Bold else FontWeight.Normal)
            }
        }

        // Folded, the player would not see a place of theirs further down: the button says where it is.
        val ownHidden = ranking.entries.drop(FOLDED_ROWS).firstOrNull { it.playerId == ownPlayer }
        ShowMoreButton(
            total = ranking.entries.size,
            expanded = expanded,
            onToggle = onToggle,
            hint = ownHidden?.let { stringResource(R.string.ranking_you_at, it.rank) },
        )

        // How old the copy shown is, and whether a newer one is coming or could not be had.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp)) {
            Text(
                text = stringResource(R.string.ranking_updated, updatedAgo(ranking.fetchedAt)) + when {
                    loading -> " · " + stringResource(R.string.ranking_updating)
                    failed -> " · " + stringResource(R.string.ranking_update_failed)
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (failed && !loading) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else if (failed) {
                TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.ranking_retry), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** The room of a folded ranking, with a spinner in it, so that nothing jumps once it arrives. */
@Composable
private fun RankingLoading() {
    val label = stringResource(R.string.ranking_loading)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(RankingRowHeight * FOLDED_ROWS + RankingRowGap * (FOLDED_ROWS - 1))
            .semantics { contentDescription = label },
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
    }
}

/** A ranking that never arrived: no connection, or no answer from the server in time. */
@Composable
private fun RankingFailed(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RankingRowHeight * FOLDED_ROWS + RankingRowGap * (FOLDED_ROWS - 1)),
    ) {
        Text(
            text = stringResource(R.string.ranking_error),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.ranking_retry)) }
    }
}

/** A row of the global ranking: its avatar and padding; and the gap between rows, as in [RecordsCard]. */
private val RankingRowHeight = 32.dp
private val RankingRowGap = 6.dp

/** In the app's words, not the system's: Android's own would follow the phone's language. */
@Composable
private fun updatedAgo(fetchedAt: Long): String {
    val ago = (System.currentTimeMillis() - fetchedAt).coerceAtLeast(0)
    return when {
        ago < DateUtils.MINUTE_IN_MILLIS -> stringResource(R.string.ranking_just_now)
        ago < DateUtils.HOUR_IN_MILLIS -> (ago / DateUtils.MINUTE_IN_MILLIS).toInt().let { pluralStringResource(R.plurals.ranking_minutes_ago, it, it) }
        ago < DateUtils.DAY_IN_MILLIS -> (ago / DateUtils.HOUR_IN_MILLIS).toInt().let { pluralStringResource(R.plurals.ranking_hours_ago, it, it) }
        else -> (ago / DateUtils.DAY_IN_MILLIS).toInt().let { pluralStringResource(R.plurals.ranking_days_ago, it, it) }
    }
}

/** The star beside a game's name that keeps it among the favourites on the home screen. */
@Composable
private fun FavoriteButton(favorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add)

    IconButton(
        onClick = onClick,
        modifier = modifier
            .semantics {
                contentDescription = label
                role = Role.Button
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        ) {
            Text(
                text = if (favorite) "⭐" else "☆",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = if (favorite) 18.sp else 22.sp,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}
