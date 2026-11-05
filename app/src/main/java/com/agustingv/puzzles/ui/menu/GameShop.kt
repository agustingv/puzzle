package com.agustingv.puzzles.ui.menu

import com.agustingv.puzzles.data.Products

/**
 * What the menu needs to know about buying games, away from Google Play itself so that the screens can
 * be previewed and tested without it. Prices are Google Play's own text, null while unknown.
 */
class GameShop(
    val unlocked: (GameInfo) -> Boolean,
    val price: (productId: String) -> String?,
    val pending: (productId: String) -> Boolean,
    /** Whether the product for all the paid games is owned. */
    val ownsAll: () -> Boolean,
    val buy: (productId: String) -> Unit,
    /** Asks Google Play again for what this account owns. */
    val restore: () -> Unit,
) {
    val allPrice: String? get() = price(Products.ALL_GAMES)

    companion object {
        /** Everything open and nothing for sale, as in previews. */
        val Free = GameShop(
            unlocked = { true },
            price = { null },
            pending = { false },
            ownsAll = { false },
            buy = {},
            restore = {},
        )
    }
}
