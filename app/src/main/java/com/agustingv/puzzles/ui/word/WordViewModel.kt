package com.agustingv.puzzles.ui.word

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.agustingv.puzzles.game.word.WordGuessGame
import com.agustingv.puzzles.game.word.WordList

class WordViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {

    private val words: Map<Int, List<String>> by lazy {
        WordList.parse(application.assets.open(WordList.ASSET).bufferedReader().use { it.readText() })
    }

    var game by mutableStateOf(restore() ?: pickGame(WordList.LENGTHS.first()))
        private set

    var typed by mutableStateOf(saved[KEY_TYPED] ?: "")
        private set

    var elapsedSeconds by mutableIntStateOf(saved[KEY_ELAPSED] ?: 0)
        private set

    fun newGame(length: Int) {
        update(pickGame(length))
        updateTyped("")
        elapsedSeconds = 0
        saved[KEY_ELAPSED] = 0
    }

    fun type(letter: Char) {
        if (!game.isOver && typed.length < game.length) updateTyped(typed + letter)
    }

    fun delete() {
        if (typed.isNotEmpty()) updateTyped(typed.dropLast(1))
    }

    fun submit() {
        if (game.isOver || typed.length != game.length) return
        update(game.guess(typed))
        updateTyped("")
    }

    fun tick() {
        if (game.isOver) return
        elapsedSeconds++
        saved[KEY_ELAPSED] = elapsedSeconds
    }

    private fun pickGame(length: Int) = WordGuessGame(words.getValue(length).random())

    private fun update(next: WordGuessGame) {
        game = next
        saved[KEY_WORD] = next.word
        saved[KEY_GUESSES] = ArrayList(next.guesses)
    }

    private fun updateTyped(value: String) {
        typed = value
        saved[KEY_TYPED] = value
    }

    private fun restore(): WordGuessGame? {
        val word = saved.get<String>(KEY_WORD) ?: return null
        return WordGuessGame(word, saved.get<ArrayList<String>>(KEY_GUESSES).orEmpty())
    }

    private companion object {
        const val KEY_WORD = "word"
        const val KEY_GUESSES = "guesses"
        const val KEY_TYPED = "typed"
        const val KEY_ELAPSED = "elapsed"
    }
}
