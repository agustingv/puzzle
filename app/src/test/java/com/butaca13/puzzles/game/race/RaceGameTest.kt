package com.butaca13.puzzles.game.race

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RaceGameTest {

    private fun race(vararg words: String) = RaceGame(words.map { RaceRound(it, it.reversed()) })

    private fun RaceGame.wait(seconds: Int) = (1..seconds).fold(this) { g, _ -> g.tick() }

    @Test
    fun `a right answer scores and opens the next round with a full clock`() {
        val next = race("casa", "mesa").wait(5).submit("casa")

        assertEquals(1, next.index)
        assertEquals(1, next.correctCount)
        assertEquals(RaceGame.TIME_LIMIT, next.secondsLeft)
        assertEquals("mesa", next.current?.word)
    }

    @Test
    fun `answers ignore case, surrounding spaces and accents`() {
        assertEquals(1, race("ágil").submit(" AGIL ").correctCount)
    }

    @Test
    fun `enye must be typed`() {
        val game = race("año")

        assertSame(game, game.submit("ano"))
    }

    @Test
    fun `a wrong answer keeps the round and its clock`() {
        val game = race("casa").wait(3)

        assertSame(game, game.submit("saca"))
    }

    @Test
    fun `the clock runs out exactly at the limit and moves on`() {
        val almost = race("casa", "mesa").wait(RaceGame.TIME_LIMIT - 1)
        assertEquals(1, almost.secondsLeft)
        assertEquals(0, almost.index)

        val next = almost.tick()
        assertEquals(1, next.index)
        assertEquals(listOf(RaceResult.TIMEOUT), next.results)
        assertEquals(RaceGame.TIME_LIMIT, next.secondsLeft)
    }

    @Test
    fun `winning takes every word`() {
        val perfect = race("casa", "mesa").submit("casa").submit("mesa")
        assertTrue(perfect.isFinished)
        assertTrue(perfect.isWon)

        val missedOne = race("casa", "mesa").submit("casa").wait(RaceGame.TIME_LIMIT)
        assertTrue(missedOne.isFinished)
        assertFalse(missedOne.isWon)
        assertEquals(listOf("casa"), missedOne.correctWords)
    }

    @Test
    fun `a finished race ignores everything`() {
        val done = race("casa").submit("casa")

        assertSame(done, done.tick())
        assertSame(done, done.submit("casa"))
    }

    @Test
    fun `generate picks ten different words from the pool`() {
        val pool = (1..50).map { "palabra$it" }
        val game = RaceGame.generate(pool, Random(1))

        assertEquals(RaceGame.TOTAL_ROUNDS, game.rounds.size)
        assertEquals(RaceGame.TOTAL_ROUNDS, game.rounds.map { it.word }.toSet().size)
        assertTrue(game.rounds.all { it.word in pool })
    }

    @Test
    fun `scramble keeps the letters and changes the order`() {
        repeat(200) { seed ->
            val scrambled = RaceGame.scramble("palabra", Random(seed))

            assertEquals("palabra".toList().sorted(), scrambled.toList().sorted())
            assertNotEquals("palabra", scrambled)
        }
    }

    @Test
    fun `json round trip keeps the whole state`() {
        val played = race("ágil", "año", "mesa").submit("agil").wait(RaceGame.TIME_LIMIT).wait(3)

        assertEquals(played, RaceGame.fromJson(played.toJson()))
        assertEquals(RaceGame.TIME_LIMIT - 3, RaceGame.fromJson(played.toJson()).secondsLeft)
    }
}
