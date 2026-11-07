package com.butaca13.puzzles.game.starfall

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The alien craft, each with its own movement and weapon:
 *
 *   GRUNT    slow wide sway            single aimed bolt
 *   DARTER   fast tight sway           quick two-bolt burst
 *   WEAVER   broad fast weave          angled bolt pair
 *   SENTINEL drops to a hold band      charged laser beam
 *   BRUTE    slow big sway, tanky      three-way bolt spread
 *   HUNTER   slides toward the player  guided missile, which can be shot down
 *   RACER    steep diagonal dive       fast bolt volley along its heading
 *   WARDEN   slow menacing weave       missiles and a wide spread, by turns
 */
enum class EnemyKind { GRUNT, DARTER, WEAVER, SENTINEL, BRUTE, HUNTER, RACER, WARDEN }

private enum class EnemyWeapon { BOLT, BURST, PAIR, SPREAD, DIVE_BOLTS, MISSILE, BEAM, MISSILE_SPREAD }

class Enemy : Entity() {
    var hp = 0f
    var hpMax = 0f
        private set
    var scoreValue = 0
        private set
    var kind = EnemyKind.GRUNT
        private set

    /** Holding still: the sentinel charging its beam. */
    var holding = false
        private set

    private var weapon = EnemyWeapon.BOLT
    private var baseX = 0f
    private var baseVy = 0f
    private var swayPhase = 0f
    private var swayFreq = 0f
    private var swayAmp = 0f
    private var chaseK = 0f
    private var holdY = 0f
    private var freeX = false
    private var holdTimer = 0f
    private var alt = 0
    private var fireInterval = 0f
    private var fireTimer = 0f

    fun configure(x: Float, y: Float, kind: EnemyKind, rng: Random) {
        this.kind = kind
        baseX = x
        pos.set(x, y)
        vel.set(0f, 0f)
        active = true
        swayPhase = rng.value(0, 628) / 100f
        chaseK = 0f
        holdY = 0f
        freeX = false
        holding = false
        holdTimer = 0f
        alt = 0

        when (kind) {
            EnemyKind.GRUNT -> {
                radius = 14f; hp = 1f; scoreValue = 100
                vel.y = 70f; swayAmp = 28f; swayFreq = 1.4f
                weapon = EnemyWeapon.BOLT; fireInterval = 2.7f
            }
            EnemyKind.DARTER -> {
                radius = 11f; hp = 1f; scoreValue = 150
                vel.y = 135f; swayAmp = 10f; swayFreq = 3f
                weapon = EnemyWeapon.BURST; fireInterval = 3.1f
            }
            EnemyKind.WEAVER -> {
                radius = 15f; hp = 2f; scoreValue = 200
                vel.y = 40f; swayAmp = 120f; swayFreq = 1.7f
                weapon = EnemyWeapon.PAIR; fireInterval = 2.8f
            }
            EnemyKind.SENTINEL -> {
                radius = 16f; hp = 3f; scoreValue = 300
                vel.y = 95f; swayAmp = 6f; swayFreq = 1f
                holdY = 96f + rng.value(0, 80)
                baseVy = 70f
                weapon = EnemyWeapon.BEAM; fireInterval = 3.2f
            }
            EnemyKind.BRUTE -> {
                radius = 20f; hp = 4f; scoreValue = 400
                vel.y = 45f; swayAmp = 42f; swayFreq = 0.8f
                weapon = EnemyWeapon.SPREAD; fireInterval = 2.7f
            }
            EnemyKind.HUNTER -> {
                radius = 14f; hp = 2f; scoreValue = 300
                vel.y = 52f; swayAmp = 8f; swayFreq = 1.6f
                chaseK = 70f
                weapon = EnemyWeapon.MISSILE; fireInterval = 3.6f
            }
            EnemyKind.RACER -> {
                radius = 11f; hp = 1f; scoreValue = 220
                vel.y = 205f; swayAmp = 0f; swayFreq = 0f
                freeX = true
                vel.x = if (x < Config.SCREEN_W / 2) 150f else -150f
                weapon = EnemyWeapon.DIVE_BOLTS; fireInterval = 1.9f
            }
            EnemyKind.WARDEN -> {
                radius = 22f; hp = 6f; scoreValue = 700
                vel.y = 44f; swayAmp = 64f; swayFreq = 0.7f
                chaseK = 24f
                weapon = EnemyWeapon.MISSILE_SPREAD; fireInterval = 3.1f
            }
        }
        hpMax = hp
        // The first shots staggered, so that a fresh wave doesn't fire all at once.
        fireTimer = fireInterval * (0.75f + rng.value(0, 60) / 100f)
    }

