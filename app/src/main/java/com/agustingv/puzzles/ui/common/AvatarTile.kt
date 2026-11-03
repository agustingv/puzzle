package com.agustingv.puzzles.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import com.agustingv.puzzles.data.Avatar
import com.agustingv.puzzles.data.AvatarCatalog
import com.agustingv.puzzles.data.AvatarPiece
import com.agustingv.puzzles.ui.theme.BedDim
import com.agustingv.puzzles.ui.theme.GardenBeds

/** The colour of an avatar's tile: the garden's pastels, darkened a little on the dark theme. */
@Composable
fun avatarColor(color: AvatarPiece): Color {
    val index = AvatarCatalog.colors.indexOf(color).coerceAtLeast(0)
    return lerp(GardenBeds[index % GardenBeds.size], Color.Black, BedDim)
}

/**
 * The player's avatar, drawn like the title's tiles: the animal on a raised pastel tile, and the
 * accessory, if any, over its top right corner, a little outside the tile. It says nothing to screen
 * readers: whatever holds it describes it.
 */
@Composable
fun AvatarTile(avatar: Avatar, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).clearAndSetSemantics {}) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .shadow(size / 22, RoundedCornerShape(size * 0.18f))
                .background(avatarColor(avatar.color)),
        ) {
            Emoji(avatar.animal.emoji, size * 0.62f)
        }
        avatar.accessory?.let {
            Emoji(
                it.emoji,
                size * 0.4f,
                Modifier.align(Alignment.TopEnd).offset(x = size * 0.14f, y = -size * 0.14f),
            )
        }
    }
}

/** An emoji [height] tall, centred without the theme's line spacing, which would push it off a tile. */
@Composable
internal fun Emoji(emoji: String, height: Dp, modifier: Modifier = Modifier) {
    val fontSize = with(LocalDensity.current) { (height * 0.82f).toSp() }
    Text(
        text = emoji,
        fontSize = fontSize,
        style = LocalTextStyle.current.merge(
            lineHeight = fontSize,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        ),
        modifier = modifier,
    )
}
