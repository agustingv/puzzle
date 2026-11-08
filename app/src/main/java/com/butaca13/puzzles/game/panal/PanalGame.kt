package com.butaca13.puzzles.game.panal

import com.butaca13.puzzles.game.Difficulty
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private const val ALPHABET = "abcdefghijklmnñopqrstuvwxyz"

/** A word's letters as bits, one per letter of the alphabet, whatever their order or repeats. */
fun letterMask(word: String): Int = word.fold(0) { mask, c ->
    val i = ALPHABET.indexOf(c)
    if (i < 0) mask else mask or (1 shl i)
}

/**
 * The words the honeycomb accepts, plain and without accents, each with its letters as a mask, and the
 * app's [common] words, which puzzles are made from.
 */
class PanalWords(words: Collection<String>, common: Collection<String>) {
    private val all: List<String> = words.filter { it.length >= MIN_LENGTH }
    private val masks: IntArray = IntArray(all.size) { letterMask(all[it]) }
    private val common: List<String> = common.filter { it.length >= 7 }

    /** Every word made of [letters] alone that has [center] in it. */
    fun answers(letters: String, center: Char): List<String> {
        val allowed = letterMask(letters)
        val needed = letterMask(center.toString())
        return all.indices.filter { masks[it] and allowed.inv() == 0 && masks[it] and needed != 0 }.map { all[it] }
    }

    /**
     * A puzzle: the seven letters of a common word with seven different ones, a pangram, and one of them
     * at the centre. Without an S, which would let in a plural for every word, and with neither too few
     * words to find nor too many to read.
     */
    fun puzzle(difficulty: Difficulty, random: Random = Random.Default): PanalGame? {
        val pangrams = common.filter { it.toSet().size == LETTERS && 's' !in it }.shuffled(random)
        for (word in pangrams.take(200)) {
            val letters = word.toSet().joinToString("")
            for (center in letters.toList().shuffled(random)) {
                val answers = answers(letters, center)
                if (answers.size in 30..180) return PanalGame.of(letters, center, answers, difficulty, random)
            }
        }
        return null
    }

    companion object {
        const val MIN_LENGTH = 4
        const val LETTERS = 7
    }
}

/**
 * The honeycomb: seven letters, one of them at the centre, to make words of at least four letters with,
 * each of them having the centre letter, any letter used again and again. A four-letter word scores one,
 * a longer one as many as its letters, and one with all seven, a pangram, seven more. The level sets
 * the share of all the points to reach, [target]: the list is long, and has rare words few would know.
 */
data class PanalGame(
    val center: Char,
    /** The six letters round the centre, in the order they are shown. */
    val outer: List<Char>,
    /** Every word that counts, plain. */
    val answers: Set<String>,
    val target: Int,
    val difficulty: Difficulty,
    /** The words found, in the order found. */
    val found: List<String> = emptyList(),
    val misses: Int = 0,
) {
    val letters: Set<Char> get() = outer.toSet() + center

    val score: Int get() = found.sumOf(::points)

    val total: Int get() = answers.sumOf(::points)

    val isWon: Boolean get() = score >= target

    fun isPangram(word: String) = word.toSet() == letters

    fun points(word: String): Int = when {
        word.length == 4 -> 1
        else -> word.length
    } + if (isPangram(word)) 7 else 0

    /** Why a word does not count, or null when it does. */
    fun check(word: String): Rejection? = when {
        word.length < PanalWords.MIN_LENGTH -> Rejection.TOO_SHORT
        word.any { it !in letters } -> Rejection.WRONG_LETTER
        center !in word -> Rejection.NO_CENTER
        word in found -> Rejection.ALREADY_FOUND
        word !in answers -> Rejection.NOT_A_WORD
        else -> null
    }

    /** The word sent: found if it counts, else a miss. */
    fun submit(word: String): PanalGame {
        if (isWon) return this
        return if (check(word) == null) copy(found = found + word) else copy(misses = misses + 1)
    }

    /** The six outer letters in a new order, to see them anew. */
    fun shuffle(random: Random = Random.Default): PanalGame = copy(outer = outer.shuffled(random))

    fun toJson(): String = JSONObject()
        .put("center", center.toString())
        .put("outer", outer.joinToString(""))
        .put("answers", JSONArray(answers.toList()))
        .put("target", target)
        .put("difficulty", difficulty.name)
        .put("found", JSONArray(found))
        .put("misses", misses)
        .toString()

    enum class Rejection { TOO_SHORT, WRONG_LETTER, NO_CENTER, ALREADY_FOUND, NOT_A_WORD }

    companion object {
        /** The share of all the points each level asks for. */
        fun share(difficulty: Difficulty) = when (difficulty) {
            Difficulty.EASY -> 0.06
            Difficulty.MEDIUM -> 0.12
            else -> 0.22
        }

        fun of(letters: String, center: Char, answers: List<String>, difficulty: Difficulty, random: Random = Random.Default): PanalGame {
            val outer = letters.filter { it != center }.toList().shuffled(random)
            val game = PanalGame(center, outer, answers.toSet(), 0, difficulty)
            return game.copy(target = maxOf(1, Math.round(game.total * share(difficulty)).toInt()))
        }

        fun fromJson(json: String): PanalGame {
            val o = JSONObject(json)
            val answers = o.getJSONArray("answers")
            val found = o.getJSONArray("found")
            return PanalGame(
                center = o.getString("center")[0],
                outer = o.getString("outer").toList(),
                answers = List(answers.length()) { answers.getString(it) }.toSet(),
                target = o.getInt("target"),
                difficulty = Difficulty.valueOf(o.getString("difficulty")),
                found = List(found.length()) { found.getString(it) },
                misses = o.getInt("misses"),
            )
        }
    }
}
