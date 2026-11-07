package com.butaca13.puzzles.game.starfall

import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Starfall: a vertical shoot-'em-up, the prize for solving the secret message. Ported from the Vala and
 * raylib original (projects/gnome/vala/game), keeping its rules, numbers and art; only the controls
 * change, to a finger that drags the ship and a gun that fires on its own.
 *
 * This package holds the game itself, with no Android in it: the world steps in fixed ticks of 1/60 s
 * at a logical 480 × 720, and the screen draws it scaled to fit.
 */

object Config {
    const val SCREEN_W = 480f
    const val SCREEN_H = 720f
    const val TICK = 1f / 60f

    /** How far off screen things may go before they are dropped. */
    const val MARGIN = 80f

    const val LEVELS = 5
    const val SECTIONS_PER_LEVEL = 3

    /** Percent chance a destroyed enemy drops a power-up. */
    const val POWERUP_DROP_PCT = 12
}

/** Colours as ARGB, the original's raylib palette. */
object Palette {
    const val BG = 0xFF080A18.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val RAYWHITE = 0xFFF5F5F5.toInt()
    const val LIGHTGRAY = 0xFFC8C8C8.toInt()
    const val GRAY = 0xFF828282.toInt()
    const val YELLOW = 0xFFFDF900.toInt()
    const val ORANGE = 0xFFFFA100.toInt()
    const val RED = 0xFFE62937.toInt()
    const val LIME = 0xFF009E2F.toInt()
    const val SKYBLUE = 0xFF66BFFF.toInt()
    const val MAGENTA = 0xFFFF00FF.toInt()
}

/** The sound effects, by the original's file names. */
enum class Sfx(val file: String) {
    SHOT_PLAYER("shot_player"),
    SHOT_ENEMY("shot_enemy"),
    EXPLOSION_SMALL("explosion_small"),
    EXPLOSION_BIG("explosion_big"),
    HIT("hit"),
    SPECIAL("special"),
    POWERUP("powerup"),
    UI_MOVE("ui_move"),
    UI_SELECT("ui_select"),

    /** Not the original's: the victory fanfare, made by tools/generate_starfall_fanfare.py. */
    FANFARE("fanfare"),
}

/**
 * Where the world's sounds go. [pitchJitter] (0..1) varies the pitch by up to that fraction either way,
 * so that repeats don't sound stamped; [volume] scales this one.
 */
fun interface SoundSink {
    fun play(sfx: Sfx, pitchJitter: Float, volume: Float)
}

fun SoundSink.play(sfx: Sfx, pitchJitter: Float = 0f) = play(sfx, pitchJitter, 1f)

/** A point or a velocity, changed in place so that nothing is allocated while playing. */
class Vec(var x: Float = 0f, var y: Float = 0f) {
    fun set(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    fun set(other: Vec) = set(other.x, other.y)
}

fun circles(a: Vec, ra: Float, b: Vec, rb: Float): Boolean {
    val dx = a.x - b.x
    val dy = a.y - b.y
    val r = ra + rb
    return dx * dx + dy * dy <= r * r
}

fun length(x: Float, y: Float) = sqrt(x * x + y * y)

/** raylib's GetRandomValue: a whole number from [min] to [max], both included. */
fun Random.value(min: Int, max: Int): Int = nextInt(min, max + 1)

/** Anything that moves and may leave the play area. */
abstract class Entity {
    val pos = Vec()
    val vel = Vec()
    var radius = 0f
    var active = false

    fun offScreen() = pos.y < -Config.MARGIN || pos.y > Config.SCREEN_H + Config.MARGIN ||
        pos.x < -Config.MARGIN || pos.x > Config.SCREEN_W + Config.MARGIN
}
