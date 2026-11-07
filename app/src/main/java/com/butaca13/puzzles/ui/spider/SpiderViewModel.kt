package com.butaca13.puzzles.ui.spider

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.GameRules
import com.butaca13.puzzles.data.GameSession
import com.butaca13.puzzles.game.spider.Heap
import com.butaca13.puzzles.game.spider.SpiderGame

class SpiderViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "spider",
            encode = SpiderGame::toJson,
            decode = SpiderGame::fromJson,
            isOver = SpiderGame::isWon,
            isWon = SpiderGame::isWon,
            variantOf = SpiderGame::variant,
            eventFor = { previous, next ->
                when {
                    next.done.size > previous.done.size -> GameEvent.HIT
                    next.moves != previous.moves -> GameEvent.TICK
                    else -> null
                }
            },
        ),
    )

    /**
     * The games before each move, to undo them. Kept in memory only: after the app is closed, the
     * game comes back as it was left, with nothing to undo.
     */
    private val history = ArrayDeque<SpiderGame>()

    var canUndo by mutableStateOf(false)
        private set

    fun newGame(suits: Int) {
        clearHistory()
        session.start(SpiderGame.deal(suits))
    }

    fun deal() = play { it.deal() }

    fun move(from: Heap, index: Int, to: Heap) = play { it.move(from, index, to) }

    /** A tap on a card: to wherever it fits best, if anywhere. */
    fun send(from: Heap, index: Int) = play { game -> game.autoTarget(from, index)?.let { game.move(from, index, it) } }

    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        canUndo = history.isNotEmpty()
        session.update(previous)
    }

    private fun play(action: (SpiderGame) -> SpiderGame?) {
        val current = session.game?.takeIf { !it.isWon } ?: return
        val next = action(current) ?: return
        history.addLast(current)
        if (history.size > MAX_UNDO) history.removeFirst()
        canUndo = !next.isWon
        session.update(next)
        if (next.isWon) clearHistory()
    }

    private fun clearHistory() {
        history.clear()
        canUndo = false
    }

    private companion object {
        const val MAX_UNDO = 500
    }
}
