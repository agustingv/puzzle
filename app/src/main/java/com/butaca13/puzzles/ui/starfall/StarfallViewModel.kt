package com.butaca13.puzzles.ui.starfall

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.AvatarPiece
import com.butaca13.puzzles.data.AvatarPrizes
import com.butaca13.puzzles.game.starfall.Phase
import com.butaca13.puzzles.game.starfall.Config
import com.butaca13.puzzles.game.starfall.HighScores
import com.butaca13.puzzles.game.starfall.ScoreEntry
import com.butaca13.puzzles.game.starfall.Sfx
import com.butaca13.puzzles.game.starfall.Starfield
import com.butaca13.puzzles.game.starfall.World
import com.butaca13.puzzles.ui.common.GameSounds
import kotlin.random.Random

/** Which of Starfall's screens shows: its title, a run, or the table of scores. */
enum class StarfallStage { TITLE, PLAYING, SCORES }

/**
 * Starfall's state, kept across a turn of the phone: the stage, the run being played, and the table of
 * scores. A run is not saved: like the arcade it comes from, closing the app ends it.
 */
class StarfallViewModel(application: Application) : AndroidViewModel(application) {

    val audio = StarfallAudio(application, GameSounds.of(application))
    val atlas = StarfallAtlas.load(application)
    val scores = HighScores(AppStorage.of(application))
    private val prizes = AvatarPrizes(AppStorage.of(application))

    /** The avatar prize the run just ended won: the medal, for freeing the Earth the first time. */
    var prize: AvatarPiece? by mutableStateOf(null)
        private set

    var stage by mutableStateOf(StarfallStage.TITLE)
        private set

    var world: World? by mutableStateOf(null)
        private set

    var paused by mutableStateOf(false)
        private set

    /** The row of the run just filed, to light up in the table, or -1. */
    var lastRow by mutableIntStateOf(-1)
        private set

    /** The score of the run just ended, shown over the table even if it did not make it. */
    var lastScore by mutableIntStateOf(0)
        private set

    /** The stars behind the title and the table. */
    val backdrop = Starfield(Random.Default)

    /** Seconds the title has shown, for the ship's bob. */
    var titleTime = 0f
        private set

    fun play() {
        audio.play(Sfx.UI_SELECT, 0f, 1f)
        prize = null
        world = World(Random.Default, audio)
        paused = false
        stage = StarfallStage.PLAYING
    }

    fun showScores() {
        audio.play(Sfx.UI_SELECT, 0f, 1f)
        prize = null
        lastRow = -1
        lastScore = 0
        stage = StarfallStage.SCORES
    }

    fun toTitle() {
        audio.play(Sfx.UI_SELECT, 0f, 1f)
        world = null
        paused = false
        stage = StarfallStage.TITLE
    }

    /** Freezes the run where it is: from its button, the back gesture, or leaving the app. */
    fun pause() {
        if (stage == StarfallStage.PLAYING && !paused) {
            paused = true
            audio.play(Sfx.UI_SELECT, 0f, 1f)
        }
    }

    fun resume() {
        if (paused) {
            paused = false
            audio.play(Sfx.UI_SELECT, 0f, 1f)
        }
    }

    /** Moves everything on by [ticks] fixed steps: the run unless paused, else the stars behind the menus. */
    fun advance(ticks: Int) {
        val w = world
        repeat(ticks) {
            if (stage == StarfallStage.PLAYING && w != null) {
                if (!paused) w.step(Config.TICK)
            } else {
                titleTime += Config.TICK
                backdrop.update(Config.TICK)
            }
        }
        if (stage == StarfallStage.PLAYING && w != null && w.finished) finish(w)
    }

    private fun finish(w: World) {
        lastScore = w.score
        lastRow = scores.add(ScoreEntry(w.score, w.level, System.currentTimeMillis()))
        prize = if (w.phase == Phase.VICTORY) prizes.recordStarfallWon() else null
        world = null
        stage = StarfallStage.SCORES
    }

    override fun onCleared() {
        audio.release()
    }
}
