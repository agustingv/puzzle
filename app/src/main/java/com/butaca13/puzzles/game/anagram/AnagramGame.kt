package com.butaca13.puzzles.game.anagram

import com.butaca13.puzzles.game.foldAccents
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Anagram: a word's letters shuffled, to put back in order. Letters go from the pool into the answer one
 * at a time and come back with a tap; once every letter is placed, the answer wins if it spells the word,
 * or any other word of the list made of the same letters, [accepted]. Accents do not count: the tiles
 * show the plain letters. The clue, the word's definition, can be asked for, and is counted.
 */
data class AnagramGame(
    /** The word as the list has it, with its accents. */
    val word: String,
    /** The plain letters, shuffled: each tile keeps its place here, whether in the pool or the answer. */
    val tiles: List<Char>,
    /** Which tiles are in the answer, in order. */
    val answer: List<Int> = emptyList(),
    /** Every word, plain, that the letters may spell and win: the word itself and its anagrams. */
    val accepted: Set<String> = setOf(foldAccents(word)),
    val clueShown: Boolean = false,
    /** Full answers that spelled no word. */
    val misses: Int = 0,
    val solved: Boolean = false,
) {
    val length: Int get() = tiles.size

    val isWon: Boolean get() = solved

    /** The answer as typed so far. */
    val typed: String get() = answer.map { tiles[it] }.joinToString("")

    /** The tiles still in the pool, by their place in [tiles]. */
    val pool: List<Int> get() = tiles.indices.filter { it !in answer }

    /** Puts tile [index] at the end of the answer; with the last letter in, the answer is checked. */
    fun place(index: Int): AnagramGame {
        if (solved || index !in tiles.indices || index in answer) return this
        val next = copy(answer = answer + index)
        if (next.answer.size < length) return next
        return if (next.typed in accepted) next.copy(solved = true) else next.copy(misses = misses + 1)
    }

    /** Takes the letter at [position] of the answer back to the pool, and those after it. */
    fun takeBack(position: Int): AnagramGame {
        if (solved || position !in answer.indices) return this
        return copy(answer = answer.take(position))
    }

    fun clear(): AnagramGame = if (solved) this else copy(answer = emptyList())

    /** Shuffles the pool's letters among their places, to see them anew. The answer stays. */
    fun shuffle(random: Random = Random.Default): AnagramGame {
        if (solved) return this
        val free = pool
        val letters = free.map { tiles[it] }.shuffled(random)
        val next = tiles.toMutableList()
        free.forEachIndexed { i, place -> next[place] = letters[i] }
        return copy(tiles = next)
    }

    fun showClue(): AnagramGame = copy(clueShown = true)

    fun toJson(): String = JSONObject()
        .put("word", word)
        .put("tiles", tiles.joinToString(""))
        .put("answer", JSONArray(answer))
        .put("accepted", JSONArray(accepted.toList()))
        .put("clue", clueShown)
        .put("misses", misses)
        .put("solved", solved)
        .toString()

    companion object {
        /**
         * A game for [word]: its plain letters shuffled until they spell none of the words that would
         * win, so that the answer is never already there. [words] are the list's words of that length.
         */
        fun of(word: String, words: List<String>, random: Random = Random.Default): AnagramGame {
            val plain = foldAccents(word)
            val key = plain.toList().sorted()
            val accepted = words.map(::foldAccents).filter { it.toList().sorted() == key }.toSet() + plain
            var tiles = plain.toList()
            // A word of one letter repeated has no other order: give up after a few tries.
            repeat(50) {
                tiles = plain.toList().shuffled(random)
                if (tiles.joinToString("") !in accepted) return AnagramGame(word, tiles, accepted = accepted)
            }
            return AnagramGame(word, tiles, accepted = accepted)
        }

        fun fromJson(json: String): AnagramGame {
            val o = JSONObject(json)
            val answer = o.getJSONArray("answer")
            val accepted = o.getJSONArray("accepted")
            return AnagramGame(
                word = o.getString("word"),
                tiles = o.getString("tiles").toList(),
                answer = List(answer.length()) { answer.getInt(it) },
                accepted = List(accepted.length()) { accepted.getString(it) }.toSet(),
                clueShown = o.getBoolean("clue"),
                misses = o.getInt("misses"),
                solved = o.getBoolean("solved"),
            )
        }
    }
}
