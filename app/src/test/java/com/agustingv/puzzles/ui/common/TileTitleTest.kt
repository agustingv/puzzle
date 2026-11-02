package com.agustingv.puzzles.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `all cards are sized by the widest line of any title`() {
        val titles = listOf("Sudoku", "Puzle Deslizante", "Crucigrama")

        assertEquals(lineWidth("Deslizante"), widestTitle(titles), 0.001f)
    }

    private val games = listOf(
        "Carrera", "Ahorcado", "Adivina la Palabra", "Sopa de Letras", "Crucigrama", "Sudoku",
        "Jardín", "Camino", "Kakuro", "Memorama", "Puzle Deslizante",
    )

    @Test
    fun `the lit letter is a letter of the title, and never the last one`() {
        for (title in games) {
            val letters = title.count { it != ' ' }
            assertTrue(title, litLetter(title) in 0 until letters - 1)
        }
    }

    @Test
    fun `a title keeps its lit letter, and the games do not all share one`() {
        assertEquals(litLetter("Sudoku"), litLetter("Sudoku"))
        assertTrue(games.map(::litLetter).toSet().size >= 4)
    }

    @Test
    fun `a one letter title lights that letter`() {
        assertEquals(0, litLetter("A"))
    }
}
