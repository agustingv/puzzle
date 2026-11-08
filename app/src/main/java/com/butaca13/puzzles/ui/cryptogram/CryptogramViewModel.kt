package com.butaca13.puzzles.ui.cryptogram

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.GameRules
import com.butaca13.puzzles.data.GameSession
import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.game.cryptogram.ALPHABET
import com.butaca13.puzzles.game.cryptogram.CryptogramGame

class CryptogramViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "cryptogram",
            encode = CryptogramGame::toJson,
            decode = CryptogramGame::fromJson,
            isOver = CryptogramGame::isWon,
            isWon = CryptogramGame::isWon,
            variantOf = { it.difficulty.name },
            eventFor = { previous, next ->
                when {
                    next.hints > previous.hints -> GameEvent.HIT
                    next.guesses != previous.guesses -> GameEvent.TICK
                    else -> null
                }
            },
        ),
    )

    /** The proverbs, from assets/refranes.txt. */
    private val proverbs: List<String> by lazy {
        getApplication<Application>().assets.open("refranes.txt").bufferedReader().readLines().filter { it.isNotBlank() }
    }

    /** The code letter picked to read, or null. */
    var selected by mutableStateOf<Char?>(null)
        private set

    fun newGame(difficulty: Difficulty) {
        val last = session.game?.phrase
        val phrase = proverbs.filter { it != last }.random()
        val game = CryptogramGame.of(phrase, difficulty)
        session.start(game)
        selected = firstOpen(game, null)
    }

    fun select(codeLetter: Char) {
        val game = session.game ?: return
        if (game.isWon || codeLetter in game.given) return
        selected = codeLetter
    }

    /** The picked code letter read as [letter]; then on to the next one still to read. */
    fun letter(letter: Char) {
        val game = session.game ?: return
        val codeLetter = selected ?: return
        val next = game.guess(codeLetter, letter)
        if (next == game) return
        session.update(next)
        selected = if (next.isWon) null else firstOpen(next, codeLetter)
    }

    fun clear() {
        val game = session.game ?: return
        val codeLetter = selected ?: return
        session.update(game.clear(codeLetter))
    }

    fun hint() {
        val game = session.game?.takeIf { !it.isWon } ?: return
        val next = game.hint(selected)
        session.update(next)
        selected = if (next.isWon) null else firstOpen(next, selected)
    }

    /** The next code letter of the proverb still to read, after [after] in reading order. */
    private fun firstOpen(game: CryptogramGame, after: Char?): Char? {
        val order = game.coded.filter { it in ALPHABET }.toList().distinct()
        val start = after?.let { order.indexOf(it) + 1 } ?: 0
        return (order.drop(start) + order.take(start)).firstOrNull { game.reading(it) == null }
    }
}
