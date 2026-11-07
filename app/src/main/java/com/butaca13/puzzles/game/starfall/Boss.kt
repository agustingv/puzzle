package com.butaca13.puzzles.game.starfall

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (2 * PI).toFloat()

/**
 * The boss that closes each level, every one its own fight:
 *
 *   1 BLOCKADE WARDEN   glides, dwells at the edges   wide slow fans, bullet curtains        easiest
 *   2 BELT CRUSHER      fast lateral charges, bob     rotating spiral, radial rings          moderate
 *   3 YARD SOVEREIGN    mirrors the player            aimed rivet streams, homing missiles   hard
 *   4 SIEGE COLOSSUS    near-stationary, ponderous    charged laser, slow mortars            very hard
 *   5 ANDROMEDAN THRONE figure-eight weave, dips      everything, more in each third of hp   hardest
 *
 * The pace still eases as a fight goes on, so that the patterns stay readable.
 */
class Boss(private val rng: Random, private val sounds: SoundSink) {
    val pos = Vec()
    var radius = 0f
        private set
    var hp = 0f
        private set
    var maxHp = 0f
        private set
    var active = false
    var level = 1
        private set

    private var entering = 0f
    private var moveT = 0f
    private var fireT = 0f
    private var step = 0
    private var sub = 0
    private var spiralAng = 0f
    private var moveSpeed = 0f
    private var anchorY = 0f

    fun spawn(level: Int) {
        this.level = level
        active = true
        entering = 1f
        moveT = 0f
        fireT = 2.2f
        step = 0
        sub = 0
        spiralAng = 0f
        when (level) {
            1 -> { radius = 46f; maxHp = 75f; moveSpeed = 0.55f; anchorY = 92f }
            2 -> { radius = 44f; maxHp = 120f; moveSpeed = 1.30f; anchorY = 104f }
            3 -> { radius = 40f; maxHp = 155f; moveSpeed = 0.90f; anchorY = 118f }
            4 -> { radius = 54f; maxHp = 230f; moveSpeed = 0.30f; anchorY = 88f }
            else -> { radius = 46f; maxHp = 210f; moveSpeed = 1.60f; anchorY = 104f }
        }
        hp = maxHp
        pos.set(Config.SCREEN_W / 2, -radius)
    }

    fun hittable() = active && entering <= 0f

    fun damage(amount: Float) {
        hp -= amount
        if (hp <= 0f) {
            hp = 0f
            active = false
        }
    }

    private fun phase(): Int {
        val f = hp / maxHp
        return if (f > 0.66f) 0 else if (f > 0.33f) 1 else 2
    }

    fun update(dt: Float, bullets: BulletPool, beams: BeamPool, player: Player) {
        if (entering > 0f) {
            pos.y += 55f * dt
            if (pos.y >= anchorY) {
                pos.y = anchorY
                entering = 0f
            }
            return
        }
        moveT += dt
        move(dt, player)
        fireT -= dt
        if (fireT <= 0f) fire(bullets, beams, player)
    }

    private fun move(dt: Float, player: Player) {
        val cx = Config.SCREEN_W / 2
        val span = cx - radius - 10f
        when (level) {
            // A slow glide that dwells at the edges.
            1 -> pos.set(cx + (sin(moveT * moveSpeed) * 1.5f).coerceIn(-1f, 1f) * span, anchorY)
            // Fast lateral charges and a bob.
            2 -> pos.set(cx + sin(moveT * moveSpeed) * span, anchorY + sin(moveT * 2.4f) * 20f)
            // Following the player's side.
            3 -> {
                val tgt = player.pos.x.coerceIn(cx - span, cx + span)
                pos.x += (tgt - pos.x) * (1.6f * dt).coerceIn(0f, 1f)
                pos.y = anchorY + sin(moveT * 1.2f) * 10f
            }
            // Heavy, hardly moving.
            4 -> pos.set(cx + sin(moveT * moveSpeed) * (span * 0.22f), anchorY + sin(moveT * 0.4f) * 6f)
            // A figure of eight that dips toward the player.
            else -> pos.set(cx + sin(moveT * moveSpeed) * span, anchorY + 24f + sin(moveT * moveSpeed * 2f) * 34f)
        }
    }

