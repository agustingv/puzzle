package com.agustingv.puzzles.ui.menu

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.agustingv.puzzles.R
import com.agustingv.puzzles.data.Avatar
import com.agustingv.puzzles.data.AvatarCatalog
import com.agustingv.puzzles.data.AvatarPiece
import com.agustingv.puzzles.data.AvatarPrizes
import com.agustingv.puzzles.ui.common.AvatarTile
import com.agustingv.puzzles.ui.common.Emoji
import com.agustingv.puzzles.ui.common.ScreenTopBar
import com.agustingv.puzzles.ui.common.avatarColor
import com.agustingv.puzzles.ui.theme.Paper

private val PieceTile = 44.dp
private val ColorDot = 34.dp
private val CellGap = 6.dp
private val SummarySize = 26.dp

/** Where the player puts their avatar together. Every choice is kept at once; there is no "save". */
@Composable
fun AvatarScreen(
    avatar: Avatar,
    name: String,
    prizes: AvatarPrizes,
    onChange: (Avatar) -> Unit,
    onExit: () -> Unit,
) {
    BackHandler(onBack = onExit)
    // One section open at a time, so that the three of them can always be seen.
    var open by rememberSaveable { mutableStateOf<Section?>(null) }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenTopBar(title = stringResource(R.string.avatar_title), onBack = onExit)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                AvatarTile(avatar, 96.dp)
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            FoldingCard(
                title = R.string.avatar_animal,
                summary = avatar.animal.label,
                expanded = open == Section.ANIMAL,
                onToggle = { open = if (open == Section.ANIMAL) null else Section.ANIMAL },
                preview = { AvatarTile(Avatar(avatar.animal, avatar.color), SummarySize) },
            ) {
                PieceGrid(AvatarCatalog.animals, minCell = 58.dp) { animal ->
                    PieceCell(
                        label = animal.label,
                        selected = animal == avatar.animal,
                        onClick = { onChange(avatar.copy(animal = animal)) },
                        showLabel = true,
                    ) {
                        AvatarTile(Avatar(animal, avatar.color), PieceTile)
                    }
                }
            }

            FoldingCard(
                title = R.string.avatar_color,
                summary = avatar.color.label,
                expanded = open == Section.COLOR,
                onToggle = { open = if (open == Section.COLOR) null else Section.COLOR },
                preview = { Box(Modifier.size(SummarySize).background(avatarColor(avatar.color), CircleShape)) },
            ) {
                PieceGrid(AvatarCatalog.colors, minCell = 46.dp) { color ->
                    PieceCell(
                        label = color.label,
                        selected = color == avatar.color,
                        onClick = { onChange(avatar.copy(color = color)) },
                        shape = CircleShape,
                    ) {
                        Box(Modifier.size(ColorDot).background(avatarColor(color), CircleShape))
                    }
                }
            }

            FoldingCard(
                title = R.string.avatar_accessory,
                summary = avatar.accessory?.label ?: stringResource(R.string.avatar_none),
                expanded = open == Section.ACCESSORY,
                onToggle = { open = if (open == Section.ACCESSORY) null else Section.ACCESSORY },
                preview = { avatar.accessory?.let { Emoji(it.emoji, SummarySize) } },
            ) {
                val choices = listOf<AvatarPiece?>(null) + AvatarCatalog.accessories
                PieceGrid(choices, minCell = 58.dp) { accessory ->
                    val won = accessory == null || prizes.has(accessory)
                    val label = accessory?.label ?: stringResource(R.string.avatar_none)
                    PieceCell(
                        label = label,
                        description = if (won) label else stringResource(R.string.avatar_locked, label),
                        selected = accessory == avatar.accessory,
                        enabled = won,
                        onClick = { onChange(avatar.copy(accessory = accessory)) },
                        showLabel = true,
                    ) {
                        AccessoryTile(accessory, won)
                    }
                }
                if (AvatarCatalog.accessories.any { !prizes.has(it) }) {
                    Text(
                        text = stringResource(R.string.avatar_prizes_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private enum class Section { ANIMAL, COLOR, ACCESSORY }

/**
 * A group of choices that folds away. Closed, its header still tells the current choice: its name and
 * a small picture of it, [preview].
 */
@Composable
private fun FoldingCard(
    title: Int,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    preview: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = stringResource(if (expanded) R.string.avatar_section_open else R.string.avatar_section_closed)
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    SettingsCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics(mergeDescendants = true) {
                    heading()
                    stateDescription = state
                },
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(SummarySize + 6.dp)) { preview() }
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "▾",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.rotate(turn).clearAndSetSemantics {},
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                content()
            }
        }
    }
}

/** The accessory on its own on a plain tile; a dash for none, and a padlock on one still to be won. */
@Composable
private fun AccessoryTile(accessory: AvatarPiece?, won: Boolean) {
    val shape = RoundedCornerShape(PieceTile * 0.18f)
    val outline = MaterialTheme.colorScheme.outline

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(PieceTile)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(PieceTile)
                .alpha(if (won) 1f else 0.35f)
                .background(Paper, shape)
                .border(1.dp, outline, shape),
        ) {
            if (accessory == null) {
                Text("—", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Emoji(accessory.emoji, PieceTile * 0.6f)
            }
        }
        if (!won) Emoji("🔒", PieceTile * 0.38f, Modifier.align(Alignment.BottomEnd))
    }
}

/**
 * One choice: its picture, ringed when chosen, and maybe its name under it. Screen readers hear the
 * name, or [description], and whether it is the chosen one.
 */
@Composable
private fun PieceCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    description: String = label,
    enabled: Boolean = true,
    showLabel: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp),
    picture: @Composable () -> Unit,
) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 4.dp)
                .size(PieceTile + 10.dp)
                .border(3.dp, ring, shape)
                .clearAndSetSemantics {},
        ) {
            picture()
        }
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** As many columns as fit cells at least [minCell] wide, sharing the width out evenly. */
@Composable
private fun <T> PieceGrid(items: List<T>, minCell: Dp, cell: @Composable (T) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + CellGap) / (minCell + CellGap)).toInt().coerceAtLeast(1)
        val width = max(0.dp, (maxWidth - CellGap * (columns - 1)) / columns)

        Column(verticalArrangement = Arrangement.spacedBy(CellGap)) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                    row.forEach { item ->
                        Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.width(width)) { cell(item) }
                    }
                }
            }
        }
    }
}
