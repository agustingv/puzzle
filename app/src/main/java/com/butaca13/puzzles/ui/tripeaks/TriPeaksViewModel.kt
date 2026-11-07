package com.butaca13.puzzles.ui.tripeaks

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.GameRules
import com.butaca13.puzzles.data.GameSession
import com.butaca13.puzzles.game.tripeaks.Place
import com.butaca13.puzzles.game.tripeaks.TriPeaksGame

class TriPeaksViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "tripeaks",
            encode = TriPeaksGame::toJson,
            decode = TriPeaksGame::fromJson,
            isOver = TriPeaksGame::isWon,
            isWon = TriPeaksGame::isWon,
            variantOf = TriPeaksGame::variant,
            eventFor = { previous, next ->
                when {
                    next.peaks.count { it == null } > previous.peaks.count { it == null } -> GameEvent.HIT
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
    private val history = ArrayDeque<TriPeaksGame>()

    var canUndo by mutableStateOf(false)
        private set

    fun newGame(wraps: Boolean) {
        clearHistory()
        session.start(TriPeaksGame.deal(wraps))
    }

    fun draw() = play { it.draw() }

    /** A tap on a card, or a card let go on the waste: onto the waste if it fits there. */
    fun playCard(index: Int) {
        val game = session.game ?: return
        if (game.playable(index) != null) play { it.play(index) } else if (game.peaks.getOrNull(index)?.faceUp == true) session.signal(GameEvent.MISS)
    }

    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        canUndo = history.isNotEmpty()
        session.update(previous)
    }

    private fun play(action: (TriPeaksGame) -> TriPeaksGame?) {
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
