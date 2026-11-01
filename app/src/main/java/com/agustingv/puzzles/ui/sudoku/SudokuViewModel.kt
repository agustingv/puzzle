package com.agustingv.puzzles.ui.sudoku

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agustingv.puzzles.data.AppStorage
import com.agustingv.puzzles.data.GameRules
import com.agustingv.puzzles.data.GameSession
import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.game.sudoku.SudokuGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SudokuViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "sudoku",
            encode = SudokuGame::toJson,
            decode = SudokuGame::fromJson,
            isOver = SudokuGame::isComplete,
            isWon = SudokuGame::isComplete,
            variantOf = { it.difficulty.name },
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
            session.start(withContext(Dispatchers.Default) { SudokuGame.generate(difficulty) })
        }
    }

    fun select(cell: Int) = play { it.select(cell) }

    fun type(digit: Int) = play { it.type(digit) }

    fun clear() = play { it.clear() }

    fun check() = play { it.check() }

    private fun play(action: (SudokuGame) -> SudokuGame) {
        session.game?.let { session.update(action(it)) }
    }
}
