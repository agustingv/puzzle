package com.butaca13.puzzles.ui.bakersdozen

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.GameRules
import com.butaca13.puzzles.data.GameSession
import com.butaca13.puzzles.game.bakersdozen.BakersDozenGame
import com.butaca13.puzzles.game.bakersdozen.Pile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BakersDozenViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "bakersdozen",
            encode = BakersDozenGame::toJson,
            decode = BakersDozenGame::fromJson,
            isOver = BakersDozenGame::isWon,
            isWon = BakersDozenGame::isWon,
            variantOf = BakersDozenGame::variant,
            eventFor = { previous, next ->
                when {
                    next.foundations.sumOf { it.size } > previous.foundations.sumOf { it.size } -> GameEvent.HIT
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
    private val history = ArrayDeque<BakersDozenGame>()

    var canUndo by mutableStateOf(false)
        private set

    /** True while the rest plays itself out: nothing else can be done meanwhile. */
    var completing by mutableStateOf(false)
        private set

    private var completion: Job? = null

    fun newGame(kingsOnEmpty: Boolean) {
        completion?.cancel()
        completing = false
        clearHistory()
        session.start(BakersDozenGame.deal(kingsOnEmpty))
    }

    fun move(from: Pile, index: Int, to: Pile) = play { it.move(from, index, to) }

    /** A tap on a card: to wherever it fits best, if anywhere. */
    fun send(from: Pile, index: Int) = play { game -> game.autoTarget(from, index)?.let { game.move(from, index, it) } }

    fun undo() {
        if (completing) return
        val previous = history.removeLastOrNull() ?: return
        canUndo = history.isNotEmpty()
        session.update(previous)
    }

    /** Plays the rest out one card at a time, so that the player sees them fly home. */
    fun complete() {
        if (completing || session.game?.canAutoComplete != true) return
        completing = true
        completion = viewModelScope.launch {
            while (true) {
                val next = session.game?.autoCompleteStep() ?: break
                session.update(next)
                delay(STEP_MILLIS)
            }
            completing = false
            clearHistory()
        }
    }

    private fun play(action: (BakersDozenGame) -> BakersDozenGame?) {
        if (completing) return
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
        const val STEP_MILLIS = 90L
    }
}
