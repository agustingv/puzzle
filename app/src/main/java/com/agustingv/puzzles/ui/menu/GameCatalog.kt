package com.agustingv.puzzles.ui.menu

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.agustingv.puzzles.R
import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.game.word.WordList

private val BY_DIFFICULTY = Difficulty.entries.map { it.name }
private val BY_LENGTH = WordList.LENGTHS.map { it.toString() }

/**
 * Every game, in menu order. [id] names its saved game and records. Icons are kept to old emoji:
 * minSdk 24 has no glyph for newer ones like 🟩, 🪢 or 🧠. [variants] are a game's difficulties or
 * word lengths.
 */
enum class GameInfo(
    val id: String,
    val icon: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val variants: List<String>,
) {
    RACE("race", "🏁", R.string.race_title, R.string.race_description, BY_LENGTH),
    HANGMAN("hangman", "🎯", R.string.hangman_title, R.string.hangman_description, BY_LENGTH),
    WORD("word", "🔠", R.string.word_title, R.string.word_description, BY_LENGTH),
    WORD_SEARCH("wordsearch", "🔍", R.string.wordsearch_title, R.string.wordsearch_description, BY_DIFFICULTY),
    CROSSWORD("crossword", "🧩", R.string.crossword_title, R.string.crossword_description, BY_DIFFICULTY),
    SUDOKU("sudoku", "🔢", R.string.sudoku_title, R.string.sudoku_description, BY_DIFFICULTY),
    PATH("path", "🔗", R.string.path_title, R.string.path_description, BY_DIFFICULTY),
    KAKURO("kakuro", "➕", R.string.kakuro_title, R.string.kakuro_description, BY_DIFFICULTY),
    MEMORAMA("memorama", "🃏", R.string.memorama_title, R.string.memorama_description, BY_DIFFICULTY),
    SLIDING("sliding", "🔀", R.string.sliding_title, R.string.sliding_description, BY_DIFFICULTY),
}

val GameInfo.usesWordLengths: Boolean get() = variants === BY_LENGTH

/** Short label for a selector: "Fácil", or just "5" where the context says what it counts. */
@Composable
fun variantShortLabel(variant: String): String =
    variant.toDifficultyOrNull()?.let { stringResource(it.labelRes) } ?: variant

/** Label that stands on its own: "Fácil" or "5 letras". */
@Composable
fun variantLabel(variant: String): String =
    variant.toDifficultyOrNull()?.let { stringResource(it.labelRes) }
        ?: stringResource(R.string.variant_letters, variant.toInt())

private fun String.toDifficultyOrNull() = Difficulty.entries.firstOrNull { it.name == this }

@get:StringRes
private val Difficulty.labelRes: Int
    get() = when (this) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.MEDIUM -> R.string.difficulty_medium
        Difficulty.HARD -> R.string.difficulty_hard
        Difficulty.EXPERT -> R.string.difficulty_expert
    }
