package com.butaca13.puzzles.game.starfall

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (2 * PI).toFloat()

/** A shot of either side. Served from a fixed pool: one that finds no free place is simply not fired. */
class Bullet : Entity() {
    var fromPlayer = false
    var color = Palette.WHITE

    /** Guided: while [steerTime] lasts, the heading turns toward a target at [turn] rad/s, at [speed]. */
    var missile = false
    var speed = 0f
    var turn = 0f
    var steerTime = 0f

    /** Hit points a hit takes away. */
    var damage = 1f

    /** How many more enemies the shot goes through: the railgun's. */
    var pierce = 0

    /** Above 0, the splash radius on impact: the spread battery's. */
    var aoe = 0f

    fun update(dt: Float) {
        pos.x += vel.x * dt
        pos.y += vel.y * dt
    }

    fun steer(tx: Float, ty: Float, dt: Float) {
        if (turn <= 0f || steerTime <= 0f) return
        steerTime -= dt
        val cur = atan2(vel.y, vel.x)
        val des = atan2(ty - pos.y, tx - pos.x)
        var diff = des - cur
        while (diff > PI) diff -= TAU
        while (diff < -PI) diff += TAU
        val lim = turn * dt
        diff = diff.coerceIn(-lim, lim)
        val na = cur + diff
        val sp = if (speed > 0f) speed else length(vel.x, vel.y)
        vel.set(cos(na) * sp, sin(na) * sp)
    }
}

class BulletPool {
    val items = Array(CAP) { Bullet() }

    fun clear() = items.forEach { it.active = false }

    /** Every hostile shot gone: the bomb's doing. */
    fun clearEnemy() = items.forEach { if (it.active && !it.fromPlayer) it.active = false }

    fun spawn(x: Float, y: Float, vx: Float, vy: Float, fromPlayer: Boolean, radius: Float, color: Int): Bullet? {
        val b = items.firstOrNull { !it.active } ?: return null
        b.pos.set(x, y)
        b.vel.set(vx, vy)
        b.fromPlayer = fromPlayer
        b.radius = radius
        b.color = color
        b.missile = false
        b.speed = 0f
        b.turn = 0f
        b.steerTime = 0f
        b.damage = 1f
        b.pierce = 0
        b.aoe = 0f
        b.active = true
        return b
    }

    fun update(dt: Float, player: Player, enemies: EnemyPool, boss: Boss) {
        for (b in items) {
            if (!b.active) continue
            if (b.missile) {
                if (b.fromPlayer) {
                    nearestTarget(b.pos, enemies, boss)?.let { b.steer(it.x, it.y, dt) }
                } else {
                    b.steer(player.pos.x, player.pos.y, dt)
                }
            }
            b.update(dt)
            if (b.offScreen()) b.active = false
        }
    }

    /** The nearest live enemy, or the boss, to [from]: what the player's seekers chase. */
    private fun nearestTarget(from: Vec, enemies: EnemyPool, boss: Boss): Vec? {
        var best = Float.MAX_VALUE
        var target: Vec? = null
        for (e in enemies.items) {
            if (!e.active) continue
            val dx = e.pos.x - from.x
            val dy = e.pos.y - from.y
            val d = dx * dx + dy * dy
            if (d < best) {
                best = d
                target = e.pos
            }
        }
        if (boss.hittable()) {
            val dx = boss.pos.x - from.x
            val dy = boss.pos.y - from.y
            if (dx * dx + dy * dy < best) target = boss.pos
        }
        return target
    }

    companion object {
        const val CAP = 512
    }
}

/**
 * An enemy laser: first a thin line that still follows the gun, then a thick beam fixed at the angle it
 * fired at. Only the beam hurts.
 */
class Beam {
    var active = false
        private set
    private var src: Enemy? = null
    val origin = Vec()
    var angle = 0f
        private set
    var chargeT = 0f
        private set
    private var fireT = 0f

    fun reset(e: Enemy?, fromX: Float, fromY: Float, player: Player) {
        src = e
        origin.set(fromX, fromY)
        val a = atan2(player.pos.y - fromY, player.pos.x - fromX)
        // Always pointing down.
        angle = a.coerceIn(0.55f, PI.toFloat() - 0.55f)
        chargeT = CHARGE
        fireT = FIRE
        active = true
    }

    val charging get() = chargeT > 0f
    private val firing get() = chargeT <= 0f && fireT > 0f

    fun update(dt: Float, sounds: SoundSink) {
        val s = src
        if (s != null && s.active && charging) origin.set(s.pos)
        if (charging) {
            chargeT -= dt
            if (chargeT <= 0f) sounds.play(Sfx.SPECIAL, 0.04f, 1f)
        } else {
            fireT -= dt
            if (fireT <= 0f) {
                active = false
                src = null
            }
        }
    }

    fun hits(p: Player): Boolean {
        if (!firing) return false
        val ex = origin.x + cos(angle) * LEN
        val ey = origin.y + sin(angle) * LEN
        return segmentDistance(p.pos.x, p.pos.y, origin.x, origin.y, ex, ey) <= p.radius + HALF_W
    }

    fun deactivate() {
        active = false
        src = null
    }

