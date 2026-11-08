package com.butaca13.puzzles.ui.ladder

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
import com.butaca13.puzzles.game.foldAccents
import com.butaca13.puzzles.game.ladder.LadderGame
import com.butaca13.puzzles.game.ladder.LadderWords
import com.butaca13.puzzles.ui.common.BundledWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LadderViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(
        AppStorage.of(application),
        GameRules(
            id = "ladder",
            encode = LadderGame::toJson,
            decode = LadderGame::fromJson,
            isOver = LadderGame::isWon,
            isWon = LadderGame::isWon,
            variantOf = { it.difficulty.name },
            eventFor = { previous, next ->
                when {
                    next.misses > previous.misses -> GameEvent.MISS
                    next.chain.size > previous.chain.size -> GameEvent.HIT
                    next.chain != previous.chain -> GameEvent.TICK
                    else -> null
                }
            },
        ),
    )

    /** The words, loaded once: the ladder's own list and the app's common words. */
    private val words: LadderWords by lazy {
        val app = getApplication<Application>()
        val ladder = app.assets.open("ladder.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        val common = BundledWords.all(app).map(::foldAccents)
        LadderWords(ladder, common)
    }

    /** The letter of the current word picked to change, or -1. */
    var selected by mutableIntStateOf(-1)
        private set

    fun newGame(difficulty: Difficulty) {
        selected = -1
        session.startGenerating(difficulty.name)
        viewModelScope.launch {
            val game = withContext(Dispatchers.Default) {
                val (start, target) = checkNotNull(words.puzzle(difficulty)) { "no ladder for $difficulty" }
                LadderGame.of(start, target, difficulty, words)
            }
            session.start(game)
        }
    }

    init {
        // A generation the app was closed in the middle of is finished now.
        session.pendingVariant?.let { variant -> if (session.game == null) newGame(Difficulty.valueOf(variant)) }
    }

    fun select(position: Int) {
        selected = if (selected == position) -1 else position
    }

    /** The picked letter of the current word changed to [letter]: a step, if that makes a word. */
    fun letter(letter: Char) {
        val game = session.game?.takeIf { !it.isWon } ?: return
        val position = selected.takeIf { it in game.current.indices } ?: return
        val word = game.current.substring(0, position) + letter + game.current.substring(position + 1)
        if (word == game.current) return
        val next = game.step(word, words)
        session.update(next)
        if (next.chain.size != game.chain.size) selected = -1
    }

    /** Back to an earlier word of the way. */
    fun backTo(index: Int) {
        val game = session.game?.takeIf { !it.isWon } ?: return
        if (index in game.chain.indices && index < game.chain.lastIndex) {
            selected = -1
            session.update(game.step(game.chain[index], words))
        }
    }

    fun undo() {
        val game = session.game ?: return
        selected = -1
        session.update(game.undo())
    }

    fun hint() {
        val game = session.game?.takeIf { !it.isWon } ?: return
        selected = -1
        viewModelScope.launch {
            val next = withContext(Dispatchers.Default) { game.hint(words) }
            session.update(next)
        }
    }
}
