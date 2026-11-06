package com.butaca13.puzzles.game

private val FOLD = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u')

/**
 * Lowercases and folds accented vowels for lenient matching: typing "u" matches "ú" or "ü".
 * "ñ" is intentionally never folded: it is a distinct Spanish letter, not an accented "n".
 */
fun foldAccents(text: String): String =
    text.lowercase().map { FOLD[it] ?: it }.joinToString("")