    companion object {
        const val CHARGE = 0.70f
        const val FIRE = 0.50f
        const val LEN = 900f
        const val HALF_W = 7f

        fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = (if (len2 > 0.0001f) ((px - ax) * dx + (py - ay) * dy) / len2 else 0f).coerceIn(0f, 1f)
            return length(px - (ax + t * dx), py - (ay + t * dy))
        }
    }
}

class BeamPool {
    val items = Array(12) { Beam() }

    fun clear() = items.forEach { it.deactivate() }

    /** A beam from an enemy, following it while it charges. */
    fun spawn(e: Enemy, player: Player) {
        items.firstOrNull { !it.active }?.reset(e, e.pos.x, e.pos.y, player)
    }

    /** A beam from a fixed point: the bosses'. */
    fun spawnFrom(x: Float, y: Float, player: Player) {
        items.firstOrNull { !it.active }?.reset(null, x, y, player)
    }

    fun update(dt: Float, sounds: SoundSink) = items.forEach { if (it.active) it.update(dt, sounds) }

    fun strike(player: Player) = items.any { it.active && it.hits(player) }
}

/** The bomb's homing missiles. */
class Missile {
    val pos = Vec()
    val vel = Vec()
    var radius = 4f
    var active = false
    private var life = 0f
    private var target: Enemy? = null

    fun launch(fromX: Float, fromY: Float, angle: Float, target: Enemy?) {
        pos.set(fromX, fromY)
        vel.set(sin(angle) * 130f, -cos(angle) * 130f)
        radius = 4f
        life = 2.6f
        this.target = target
        active = true
    }

    fun update(dt: Float) {
        life -= dt
        if (life <= 0f) {
            deactivate()
            return
        }
        val t = target
        if (t != null && t.active) {
            val dx = t.pos.x - pos.x
            val dy = t.pos.y - pos.y
            val len = length(dx, dy)
            if (len > 0.001f) {
                val k = (7f * dt).coerceIn(0f, 1f)
                vel.x += (dx / len * 320f - vel.x) * k
                vel.y += (dy / len * 320f - vel.y) * k
            }
        } else {
            // No live target: faster and faster along its heading.
            vel.x *= 1f + 0.7f * dt
            vel.y *= 1f + 0.7f * dt
        }
        pos.x += vel.x * dt
        pos.y += vel.y * dt
        if (pos.y < -Config.MARGIN || pos.x < -Config.MARGIN || pos.x > Config.SCREEN_W + Config.MARGIN) deactivate()
    }

    fun deactivate() {
        active = false
        target = null
    }
}

class MissilePool {
    val items = Array(CAP) { Missile() }

    fun clear() = items.forEach { it.deactivate() }

    fun launch(fromX: Float, fromY: Float, angle: Float, target: Enemy?): Missile? =
        items.firstOrNull { !it.active }?.also { it.launch(fromX, fromY, angle, target) }

    fun update(dt: Float) = items.forEach { if (it.active) it.update(dt) }

    companion object {
        const val CAP = 28
    }
}

/** What a dropped power-up gives, with its colour and letter. */
enum class PickupKind(val colour: Int, val glyph: String) {
    BOMB(Palette.YELLOW, "B"),
    SHIELD(Palette.SKYBLUE, "+"),
    W_SPREAD(Palette.ORANGE, "W"),
    W_RAIL(Palette.RAYWHITE, "R"),
    W_HOMING(Palette.LIME, "M"),
    ;

    companion object {
        /** A weighted roll for what a destroyed enemy leaves behind. */
        fun random(rng: Random): PickupKind {
            val r = rng.value(0, 99)
            return when {
                r < 30 -> BOMB
                r < 48 -> SHIELD
                r < 66 -> W_SPREAD
                r < 83 -> W_RAIL
                else -> W_HOMING
            }
        }
    }
}

class Pickup {
    val pos = Vec()
    var radius = 10f
    var active = false
    var kind = PickupKind.BOMB
        private set
    private var wob = 0f

    fun spawn(x: Float, y: Float, kind: PickupKind, rng: Random) {
        pos.set(x, y)
        this.kind = kind
        radius = 10f
        wob = rng.value(0, 628) / 100f
        active = true
    }

    fun update(dt: Float) {
        wob += dt * 3f
        pos.y += 55f * dt
        pos.x += sin(wob) * 18f * dt
        if (pos.y > Config.SCREEN_H + Config.MARGIN) active = false
    }
}

class PickupPool {
    val items = Array(16) { Pickup() }

    fun clear() = items.forEach { it.active = false }

    fun spawn(x: Float, y: Float, kind: PickupKind, rng: Random) {
        items.firstOrNull { !it.active }?.spawn(x, y, kind, rng)
    }

    fun update(dt: Float) = items.forEach { if (it.active) it.update(dt) }
}

/** An expanding puff, for the explosion sprite sheet. */
class Explosion {
    val pos = Vec()
    var size = 0f
    var t = 0f
    val life = 0.45f
    var active = false
}

class ExplosionPool {
    val items = Array(48) { Explosion() }

    fun clear() = items.forEach { it.active = false }

    fun spawn(x: Float, y: Float, size: Float) {
        val e = items.firstOrNull { !it.active } ?: return
        e.pos.set(x, y)
        e.size = size
        e.t = 0f
        e.active = true
    }

    fun update(dt: Float) {
        for (e in items) {
            if (!e.active) continue
            e.t += dt
            if (e.t >= e.life) e.active = false
        }
    }
}
