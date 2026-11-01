package com.agustingv.puzzles.ui.memorama

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.game.memorama.MemoramaGame

class MemoramaViewModel(private val saved: SavedStateHandle) : ViewModel() {

    /** Null only until the first [newGame]. */
    var game by mutableStateOf(saved.get<String>(KEY_GAME)?.let(MemoramaGame::fromJson))
        private set

    var elapsedSeconds by mutableIntStateOf(saved[KEY_ELAPSED] ?: 0)
        private set

    fun newGame(difficulty: Difficulty) {
        update(MemoramaGame.generate(difficulty))
        elapsedSeconds = 0
        saved[KEY_ELAPSED] = 0
    }

    fun flip(index: Int) {
        game?.let { update(it.flip(index)) }
    }

    fun tick() {
        if (game?.isWon != false) return
        elapsedSeconds++
        saved[KEY_ELAPSED] = elapsedSeconds
    }

    private fun update(next: MemoramaGame) {
        game = next
        saved[KEY_GAME] = next.toJson()
    }

    private companion object {
        const val KEY_GAME = "game"
        const val KEY_ELAPSED = "elapsed"
    }
}
