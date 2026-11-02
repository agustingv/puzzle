package com.agustingv.puzzles.ui.memorama

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.agustingv.puzzles.data.AppStorage
import com.agustingv.puzzles.data.GameEvent
import com.agustingv.puzzles.data.GameRules
import com.agustingv.puzzles.data.GameSession
import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.game.memorama.MemoramaGame

class MemoramaViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "memorama",
            encode = MemoramaGame::toJson,
            decode = MemoramaGame::fromJson,
            isOver = MemoramaGame::isWon,
            isWon = MemoramaGame::isWon,
            variantOf = { it.difficulty.name },
            eventFor = { previous, next ->
                when {
                    next.matched.size > previous.matched.size -> GameEvent.HIT
                    next.moves > previous.moves -> GameEvent.MISS
                    next.flipped != previous.flipped -> GameEvent.TICK
                    else -> null
                }
            },
        ),
    )

    fun newGame(difficulty: Difficulty) = session.start(MemoramaGame.generate(difficulty))

    fun flip(index: Int) {
        session.game?.let { session.update(it.flip(index)) }
    }
}
