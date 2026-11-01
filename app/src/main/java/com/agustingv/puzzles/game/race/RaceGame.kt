package com.agustingv.puzzles.game.race

import com.agustingv.puzzles.game.foldAccents
import kotlin.random.Random

data class RaceRound(val word: String, val scrambled: String)

enum class RaceResult { CORRECT, TIMEOUT }

/**
 * Unscramble [TOTAL_ROUNDS] words with [TIME_LIMIT] seconds each. A wrong answer costs only time:
 * the round stays open until it is answered or the countdown runs out. Time comes from [tick], not
 * the wall clock, so a game paused in the background does not lose its round.
 */
data class RaceGame(
    val rounds: List<RaceRound>,
    val results: List<RaceResult> = emptyList(),
    val secondsLeft: Int = TIME_LIMIT,
) {
    val index: Int = results.size
    val isFinished: Boolean = index >= rounds.size
    val correctCount: Int = results.count { it == RaceResult.CORRECT }
    val isWon: Boolean = isFinished && correctCount == rounds.size
    val length: Int = rounds.first().word.length
    val current: RaceRound? = rounds.getOrNull(index)

    /** Words guessed so far, in play order. */
    val correctWords: List<String> =
        results.indices.filter { results[it] == RaceResult.CORRECT }.map { rounds[it].word }

    /** Returns this same state when the answer is wrong (or the race is over). */
    fun submit(answer: String): RaceGame {
        val round = current ?: return this
        return if (foldAccents(answer.trim()) == foldAccents(round.word)) advance(RaceResult.CORRECT) else this
    }

    fun tick(): RaceGame = when {
        isFinished -> this
        secondsLeft <= 1 -> advance(RaceResult.TIMEOUT)
        else -> copy(secondsLeft = secondsLeft - 1)
    }

    private fun advance(result: RaceResult) = copy(results = results + result, secondsLeft = TIME_LIMIT)

    companion object {
        const val TOTAL_ROUNDS = 10
        const val TIME_LIMIT = 20

        private const val SCRAMBLE_ATTEMPTS = 20

        fun generate(pool: List<String>, random: Random = Random.Default): RaceGame =
            RaceGame(pool.shuffled(random).take(TOTAL_ROUNDS).map { RaceRound(it, scramble(it, random)) })

        internal fun scramble(word: String, random: Random): String {
            var chars = word.toList()
            var attempts = 0
            do {
                chars = chars.shuffled(random)
                attempts++
            } while (chars == word.toList() && attempts < SCRAMBLE_ATTEMPTS)

            return chars.joinToString("")
        }
    }
}
