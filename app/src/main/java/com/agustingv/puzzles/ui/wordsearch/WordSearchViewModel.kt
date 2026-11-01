package com.agustingv.puzzles.ui.wordsearch

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.game.wordsearch.WordSearchGame
import com.agustingv.puzzles.ui.common.BundledWords

class WordSearchViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {

    /** Null only until the first [newGame], so a game is not generated before it is asked for. */
    var game by mutableStateOf(saved.get<String>(KEY_GAME)?.let(WordSearchGame::fromJson))
        private set

    var elapsedSeconds by mutableIntStateOf(saved[KEY_ELAPSED] ?: 0)
        private set

    /** Live selection while dragging: start and snapped end cell. */
    var dragSelection by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /** The last selection found nothing; cleared when the next one starts. */
    var lastSelectionMissed by mutableStateOf(false)
        private set

    fun newGame(difficulty: Difficulty) {
        update(WordSearchGame.generate(difficulty, BundledWords.all(getApplication())))
        dragSelection = null
        lastSelectionMissed = false
        elapsedSeconds = 0
        saved[KEY_ELAPSED] = 0
    }

    fun tap(cell: Int) {
        val current = game ?: return
        if (current.isComplete) return

        val next = current.tap(cell)
        // A second tap on a different cell is a selection; on the same cell it just cancels.
        lastSelectionMissed = current.pendingStart != null && current.pendingStart != cell &&
            next.foundCount == current.foundCount
        update(next)
    }

    fun startDrag(cell: Int) {
        val current = game ?: return
        if (current.isComplete) return
        if (current.pendingStart != null) update(current.copy(pendingStart = null))
        lastSelectionMissed = false
        dragSelection = cell to cell
    }

    fun dragTo(row: Int, col: Int) {
        val current = game ?: return
        val (start, _) = dragSelection ?: return
        dragSelection = start to WordSearchGame.snapEnd(start, row, col, current.size)
    }

    fun endDrag() {
        val current = game ?: return
        val (start, end) = dragSelection ?: return
        dragSelection = null

        val next = current.select(start, end)
        lastSelectionMissed = start != end && next.foundCount == current.foundCount
        update(next)
    }

    fun cancelDrag() {
        dragSelection = null
    }

    fun tick() {
        if (game?.isComplete != false) return
        elapsedSeconds++
        saved[KEY_ELAPSED] = elapsedSeconds
    }

    private fun update(next: WordSearchGame) {
        game = next
        saved[KEY_GAME] = next.toJson()
    }

    private companion object {
        const val KEY_GAME = "game"
        const val KEY_ELAPSED = "elapsed"
    }
}
