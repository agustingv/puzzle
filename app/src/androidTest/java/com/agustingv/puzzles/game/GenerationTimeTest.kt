package com.agustingv.puzzles.game

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agustingv.puzzles.game.kakuro.KakuroGame
import com.agustingv.puzzles.game.sudoku.SudokuGame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * On a device, not the JVM: guards against a generator getting slow enough to hurt the wait. It
 * checks the average because generation is a random search: the slowest of a few rounds varies too
 * much from run to run to assert on, so it is only logged.
 */
@RunWith(AndroidJUnit4::class)
class GenerationTimeTest {

    private fun measure(name: String, generate: (Difficulty) -> Unit) {
        for (difficulty in Difficulty.entries) {
            val times = List(ROUNDS) { measureTimeMillis { generate(difficulty) } }
            val average = times.average().toLong()

            Log.i("GenerationTime", "$name $difficulty: avg $average ms, slowest ${times.max()} ms")
            assertTrue("$name $difficulty averages $average ms", average < AVERAGE_LIMIT_MS)
        }
    }

    @Test
    fun sudokuGeneratesInTime() = measure("sudoku") { SudokuGame.generate(it) }

    @Test
    fun kakuroGeneratesInTime() = measure("kakuro") { KakuroGame.generate(it) }

    private companion object {
        const val ROUNDS = 60
        const val AVERAGE_LIMIT_MS = 2_000L
    }
}
