package com.agustingv.puzzles.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.data.Avatar
import com.agustingv.puzzles.data.AvatarPiece
import com.agustingv.puzzles.ui.theme.Card

/** Opens the settings from wherever the player is. Null on screens that do not offer them. */
val LocalOpenSettings = compositionLocalOf<(() -> Unit)?> { null }

/** The player's avatar, the way to change it, and to put on an accessory just won. */
class AvatarShortcut(val avatar: Avatar, val open: () -> Unit, val wear: (AvatarPiece) -> Unit)

/** Null on screens that do not show the avatar: the settings, which have it already, and its own. */
val LocalAvatarShortcut = compositionLocalOf<AvatarShortcut?> { null }

private val BackButtonWidth = 58.dp
private val AvatarButtonSize = 44.dp

/** The way to the settings, shown on every screen that can open them. */
@Composable
fun SettingsButton(modifier: Modifier = Modifier) {
    val open = LocalOpenSettings.current ?: return
    val label = stringResource(R.string.settings_title)

    IconButton(onClick = open, modifier = modifier.semantics { contentDescription = label }) {
        Text(text = "⚙️", fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics {})
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

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(AvatarButtonSize)
            .clickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = AvatarButtonSize / 2),
                role = Role.Button,
                onClick = shortcut.open,
            )
            .semantics { contentDescription = label },
    ) {
        AvatarTile(shortcut.avatar, AvatarButtonSize * 0.68f)
    }
}

/**
 * Top of every screen below the home one. A first row with the back arrow, title, [trailing] items,
 * avatar and settings; under it, when there is one, the [subtitle] with its [subtitleAction]. Two rows leave
 * both the title and a long subtitle room to fit on a narrow phone.
 */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleInTiles: Boolean = false,
    subtitleAction: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val backLabel = stringResource(R.string.back)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .width(BackButtonWidth)
                    .clearAndSetSemantics { contentDescription = backLabel },
            ) {
                Text("←", style = MaterialTheme.typography.titleLarge)
            }
            if (titleInTiles) {
                BarTitle(title, Modifier.weight(1f))
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            trailing()
            AvatarButton()
            SettingsButton(modifier = Modifier.padding(end = 4.dp))
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
