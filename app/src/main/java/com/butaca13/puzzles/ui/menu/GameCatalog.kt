package com.butaca13.puzzles.ui.menu

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.Products
import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.game.bakersdozen.BakersDozenGame
import com.butaca13.puzzles.game.freecell.FreeCellGame
import com.butaca13.puzzles.game.klondike.KlondikeGame
import com.butaca13.puzzles.game.pyramid.PyramidGame
import com.butaca13.puzzles.game.scorpion.ScorpionGame
import com.butaca13.puzzles.game.tripeaks.TriPeaksGame
import com.butaca13.puzzles.game.yukon.YukonGame
import com.butaca13.puzzles.game.word.WordList

private val BY_DIFFICULTY = Difficulty.entries.map { it.name }
private val BY_LENGTH = WordList.LENGTHS.map { it.toString() }
private val BY_THREE_LEVELS = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD).map { it.name }
private val BY_DRAW = listOf(KlondikeGame.DRAW_ONE, KlondikeGame.DRAW_THREE)
private val BY_CELLS = listOf(FreeCellGame.FOUR_CELLS, FreeCellGame.TWO_CELLS)
private val BY_PASSES = listOf(PyramidGame.ONE_PASS, PyramidGame.THREE_PASSES)
private val BY_WRAP = listOf(TriPeaksGame.EASY, TriPeaksGame.HARD)
private val BY_RULES = listOf(YukonGame.CLASSIC, YukonGame.RUSSIAN)
private val BY_EMPTY = listOf(ScorpionGame.CLASSIC, ScorpionGame.WASP)
private val BY_KINGS = listOf(BakersDozenGame.CLASSIC, BakersDozenGame.PORTUGUESE)

/**
 * Every game, in menu order. [id] names its saved game and records. [icon] is its pictogram, drawn in
 * white on its [tone], see GameIcon. [variants] are a game's difficulties, word
 * lengths, for the patience how many cards a draw turns over, for FreeCell how many free cells, for Pyramid how many times round the stock, for TriPeaks whether king and ace follow each other (easy) or not (hard), for Yukon whether columns build by colour or by suit, for Scorpion whether only kings go to an empty column, for Baker's Dozen whether an empty column takes a king. A [paid] game
 * is bought on its own (its [productId]) or with all of them, see [Products]. [group] is where the
 * home screen lists it.
 */
enum class GameInfo(
    val id: String,
    @param:DrawableRes val icon: Int,
    val tone: GameTone,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val variants: List<String>,
    val group: GameGroup,
    val paid: Boolean = false,
) {
    RACE("race", R.drawable.ic_game_race, GameTone.PINK, R.string.race_title, R.string.race_description, BY_LENGTH, GameGroup.LETTERS),
    HANGMAN("hangman", R.drawable.ic_game_hangman, GameTone.PEACH, R.string.hangman_title, R.string.hangman_description, BY_LENGTH, GameGroup.LETTERS),
    WORD("word", R.drawable.ic_game_word, GameTone.MINT, R.string.word_title, R.string.word_description, BY_LENGTH, GameGroup.LETTERS),
    WORD_SEARCH("wordsearch", R.drawable.ic_game_wordsearch, GameTone.LILAC, R.string.wordsearch_title, R.string.wordsearch_description, BY_DIFFICULTY, GameGroup.LETTERS),
    CROSSWORD("crossword", R.drawable.ic_game_crossword, GameTone.CORAL, R.string.crossword_title, R.string.crossword_description, BY_DIFFICULTY, GameGroup.LETTERS),
    PANAL("panal", R.drawable.ic_game_panal, GameTone.SAND, R.string.panal_title, R.string.panal_description, BY_THREE_LEVELS, GameGroup.LETTERS),
    CROSS_LETTERS("crossletters", R.drawable.ic_game_crossletters, GameTone.TEAL, R.string.crossletters_title, R.string.crossletters_description, BY_THREE_LEVELS, GameGroup.LETTERS),
    SUDOKU("sudoku", R.drawable.ic_game_sudoku, GameTone.MINT, R.string.sudoku_title, R.string.sudoku_description, BY_DIFFICULTY, GameGroup.NUMBERS),
    GARDEN("garden", R.drawable.ic_game_garden, GameTone.MINT, R.string.garden_title, R.string.garden_description, BY_DIFFICULTY, GameGroup.VARIOUS),
    PATH("path", R.drawable.ic_game_path, GameTone.PINK, R.string.path_title, R.string.path_description, BY_DIFFICULTY, GameGroup.VARIOUS),
    KAKURO("kakuro", R.drawable.ic_game_kakuro, GameTone.LILAC, R.string.kakuro_title, R.string.kakuro_description, BY_DIFFICULTY, GameGroup.NUMBERS),
    MEMORAMA("memorama", R.drawable.ic_game_memorama, GameTone.LILAC, R.string.memorama_title, R.string.memorama_description, BY_DIFFICULTY, GameGroup.VARIOUS),
    SLIDING("sliding", R.drawable.ic_game_sliding, GameTone.CORAL, R.string.sliding_title, R.string.sliding_description, BY_DIFFICULTY, GameGroup.NUMBERS),
    KLONDIKE("klondike", R.drawable.ic_game_klondike, GameTone.LIME, R.string.klondike_title, R.string.klondike_description, BY_DRAW, GameGroup.CARDS),
    FREECELL("freecell", R.drawable.ic_game_freecell, GameTone.SKY, R.string.freecell_title, R.string.freecell_description, BY_CELLS, GameGroup.CARDS),
    PYRAMID("pyramid", R.drawable.ic_game_pyramid, GameTone.PEACH, R.string.pyramid_title, R.string.pyramid_description, BY_PASSES, GameGroup.CARDS),
    TRIPEAKS("tripeaks", R.drawable.ic_game_tripeaks, GameTone.TEAL, R.string.tripeaks_title, R.string.tripeaks_description, BY_WRAP, GameGroup.CARDS),
    YUKON("yukon", R.drawable.ic_game_yukon, GameTone.SKY, R.string.yukon_title, R.string.yukon_description, BY_RULES, GameGroup.CARDS),
    SCORPION("scorpion", R.drawable.ic_game_scorpion, GameTone.CORAL, R.string.scorpion_title, R.string.scorpion_description, BY_EMPTY, GameGroup.CARDS),
    BAKERS_DOZEN("bakersdozen", R.drawable.ic_game_bakersdozen, GameTone.SAND, R.string.bakersdozen_title, R.string.bakersdozen_description, BY_KINGS, GameGroup.CARDS),
}

