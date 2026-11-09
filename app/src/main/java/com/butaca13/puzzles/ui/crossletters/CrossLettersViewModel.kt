package com.butaca13.puzzles.ui.crossletters

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.GameRules
import com.butaca13.puzzles.data.GameSession
import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.game.crossletters.CrossLettersGame
import com.butaca13.puzzles.game.crossletters.SquareWords
import com.butaca13.puzzles.game.foldAccents
import com.butaca13.puzzles.ui.common.BundledWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CrossLettersViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "crossletters",
            encode = CrossLettersGame::toJson,
            decode = CrossLettersGame::fromJson,
            isOver = CrossLettersGame::isWon,
            isWon = CrossLettersGame::isWon,
            variantOf = { it.difficulty.name },
            eventFor = { previous, next ->
                when {
                    next.hints > previous.hints -> GameEvent.HIT
                    next.cells != previous.cells -> GameEvent.TICK
                    else -> null
                }
            },
        ),
    )

    /** The words: ladder.txt's four and five letter words, and the app's common words. */
    private val words: SquareWords by lazy {
        val app = getApplication<Application>()
        val all = app.assets.open("ladder.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        SquareWords(all, BundledWords.all(app).map(::foldAccents).filter { it.length in 4..5 })
    }

    /** The place picked to put a letter in, or -1. */
    var selected by mutableIntStateOf(-1)
        private set

    init {
        if (session.game == null) session.pendingVariant?.let { newGame(Difficulty.valueOf(it)) }
    }

    fun newGame(difficulty: Difficulty) {
        selected = -1
        session.startGenerating(difficulty.name)
        viewModelScope.launch {
            val game = withContext(Dispatchers.Default) { checkNotNull(words.puzzle(difficulty)) { "no square" } }
            session.start(game)
            selected = nextEmpty(game, -1)
        }
    }

    /** A tap on a place: picks it if empty, else sends its letter back and picks it. */
    fun tapCell(cell: Int) {
        val game = session.game?.takeIf { !it.isWon } ?: return
        if (cell in game.given) return
        if (game.cells[cell] != null) session.update(game.takeBack(cell))
        selected = cell
    }

    /** A tap on a tray letter: into the picked place, then on to the next empty one. */
    fun tapTray(index: Int) {
        val game = session.game?.takeIf { !it.isWon } ?: return
        val cell = selected.takeIf { it >= 0 && it !in game.given } ?: nextEmpty(game, -1)
        if (cell < 0) return
        val next = game.place(index, cell, words)
        session.update(next)
        selected = if (next.isWon) -1 else nextEmpty(next, cell)
    }

    fun hint() {
        val game = session.game?.takeIf { !it.isWon } ?: return
        val cell = selected.takeIf { it >= 0 && it !in game.given } ?: nextEmpty(game, -1).takeIf { it >= 0 }
            ?: game.cells.indices.firstOrNull { it !in game.given && game.cells[it] != game.solution[it / game.size][it % game.size] }
            ?: return
        val next = game.hint(cell, words)
        session.update(next)
        selected = if (next.isWon) -1 else nextEmpty(next, cell)
    }

    fun shuffle() {
        session.game?.let { session.update(it.shuffleTray()) }
    }

    /** The first empty place after [after], going round. */
    private fun nextEmpty(game: CrossLettersGame, after: Int): Int {
        val n = game.cells.size
        return (1..n).map { (after + it).mod(n) }.firstOrNull { game.cells[it] == null } ?: -1
    }
}
