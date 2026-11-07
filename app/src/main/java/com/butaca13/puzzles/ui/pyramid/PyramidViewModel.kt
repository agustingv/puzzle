package com.butaca13.puzzles.ui.pyramid

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.GameRules
import com.butaca13.puzzles.data.GameSession
import com.butaca13.puzzles.game.pyramid.PyramidGame
import com.butaca13.puzzles.game.pyramid.Spot

class PyramidViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "pyramid",
            encode = PyramidGame::toJson,
            decode = PyramidGame::fromJson,
            isOver = PyramidGame::isWon,
            isWon = PyramidGame::isWon,
            variantOf = PyramidGame::variant,
            eventFor = { previous, next ->
                when {
                    next.discard.size > previous.discard.size -> GameEvent.HIT
                    next.moves != previous.moves -> GameEvent.TICK
                    else -> null
                }
            },
        ),
    )

    /** The card picked first, waiting for the one to pair it with. */
    var selected by mutableStateOf<Spot?>(null)
        private set

    /**
     * The games before each move, to undo them. Kept in memory only: after the app is closed, the
     * game comes back as it was left, with nothing to undo.
     */
    private val history = ArrayDeque<PyramidGame>()

    var canUndo by mutableStateOf(false)
        private set

    fun newGame(passes: Int) {
        clearHistory()
        selected = null
        session.start(PyramidGame.deal(passes))
    }

    fun draw() {
        selected = null
        play { it.draw() }
    }

    /**
     * A tap on a card: a king goes at once; otherwise the first card is picked, and the second one is
     * paired with it if they add up to 13, or picked instead if they do not.
     */
    fun tap(spot: Spot) {
        val game = session.game?.takeIf { !it.isWon } ?: return
        val card = game.playable(spot) ?: return
        val first = selected
        when {
            card.rank == 13 -> {
                selected = null
                play { it.remove(spot) }
            }
            first == null -> selected = spot
            first == spot -> selected = null
            else -> {
                val paired = game.pair(first, spot)
                if (paired != null) {
                    selected = null
                    play { paired }
                } else {
                    selected = spot
                    session.signal(GameEvent.MISS)
                }
            }
        }
    }

    /** One card let go on another: the two are taken away if they add up to 13. */
    fun drop(from: Spot, to: Spot) {
        selected = null
        val game = session.game ?: return
        if (game.pair(from, to) != null) play { it.pair(from, to) } else session.signal(GameEvent.MISS)
    }

    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        selected = null
        canUndo = history.isNotEmpty()
        session.update(previous)
    }

    private fun play(action: (PyramidGame) -> PyramidGame?) {
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
