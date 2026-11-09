package com.butaca13.puzzles.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TileTitleTest {

    @Test
    fun `one word stays on one line`() {
        assertEquals(listOf("Crucigrama"), titleLines("Crucigrama"))
    }

    @Test
    fun `titles break between words, keeping the longest line short`() {
        assertEquals(listOf("Puzle", "Deslizante"), titleLines("Puzle Deslizante"))
        assertEquals(listOf("Sopa de", "Letras"), titleLines("Sopa de Letras"))
        assertEquals(listOf("Adivina", "la Palabra"), titleLines("Adivina la Palabra"))
    }

    @Test
    fun `a line is as wide as its tiles, gaps and half a tile per space`() {
        assertEquals(1f, lineWidth("A"), 0.001f)
        assertEquals(2.18f, lineWidth("AB"), 0.001f)
        assertEquals(2.86f, lineWidth("A B"), 0.001f)
    }
}
