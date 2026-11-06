package com.butaca13.puzzles.ui.hangman

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.GameRules
import com.butaca13.puzzles.data.GameSession
import com.butaca13.puzzles.game.hangman.HangmanGame
import com.butaca13.puzzles.ui.common.BundledWords

class HangmanViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "hangman",
            encode = HangmanGame::toJson,
            decode = HangmanGame::fromJson,
            isOver = HangmanGame::isOver,
            isWon = HangmanGame::isWon,
            variantOf = { it.word.length.toString() },
            eventFor = { previous, next ->
                when {
                    next.guessed.size == previous.guessed.size -> null
                    next.wrongCount > previous.wrongCount -> GameEvent.MISS
                    else -> GameEvent.HIT
                }
            },
        ),
    )

    fun newGame(length: Int) =
        session.start(HangmanGame(BundledWords.get(getApplication()).getValue(length).random()))

    fun guess(letter: Char) {
        session.game?.let { session.update(it.guess(letter)) }
    }
}
