package com.butaca13.puzzles.ui.crossword

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.GameRules
import com.butaca13.puzzles.data.GameSession
import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.game.crossword.CrosswordGame
import com.butaca13.puzzles.ui.common.BundledWords

class CrosswordViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "crossword",
            encode = CrosswordGame::toJson,
            decode = CrosswordGame::fromJson,
            isOver = CrosswordGame::isOver,
            isWon = CrosswordGame::isComplete,
            variantOf = { it.difficulty.name },
            eventFor = { previous, next ->
                when {
                    (next.wrong - previous.wrong).isNotEmpty() -> GameEvent.MISS
                    (next.solved - previous.solved).isNotEmpty() -> GameEvent.HIT
                    else -> null
                }
            },
        ),
    )

    fun newGame(difficulty: Difficulty) {
        val context = getApplication<Application>()
        session.start(CrosswordGame.generate(difficulty, BundledWords.all(context), BundledWords.clues(context)))
    }

    fun tap(cell: Int) = play { it.tap(cell) }

    fun type(letter: Char) = play { it.type(letter) }

    fun delete() = play { it.delete() }

    fun moveWord(delta: Int) = play { it.moveWord(delta) }

    fun selectWord(index: Int) = play { it.selectWord(index) }

    fun check() = play { it.check() }

    fun finish() = play { it.finish() }

    private fun play(action: (CrosswordGame) -> CrosswordGame) {
        session.game?.let { session.update(action(it)) }
    }
}
