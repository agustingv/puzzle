package com.agustingv.puzzles.ui.path

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.game.path.PathGame

class PathViewModel(private val saved: SavedStateHandle) : ViewModel() {

    var game by mutableStateOf(restore() ?: PathGame.generate(Difficulty.EASY))
        private set

    var elapsedSeconds by mutableIntStateOf(saved[KEY_ELAPSED] ?: 0)
        private set

    fun newGame(difficulty: Difficulty) {
        update(PathGame.generate(difficulty))
        elapsedSeconds = 0
        saved[KEY_ELAPSED] = 0
    }

    /** Finger down: pressing the line cuts it back there, anything else is a normal step. */
    fun press(cell: Int) = update(if (cell in game.path) game.rewindTo(cell) else game.extend(cell))

    fun dragTo(cell: Int) = update(game.extend(cell))

    fun reset() = update(game.reset())

    fun tick() {
        if (game.isComplete) return
        elapsedSeconds++
        saved[KEY_ELAPSED] = elapsedSeconds
    }

    private fun update(next: PathGame) {
        if (next === game && saved.contains(KEY_SIZE)) return

        game = next
        saved[KEY_SIZE] = next.size
        saved[KEY_DIFFICULTY] = next.difficulty.name
        saved[KEY_PATH] = next.path.toIntArray()
        saved[KEY_CHECKPOINT_CELLS] = next.checkpoints.keys.toIntArray()
        saved[KEY_CHECKPOINT_NUMBERS] = next.checkpoints.values.toIntArray()
    }

    private fun restore(): PathGame? {
        val size = saved.get<Int>(KEY_SIZE) ?: return null
        val difficulty = saved.get<String>(KEY_DIFFICULTY) ?: return null
        val cells = saved.get<IntArray>(KEY_CHECKPOINT_CELLS) ?: return null
        val numbers = saved.get<IntArray>(KEY_CHECKPOINT_NUMBERS) ?: return null
        val path = saved.get<IntArray>(KEY_PATH) ?: return null

        return PathGame(size, Difficulty.valueOf(difficulty), cells.zip(numbers.asList()).toMap(), path.toList())
    }

    private companion object {
        const val KEY_SIZE = "size"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_PATH = "path"
        const val KEY_CHECKPOINT_CELLS = "checkpointCells"
        const val KEY_CHECKPOINT_NUMBERS = "checkpointNumbers"
        const val KEY_ELAPSED = "elapsed"
    }
}
