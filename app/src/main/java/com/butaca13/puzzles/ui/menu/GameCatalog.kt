package com.butaca13.puzzles.ui.menu

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.Products
import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.game.freecell.FreeCellGame
import com.butaca13.puzzles.game.klondike.KlondikeGame
import com.butaca13.puzzles.game.pyramid.PyramidGame
import com.butaca13.puzzles.game.spider.SpiderGame
import com.butaca13.puzzles.game.tripeaks.TriPeaksGame
import com.butaca13.puzzles.game.word.WordList
import com.butaca13.puzzles.ui.theme.PastelCoral
import com.butaca13.puzzles.ui.theme.PastelLilac
import com.butaca13.puzzles.ui.theme.PastelLime
import com.butaca13.puzzles.ui.theme.PastelMint
import com.butaca13.puzzles.ui.theme.PastelPeach
import com.butaca13.puzzles.ui.theme.PastelPink
import com.butaca13.puzzles.ui.theme.PastelSand
import com.butaca13.puzzles.ui.theme.PastelSky
import com.butaca13.puzzles.ui.theme.PastelTeal

private val BY_DIFFICULTY = Difficulty.entries.map { it.name }
private val BY_LENGTH = WordList.LENGTHS.map { it.toString() }
private val BY_DRAW = listOf(KlondikeGame.DRAW_ONE, KlondikeGame.DRAW_THREE)
private val BY_CELLS = listOf(FreeCellGame.FOUR_CELLS, FreeCellGame.TWO_CELLS)
private val BY_PASSES = listOf(PyramidGame.ONE_PASS, PyramidGame.THREE_PASSES)
private val BY_WRAP = listOf(TriPeaksGame.EASY, TriPeaksGame.HARD)
private val BY_SUITS = listOf(SpiderGame.ONE_SUIT, SpiderGame.TWO_SUITS, SpiderGame.FOUR_SUITS)

/**
 * Every game, in menu order. [id] names its saved game and records. Icons are kept to old emoji:
 * minSdk 24 has no glyph for newer ones like 🟩, 🪢 or 🧠. [variants] are a game's difficulties, word
 * lengths, for the patience how many cards a draw turns over, for FreeCell how many free cells, for Spider how many suits, for Pyramid how many times round the stock, for TriPeaks whether king and ace follow each other (easy) or not (hard). [gradient] is its pair of pastels, the web's colour pair made pastel. A [paid] game
 * is bought on its own (its [productId]) or with all of them, see [Products]. [group] is where the
 * home screen lists it.
 */
enum class GameInfo(
    val id: String,
    val icon: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val variants: List<String>,
    val gradient: List<Color>,
    val group: GameGroup,
    val paid: Boolean = false,
) {
    RACE("race", "🏁", R.string.race_title, R.string.race_description, BY_LENGTH, listOf(PastelPink, PastelLilac), GameGroup.LETTERS),
    HANGMAN("hangman", "🎯", R.string.hangman_title, R.string.hangman_description, BY_LENGTH, listOf(PastelPeach, PastelCoral), GameGroup.LETTERS),
    WORD("word", "🔠", R.string.word_title, R.string.word_description, BY_LENGTH, listOf(PastelMint, PastelTeal), GameGroup.LETTERS),
    WORD_SEARCH("wordsearch", "🔍", R.string.wordsearch_title, R.string.wordsearch_description, BY_DIFFICULTY, listOf(PastelLilac, PastelPink), GameGroup.LETTERS),
    CROSSWORD("crossword", "🧩", R.string.crossword_title, R.string.crossword_description, BY_DIFFICULTY, listOf(PastelPeach, PastelLilac), GameGroup.LETTERS),
    SUDOKU("sudoku", "🔢", R.string.sudoku_title, R.string.sudoku_description, BY_DIFFICULTY, listOf(PastelMint, PastelPink), GameGroup.NUMBERS),
    GARDEN("garden", "🌻", R.string.garden_title, R.string.garden_description, BY_DIFFICULTY, listOf(PastelMint, PastelPeach), GameGroup.VARIOUS),
    PATH("path", "🔗", R.string.path_title, R.string.path_description, BY_DIFFICULTY, listOf(PastelPink, PastelMint), GameGroup.VARIOUS),
    KAKURO("kakuro", "➕", R.string.kakuro_title, R.string.kakuro_description, BY_DIFFICULTY, listOf(PastelLilac, PastelMint), GameGroup.NUMBERS),
    MEMORAMA("memorama", "🃏", R.string.memorama_title, R.string.memorama_description, BY_DIFFICULTY, listOf(PastelLilac, PastelPeach), GameGroup.VARIOUS),
    // Provisional, to try the purchases out on the compras branch.
    SLIDING("sliding", "🔀", R.string.sliding_title, R.string.sliding_description, BY_DIFFICULTY, listOf(PastelCoral, PastelMint), GameGroup.NUMBERS, paid = true),
    KLONDIKE("klondike", "♠️", R.string.klondike_title, R.string.klondike_description, BY_DRAW, listOf(PastelLime, PastelTeal), GameGroup.CARDS),
    FREECELL("freecell", "♣️", R.string.freecell_title, R.string.freecell_description, BY_CELLS, listOf(PastelSky, PastelLilac), GameGroup.CARDS),
    SPIDER("spider", "🕷️", R.string.spider_title, R.string.spider_description, BY_SUITS, listOf(PastelSand, PastelCoral), GameGroup.CARDS),
    PYRAMID("pyramid", "🔺", R.string.pyramid_title, R.string.pyramid_description, BY_PASSES, listOf(PastelPeach, PastelSand), GameGroup.CARDS),
    TRIPEAKS("tripeaks", "⛰️", R.string.tripeaks_title, R.string.tripeaks_description, BY_WRAP, listOf(PastelMint, PastelSky), GameGroup.CARDS),
}

/**
 * The groups the home screen sorts the games into, in its order. [VARIOUS] takes those that fit none of
 * the others. A group may have no games yet: it shows as coming soon.
 */
enum class GameGroup(@param:StringRes val title: Int, val icon: String, val gradient: List<Color>) {
    LETTERS(R.string.group_letters, "🔤", listOf(PastelLilac, PastelPink)),
    NUMBERS(R.string.group_numbers, "🔢", listOf(PastelMint, PastelTeal)),
    CARDS(R.string.group_cards, "🃏", listOf(PastelCoral, PastelPeach)),
    VARIOUS(R.string.group_various, "🎲", listOf(PastelPeach, PastelMint)),
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
        variants === BY_SUITS -> R.string.option_suits
        variants === BY_PASSES -> R.string.option_passes
        else -> R.string.option_difficulty
    }

/** Short label for a selector: "Fácil", or just "5" where the context says what it counts. */
@Composable
fun variantShortLabel(variant: String): String = when (variant) {
    KlondikeGame.DRAW_ONE -> stringResource(R.string.klondike_draw_one)
    KlondikeGame.DRAW_THREE -> stringResource(R.string.klondike_draw_three)
    FreeCellGame.FOUR_CELLS -> stringResource(R.string.freecell_four_cells)
    FreeCellGame.TWO_CELLS -> stringResource(R.string.freecell_two_cells)
    SpiderGame.ONE_SUIT -> stringResource(R.string.spider_one_suit)
    SpiderGame.TWO_SUITS -> stringResource(R.string.spider_two_suits)
    SpiderGame.FOUR_SUITS -> stringResource(R.string.spider_four_suits)
    PyramidGame.ONE_PASS -> stringResource(R.string.pyramid_one_pass)
    PyramidGame.THREE_PASSES -> stringResource(R.string.pyramid_three_passes)
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