    /** The kinds that chase drift toward the player's side. */
    fun steer(dt: Float, player: Player) {
        if (chaseK <= 0f) return
        val target = player.pos.x.coerceIn(40f, Config.SCREEN_W - 40f)
        val d = target - baseX
        val step = chaseK * dt
        baseX += d.coerceIn(-step, step)
    }

    fun update(dt: Float) {
        swayPhase += swayFreq * dt
        if (holding) {
            holdTimer -= dt
            if (holdTimer <= 0f) {
                holding = false
                // Never again.
                holdY = 0f
                vel.y = baseVy
            }
        } else {
            pos.y += vel.y * dt
            if (holdY > 0f && pos.y >= holdY) {
                pos.y = holdY
                holding = true
                holdTimer = 4.5f
            }
        }
        if (freeX) pos.x += vel.x * dt else pos.x = baseX + sin(swayPhase) * swayAmp

        if (pos.y > Config.SCREEN_H + Config.MARGIN || pos.x < -Config.MARGIN - 60f || pos.x > Config.SCREEN_W + Config.MARGIN + 60f) {
            active = false
        }
    }

    fun tryFire(dt: Float, bullets: BulletPool, beams: BeamPool, player: Player, sounds: SoundSink) {
        fireTimer -= dt
        if (fireTimer > 0f) return
        fireTimer = fireInterval
        when (weapon) {
            EnemyWeapon.BOLT -> fireBolts(bullets, player, 1, 0f, 190f, sounds)
            EnemyWeapon.BURST -> fireBolts(bullets, player, 2, 0.09f, 235f, sounds)
            EnemyWeapon.PAIR -> fireBolts(bullets, player, 2, 0.18f, 205f, sounds)
            EnemyWeapon.SPREAD -> fireBolts(bullets, player, 3, 0.24f, 155f, sounds)
            EnemyWeapon.DIVE_BOLTS -> fireDive(bullets, sounds)
            EnemyWeapon.MISSILE -> fireMissiles(bullets, player, 1, sounds)
            EnemyWeapon.BEAM -> {
                beams.spawn(this, player)
                sounds.play(Sfx.SPECIAL, 0.10f, 0.75f)
            }
            EnemyWeapon.MISSILE_SPREAD ->
                if ((alt++ and 1) == 0) fireMissiles(bullets, player, 2, sounds) else fireBolts(bullets, player, 4, 0.30f, 160f, sounds)
        }
    }

    private fun aimAngle(player: Player) = atan2(player.pos.y - pos.y, player.pos.x - pos.x)

    private fun fireBolts(bullets: BulletPool, player: Player, count: Int, spread: Float, speed: Float, sounds: SoundSink) {
        val base = aimAngle(player)
        for (i in 0 until count) {
            val off = if (count == 1) 0f else -spread + 2f * spread * i / (count - 1)
            val a = base + off
            bullets.spawn(pos.x, pos.y + radius, cos(a) * speed, sin(a) * speed, false, 4f, Palette.RED)
        }
        sounds.play(Sfx.SHOT_ENEMY, 0.10f, 0.5f)
    }

    private fun fireDive(bullets: BulletPool, sounds: SoundSink) {
        val vx = vel.x * 0.35f
        for (i in 0 until 2) bullets.spawn(pos.x + if (i == 0) -5f else 5f, pos.y + radius, vx, 300f, false, 3.5f, Palette.ORANGE)
        sounds.play(Sfx.SHOT_ENEMY, 0.10f, 0.8f)
    }

    private fun fireMissiles(bullets: BulletPool, player: Player, n: Int, sounds: SoundSink) {
        val base = aimAngle(player)
        for (i in 0 until n) {
            val off = if (n == 1) 0f else -0.30f + 0.60f * i / (n - 1)
            val a = base + off
            bullets.spawn(pos.x, pos.y + radius, cos(a) * 120f, sin(a) * 120f, false, 6f, Palette.ORANGE)?.let {
                it.missile = true
                it.speed = 150f
                it.turn = 2.1f
                it.steerTime = 2.6f
            }
        }
        sounds.play(Sfx.SHOT_ENEMY, 0.10f, 0.35f)
    }
}

class EnemyPool {
    val items = Array(CAP) { Enemy() }

    fun clear() = items.forEach { it.active = false }

    fun activeCount() = items.count { it.active }

    fun spawn(x: Float, y: Float, kind: EnemyKind, rng: Random): Enemy? =
        items.firstOrNull { !it.active }?.also { it.configure(x, y, kind, rng) }

    fun update(dt: Float, bullets: BulletPool, beams: BeamPool, player: Player, sounds: SoundSink) {
        for (e in items) {
            if (!e.active) continue
            e.steer(dt, player)
            e.update(dt)
            if (e.active) e.tryFire(dt, bullets, beams, player, sounds)
        }
    }

    companion object {
        const val CAP = 64
    }
}
