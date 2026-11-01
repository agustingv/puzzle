package com.agustingv.puzzles.ui.word

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.agustingv.puzzles.data.AppStorage
import com.agustingv.puzzles.data.GameRules
import com.agustingv.puzzles.data.GameSession
import com.agustingv.puzzles.game.word.WordGuessGame
import com.agustingv.puzzles.ui.common.BundledWords

class WordViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "word",
            encode = WordGuessGame::toJson,
            decode = WordGuessGame::fromJson,
            isOver = WordGuessGame::isOver,
            isWon = WordGuessGame::isWon,
            variantOf = { it.length.toString() },
        ),
    )

    var typed by mutableStateOf(session.loadExtra().orEmpty())
        private set

    fun newGame(length: Int) {
        session.start(WordGuessGame(BundledWords.get(getApplication()).getValue(length).random()))
        typed = ""
    }

    fun type(letter: Char) {
        val game = session.game ?: return
        if (!game.isOver && typed.length < game.length) updateTyped(typed + letter)
    }

    fun delete() {
        if (typed.isNotEmpty()) updateTyped(typed.dropLast(1))
    }

    fun submit() {
        val game = session.game ?: return
        if (game.isOver || typed.length != game.length) return

        session.update(game.guess(typed))
        updateTyped("")
    }

    private fun updateTyped(value: String) {
        typed = value
        session.saveExtra(value)
    }
}
