package com.butaca13.puzzles.ui.path

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.GameRules
import com.butaca13.puzzles.data.GameSession
import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.game.path.PathGame

class PathViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "path",
            encode = PathGame::toJson,
            decode = PathGame::fromJson,
            isOver = PathGame::isComplete,
            isWon = PathGame::isComplete,
            variantOf = { it.difficulty.name },
            eventFor = { previous, next ->
                when {
                    next.path.size <= previous.path.size -> null
                    next.path.last() in next.checkpoints -> GameEvent.HIT
                    else -> GameEvent.TICK
                }
            },
        ),
    )

    fun newGame(difficulty: Difficulty) = session.start(PathGame.generate(difficulty))

    /** Finger down: pressing the line cuts it back there, anything else is a normal step. */
    fun press(cell: Int) = play { if (cell in it.path) it.rewindTo(cell) else it.extend(cell) }

    fun dragTo(cell: Int) = play { it.extend(cell) }

    fun reset() = play { it.reset() }

    private fun play(action: (PathGame) -> PathGame) {
        val current = session.game ?: return
        val next = action(current)
        // A drag fires many times per cell: most events change nothing and need no save.
        if (next !== current) session.update(next)
    }
}
