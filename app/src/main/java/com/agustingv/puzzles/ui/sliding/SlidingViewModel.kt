package com.agustingv.puzzles.ui.sliding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.game.sliding.SlidingPuzzleGame

/** Holds the running game; [SavedStateHandle] lets it survive Android killing the process. */
class SlidingViewModel(private val saved: SavedStateHandle) : ViewModel() {

    var game by mutableStateOf(restore() ?: SlidingPuzzleGame.generate(Difficulty.EASY))
        private set

    var elapsedSeconds by mutableIntStateOf(saved[KEY_ELAPSED] ?: 0)
        private set

    fun newGame(difficulty: Difficulty) {
        update(SlidingPuzzleGame.generate(difficulty))
        elapsedSeconds = 0
        saved[KEY_ELAPSED] = 0
    }

    fun move(index: Int) = update(game.move(index))

    /** Called once per second, only while the screen is resumed: backgrounding pauses the clock. */
    fun tick() {
        if (game.isSolved) return
        elapsedSeconds++
        saved[KEY_ELAPSED] = elapsedSeconds
    }

    private fun update(next: SlidingPuzzleGame) {
        game = next
        saved[KEY_TILES] = next.tiles.toIntArray()
        saved[KEY_DIFFICULTY] = next.difficulty.name
        saved[KEY_MOVES] = next.moves
    }

    private fun restore(): SlidingPuzzleGame? {
        val tiles = saved.get<IntArray>(KEY_TILES) ?: return null
        val difficulty = saved.get<String>(KEY_DIFFICULTY) ?: return null

        return SlidingPuzzleGame(tiles.toList(), Difficulty.valueOf(difficulty), saved[KEY_MOVES] ?: 0)
    }

    private companion object {
        const val KEY_TILES = "tiles"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_MOVES = "moves"
        const val KEY_ELAPSED = "elapsed"
    }
}
