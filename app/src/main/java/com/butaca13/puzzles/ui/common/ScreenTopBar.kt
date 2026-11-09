package com.butaca13.puzzles.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.Avatar
import com.butaca13.puzzles.data.AvatarPiece
import com.butaca13.puzzles.ui.theme.Card

/** Opens the settings from wherever the player is. Null on screens that do not offer them. */
val LocalOpenSettings = compositionLocalOf<(() -> Unit)?> { null }

/** True on the settings themselves: the button still shows, disabled, so that the header stays the same. */
val LocalOnSettings = compositionLocalOf { false }

/**
 * The player's avatar, the way to change it, and to put on an accessory just won. [open] is null on
 * the avatar's own screen, where the button shows disabled.
 */
class AvatarShortcut(val avatar: Avatar, val open: (() -> Unit)?, val wear: (AvatarPiece) -> Unit)

/** Null on screens that do not show the avatar: the settings, which have it already, and its own. */
val LocalAvatarShortcut = compositionLocalOf<AvatarShortcut?> { null }

/**
 * True on a screen shown as one of the bottom bar's places: the bar already leads everywhere, so the
 * screen shows only its own title, without the app's header or the way back.
 */
val LocalInTab = compositionLocalOf { false }

private val BackButtonWidth = 58.dp

/** Room for what goes at the right of the title, the clock: the title keeps clear of it on both sides. */
private val TrailingWidth = 104.dp

/** How a header button looks on its own screen, where it leads nowhere. */
private const val DisabledAlpha = 0.38f
private val AvatarButtonSize = 44.dp

/** What the home screen's avatar and settings buttons take beside the title, with their padding. */
private val HomeButtonsWidth = AvatarButtonSize + 48.dp + 8.dp

/** The way to the settings, shown on every screen that can open them. */
@Composable
fun SettingsButton(modifier: Modifier = Modifier) {
    val here = LocalOnSettings.current
    val open = LocalOpenSettings.current
    if (open == null && !here) return
    val label = stringResource(R.string.settings_title)

    IconButton(onClick = { open?.invoke() }, enabled = !here && open != null, modifier = modifier.semantics { contentDescription = label }) {
        Text(
            text = "⚙️",
            fontSize = 22.sp,
            modifier = Modifier.alpha(if (here) DisabledAlpha else 1f).clearAndSetSemantics {},
        )
    }
}

/**
 * The player's avatar, next to the settings. Not an IconButton: that clips its content to a circle,
 * and the accessory sticks out of the tile on purpose.
 */
@Composable
fun AvatarButton(modifier: Modifier = Modifier) {
    val shortcut = LocalAvatarShortcut.current ?: return
    val label = stringResource(R.string.avatar_change)
    val open = shortcut.open

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(AvatarButtonSize)
            .clickable(
                enabled = open != null,
                interactionSource = null,
                indication = ripple(bounded = false, radius = AvatarButtonSize / 2),
                role = Role.Button,
                onClick = { open?.invoke() },
            )
            .semantics { contentDescription = label },
    ) {
        AvatarTile(shortcut.avatar, AvatarButtonSize * 0.68f, Modifier.alpha(if (open == null) DisabledAlpha else 1f))
    }
}

/** Whether the secret message is solved, for the app's title on every screen: its last tile turns gold. */
val LocalSecretSolved = compositionLocalOf { false }

/**
 * The app's header, as on the home screen: PUZZLES in tiles, the avatar and the settings. Where one of
 * them is not offered, on its own screen, its room is kept, so that the title sits in the same place and
 * at the same size on every screen.
 */
@Composable
fun AppHeader(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    ) {
        AppTitle(LocalSecretSolved.current, onLastTile = null, modifier = Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(HomeButtonsWidth),
        ) {
            AvatarButton(modifier = Modifier.padding(start = 4.dp))
            SettingsButton(modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/**
 * Top of every screen below the home one: the app's header, as on the home screen, and under it the way
 * back with the screen's own [title] as a subtitle, and its [trailing] items; then, when there is one,
 * the [subtitle] with its [subtitleAction].
 */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleInTiles: Boolean = false,
    /** False in a game: only the way back, the title and the trailing items, to leave room for the board. */
    appHeader: Boolean = true,
    subtitleAction: @Composable RowScope.() -> Unit = {},
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val backLabel = stringResource(R.string.back)

    if (LocalInTab.current) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp).semantics { heading() },
        )
        return
    }

    Column(modifier = modifier.fillMaxWidth().padding(top = if (appHeader) 0.dp else 12.dp)) {
        if (appHeader) AppHeader()
        // The title centred on the screen, clear of the back arrow on the left and of whatever is on the
        // right, such as the clock, which take the same room on both sides so that it stays centred.
        val side = if (trailing != null) TrailingWidth else BackButtonWidth
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            if (titleInTiles) {
                BarTitle(title, Modifier.fillMaxWidth().padding(horizontal = side), small = true, wrap = !appHeader)
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = side).semantics { heading() },
                )
            }
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(BackButtonWidth)
                    .clearAndSetSemantics { contentDescription = backLabel },
            ) {
                Text("←", style = MaterialTheme.typography.titleLarge)
            }
            if (trailing != null) {
                Row(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
            }
        }
        if (subtitle != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = BackButtonWidth, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                subtitleAction()
            }
        }
    }
}

/** The game clock. Its digits all take the same width, so the pill does not shift as they change. */
@Composable
fun TimerPill(seconds: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    val time = formatTime(seconds)
    val description = stringResource(R.string.time_value, time)

    Text(
        text = "⏱ $time",
        style = MaterialTheme.typography.labelLarge.merge(TextStyle(fontFeatureSettings = "tnum")),
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(shape)
            .background(Card)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = description },
    )
}
