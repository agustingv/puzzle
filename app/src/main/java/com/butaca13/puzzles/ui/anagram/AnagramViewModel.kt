package com.butaca13.puzzles.ui.anagram

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.GameRules
import com.butaca13.puzzles.data.GameSession
import com.butaca13.puzzles.game.anagram.AnagramGame
import com.butaca13.puzzles.ui.common.BundledWords

class AnagramViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "anagram",
            encode = AnagramGame::toJson,
            decode = AnagramGame::fromJson,
            isOver = AnagramGame::isWon,
            isWon = AnagramGame::isWon,
            variantOf = { it.length.toString() },
            eventFor = { previous, next ->
                when {
                    next.misses > previous.misses -> GameEvent.MISS
                    next.answer.size > previous.answer.size -> GameEvent.TICK
                    else -> null
                }
            },
        ),
    )

    /** The word's definition, from the crossword's clues; null for the few words without one. */
    val clue: String?
        get() = session.game?.let { BundledWords.clues(getApplication())[it.word] }

    fun newGame(length: Int) {
        val words = BundledWords.get(getApplication()).getValue(length)
        // Only words with a clue: the clue is half the game when the letters will not come.
        val clues = BundledWords.clues(getApplication())
        val word = words.filter { it in clues }.ifEmpty { words }.random()
        session.start(AnagramGame.of(word, words))
    }

    fun place(index: Int) = update { it.place(index) }

    fun takeBack(position: Int) = update { it.takeBack(position) }

    fun clear() = update { it.clear() }

    fun shuffle() = update { it.shuffle() }

    fun showClue() = update { it.showClue() }

    private fun update(action: (AnagramGame) -> AnagramGame) {
        val game = session.game?.takeIf { !it.isWon } ?: return
        val next = action(game)
        if (next != game) session.update(next)
    }
}
