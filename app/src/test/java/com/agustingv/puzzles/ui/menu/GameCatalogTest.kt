package com.agustingv.puzzles.ui.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
