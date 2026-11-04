package com.agustingv.puzzles.ui.menu

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.data.Avatar
import com.agustingv.puzzles.data.GlobalRankings
import com.agustingv.puzzles.data.KeyValueStore
import com.agustingv.puzzles.data.PlayerAccount
import com.agustingv.puzzles.data.Ranking
import com.agustingv.puzzles.data.RecordEntry
import com.agustingv.puzzles.data.Records
import com.agustingv.puzzles.ui.common.AvatarTile
import com.agustingv.puzzles.ui.common.ScreenTopBar
import com.agustingv.puzzles.ui.common.formatTime
import com.agustingv.puzzles.ui.theme.Accent
import com.agustingv.puzzles.ui.theme.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val LAST_VARIANT_KEY = "last-variant"

/**
 * A game's page: what it is, which level to play, the player's best times on that level and the best
 * of everyone, and how to start.
 */
@Composable
fun GameDetailScreen(
    info: GameInfo,
    records: Records,
    rankings: GlobalRankings,
    account: PlayerAccount,
    storage: KeyValueStore,
    onPlay: (variant: String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    // Opens on the last level played, so a repeat game takes one tap.
    val lastKey = "$LAST_VARIANT_KEY/${info.id}"
    var selected by rememberSaveable(info) {
        mutableStateOf(storage.get(lastKey)?.takeIf { it in info.variants } ?: info.variants.first())
    }

    // The global ranking shows as last downloaded, at once, and is brought up to date behind it.
    var global by remember(info, selected) { mutableStateOf(rankings.cached(info.id, selected)) }
    LaunchedEffect(info, selected, account.registered, account.offline) {
        withContext(Dispatchers.IO) { rankings.refresh(account, info.id, selected) }?.let { global = it }
    }

    fun play() {
        storage.put(lastKey, selected)
        onPlay(selected)
    }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenTopBar(title = stringResource(info.title), onBack = onBack, titleInTiles = true)

        val intro: @Composable () -> Unit = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(info.gradient)),
            ) {
                Text(text = info.icon, fontSize = 56.sp, modifier = Modifier.clearAndSetSemantics {})
            }

            Text(text = stringResource(info.description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val options: @Composable () -> Unit = {
            VariantSelector(info, selected, onSelect = { selected = it })
            // Both lists open folded on each game and level: five rows are enough to see where one stands.
            var allMine by rememberSaveable(info, selected) { mutableStateOf(false) }
            var allGlobal by rememberSaveable(info, selected) { mutableStateOf(false) }
            PersonalRecords(records.entries(info.id, selected), allMine, onToggle = { allMine = !allMine })
            global?.let { GlobalRanking(it, ownPlayer = account.id, allGlobal, onToggle = { allGlobal = !allGlobal }) }
        }
        val spacing = Arrangement.spacedBy(16.dp)
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (landscape) {
            // Little height: the intro goes beside the options, and each side scrolls on its own.
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = spacing,
                ) { intro() }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = spacing,
                ) { options() }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = spacing,
            ) {
                intro()
                options()
            }
        }

        Button(
            onClick = ::play,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp),
        ) {
            Text(stringResource(R.string.play), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariantSelector(info: GameInfo, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(if (info.usesWordLengths) R.string.option_letters else R.string.option_difficulty),
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
 * stand out. Without a connection it is the copy kept, and says how old it is.
 */
@Composable
private fun GlobalRanking(ranking: Ranking, ownPlayer: String, expanded: Boolean, onToggle: () -> Unit) {
    RecordsCard(stringResource(R.string.ranking_global)) {
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

        Text(
            text = stringResource(R.string.ranking_updated, updatedAgo(ranking.fetchedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
