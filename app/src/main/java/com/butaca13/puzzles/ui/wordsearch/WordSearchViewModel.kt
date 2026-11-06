package com.butaca13.puzzles.ui.wordsearch

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.GameRules
import com.butaca13.puzzles.data.GameSession
import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.game.wordsearch.WordSearchGame
import com.butaca13.puzzles.ui.common.BundledWords

class WordSearchViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "wordsearch",
            encode = WordSearchGame::toJson,
            decode = WordSearchGame::fromJson,
            isOver = WordSearchGame::isComplete,
            isWon = WordSearchGame::isComplete,
            variantOf = { it.difficulty.name },
            eventFor = { previous, next -> GameEvent.HIT.takeIf { next.foundCount > previous.foundCount } },
        ),
    )

    /** Live selection while dragging: start and snapped end cell. */
    var dragSelection by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /** The last selection found nothing; cleared when the next one starts. */
    var lastSelectionMissed by mutableStateOf(false)
        private set

    fun newGame(difficulty: Difficulty) {
        session.start(WordSearchGame.generate(difficulty, BundledWords.all(getApplication())))
        dragSelection = null
        lastSelectionMissed = false
    }

    fun tap(cell: Int) {
        val current = session.game ?: return
        if (current.isComplete) return

        val next = current.tap(cell)
        // A second tap on a different cell is a selection; on the same cell it just cancels.
        lastSelectionMissed = current.pendingStart != null && current.pendingStart != cell &&
            next.foundCount == current.foundCount
        session.update(next)
        if (lastSelectionMissed) session.signal(GameEvent.MISS)
    }

    fun startDrag(cell: Int) {
        val current = session.game ?: return
        if (current.isComplete) return
        if (current.pendingStart != null) session.update(current.copy(pendingStart = null))
        lastSelectionMissed = false
        dragSelection = cell to cell
    }

    fun dragTo(row: Int, col: Int) {
        val current = session.game ?: return
        val (start, _) = dragSelection ?: return
        dragSelection = start to WordSearchGame.snapEnd(start, row, col, current.size)
    }

    fun endDrag() {
        val current = session.game ?: return
        val (start, end) = dragSelection ?: return
        dragSelection = null

        val next = current.select(start, end)
        lastSelectionMissed = start != end && next.foundCount == current.foundCount
        session.update(next)
        if (lastSelectionMissed) session.signal(GameEvent.MISS)
    }

    fun cancelDrag() {
        dragSelection = null
    }
}
