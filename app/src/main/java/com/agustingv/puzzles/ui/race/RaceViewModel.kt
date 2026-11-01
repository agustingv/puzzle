package com.agustingv.puzzles.ui.race

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.agustingv.puzzles.data.AppStorage
import com.agustingv.puzzles.data.GameRules
import com.agustingv.puzzles.data.GameSession
import com.agustingv.puzzles.game.race.RaceGame
import com.agustingv.puzzles.ui.common.BundledWords

class RaceViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "race",
            encode = RaceGame::toJson,
            decode = RaceGame::fromJson,
            isOver = RaceGame::isFinished,
            isWon = RaceGame::isWon,
            variantOf = { it.length.toString() },
        ),
    )

    var typed by mutableStateOf(session.loadExtra().orEmpty())
        private set

    /** The last submitted answer was wrong; cleared as soon as the player types again. */
    var lastAnswerWrong by mutableStateOf(false)
        private set

    fun newGame(length: Int) {
        session.start(RaceGame.generate(BundledWords.get(getApplication()).getValue(length)))
        typed = ""
        lastAnswerWrong = false
    }

    fun type(letter: Char) {
        val round = session.game?.current ?: return
        if (typed.length < round.word.length) updateTyped(typed + letter)
        lastAnswerWrong = false
    }

    fun delete() {
        if (typed.isNotEmpty()) updateTyped(typed.dropLast(1))
        lastAnswerWrong = false
    }

    fun submit() {
        val game = session.game ?: return
        val next = game.submit(typed)

        lastAnswerWrong = next === game
        session.update(next)
        updateTyped("")
    }

    /** Besides the total time, a race's clock also runs each round's countdown. */
    fun tick() {
        val game = session.game ?: return
        if (game.isFinished) return

        session.tick()

        val next = game.tick()
        if (next.index != game.index) {
            updateTyped("")
            lastAnswerWrong = false
        }
        session.update(next)
    }

    private fun updateTyped(value: String) {
        typed = value
        session.saveExtra(value)
    }
}
