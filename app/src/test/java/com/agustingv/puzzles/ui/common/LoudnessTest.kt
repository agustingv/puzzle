package com.agustingv.puzzles.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessTest {

    @Test
    fun `the ends of the slider are silence and full volume`() {
        assertEquals(0f, loudness(0f))
        assertEquals(1f, loudness(1f))
    }

    @Test
    fun `half way down the slider is clearly quieter, and it always gets louder`() {
        assertEquals(0.25f, loudness(0.5f), 0.001f)

        val levels = (0..100).map { loudness(it / 100f) }
        assertTrue(levels.zipWithNext().all { (a, b) -> a < b })
    }
}
