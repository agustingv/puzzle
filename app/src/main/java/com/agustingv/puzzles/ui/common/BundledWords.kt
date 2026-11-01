package com.agustingv.puzzles.ui.common

import android.content.Context
import com.agustingv.puzzles.game.word.WordList

/** The word list and clues shipped in assets, parsed once and shared by every word game. */
object BundledWords {
    @Volatile
    private var words: Map<Int, List<String>>? = null

    @Volatile
    private var clues: Map<String, String>? = null

    fun get(context: Context): Map<Int, List<String>> = words ?: synchronized(this) {
        words ?: WordList.parse(read(context, WordList.ASSET)).also { words = it }
    }

    /** Every word of every length: what the grid games draw from. */
    fun all(context: Context): List<String> = get(context).values.flatten()

    fun clues(context: Context): Map<String, String> = clues ?: synchronized(this) {
        clues ?: WordList.parseClues(read(context, WordList.CLUES_ASSET)).also { clues = it }
    }

    private fun read(context: Context, asset: String): String =
        context.applicationContext.assets.open(asset).bufferedReader().use { it.readText() }
}
