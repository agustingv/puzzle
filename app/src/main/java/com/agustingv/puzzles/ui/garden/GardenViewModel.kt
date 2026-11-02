package com.agustingv.puzzles.ui.garden

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agustingv.puzzles.data.AppStorage
import com.agustingv.puzzles.data.GameEvent
import com.agustingv.puzzles.data.GameRules
import com.agustingv.puzzles.data.GameSession
import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.game.garden.GardenCell
import com.agustingv.puzzles.game.garden.GardenGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GardenViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "garden",
            encode = GardenGame::toJson,
            decode = GardenGame::fromJson,
            isOver = GardenGame::isComplete,
            isWon = GardenGame::isComplete,
            variantOf = { it.difficulty.name },
            eventFor = { previous, next ->
                val planted = next.flowers.size > previous.flowers.size
                when {
                    planted && (next.conflicts - previous.conflicts).isNotEmpty() -> GameEvent.MISS
                    planted -> GameEvent.HIT
                    next.cells.count { it == GardenCell.MARK } != previous.cells.count { it == GardenCell.MARK } ->
                        GameEvent.TICK
                    else -> null
                }
            },
        ),
    )

    private var generation: Job? = null

    init {
        // Android may have killed the process mid-generation: finish what was asked for.
        if (session.game == null) session.pendingVariant?.let { newGame(Difficulty.valueOf(it)) }
    }

    fun newGame(difficulty: Difficulty) {
        session.startGenerating(difficulty.name)

        generation?.cancel()
        generation = viewModelScope.launch {
            session.start(withContext(Dispatchers.Default) { GardenGame.generate(difficulty) })
        }
    }

    fun tap(cell: Int) = play { it.tap(cell) }

    fun mark(cell: Int) = play { it.mark(cell) }

    fun reset() = play { it.reset() }

    private fun play(action: (GardenGame) -> GardenGame) {
        val current = session.game ?: return
        val next = action(current)
        // A drag fires many times per cell: most events change nothing and need no save.
        if (next !== current) session.update(next)
    }
}