/**
 * The groups the home screen sorts the games into, in its order. [VARIOUS] takes those that fit none of
 * the others. A group may have no games yet: it shows as coming soon.
 */
enum class GameGroup(@param:StringRes val title: Int) {
    LETTERS(R.string.group_letters),
    NUMBERS(R.string.group_numbers),
    CARDS(R.string.group_cards),
    VARIOUS(R.string.group_various),
    ;

    val games: List<GameInfo> get() = GameInfo.entries.filter { it.group == this }
}

/** The Google Play product that opens this game; null for a free one. */
val GameInfo.productId: String? get() = if (paid) Products.forGame(id) else null

/**
 * The products of every paid game, for Google Play to price. Worked out when first asked for, not when
 * this file loads: the games load this file for their levels, and would not exist yet.
 */
val PAID_GAME_PRODUCTS: List<String> by lazy { GameInfo.entries.mapNotNull { it.productId } }

val GameInfo.usesWordLengths: Boolean get() = variants === BY_LENGTH

/** What a game's levels are, as the heading of their selector says. */
@get:StringRes
val GameInfo.variantsTitle: Int
    get() = when {
        variants === BY_LENGTH -> R.string.option_letters
        variants === BY_DRAW -> R.string.option_draw
        variants === BY_CELLS -> R.string.option_cells
        variants === BY_PASSES -> R.string.option_passes
        variants === BY_RULES || variants === BY_EMPTY || variants === BY_KINGS -> R.string.option_rules
        else -> R.string.option_difficulty
    }

/** Short label for a selector: "Fácil", or just "5" where the context says what it counts. */
@Composable
fun variantShortLabel(variant: String): String = when (variant) {
    KlondikeGame.DRAW_ONE -> stringResource(R.string.klondike_draw_one)
    KlondikeGame.DRAW_THREE -> stringResource(R.string.klondike_draw_three)
    FreeCellGame.FOUR_CELLS -> stringResource(R.string.freecell_four_cells)
    FreeCellGame.TWO_CELLS -> stringResource(R.string.freecell_two_cells)
    PyramidGame.ONE_PASS -> stringResource(R.string.pyramid_one_pass)
    PyramidGame.THREE_PASSES -> stringResource(R.string.pyramid_three_passes)
    YukonGame.CLASSIC -> stringResource(R.string.yukon_classic)
    YukonGame.RUSSIAN -> stringResource(R.string.yukon_russian)
    ScorpionGame.WASP -> stringResource(R.string.scorpion_wasp)
    BakersDozenGame.PORTUGUESE -> stringResource(R.string.bakersdozen_portuguese)
    else -> variant.toDifficultyOrNull()?.let { stringResource(it.labelRes) } ?: variant
}

private fun String.toDifficultyOrNull() = Difficulty.entries.firstOrNull { it.name == this }

@get:StringRes
private val Difficulty.labelRes: Int
    get() = when (this) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.MEDIUM -> R.string.difficulty_medium
        Difficulty.HARD -> R.string.difficulty_hard
        Difficulty.EXPERT -> R.string.difficulty_expert
    }
