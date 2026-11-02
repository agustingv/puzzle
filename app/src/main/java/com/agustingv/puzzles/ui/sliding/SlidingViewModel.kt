package com.agustingv.puzzles.ui.sliding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.agustingv.puzzles.data.AppStorage
import com.agustingv.puzzles.data.GameEvent
import com.agustingv.puzzles.data.GameRules
import com.agustingv.puzzles.data.GameSession
import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.game.sliding.SlidingPuzzleGame

class SlidingViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "sliding",
            encode = SlidingPuzzleGame::toJson,
            decode = SlidingPuzzleGame::fromJson,
            isOver = SlidingPuzzleGame::isSolved,
            isWon = SlidingPuzzleGame::isSolved,
            variantOf = { it.difficulty.name },
            eventFor = { previous, next -> GameEvent.TICK.takeIf { next.moves > previous.moves } },
        ),
    )

    fun newGame(difficulty: Difficulty) = session.start(SlidingPuzzleGame.generate(difficulty))

    fun move(index: Int) {
        session.game?.let { session.update(it.move(index)) }
    }
}
