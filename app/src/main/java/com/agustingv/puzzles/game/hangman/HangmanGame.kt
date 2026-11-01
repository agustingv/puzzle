package com.agustingv.puzzles.game.hangman

import com.agustingv.puzzles.game.foldAccents
import org.json.JSONObject

enum class HangmanLetterState { UNUSED, CORRECT, INCORRECT }

/** [guessed] holds folded letters: guessing "a" reveals both "a" and "á"; "ñ" is its own letter. */
data class HangmanGame(val word: String, val guessed: List<Char> = emptyList()) {
    private val folded = foldAccents(word)

    val wrongCount: Int = guessed.count { it !in folded }

    /** Character per position, null while it is still hidden. */
    val revealed: List<Char?> = word.indices.map { if (folded[it] in guessed) word[it] else null }

    val isWon: Boolean = revealed.none { it == null }
    val isLost: Boolean = wrongCount >= MAX_WRONG
    val isOver: Boolean = isWon || isLost

    fun guess(letter: Char): HangmanGame =
        if (isOver || letter in guessed) this else copy(guessed = guessed + letter)

    fun letterState(letter: Char): HangmanLetterState = when {
        letter !in guessed -> HangmanLetterState.UNUSED
        letter in folded -> HangmanLetterState.CORRECT
        else -> HangmanLetterState.INCORRECT
    }

    fun toJson(): String = JSONObject().put("word", word).put("guessed", guessed.joinToString("")).toString()

    companion object {
        const val MAX_WRONG = 6

        fun fromJson(json: String): HangmanGame {
            val root = JSONObject(json)
            return HangmanGame(root.getString("word"), root.getString("guessed").toList())
        }
    }
}
