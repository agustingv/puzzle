package com.agustingv.puzzles.ui.race

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.agustingv.puzzles.game.race.RaceGame
import com.agustingv.puzzles.game.race.RaceResult
import com.agustingv.puzzles.game.race.RaceRound
import com.agustingv.puzzles.game.word.WordList
import com.agustingv.puzzles.ui.common.BundledWords

class RaceViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {

    private val words = BundledWords.get(application)

    var game by mutableStateOf(restore() ?: pickGame(WordList.LENGTHS.first()))
        private set

    var typed by mutableStateOf(saved[KEY_TYPED] ?: "")
        private set

    /** The last submitted answer was wrong; cleared as soon as the player types again. */
    var lastAnswerWrong by mutableStateOf(false)
        private set

    var elapsedSeconds by mutableIntStateOf(saved[KEY_ELAPSED] ?: 0)
        private set

    fun newGame(length: Int) {
        update(pickGame(length))
        updateTyped("")
        lastAnswerWrong = false
        elapsedSeconds = 0
        saved[KEY_ELAPSED] = 0
    }

    fun type(letter: Char) {
        val round = game.current ?: return
        if (typed.length < round.word.length) updateTyped(typed + letter)
        lastAnswerWrong = false
    }

    fun delete() {
        if (typed.isNotEmpty()) updateTyped(typed.dropLast(1))
        lastAnswerWrong = false
    }

    fun submit() {
        val next = game.submit(typed)
        lastAnswerWrong = next === game
        update(next)
        updateTyped("")
    }

    fun tick() {
        if (game.isFinished) return
        elapsedSeconds++
        saved[KEY_ELAPSED] = elapsedSeconds

        val next = game.tick()
        if (next.index != game.index) {
            updateTyped("")
            lastAnswerWrong = false
        }
        update(next)
    }

    private fun pickGame(length: Int) = RaceGame.generate(words.getValue(length))

    private fun update(next: RaceGame) {
        game = next
        saved[KEY_WORDS] = ArrayList(next.rounds.map { it.word })
        saved[KEY_SCRAMBLED] = ArrayList(next.rounds.map { it.scrambled })
        saved[KEY_RESULTS] = ArrayList(next.results.map { it.name })
        saved[KEY_SECONDS_LEFT] = next.secondsLeft
    }

    private fun updateTyped(value: String) {
        typed = value
        saved[KEY_TYPED] = value
    }

    private fun restore(): RaceGame? {
        val words = saved.get<ArrayList<String>>(KEY_WORDS) ?: return null
        val scrambled = saved.get<ArrayList<String>>(KEY_SCRAMBLED) ?: return null
        val results = saved.get<ArrayList<String>>(KEY_RESULTS).orEmpty().map(RaceResult::valueOf)
        val secondsLeft = saved.get<Int>(KEY_SECONDS_LEFT) ?: RaceGame.TIME_LIMIT

        return RaceGame(words.zip(scrambled, ::RaceRound), results, secondsLeft)
    }

    private companion object {
        const val KEY_WORDS = "words"
        const val KEY_SCRAMBLED = "scrambled"
        const val KEY_RESULTS = "results"
        const val KEY_SECONDS_LEFT = "secondsLeft"
        const val KEY_TYPED = "typed"
        const val KEY_ELAPSED = "elapsed"
    }
}