    private fun fire(b: BulletPool, beams: BeamPool, player: Player) {
        when (level) {
            1 -> fireWarden(b, player)
            2 -> fireCrusher(b, player)
            3 -> fireSovereign(b, player)
            4 -> fireColossus(b, beams, player)
            else -> fireThrone(b, beams, player)
        }
    }

    /** 1: wide slow fans and falling curtains. */
    private fun fireWarden(b: BulletPool, player: Player) {
        val ph = phase()
        when (step % 3) {
            0 -> {
                snd(0.75f)
                fan(b, player, 3 + ph, 0.24f, 118f, Palette.MAGENTA, 5f)
                next(1.6f)
            }
            1 -> {
                snd(0.70f)
                fan(b, player, 3 + ph, 0.55f, 128f, Palette.SKYBLUE, 5f)
                next(1.5f)
            }
            else -> {
                snd(0.90f)
                curtain(b, 8, if (ph >= 2) 2 else 1, 96f, Palette.MAGENTA)
                next(1.9f)
            }
        }
    }

    /** 2: a rotating spiral, then radial rings. */
    private fun fireCrusher(b: BulletPool, player: Player) {
        val ph = phase()
        when (step % 3) {
            0 -> {
                if (sub == 0) snd(0.95f)
                spiral(b, 2 + if (ph >= 2) 1 else 0, 132f, Palette.ORANGE)
                burst(6, 0.12f, 1.2f)
            }
            1 -> {
                snd(0.65f)
                ring(b, 10 + 4 * ph, 118f, Palette.RED, spiralAng)
                next(1.3f)
            }
            else -> {
                snd(0.60f)
                fan(b, player, 3, 0.16f, 195f, Palette.ORANGE, 5f)
                next(0.95f)
            }
        }
    }

    /** 3: aimed pressure, rivet streams and seekers. */
    private fun fireSovereign(b: BulletPool, player: Player) {
        val ph = phase()
        when (step % 4) {
            0 -> {
                if (sub == 0) snd(0.55f)
                fan(b, player, 1, 0f, 215f, Palette.RED, 4f)
                burst(4 + ph, 0.09f, 1.1f)
            }
            1 -> {
                snd(0.70f)
                fan(b, player, 3 + 2 * ph, 0.22f, 175f, Palette.RED, 5f)
                next(1.15f)
            }
            2 -> {
                snd(0.45f)
                missiles(b, player, 2 + if (ph >= 2) 1 else 0, 150f, 2.3f)
                next(1.5f)
            }
            else -> {
                snd(0.80f)
                ring(b, 4, 132f, Palette.MAGENTA, spiralAng)
                spiralAng += 0.5f
                next(0.8f)
            }
        }
    }

    /** 4: a charged laser and slow mortars. */
    private fun fireColossus(b: BulletPool, beams: BeamPool, player: Player) {
        val ph = phase()
        when (step % 3) {
            0 -> {
                beams.spawnFrom(pos.x, pos.y + radius * 0.4f, player)
                sounds.play(Sfx.SPECIAL, 0.06f, 0.60f)
                next(2.2f)
            }
            1 -> {
                snd(0.45f)
                fan(b, player, 3 + ph, 0.50f, 92f, Palette.LIGHTGRAY, 8f)
                next(1.9f)
            }
            else -> {
                snd(0.55f)
                ring(b, 12 + 2 * ph, 84f, Palette.LIGHTGRAY, spiralAng)
                spiralAng += 0.3f
                next(2f)
            }
        }
    }

    /** 5: the whole arsenal, more of it with each third of its hit points gone. */
    private fun fireThrone(b: BulletPool, beams: BeamPool, player: Player) {
        val ph = phase()
        // 3 patterns at full strength, then 5, then 7.
        val len = 3 + ph * 2
        when (step % len) {
            0 -> {
                if (sub == 0) snd(0.95f)
                spiral(b, 3, 150f, Palette.MAGENTA)
                burst(7, 0.10f, 1f)
            }
            1 -> {
                snd(0.70f)
                fan(b, player, 5, 0.26f, 190f, Palette.RED, 5f)
                next(0.95f)
            }
            2 -> {
                snd(0.60f)
                ring(b, 16, 150f, Palette.ORANGE, spiralAng)
                spiralAng += 0.4f
                next(1f)
            }
            3 -> {
                snd(0.45f)
                missiles(b, player, 3, 160f, 2.6f)
                next(1.1f)
            }
            4 -> {
                snd(0.90f)
                curtain(b, 10, 2, 150f, Palette.MAGENTA)
                next(1f)
            }
            5 -> {
                snd(0.80f)
                ring(b, 14, 140f, Palette.RED, spiralAng)
                ring(b, 14, 175f, Palette.ORANGE, spiralAng + 0.22f)
                spiralAng += 0.5f
                next(0.9f)
            }
            else -> {
                beams.spawnFrom(pos.x, pos.y + radius * 0.4f, player)
                sounds.play(Sfx.SPECIAL, 0.05f, 0.9f)
                fan(b, player, 3, 0.12f, 220f, Palette.RED, 5f)
                next(1.2f)
            }
        }
    }

