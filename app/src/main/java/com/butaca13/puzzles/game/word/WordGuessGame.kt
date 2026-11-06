package com.butaca13.puzzles.game.word

import com.butaca13.puzzles.game.foldAccents
import org.json.JSONArray
import org.json.JSONObject

enum class LetterState { CORRECT, PRESENT, ABSENT }

data class GuessedLetter(val char: Char, val state: LetterState)

/** Guesses are compared ignoring accents, so typing "u" matches "ú" or "ü"; "ñ" stays distinct. */
data class WordGuessGame(val word: String, val guesses: List<String> = emptyList()) {
    val length: Int = word.length
    val isWon: Boolean = guesses.any { fold(it) == fold(word) }
    val isLost: Boolean = !isWon && guesses.size >= MAX_ATTEMPTS
    val isOver: Boolean = isWon || isLost

    fun guess(guess: String): WordGuessGame =
        if (isOver || guess.length != length) this else copy(guesses = guesses + guess)

    fun feedback(guess: String): List<GuessedLetter> {
        val target = fold(word)
        val folded = fold(guess)
        val states = MutableList(length) { LetterState.ABSENT }
        val remaining = mutableMapOf<Char, Int>()

        // First pass: exact matches, tallying the target letters left over.
        for (i in 0 until length) {
            if (folded[i] == target[i]) {
                states[i] = LetterState.CORRECT
            } else {
                remaining[target[i]] = (remaining[target[i]] ?: 0) + 1
            }
        }

        // Second pass: wrong-position matches, consuming leftover letters from left to right.
        for (i in 0 until length) {
            val left = remaining[folded[i]] ?: 0
            if (states[i] != LetterState.CORRECT && left > 0) {
                states[i] = LetterState.PRESENT
                remaining[folded[i]] = left - 1
            }
        }

        return List(length) { GuessedLetter(guess[it], states[it]) }
    }

    /** Best state seen so far per (folded) letter, to colour the keyboard. */
    fun keyboardStates(): Map<Char, LetterState> {
        val best = mutableMapOf<Char, LetterState>()

        for (guess in guesses) {
            for (letter in feedback(guess)) {
                val key = fold(letter.char.toString())[0]
                val current = best[key]
                if (current == null || letter.state < current) best[key] = letter.state
            }
        }

        return best
    }

    fun toJson(): String = JSONObject().put("word", word).put("guesses", JSONArray(guesses)).toString()

    companion object {
        const val MAX_ATTEMPTS = 6

        fun fromJson(json: String): WordGuessGame {
            val root = JSONObject(json)
            val guesses = root.getJSONArray("guesses")

            return WordGuessGame(root.getString("word"), List(guesses.length()) { guesses.getString(it) })
        }

        private fun fold(text: String) = foldAccents(text)
    }
}
