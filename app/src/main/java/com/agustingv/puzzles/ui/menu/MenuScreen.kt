package com.agustingv.puzzles.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.data.SavedGame
import com.agustingv.puzzles.ui.common.formatTime
import com.agustingv.puzzles.ui.theme.PuzzlesTheme

@Composable
fun MenuScreen(
    saved: (GameInfo) -> SavedGame?,
    onNewGame: (GameInfo, variant: String) -> Unit,
    onContinue: (GameInfo) -> Unit,
    onRecords: (GameInfo) -> Unit,
    onPrivacy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = stringResource(R.string.menu_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GameInfo.entries.forEach { info ->
            GameCard(
                info = info,
                saved = saved(info),
                onNewGame = { onNewGame(info, it) },
                onContinue = { onContinue(info) },
                onRecords = { onRecords(info) },
            )
        }

        TextButton(onClick = onPrivacy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.privacy_title))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameCard(
    info: GameInfo,
    saved: SavedGame?,
    onNewGame: (String) -> Unit,
    onContinue: () -> Unit,
    onRecords: () -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = info.icon,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRecords) { Text(stringResource(R.string.records_link)) }
        }
        Text(
            text = stringResource(info.title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(text = stringResource(info.description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = stringResource(if (info.usesWordLengths) R.string.option_letters else R.string.option_difficulty),
            style = MaterialTheme.typography.labelMedium,
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            info.variants.forEachIndexed { index, variant ->
                SegmentedButton(
                    selected = index == selected,
                    onClick = { selected = index },
                    shape = SegmentedButtonDefaults.itemShape(index, info.variants.size),
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

        if (saved == null) {
            Button(onClick = { onNewGame(info.variants[selected]) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.play))
            }
        } else {
            Text(
                text = stringResource(
                    R.string.saved_game,
                    variantLabel(saved.variant),
                    formatTime(saved.elapsedSeconds),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onNewGame(info.variants[selected]) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.new_game)) }
                Button(onClick = onContinue, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.continue_game))
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun MenuPreview() {
    PuzzlesTheme {
        MenuScreen(
            saved = { if (it == GameInfo.HANGMAN) SavedGame("5", 83) else null },
            onNewGame = { _, _ -> },
            onContinue = {},
            onRecords = {},
            onPrivacy = {},
        )
    }
}
