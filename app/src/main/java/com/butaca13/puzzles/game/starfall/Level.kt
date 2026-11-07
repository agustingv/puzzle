package com.butaca13.puzzles.game.starfall

import kotlin.math.abs
import kotlin.random.Random

enum class Formation(val size: Int) { ROW_5(5), ROW_3(3), V_5(5), COLUMNS(6), DIAMOND(4) }

class SpawnEvent(val at: Float, val formation: Formation, val kind: EnemyKind)

/**
 * A section of a level: a timed list of waves. It is clear once every wave has come and no enemy is
 * left. The waves grow with the level and section, but only up to a cap, so that late stages stay
 * intense without becoming walls; only each wave's kind of craft is random.
 */
class SectionScript(level: Int, section: Int, rng: Random) {
    val events: List<SpawnEvent>
    val lastAt: Float

    init {
        val bursts = burstCount(level, section)
        val gap = (3f - 0.11f * level - 0.09f * section).coerceIn(1.4f, 3f)
        val roster = roster(level)
        events = List(bursts) { i ->
            val formation = CYCLE[(i + section) % CYCLE.size]
            val kind = roster[(i * 2 + section + rng.value(0, 2)) % roster.size]
            SpawnEvent(1f + i * gap, formation, kind)
        }
        lastAt = events.lastOrNull()?.at ?: 0f
    }

    companion object {
        private val CYCLE = listOf(Formation.ROW_5, Formation.V_5, Formation.ROW_3, Formation.COLUMNS, Formation.DIAMOND)
        private const val MAX_BURSTS = 8

        fun burstCount(level: Int, section: Int) = minOf(3 + level + section, MAX_BURSTS)

        /** How many craft a stage sends in all. */
        fun enemyCount(level: Int, section: Int) =
            (0 until burstCount(level, section)).sumOf { CYCLE[(it + section) % CYCLE.size].size }

        /** Each level's own mix of craft; the later in its list, the heavier, and the more often met. */
        fun roster(level: Int): List<EnemyKind> = when (level) {
            1 -> listOf(EnemyKind.GRUNT, EnemyKind.GRUNT, EnemyKind.DARTER)
            2 -> listOf(EnemyKind.DARTER, EnemyKind.WEAVER, EnemyKind.WEAVER, EnemyKind.SENTINEL)
            3 -> listOf(EnemyKind.WEAVER, EnemyKind.SENTINEL, EnemyKind.BRUTE, EnemyKind.HUNTER)
            4 -> listOf(EnemyKind.SENTINEL, EnemyKind.BRUTE, EnemyKind.HUNTER, EnemyKind.RACER, EnemyKind.RACER)
            else -> listOf(EnemyKind.HUNTER, EnemyKind.RACER, EnemyKind.BRUTE, EnemyKind.WARDEN, EnemyKind.WARDEN)
        }
    }
}

/** Turns a formation into enemies, coming in from above the screen. */
object Formations {
    fun spawn(pool: EnemyPool, f: Formation, kind: EnemyKind, rng: Random) {
        when (f) {
            Formation.ROW_5 -> row(pool, 5, kind, rng)
            Formation.ROW_3 -> row(pool, 3, kind, rng)
            Formation.V_5 -> {
                val cx = Config.SCREEN_W / 2
                for (i in 0 until 5) {
                    val off = (i - 5 / 2) * 46f
                    pool.spawn(cx + off, -40f - abs(off), kind, rng)
                }
            }
            Formation.COLUMNS -> for (i in 0 until 3) {
                pool.spawn(Config.SCREEN_W * 0.28f, -40f - i * 60f, kind, rng)
                pool.spawn(Config.SCREEN_W * 0.72f, -40f - i * 60f, kind, rng)
            }
            Formation.DIAMOND -> {
                val cx = Config.SCREEN_W / 2
                pool.spawn(cx, -40f, kind, rng)
                pool.spawn(cx - 44f, -80f, kind, rng)
                pool.spawn(cx + 44f, -80f, kind, rng)
                pool.spawn(cx, -120f, kind, rng)
            }
        }
    }

    private fun row(pool: EnemyPool, count: Int, kind: EnemyKind, rng: Random) {
        val gap = Config.SCREEN_W / (count + 1f)
        for (i in 0 until count) pool.spawn(gap * (i + 1), -40f, kind, rng)
    }
}

/** The stars, in three layers that scroll down at different speeds, as the ship flies up. */
class Starfield(private val rng: Random) {
    class Star(var x: Float, var y: Float, val speed: Float, val size: Float, val bright: Int)

    val stars = List(150) {
        val layer = rng.value(0, 2)
        Star(
            x = rng.value(0, Config.SCREEN_W.toInt()).toFloat(),
            y = rng.value(0, Config.SCREEN_H.toInt()).toFloat(),
            speed = 25f + layer * 55f + rng.value(0, 18),
            size = if (layer == 2) 2f else 1f,
            bright = 80 + layer * 60,
        )
    }

    fun update(dt: Float) {
        for (s in stars) {
            s.y += s.speed * dt
            if (s.y > Config.SCREEN_H) {
                s.y = 0f
                s.x = rng.value(0, Config.SCREEN_W.toInt()).toFloat()
            }
        }
    }
}

/**
 * Each level's distant world, from Titan to the Earth, drifting down behind the stars far slower than any
 * of them. Once past the bottom it comes back only after a long gap, so it goes by once or twice a level.
 */
class Planet {
    var level = 1
        private set
    var x = 0f
        private set
    var y = 0f
        private set

    /** Its height on screen. */
    var size = 0f
        private set
    private var speed = 0f

    init {
        setLevel(1)
    }

    fun setLevel(level: Int) {
        this.level = level.coerceIn(1, Config.LEVELS)
        when (this.level) {
            1 -> { x = Config.SCREEN_W * 0.70f; size = 210f; speed = 8f }
            2 -> { x = Config.SCREEN_W * 0.32f; size = 264f; speed = 7f }
            3 -> { x = Config.SCREEN_W * 0.74f; size = 184f; speed = 10f }
            4 -> { x = Config.SCREEN_W * 0.26f; size = 150f; speed = 12f }
            else -> { x = Config.SCREEN_W * 0.58f; size = 300f; speed = 6f }
        }
        y = -size * 0.5f - 40f
    }

    fun update(dt: Float) {
        y += speed * dt
        if (y - size * 0.5f > Config.SCREEN_H) y = -size * 0.5f - GAP
    }

    private companion object {
        const val GAP = 820f
    }
}
