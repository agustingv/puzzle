package com.butaca13.puzzles.ui.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCatalogTest {

    /**
     * The games are loaded first, as the app does on starting. The paid products live beside them, and
     * worked out while the games were still being made they found none: the app crashed on starting.
     */
    @Test
    fun `the games load first and the paid products after them, as on starting the app`() {
        val ids = GameInfo.entries.map { it.id }

        assertEquals(GameInfo.entries.mapNotNull { it.productId }, PAID_GAME_PRODUCTS)
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `a free game has no product, a paid one its own`() {
        assertNull(GameInfo.SUDOKU.productId)
        GameInfo.entries.filter { it.paid }.forEach { assertEquals("game_${it.id}", it.productId) }
    }

    @Test
    fun `every game is in one group, and the groups keep the home screen's order`() {
        assertEquals(GameInfo.entries.toSet(), GameGroup.entries.flatMap { it.games }.toSet())
        assertEquals(GameInfo.entries.size, GameGroup.entries.sumOf { it.games.size })
        assertEquals(listOf(GameGroup.LETTERS, GameGroup.NUMBERS, GameGroup.CARDS, GameGroup.VARIOUS), GameGroup.entries)
    }

    @Test
    fun `the groups hold the games they were made for`() {
        assertEquals(
            listOf(GameInfo.RACE, GameInfo.HANGMAN, GameInfo.WORD, GameInfo.WORD_SEARCH, GameInfo.CROSSWORD),
            GameGroup.LETTERS.games,
        )
        assertEquals(setOf(GameInfo.SUDOKU, GameInfo.KAKURO, GameInfo.SLIDING), GameGroup.NUMBERS.games.toSet())
        assertEquals(listOf(GameInfo.KLONDIKE, GameInfo.FREECELL, GameInfo.SPIDER, GameInfo.PYRAMID, GameInfo.TRIPEAKS), GameGroup.CARDS.games)
        assertEquals(setOf(GameInfo.GARDEN, GameInfo.PATH, GameInfo.MEMORAMA), GameGroup.VARIOUS.games.toSet())
    }
}
