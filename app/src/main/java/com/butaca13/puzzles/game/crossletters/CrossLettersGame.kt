package com.butaca13.puzzles.game.crossletters

import com.butaca13.puzzles.game.Difficulty
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * The words letter squares are made of and checked against: [common] for the rows, and the first
 * levels' columns; [words] for the hard level's columns and to accept any square the player makes.
 * All plain, without accents.
 */
class SquareWords(words: Collection<String>, common: Collection<String>) {
    val words: Set<String> = words.toSet() + common
    val common: Set<String> = common.toSet()

    /**
     * A square of [size] whose every row is one of [rowWords] and every column one of [columnWords], or
     * null when none turns up within [budget] steps of the search. Rows are tried in a random order, so
     * that each square comes out different.
     */
    fun square(size: Int, rowWords: Collection<String>, columnWords: Collection<String>, random: Random, budget: Int = 200_000): List<String>? {
        val rows = rowWords.filter { it.length == size }.shuffled(random)
        val columns = columnWords.filter { it.length == size }.toSet()
        val prefixes = columns.flatMap { w -> (0..size).map { w.substring(0, it) } }.toHashSet()
        val grid = ArrayList<String>()
        var steps = 0

        fun fits(word: String) = (0 until size).all { c -> grid.joinToString("") { it[c].toString() } + word[c] in prefixes }

        fun search(): Boolean {
            if (grid.size == size) return true
            for (word in rows) {
                if (++steps > budget) return false
                if (word in grid || !fits(word)) continue
                grid += word
                if (search()) return true
                grid.removeAt(grid.lastIndex)
            }
            return false
        }
        return if (search()) grid.toList() else null
    }

    /** A puzzle for [difficulty]: its solution, and which places come filled. */
    fun puzzle(difficulty: Difficulty, random: Random = Random.Default): CrossLettersGame? {
        val (size, given) = when (difficulty) {
            Difficulty.EASY -> 4 to 6
            Difficulty.MEDIUM -> 4 to 3
            else -> 5 to 5
        }
        // The hard square's columns may be any word: of common ones alone there are hardly any squares of five.
        val columns = if (size == 4) common else words
        repeat(20) {
            val solution = square(size, common, columns, random) ?: return@repeat
            val places = (0 until size * size).shuffled(random).take(given).toSet()
            return CrossLettersGame.of(solution, places, difficulty, random)
        }
        return null
    }
}

/**
 * Letter square: a grid where every row and every column is a word. Some letters come in their places;
 * the rest wait in the tray, shuffled, to be put in. Any square whose rows and columns are all words
 * wins, not only the one the puzzle was made from.
 */
data class CrossLettersGame(
    val size: Int,
    /** The square the puzzle was made from, row by row: what hints give. */
    val solution: List<String>,
    /** Every place, row by row: its letter, or null while empty. */
    val cells: List<Char?>,
    /** The places that came filled, or were filled by a hint: fixed. */
    val given: Set<Int>,
    /** The letters still to place, in the order shown. */
    val tray: List<Char>,
    val difficulty: Difficulty,
    val hints: Int = 0,
    val solved: Boolean = false,
) {
    val isWon: Boolean get() = solved

    fun row(r: Int): String? = (0 until size).map { cells[r * size + it] }.takeIf { it.none { c -> c == null } }?.joinToString("")

    fun column(c: Int): String? = (0 until size).map { cells[it * size + c] }.takeIf { it.none { ch -> ch == null } }?.joinToString("")

    /** Puts the tray's letter at [trayIndex] in [cell], sending back to the tray whatever was there. */
    fun place(trayIndex: Int, cell: Int, words: SquareWords): CrossLettersGame {
        if (solved || trayIndex !in tray.indices || cell !in cells.indices || cell in given) return this
        val back = cells[cell]
        val nextTray = tray.toMutableList().apply { removeAt(trayIndex); back?.let { add(it) } }
        val next = copy(cells = cells.toMutableList().also { it[cell] = tray[trayIndex] }, tray = nextTray)
        return next.checked(words)
    }

    /** Sends the letter in [cell] back to the tray. */
    fun takeBack(cell: Int): CrossLettersGame {
        if (solved || cell in given) return this
        val letter = cells.getOrNull(cell) ?: return this
        return copy(cells = cells.toMutableList().also { it[cell] = null }, tray = tray + letter)
    }

    /** Puts the solution's letter in [cell], fixed, taking it from the tray or from wherever it was. */
    fun hint(cell: Int, words: SquareWords): CrossLettersGame {
        if (solved || cell in given) return this
        val letter = solution[cell / size][cell % size]
        var game = if (cells[cell] != null) takeBack(cell) else this
        val fromTray = game.tray.indexOf(letter)
        game = if (fromTray >= 0) {
            game.copy(tray = game.tray.toMutableList().apply { removeAt(fromTray) })
        } else {
            // The letter is in another place, not fixed: it moves here.
            val elsewhere = game.cells.indices.first { it !in game.given && game.cells[it] == letter }
            game.copy(cells = game.cells.toMutableList().also { it[elsewhere] = null })
        }
        return game.copy(
            cells = game.cells.toMutableList().also { it[cell] = letter },
            given = game.given + cell,
            hints = hints + 1,
        ).checked(words)
    }

    fun shuffleTray(random: Random = Random.Default) = copy(tray = tray.shuffled(random))

    private fun checked(words: SquareWords): CrossLettersGame {
        val rows = (0 until size).map(::row)
        val columns = (0 until size).map(::column)
        val done = rows.all { it != null && it in words.words } && columns.all { it != null && it in words.words }
        return if (done) copy(solved = true) else this
    }

    fun toJson(): String = JSONObject()
        .put("size", size)
        .put("solution", JSONArray(solution))
        .put("cells", cells.joinToString("") { (it ?: '.').toString() })
        .put("given", JSONArray(given.toList()))
        .put("tray", tray.joinToString(""))
        .put("difficulty", difficulty.name)
        .put("hints", hints)
        .put("solved", solved)
        .toString()

    companion object {
        fun of(solution: List<String>, places: Set<Int>, difficulty: Difficulty, random: Random = Random.Default): CrossLettersGame {
            val size = solution.size
            val letters = solution.joinToString("")
            val cells = letters.indices.map { if (it in places) letters[it] else null }
            val tray = letters.indices.filter { it !in places }.map { letters[it] }.shuffled(random)
            return CrossLettersGame(size, solution, cells, places, tray, difficulty)
        }

        fun fromJson(json: String): CrossLettersGame {
            val o = JSONObject(json)
            val solution = o.getJSONArray("solution")
            val given = o.getJSONArray("given")
            return CrossLettersGame(
                size = o.getInt("size"),
                solution = List(solution.length()) { solution.getString(it) },
                cells = o.getString("cells").map { if (it == '.') null else it },
                given = List(given.length()) { given.getInt(it) }.toSet(),
                tray = o.getString("tray").toList(),
                difficulty = Difficulty.valueOf(o.getString("difficulty")),
                hints = o.getInt("hints"),
                solved = o.getBoolean("solved"),
            )
        }
    }
}
