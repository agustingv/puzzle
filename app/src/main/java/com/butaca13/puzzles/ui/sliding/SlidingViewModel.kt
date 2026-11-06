package com.butaca13.puzzles.ui.sliding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.GameRules
import com.butaca13.puzzles.data.GameSession
import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.game.sliding.SlidingPuzzleGame

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
