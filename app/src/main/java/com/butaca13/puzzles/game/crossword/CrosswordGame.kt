package com.butaca13.puzzles.game.crossword

import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.game.foldAccents
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

enum class CrosswordDirection { ACROSS, DOWN }

enum class ClueState { PENDING, SOLVED, WRONG }

/** [cells] are board indexes in reading order, from the word's first letter to its last. */
data class CrosswordWord(
    val word: String,
    val cells: List<Int>,
    val direction: CrosswordDirection,
    val number: Int,
    val clue: String,
)

/**
 * [solution] holds one folded letter per cell, [BLOCKED] where there is none. [input] is the same
 * shape, with [EMPTY] in unfilled cells. As on the web, right and wrong words are only revealed by
 * [check], never while typing. [words] are in play order: across by number, then down by number.
 */
data class CrosswordGame(
    val size: Int,
    val difficulty: Difficulty,
    val solution: String,
    val words: List<CrosswordWord>,
    val input: String = solution.map { if (it == BLOCKED) BLOCKED else EMPTY }.joinToString(""),
    val solved: Set<Int> = emptySet(),
    val wrong: Set<Int> = emptySet(),
    val finished: Boolean = false,
    val cursorWord: Int = 0,
    val cursorCell: Int = words.firstOrNull()?.cells?.first() ?: 0,
) {
    val isComplete: Boolean = words.isNotEmpty() && solved.size == words.size
    val isOver: Boolean = finished || isComplete

    /** Number shown in each cell where a word starts. */
    val numbers: Map<Int, Int> = words.associate { it.cells.first() to it.number }

    fun isBlocked(cell: Int): Boolean = solution[cell] == BLOCKED

    fun clueState(index: Int): ClueState = when {
        index in solved -> ClueState.SOLVED
        index in wrong || (isOver) -> ClueState.WRONG
        else -> ClueState.PENDING
    }

    /** Tapping the selected cell again switches between its across and down word. */
    fun tap(cell: Int): CrosswordGame {
        if (isOver || cell !in solution.indices || isBlocked(cell)) return this

        val containing = words.indices.filter { cell in words[it].cells }
        val word = if (cell == cursorCell) {
            containing.firstOrNull { it != cursorWord } ?: cursorWord
        } else {
            containing.firstOrNull { words[it].direction == words[cursorWord].direction } ?: containing.first()
        }

        return copy(cursorWord = word, cursorCell = cell)
    }

    /** Moves to the next or previous word, landing on its first empty cell. */
    fun moveWord(delta: Int): CrosswordGame {
        if (isOver) return this
        return selectWord((cursorWord + delta).mod(words.size))
    }

    fun selectWord(index: Int): CrosswordGame {
        if (isOver) return this
        val cells = words[index].cells
        return copy(cursorWord = index, cursorCell = cells.firstOrNull { input[it] == EMPTY } ?: cells.first())
    }

    fun type(letter: Char): CrosswordGame {
        if (isOver) return this

        val cells = words[cursorWord].cells
        val next = cells.getOrElse(cells.indexOf(cursorCell) + 1) { cursorCell }
        return copy(input = withInput(cursorCell, foldAccents(letter.toString())[0]), cursorCell = next)
    }

    /** Clears the selected cell or, if it is already empty, steps back and clears the previous one. */
    fun delete(): CrosswordGame {
        if (isOver) return this
        if (input[cursorCell] != EMPTY) return copy(input = withInput(cursorCell, EMPTY))

        val cells = words[cursorWord].cells
        val previous = cells.getOrElse(cells.indexOf(cursorCell) - 1) { cursorCell }
        return copy(input = withInput(previous, EMPTY), cursorCell = previous)
    }

    fun check(): CrosswordGame {
        if (isOver) return this

        val right = words.indices.filter { i -> words[i].cells.all { input[it] == solution[it] } }.toSet()
        val filledWrong = words.indices.filter { i ->
            i !in right && words[i].cells.none { input[it] == EMPTY }
        }.toSet()

        return copy(solved = right, wrong = filledWrong)
    }

    /** Locks the current answers in: any word left wrong or incomplete counts as a loss. */
    fun finish(): CrosswordGame = if (isOver) this else check().copy(finished = true)

    private fun withInput(cell: Int, value: Char): String =
        input.replaceRange(cell, cell + 1, value.toString())

    fun toJson(): String = JSONObject()
        .put("size", size)
        .put("difficulty", difficulty.name)
        .put("solution", solution)
        .put("input", input)
        .put("solved", JSONArray(solved.toList()))
        .put("wrong", JSONArray(wrong.toList()))
        .put("finished", finished)
        .put("cursorWord", cursorWord)
        .put("cursorCell", cursorCell)
        .put(
            "words",
            JSONArray(
                words.map {
                    JSONObject()
                        .put("word", it.word)
                        .put("cells", JSONArray(it.cells))
                        .put("direction", it.direction.name)
                        .put("number", it.number)
                        .put("clue", it.clue)
                },
            ),
        )
        .toString()

    companion object {
        const val BLOCKED = '#'
        const val EMPTY = ' '

        private fun settings(difficulty: Difficulty) = when (difficulty) {
            Difficulty.EASY -> 8
            Difficulty.MEDIUM -> 10
            Difficulty.HARD -> 12
            Difficulty.EXPERT -> 15
        }

        fun generate(
            difficulty: Difficulty,
            pool: List<String>,
            clues: Map<String, String>,
            random: Random = Random.Default,
        ): CrosswordGame {
            val size = settings(difficulty)
            val target = size
            val grid = arrayOfNulls<Char>(size * size)
            val placed = mutableListOf<Pair<String, List<Int>>>()

            // Longest first: long words are the hard ones to fit once the board starts to fill up.
            val ordered = pool.shuffled(random).sortedByDescending { it.length }

            for (word in ordered) {
                if (placed.size >= target) break

                val chars = foldAccents(word)
                if (chars.length < 2 || chars.length > size) continue

                val cells = if (placed.isEmpty()) {
                    placeFirst(grid, chars, size)
                } else {
                    placeIntersecting(grid, chars, size, placed.map { it.second }, random)
                }
                if (cells != null) placed += word to cells
            }

            return assemble(size, difficulty, grid, placed, clues)
        }

        private fun assemble(
            size: Int,
            difficulty: Difficulty,
            grid: Array<Char?>,
            placed: List<Pair<String, List<Int>>>,
            clues: Map<String, String>,
        ): CrosswordGame {
            val numbers = computeNumbers(grid, size)
            val words = placed.map { (word, cells) ->
                CrosswordWord(
                    word = word,
                    cells = cells,
                    direction = directionOf(cells),
                    number = numbers.getValue(cells.first()),
                    clue = clues[word] ?: "Palabra de ${word.length} letras",
                )
            }.sortedWith(compareBy({ it.direction }, { it.number }))

            return CrosswordGame(size, difficulty, grid.map { it ?: BLOCKED }.joinToString(""), words)
        }

        private fun directionOf(cells: List<Int>) =
            if (cells[1] - cells[0] == 1) CrosswordDirection.ACROSS else CrosswordDirection.DOWN

        private fun computeNumbers(grid: Array<Char?>, size: Int): Map<Int, Int> {
            val numbers = mutableMapOf<Int, Int>()
            var counter = 1

            for (r in 0 until size) {
                for (c in 0 until size) {
                    val cell = r * size + c
                    if (grid[cell] == null) continue

                    val startsAcross = (c == 0 || grid[cell - 1] == null) && c + 1 < size && grid[cell + 1] != null
                    val startsDown = (r == 0 || grid[cell - size] == null) && r + 1 < size && grid[cell + size] != null

                    if (startsAcross || startsDown) numbers[cell] = counter++
                }
            }

            return numbers
        }

        private fun placeFirst(grid: Array<Char?>, chars: String, size: Int): List<Int> {
            val row = size / 2
            val col = (size - chars.length) / 2

            return chars.indices.map { i ->
                val cell = row * size + col + i
                grid[cell] = chars[i]
                cell
            }
        }

        private fun placeIntersecting(
            grid: Array<Char?>,
            chars: String,
            size: Int,
            placed: List<List<Int>>,
            random: Random,
        ): List<Int>? {
            data class Candidate(val cell: Int, val index: Int, val crossing: CrosswordDirection)

            val candidates = buildList {
                for (cells in placed) {
                    val direction = directionOf(cells)
                    for (cell in cells) {
                        chars.forEachIndexed { i, char ->
                            if (char == grid[cell]) add(Candidate(cell, i, direction))
                        }
                    }
                }
            }.shuffled(random)

            for ((cell, i, crossing) in candidates) {
                val dr = if (crossing == CrosswordDirection.ACROSS) 1 else 0
                val dc = 1 - dr
                val cells = tryPlace(grid, chars, cell / size - dr * i, cell % size - dc * i, dr, dc, size)
                if (cells != null) return cells
            }

            return null
        }

        private fun tryPlace(
            grid: Array<Char?>,
            chars: String,
            row: Int,
            col: Int,
            dr: Int,
            dc: Int,
            size: Int,
        ): List<Int>? {
            fun inBounds(r: Int, c: Int) = r in 0 until size && c in 0 until size
            fun clear(r: Int, c: Int) = !inBounds(r, c) || grid[r * size + c] == null

            val length = chars.length
            if (!clear(row - dr, col - dc) || !clear(row + dr * length, col + dc * length)) return null

            val cells = ArrayList<Int>(length)
            for (i in 0 until length) {
                val r = row + dr * i
                val c = col + dc * i
                if (!inBounds(r, c)) return null

                val existing = grid[r * size + c]
                if (existing != null) {
                    if (existing != chars[i]) return null
                } else if (!clear(r + dc, c + dr) || !clear(r - dc, c - dr)) {
                    return null
                }

                cells += r * size + c
            }

            cells.forEachIndexed { i, cell -> grid[cell] = chars[i] }
            return cells
        }

        fun fromJson(json: String): CrosswordGame {
            val root = JSONObject(json)
            val words = root.getJSONArray("words")

            fun ints(array: JSONArray) = List(array.length()) { array.getInt(it) }

            return CrosswordGame(
                size = root.getInt("size"),
                difficulty = Difficulty.valueOf(root.getString("difficulty")),
                solution = root.getString("solution"),
                words = List(words.length()) { i ->
                    val word = words.getJSONObject(i)
                    CrosswordWord(
                        word = word.getString("word"),
                        cells = ints(word.getJSONArray("cells")),
                        direction = CrosswordDirection.valueOf(word.getString("direction")),
                        number = word.getInt("number"),
                        clue = word.getString("clue"),
                    )
                },
                input = root.getString("input"),
                solved = ints(root.getJSONArray("solved")).toSet(),
                wrong = ints(root.getJSONArray("wrong")).toSet(),
                finished = root.getBoolean("finished"),
                cursorWord = root.getInt("cursorWord"),
                cursorCell = root.getInt("cursorCell"),
            )
        }
    }
}
