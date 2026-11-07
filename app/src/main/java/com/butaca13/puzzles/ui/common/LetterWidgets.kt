package com.butaca13.puzzles.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.RecordResult
import com.butaca13.puzzles.ui.theme.Border
import com.butaca13.puzzles.ui.theme.Card
import com.butaca13.puzzles.ui.theme.Ink
import com.butaca13.puzzles.ui.theme.InkSoft

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
            .background(fill ?: Card)
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
    val haptics = LocalHapticFeedback.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(fill ?: Border)
            .clickable(enabled = enabled, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                onClick()
            }
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

/** Secondary action during a game. Opaque, because the background's tree shows behind it. */
@Composable
fun GameActionButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Card, contentColor = Ink),
    ) {
        Text(label)
    }
}

/** End-of-game message and replay button, shared by every game. */
@Composable
fun GameResult(message: String, onPlayAgain: () -> Unit, record: RecordResult? = null) {
    val shape = RoundedCornerShape(20.dp)
    val appearing = remember { MutableTransitionState(false).apply { targetState = true } }

    AnimatedVisibility(
        visibleState = appearing,
        enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.7f, stiffness = 300f)) { it / 2 },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Card)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (record != null) {
                val pop = remember { Animatable(if (record.isNewBest) 0.6f else 1f) }
                LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f)) }

                Text(
                    modifier = Modifier.graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    },
                    text = when {
                        record.isNewBest -> stringResource(R.string.record_new_best, formatTime(record.seconds))
                        record.rank != null ->
                            stringResource(R.string.record_ranked, record.rank, formatTime(record.best))
                        else -> stringResource(R.string.record_best, formatTime(record.best))
                    },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = onPlayAgain,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(52.dp),
            ) {
                Text(stringResource(R.string.play_again), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
