package com.agustingv.puzzles.ui.hangman

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.agustingv.puzzles.game.hangman.HangmanGame
import com.agustingv.puzzles.game.word.WordList
import com.agustingv.puzzles.ui.common.BundledWords

class HangmanViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {

    private val words = BundledWords.get(application)

    var game by mutableStateOf(restore() ?: pickGame(WordList.LENGTHS.first()))
        private set

    var elapsedSeconds by mutableIntStateOf(saved[KEY_ELAPSED] ?: 0)
        private set

    fun newGame(length: Int) {
        update(pickGame(length))
        elapsedSeconds = 0
        saved[KEY_ELAPSED] = 0
    }

    fun guess(letter: Char) = update(game.guess(letter))

    fun tick() {
        if (game.isOver) return
        elapsedSeconds++
        saved[KEY_ELAPSED] = elapsedSeconds
    }

    private fun pickGame(length: Int) = HangmanGame(words.getValue(length).random())

    private fun update(next: HangmanGame) {
        game = next
        saved[KEY_WORD] = next.word
        saved[KEY_GUESSED] = next.guessed.joinToString("")
    }

    private fun restore(): HangmanGame? {
        val word = saved.get<String>(KEY_WORD) ?: return null
        return HangmanGame(word, saved.get<String>(KEY_GUESSED).orEmpty().toList())
    }

    private companion object {
        const val KEY_WORD = "word"
        const val KEY_GUESSED = "guessed"
        const val KEY_ELAPSED = "elapsed"
    }
}
