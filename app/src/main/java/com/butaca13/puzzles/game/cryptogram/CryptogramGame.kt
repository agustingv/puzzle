package com.butaca13.puzzles.game.cryptogram

import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.game.foldAccents
import org.json.JSONObject
import kotlin.random.Random

const val ALPHABET = "abcdefghijklmnñopqrstuvwxyz"

/**
 * A cryptogram: a proverb with every letter swapped for another, the same one each time, to read back.
 * The player says which letter each code letter stands for; one guess fills every place that code
 * letter has. Accents do not count: the proverb is read plain. Some letters come given, more of them
 * the easier the level, and a hint gives one more.
 */
data class CryptogramGame(
    /** The proverb as written, accents and punctuation and all. */
    val phrase: String,
    /** The code: each plain letter of the alphabet, and the letter it is written as. */
    val code: Map<Char, Char>,
    val difficulty: Difficulty,
    /** The player's reading of each code letter. */
    val guesses: Map<Char, Char> = emptyMap(),
    /** Code letters given: shown read from the start, or by a hint, and not to be changed. */
    val given: Set<Char> = emptySet(),
    val hints: Int = 0,
) {
    /** The proverb plain and lowercase, one character for each of [phrase]'s. */
    val plain: String = foldAccents(phrase)

    /** The proverb in code: letters swapped, the rest as it is. */
    val coded: String = plain.map { code[it] ?: it }.joinToString("")

    /** The code letters the proverb uses, each once. */
    val codeLetters: Set<Char> get() = coded.filter { it in ALPHABET }.toSet()

    private val decode: Map<Char, Char> get() = code.entries.associate { (p, c) -> c to p }

    /** What a code letter reads as now: given, or guessed, or nothing yet. */
    fun reading(codeLetter: Char): Char? = if (codeLetter in given) decode[codeLetter] else guesses[codeLetter]

    val isWon: Boolean get() = codeLetters.all { reading(it) == decode[it] }

    /** How many code letters are still to read. */
    val left: Int get() = codeLetters.count { reading(it) == null }

    /**
     * Reads [codeLetter] as [letter], everywhere it is. A letter can stand for one code letter only:
     * one given elsewhere can not be taken, and one guessed elsewhere moves here.
     */
    fun guess(codeLetter: Char, letter: Char): CryptogramGame {
        if (isWon || codeLetter in given || codeLetter !in codeLetters) return this
        if (given.any { decode[it] == letter }) return this
        val next = guesses.filterValues { it != letter } + (codeLetter to letter)
        return copy(guesses = next)
    }

    fun clear(codeLetter: Char): CryptogramGame = if (isWon) this else copy(guesses = guesses - codeLetter)

    /** Gives [codeLetter]'s reading, or the first one not yet read right when there is none picked. */
    fun hint(codeLetter: Char? = null): CryptogramGame {
        if (isWon) return this
        val target = codeLetter?.takeIf { it in codeLetters && it !in given }
            ?: coded.firstOrNull { it in ALPHABET && reading(it) != decode[it] }
            ?: return this
        val letter = decode.getValue(target)
        return copy(
            given = given + target,
            // A wrong guess of that letter elsewhere goes, so that it reads once only.
            guesses = (guesses - target).filterValues { it != letter },
            hints = hints + 1,
        )
    }

    fun toJson(): String = JSONObject()
        .put("phrase", phrase)
        .put("code", code.entries.joinToString("") { "${it.key}${it.value}" })
        .put("difficulty", difficulty.name)
        .put("guesses", guesses.entries.joinToString("") { "${it.key}${it.value}" })
        .put("given", given.joinToString(""))
        .put("hints", hints)
        .toString()

    companion object {
        /** The share of the proverb's letters given at the start, by level. */
        fun givenShare(difficulty: Difficulty) = when (difficulty) {
            Difficulty.EASY -> 0.35
            Difficulty.MEDIUM -> 0.15
            else -> 0.0
        }

        /** A code where no letter stands for itself. */
        fun randomCode(random: Random): Map<Char, Char> {
            val letters = ALPHABET.toList()
            while (true) {
                val shuffled = letters.shuffled(random)
                if (letters.indices.none { letters[it] == shuffled[it] }) return letters.zip(shuffled).toMap()
            }
        }

        fun of(phrase: String, difficulty: Difficulty, random: Random = Random.Default): CryptogramGame {
            val game = CryptogramGame(phrase, randomCode(random), difficulty)
            val letters = game.codeLetters.toList().shuffled(random)
            // Even the hardest gives one letter, to start from.
            val count = maxOf(1, Math.round(letters.size * givenShare(difficulty)).toInt())
            return game.copy(given = letters.take(count).toSet())
        }

        private fun pairs(text: String): Map<Char, Char> = text.chunked(2).filter { it.length == 2 }.associate { it[0] to it[1] }

        fun fromJson(json: String): CryptogramGame {
            val o = JSONObject(json)
            return CryptogramGame(
                phrase = o.getString("phrase"),
                code = pairs(o.getString("code")),
                difficulty = Difficulty.valueOf(o.getString("difficulty")),
                guesses = pairs(o.getString("guesses")),
                given = o.getString("given").toSet(),
                hints = o.getInt("hints"),
            )
        }
    }
}
