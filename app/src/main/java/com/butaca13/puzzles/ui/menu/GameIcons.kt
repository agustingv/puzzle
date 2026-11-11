package com.butaca13.puzzles.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.butaca13.puzzles.R

/**
 * A game's colour behind its white pictogram: intense, so that the icons tell apart at a glance, from
 * a lighter top-left to a deeper bottom-right. The same in both themes.
 */
enum class GameTone(light: Long, deep: Long) {
    MINT(0xFF2BC39A, 0xFF159A78),
    SKY(0xFF4AA6EE, 0xFF2378C8),
    LILAC(0xFF8E6CF0, 0xFF6545CF),
    PINK(0xFFF0609A, 0xFFC93A75),
    PEACH(0xFFFF9E52, 0xFFE5702A),
    TEAL(0xFF22BDB0, 0xFF128F86),
    SAND(0xFFC99A4E, 0xFF9E7231),
    LIME(0xFF8CC63A, 0xFF5F9A1B),
    CORAL(0xFFF26B5B, 0xFFC9473A),
    ;

    val brush: Brush = Brush.linearGradient(listOf(Color(light), Color(deep)))
}

/** Starfall's tone: the night sky it is played in. */
private val NightSky = Brush.verticalGradient(listOf(Color(0xFF080A18), Color(0xFF26214A)))

/** A game's icon: its pictogram in white on its tone, in a rounded square [size] wide. Says nothing to screen readers. */
@Composable
fun GameIcon(info: GameInfo, size: Dp, modifier: Modifier = Modifier) {
    ToneSquare(info.tone.brush, info.icon, size, modifier)
}

/** Starfall's icon, like a game's, on the night sky. */
@Composable
fun StarfallIcon(size: Dp, modifier: Modifier = Modifier) {
    ToneSquare(NightSky, R.drawable.ic_game_starfall, size, modifier)
}

@Composable
private fun ToneSquare(background: Brush, icon: Int, size: Dp, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(background),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}
