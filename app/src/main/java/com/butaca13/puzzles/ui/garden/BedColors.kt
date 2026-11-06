package com.butaca13.puzzles.ui.garden

import com.butaca13.puzzles.ui.theme.GardenBeds
import com.butaca13.puzzles.ui.theme.PastelCoral
import com.butaca13.puzzles.ui.theme.PastelMint
import com.butaca13.puzzles.ui.theme.PastelPeach
import com.butaca13.puzzles.ui.theme.PastelPink
import com.butaca13.puzzles.ui.theme.PastelSand
import com.butaca13.puzzles.ui.theme.PastelTeal

/** Pastels too alike to tell apart side by side, above all for colour-blind eyes. */
private val SIMILAR = listOf(PastelMint to PastelTeal, PastelPeach to PastelSand, PastelPink to PastelCoral)
    .map { (a, b) -> setOf(GardenBeds.indexOf(a), GardenBeds.indexOf(b)) }

/**
 * Which of [GardenBeds] each bed is painted with: a different one for every bed, and never two alike
 * on beds that touch, side by side or corner to corner. Each bed keeps its own place in the list when
 * it can, so most boards look as they always did, and the same board always gets the same colours.
 */
internal fun bedColors(beds: List<Int>, size: Int): List<Int> {
    val count = (beds.maxOrNull() ?: -1) + 1
    val touching = List(count) { mutableSetOf<Int>() }
    for (cell in beds.indices) {
        val row = cell / size
        val col = cell % size
        for (dr in -1..1) for (dc in -1..1) {
            val r = row + dr
            val c = col + dc
            if (r !in 0 until size || c !in 0 until size) continue
            val other = beds[r * size + c]
            if (other != beds[cell]) touching[beds[cell]].add(other)
        }
    }

    val colors = IntArray(count) { -1 }

    fun fits(bed: Int, color: Int): Boolean =
        colors.none { it == color } && touching[bed].none { other -> colors[other] >= 0 && setOf(color, colors[other]) in SIMILAR }

    // Few beds and few colours: trying them in order, and going back on a dead end, is quick.
    fun paint(bed: Int): Boolean {
        if (bed == count) return true
        val own = bed % GardenBeds.size
        for (color in listOf(own) + GardenBeds.indices.filter { it != own }) {
            if (!fits(bed, color)) continue
            colors[bed] = color
            if (paint(bed + 1)) return true
            colors[bed] = -1
        }
        return false
    }

    // More beds than colours, which no level has: the plain order, as before.
    return if (count <= GardenBeds.size && paint(0)) colors.toList() else List(count) { it % GardenBeds.size }
}
