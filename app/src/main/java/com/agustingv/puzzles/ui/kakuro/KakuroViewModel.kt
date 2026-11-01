package com.agustingv.puzzles.ui.kakuro

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.game.kakuro.KakuroGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KakuroViewModel(private val saved: SavedStateHandle) : ViewModel() {

    /** Null while the puzzle is being generated. */
    var game by mutableStateOf(saved.get<String>(KEY_GAME)?.let(KakuroGame::fromJson))
        private set

    var elapsedSeconds by mutableIntStateOf(saved[KEY_ELAPSED] ?: 0)
        private set

    private var generation: Job? = null

    init {
        // Android may have killed the process mid-generation: finish what was asked for.
        if (game == null) saved.get<String>(KEY_PENDING)?.let { newGame(Difficulty.valueOf(it)) }
    }

    fun newGame(difficulty: Difficulty) {
        game = null
        saved.remove<String>(KEY_GAME)
        saved[KEY_PENDING] = difficulty.name
        elapsedSeconds = 0
        saved[KEY_ELAPSED] = 0

        generation?.cancel()
        generation = viewModelScope.launch {
            update(withContext(Dispatchers.Default) { KakuroGame.generate(difficulty) })
        }
    }

    fun select(cell: Int) = play { it.select(cell) }

    fun type(digit: Int) = play { it.type(digit) }

    fun clear() = play { it.clear() }

    fun check() = play { it.check() }

    fun tick() {
        if (game?.isComplete != false) return
        elapsedSeconds++
        saved[KEY_ELAPSED] = elapsedSeconds
    }

    private fun play(action: (KakuroGame) -> KakuroGame) {
        game?.let { update(action(it)) }
    }

    private fun update(next: KakuroGame) {
        game = next
        saved[KEY_GAME] = next.toJson()
    }

    private companion object {
        const val KEY_GAME = "game"
        const val KEY_PENDING = "pending"
        const val KEY_ELAPSED = "elapsed"
    }
}
