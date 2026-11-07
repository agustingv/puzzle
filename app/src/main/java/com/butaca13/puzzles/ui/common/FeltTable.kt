package com.butaca13.puzzles.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The cloth: the same green in light and dark themes, as on any card table. */
val FeltLight = Color(0xFF2F8A55)
val FeltDark = Color(0xFF1C5E37)
private val FeltRim = Color(0xFF14432A)

/** What is drawn on the cloth itself, such as the outlines of empty places: chalk white, faint. */
val FeltChalk = Color.White

/**
 * The green baize every card game is played on: a frame for its table, lit in the middle and darker
 * towards the rim, so that the cards stand out whatever the system theme.
 */
@Composable
fun FeltTable(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .shadow(6.dp, shape)
            .background(Brush.radialGradient(listOf(FeltLight, FeltDark)), shape)
            .border(3.dp, FeltRim, shape)
            .padding(10.dp),
        content = content,
    )
}
