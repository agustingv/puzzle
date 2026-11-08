package com.butaca13.puzzles.ui.panal

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.GameRules
import com.butaca13.puzzles.data.GameSession
import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.game.foldAccents
import com.butaca13.puzzles.game.panal.PanalGame
import com.butaca13.puzzles.game.panal.PanalWords
import com.butaca13.puzzles.ui.common.BundledWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PanalViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "panal",
            encode = PanalGame::toJson,
            decode = PanalGame::fromJson,
            isOver = PanalGame::isWon,
            isWon = PanalGame::isWon,
            variantOf = { it.difficulty.name },
            eventFor = { previous, next ->
                when {
                    next.misses > previous.misses -> GameEvent.MISS
                    next.found.size > previous.found.size -> GameEvent.HIT
                    else -> null
                }
            },
        ),
    )

    /** The words, loaded once, off the main thread: some eighty thousand of them. */
    private val words: PanalWords by lazy {
        val app = getApplication<Application>()
        val all = app.assets.open("panal.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        PanalWords(all, BundledWords.all(app).map(::foldAccents))
    }

    /** The word being typed, kept with the game so that it survives the app being closed. */
    var typed by mutableStateOf(session.loadExtra().orEmpty())
        private set

    /** What the last word sent got: found, or why not. Cleared as the next one is typed. */
    var feedback by mutableStateOf<Feedback?>(null)
        private set

    sealed interface Feedback {
        data class Found(val word: String, val points: Int, val pangram: Boolean) : Feedback
        data class Rejected(val reason: PanalGame.Rejection) : Feedback
    }

    init {
        if (session.game == null) session.pendingVariant?.let { newGame(Difficulty.valueOf(it)) }
    }

    fun newGame(difficulty: Difficulty) {
        changeTyped("")
        feedback = null
        session.startGenerating(difficulty.name)
        viewModelScope.launch {
            val game = withContext(Dispatchers.Default) { checkNotNull(words.puzzle(difficulty)) { "no honeycomb" } }
            session.start(game)
        }
    }

    fun letter(c: Char) {
        if (session.game?.isWon != false) return
        feedback = null
        if (typed.length < MAX_TYPED) changeTyped(typed + c)
    }

    fun delete() {
        feedback = null
        changeTyped(typed.dropLast(1))
    }

    fun shuffle() {
        session.game?.let { session.update(it.shuffle()) }
    }

    fun submit() {
        val game = session.game?.takeIf { !it.isWon } ?: return
        if (typed.isEmpty()) return
        val word = typed
        val reason = game.check(word)
        session.update(game.submit(word))
        feedback = if (reason == null) Feedback.Found(word, game.points(word), game.isPangram(word)) else Feedback.Rejected(reason)
        changeTyped("")
    }

    private fun changeTyped(value: String) {
        typed = value
        session.saveExtra(value)
    }

    private companion object {
        const val MAX_TYPED = 16
    }
}