    /** On to the next pattern after a pause of about [atFull] seconds. */
    private fun next(atFull: Float) {
        step++
        fireT = recover(atFull)
    }

    /** One more shot of a stream of [shots], [gap] apart, then on to the next pattern. */
    private fun burst(shots: Int, gap: Float, atFull: Float) {
        sub++
        if (sub >= shots) {
            sub = 0
            next(atFull)
        } else {
            fireT = gap
        }
    }

    private fun snd(volume: Float) = sounds.play(Sfx.SHOT_ENEMY, 0.05f, volume)

    private fun aimAngle(player: Player) = atan2(player.pos.y - pos.y, player.pos.x - pos.x)

    /** [n] shots fanned ±[spread] round the aim at the player. */
    private fun fan(b: BulletPool, player: Player, n: Int, spread: Float, speed: Float, col: Int, r: Float) {
        val base = aimAngle(player)
        for (i in 0 until n) {
            val off = if (n == 1) 0f else -spread + 2f * spread * i / (n - 1)
            val a = base + off
            b.spawn(pos.x, pos.y + radius * 0.5f, cos(a) * speed, sin(a) * speed, false, r, col)
        }
    }

    /** An even ring of [n] shots, the first at [phase0]. */
    private fun ring(b: BulletPool, n: Int, speed: Float, col: Int, phase0: Float) {
        for (i in 0 until n) {
            val a = phase0 + i * (TAU / n)
            b.spawn(pos.x, pos.y, cos(a) * speed, sin(a) * speed, false, 5f, col)
        }
    }

    /** One tick of a rotating spiral of [arms] streams. */
    private fun spiral(b: BulletPool, arms: Int, speed: Float, col: Int) {
        spiralAng += 0.36f
        for (k in 0 until arms) {
            val a = spiralAng + k * (TAU / arms)
            b.spawn(pos.x, pos.y, cos(a) * speed, sin(a) * speed, false, 5f, col)
        }
    }

    /** A rank of falling shots across the screen, with [gaps] columns left out to slip through. */
    private fun curtain(b: BulletPool, cols: Int, gaps: Int, speed: Float, col: Int) {
        val x0 = 40f
        val x1 = Config.SCREEN_W - 40f
        val gapA = rng.value(0, cols - 1)
        val gapB = rng.value(0, cols - 1)
        for (i in 0 until cols) {
            if (i == gapA) continue
            if (gaps > 1 && i == gapB) continue
            val x = x0 + (x1 - x0) * i / (cols - 1)
            b.spawn(x, pos.y + radius, 0f, speed, false, 5f, col)
        }
    }

    private fun missiles(b: BulletPool, player: Player, n: Int, speed: Float, turn: Float) {
        val base = aimAngle(player)
        for (i in 0 until n) {
            val off = if (n == 1) 0f else -0.30f + 0.60f * i / (n - 1)
            val a = base + off
            b.spawn(pos.x, pos.y + radius * 0.5f, cos(a) * speed * 0.8f, sin(a) * speed * 0.8f, false, 6f, Palette.ORANGE)?.let {
                it.missile = true
                it.speed = speed
                it.turn = turn
                it.steerTime = 2f
            }
        }
    }

    /** The pause between volleys: about [atFull] at full health, down to 62% of it near the end. */
    private fun recover(atFull: Float): Float {
        val frac = (hp / maxHp).coerceIn(0f, 1f)
        return (atFull * (0.62f + 0.38f * frac)).coerceIn(0.42f, atFull)
    }
}
