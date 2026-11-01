package com.agustingv.puzzles.ui.crossword

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.game.crossword.CrosswordGame
import com.agustingv.puzzles.ui.common.BundledWords

class CrosswordViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {

    /** Null only until the first [newGame], so a game is not generated before it is asked for. */
    var game by mutableStateOf(saved.get<String>(KEY_GAME)?.let(CrosswordGame::fromJson))
        private set

    var elapsedSeconds by mutableIntStateOf(saved[KEY_ELAPSED] ?: 0)
        private set

    fun newGame(difficulty: Difficulty) {
        val context = getApplication<Application>()
        update(CrosswordGame.generate(difficulty, BundledWords.all(context), BundledWords.clues(context)))
        elapsedSeconds = 0
        saved[KEY_ELAPSED] = 0
    }

    fun tap(cell: Int) = play { it.tap(cell) }

    fun type(letter: Char) = play { it.type(letter) }

    fun delete() = play { it.delete() }

    fun moveWord(delta: Int) = play { it.moveWord(delta) }

    fun selectWord(index: Int) = play { it.selectWord(index) }

    fun check() = play { it.check() }

    fun finish() = play { it.finish() }

    fun tick() {
        if (game?.isOver != false) return
        elapsedSeconds++
        saved[KEY_ELAPSED] = elapsedSeconds
    }

    private fun play(action: (CrosswordGame) -> CrosswordGame) {
        game?.let { update(action(it)) }
    }

    private fun update(next: CrosswordGame) {
        game = next
        saved[KEY_GAME] = next.toJson()
    }

    private companion object {
        const val KEY_GAME = "game"
        const val KEY_ELAPSED = "elapsed"
    }
}
