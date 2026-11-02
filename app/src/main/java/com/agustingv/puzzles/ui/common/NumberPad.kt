package com.agustingv.puzzles.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.safeDrawingPadding
import com.agustingv.puzzles.R
import com.agustingv.puzzles.ui.theme.Border
import com.agustingv.puzzles.ui.theme.Ink

private val KeyHeight = 48.dp

/** Digits 1-9 and delete, for the games filled in with numbers. */
@Composable
fun NumberPad(onDigit: (Int) -> Unit, onClear: () -> Unit) {
    val clearLabel = stringResource(R.string.key_delete)

    Column(
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(1..5, 6..9).forEach { digits ->
            Row(
                modifier = Modifier.fillMaxWidth().height(KeyHeight),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (digit in digits) {
                    val description = stringResource(R.string.key_digit, digit)
                    PadKey(digit.toString(), description) { onDigit(digit) }
                }
                if (digits.last == 9) PadKey("←", clearLabel, onClear)
            }
        }
    }
}

@Composable
private fun RowScope.PadKey(label: String, description: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(Border)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(text = label, fontWeight = FontWeight.Bold, color = Ink, style = MaterialTheme.typography.titleLarge)
    }
}

/** Shown while a puzzle that takes time to generate is being prepared. */
@Composable
fun GameLoading(title: String, onExit: () -> Unit) {
    BackHandler(onBack = onExit)

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenTopBar(title = title, onBack = onExit)
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.generating))
            }
        }
    }
}
