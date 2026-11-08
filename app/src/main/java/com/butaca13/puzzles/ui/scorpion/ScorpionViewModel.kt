package com.butaca13.puzzles.ui.scorpion

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.GameRules
import com.butaca13.puzzles.data.GameSession
import com.butaca13.puzzles.game.scorpion.Heap
import com.butaca13.puzzles.game.scorpion.ScorpionGame

class ScorpionViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "scorpion",
            encode = ScorpionGame::toJson,
            decode = ScorpionGame::fromJson,
            isOver = ScorpionGame::isWon,
            isWon = ScorpionGame::isWon,
            variantOf = ScorpionGame::variant,
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
    private val history = ArrayDeque<ScorpionGame>()

    var canUndo by mutableStateOf(false)
        private set

    fun newGame(anyOnEmpty: Boolean) {
        clearHistory()
        session.start(ScorpionGame.deal(anyOnEmpty))
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

    private fun play(action: (ScorpionGame) -> ScorpionGame?) {
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
