package com.agustingv.puzzles.ui.menu

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.game.word.WordList
import com.agustingv.puzzles.ui.theme.PuzzlesTheme

@Composable
fun MenuScreen(
    onPlayRace: (length: Int) -> Unit,
    onPlayHangman: (length: Int) -> Unit,
    onPlayWord: (length: Int) -> Unit,
    onPlayWordSearch: (Difficulty) -> Unit,
    onPlayCrossword: (Difficulty) -> Unit,
    onPlayPath: (Difficulty) -> Unit,
    onPlaySliding: (Difficulty) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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

        // Icons are kept to old emoji: minSdk 24 has no glyph for newer ones like 🟩 or 🪢.
        LengthCard(
            icon = "🏁",
            title = stringResource(R.string.race_title),
            description = stringResource(R.string.race_description),
            onPlay = onPlayRace,
        )
        LengthCard(
            icon = "🎯",
            title = stringResource(R.string.hangman_title),
            description = stringResource(R.string.hangman_description),
            onPlay = onPlayHangman,
        )
        LengthCard(
            icon = "🔠",
            title = stringResource(R.string.word_title),
            description = stringResource(R.string.word_description),
            onPlay = onPlayWord,
        )
        DifficultyCard(
            icon = "🔍",
            title = stringResource(R.string.wordsearch_title),
            description = stringResource(R.string.wordsearch_description),
            onPlay = onPlayWordSearch,
        )
        DifficultyCard(
            icon = "🧩",
            title = stringResource(R.string.crossword_title),
            description = stringResource(R.string.crossword_description),
            onPlay = onPlayCrossword,
        )
        DifficultyCard(
            icon = "🔗",
            title = stringResource(R.string.path_title),
            description = stringResource(R.string.path_description),
            onPlay = onPlayPath,
        )
        DifficultyCard(
            icon = "🔀",
            title = stringResource(R.string.sliding_title),
            description = stringResource(R.string.sliding_description),
            onPlay = onPlaySliding,
        )
    }
}

@Composable
private fun LengthCard(icon: String, title: String, description: String, onPlay: (Int) -> Unit) {
    GameCard(
        icon = icon,
        title = title,
        description = description,
        optionsLabel = stringResource(R.string.option_letters),
        options = WordList.LENGTHS,
        optionLabel = { it.toString() },
        onPlay = onPlay,
    )
}

@Composable
private fun DifficultyCard(icon: String, title: String, description: String, onPlay: (Difficulty) -> Unit) {
    GameCard(
        icon = icon,
        title = title,
        description = description,
        optionsLabel = stringResource(R.string.option_difficulty),
        options = Difficulty.entries,
        optionLabel = { stringResource(it.labelRes) },
        onPlay = onPlay,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> GameCard(
    icon: String,
    title: String,
    description: String,
    optionsLabel: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onPlay: (T) -> Unit,
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
        Text(text = icon, style = MaterialTheme.typography.headlineMedium)
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = optionsLabel, style = MaterialTheme.typography.labelMedium)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = index == selected,
                    onClick = { selected = index },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = {},
                    label = {
                        Text(
                            text = optionLabel(option),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }

        Button(onClick = { onPlay(options[selected]) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.play))
        }
    }
}

@get:StringRes
private val Difficulty.labelRes: Int
    get() = when (this) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.MEDIUM -> R.string.difficulty_medium
        Difficulty.HARD -> R.string.difficulty_hard
        Difficulty.EXPERT -> R.string.difficulty_expert
    }

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun MenuPreview() {
    PuzzlesTheme {
        MenuScreen(
            onPlayRace = {},
            onPlayHangman = {},
            onPlayWord = {},
            onPlayWordSearch = {},
            onPlayCrossword = {},
            onPlayPath = {},
            onPlaySliding = {},
        )
    }
}
