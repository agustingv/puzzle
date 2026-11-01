package com.agustingv.puzzles.game.word

import org.json.JSONObject

object WordList {
    const val ASSET = "words.json"

    val LENGTHS: List<Int> = (4..8).toList()

    /** Parses `{ "4": ["acta", ...], "5": [...] }` into words by length. */
    fun parse(json: String): Map<Int, List<String>> {
        val root = JSONObject(json)

        return root.keys().asSequence().associate { key ->
            val words = root.getJSONArray(key)
            key.toInt() to List(words.length()) { words.getString(it) }
        }
    }
}
