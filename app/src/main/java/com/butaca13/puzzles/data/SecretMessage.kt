package com.butaca13.puzzles.data

import java.text.Normalizer

/** What winning one game for the first time reveals: its lit letter and where it goes in the message. */
data class SecretClue(val gameId: String, val title: String, val letterIndex: Int, val position: Int) {
    val letter: Char = title.filter { it != ' ' }[letterIndex].uppercaseChar()
}

/**
 * The message hidden in plain sight: each game's title lights up one letter, and in this order they
 * spell [ANSWER]. The last letter of a title can not be used: it has its own style, a tile still to
 * be placed, which is what the message refers to.
 */
object SecretMessage {
    private const val ANSWER = "ERA LA ÚLTIMA"

    val clues: List<SecretClue> = listOf(
        Triple("race", "Carrera", 4),
        Triple("garden", "Jardín", 2),
        Triple("hangman", "Ahorcado", 5),
        Triple("sliding", "Puzle Deslizante", 8),
        Triple("memorama", "Memorama", 5),
        Triple("sudoku", "Sudoku", 1),
        Triple("word", "Adivina la Palabra", 11),
        Triple("wordsearch", "Sopa de Letras", 8),
        Triple("path", "Camino", 3),
        Triple("crossword", "Crucigrama", 8),
        Triple("kakuro", "Kakuro", 1),
    ).mapIndexed { index, (id, title, letter) -> SecretClue(id, title, letter, index + 1) }

    /** Spaces, capitals and accents do not matter. */
    fun isAnswer(guess: String): Boolean = normalised(guess) == normalised(ANSWER)

    /** Which letter of this title is lit up, counting only letters. */
    fun letterIndexOf(title: String): Int = clues.firstOrNull { it.title == title }?.letterIndex ?: 0

    internal fun normalised(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .filter { it.isLetter() }
            .uppercase()
}

/** How far the player has got with the message. Kept on the phone, like saved games and records. */
class SecretProgress(private val store: KeyValueStore) {

    val found: List<SecretClue>
        get() {
            val ids = store.get(FOUND_KEY).orEmpty().split(SEPARATOR)
            return SecretMessage.clues.filter { it.gameId in ids }
        }

    val solved: Boolean
        get() = store.get(SOLVED_KEY) != null

    /** The clue this win reveals: only the first win of each game, and only while it can still help. */
    fun recordWin(gameId: String): SecretClue? {
        val clue = SecretMessage.clues.firstOrNull { it.gameId == gameId } ?: return null
        if (solved || clue in found) return null

        store.put(FOUND_KEY, (found + clue).joinToString(SEPARATOR) { it.gameId })
        return clue
    }

    /** Whether the guess was right, in which case the message stays solved. */
    fun solve(guess: String): Boolean {
        val right = SecretMessage.isAnswer(guess)
        if (right) store.put(SOLVED_KEY, "yes")
        return right
    }

    private companion object {
        const val FOUND_KEY = "secret/found"
        const val SOLVED_KEY = "secret/solved"
        const val SEPARATOR = ","
    }
}
