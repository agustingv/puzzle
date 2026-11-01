package com.agustingv.puzzles.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.ui.theme.Border
import com.agustingv.puzzles.ui.theme.Ink
import com.agustingv.puzzles.ui.theme.InkSoft

private val KeyHeight = 48.dp
private val KeyGap = 4.dp
private val KeyboardRows = listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm")

/** One letter square. [fill] set means a resolved letter: white text on that colour. */
@Composable
fun LetterBox(
    letter: Char?,
    size: Dp,
    fill: Color? = null,
    emphasized: Boolean = false,
    description: String = letter?.toString()?.uppercase().orEmpty(),
) {
    val shape = RoundedCornerShape(8.dp)
    val fontSize = with(LocalDensity.current) { (size * 0.5f).toSp() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(fill ?: Color.White)
            .border(2.dp, if (emphasized) InkSoft else Border, shape)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = letter?.toString()?.uppercase().orEmpty(),
            color = if (fill != null) Color.White else Ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
        )
    }
}

/** The extra keys of games where a word is typed and then submitted. */
class SubmitKeys(
    val canSubmit: Boolean,
    val canDelete: Boolean,
    val onSubmit: () -> Unit,
    val onDelete: () -> Unit,
)

/** On-screen Spanish keyboard. Own keyboard rather than the system's: it always has the Ñ. */
@Composable
fun LetterKeyboard(
    onLetter: (Char) -> Unit,
    keyColor: (Char) -> Color? = { null },
    keyEnabled: (Char) -> Boolean = { true },
    submitKeys: SubmitKeys? = null,
    keyHeight: Dp = KeyHeight,
) {
    Column(
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KeyGap),
    ) {
        KeyboardRows.forEachIndexed { index, letters ->
            val extras = submitKeys.takeIf { index == KeyboardRows.lastIndex }

            Row(
                modifier = Modifier.fillMaxWidth().height(keyHeight),
                horizontalArrangement = Arrangement.spacedBy(KeyGap, Alignment.CenterHorizontally),
            ) {
                if (extras != null) {
                    Key(
                        label = "OK",
                        description = stringResource(R.string.key_submit),
                        enabled = extras.canSubmit,
                        weight = 1.5f,
                        onClick = extras.onSubmit,
                    )
                }
                for (letter in letters) {
                    Key(
                        label = letter.uppercase(),
                        description = letter.uppercase(),
                        fill = keyColor(letter),
                        enabled = keyEnabled(letter),
                        onClick = { onLetter(letter) },
                    )
                }
                if (extras != null) {
                    Key(
                        label = "←",
                        description = stringResource(R.string.key_delete),
                        enabled = extras.canDelete,
                        weight = 1.5f,
                        onClick = extras.onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.Key(
    label: String,
    description: String,
    onClick: () -> Unit,
    fill: Color? = null,
    enabled: Boolean = true,
    weight: Float = 1f,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(fill ?: Border)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            color = when {
                fill != null -> Color.White
                enabled -> Ink
                else -> InkSoft.copy(alpha = 0.5f)
            },
        )
    }
}

/** End-of-game message and replay button, shared by every game. */
@Composable
fun GameResult(message: String, onPlayAgain: () -> Unit) {
    Text(
        text = message,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Button(onClick = onPlayAgain) { Text(stringResource(R.string.play_again)) }
}
