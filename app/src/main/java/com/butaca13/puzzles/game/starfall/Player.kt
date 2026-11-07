package com.butaca13.puzzles.game.starfall

import kotlin.math.cos
import kotlin.math.sin

/** Front gun, swapped by weapon power-ups. A run starts, and falls back to, the blaster; the rest are timed. */
enum class WeaponType {
    /** Rapid single shots ahead. */
    BLASTER,

    /** A fan of splash pellets. */
    SPREAD,

    /** A fast slug that pierces and hits hard. */
    RAIL,

    /** Guided shots that chase the nearest enemy. */
    HOMING,
}

/**
 * The player's ship: a cannon that fires on its own, lives, and a short invulnerability after appearing
 * or being hit. It goes where the finger drags it, as far as [move] is told each tick.
 */
class Player(private val sounds: SoundSink) {
    val pos = Vec()
    val radius = 12f
    var lives = 0
        private set
    var alive = false
        private set
    var specialCharges = 0
        private set
    var weapon = WeaponType.BLASTER
        private set

    /** The original's developer toggle: nothing hurts. Only tests use it, to walk the whole campaign. */
    internal var godmode = false

    private var fireCooldown = 0f
    private var invulnTimer = 0f
    private var weaponTimer = 0f
    private var shieldTimer = 0f

    val invulnerable get() = invulnTimer > 0f

    /** Whether incoming fire is ignored: the blink after a hit, the bomb's immunity, or a shield. */
    val damageImmune get() = invulnTimer > 0f || shieldTimer > 0f

    val shielded get() = shieldTimer > 0f
    val weaponFrac get() = (weaponTimer / WEAPON_TIME).coerceIn(0f, 1f)
    val shieldFrac get() = (shieldTimer / SHIELD_TIME).coerceIn(0f, 1f)

    init {
        reset()
    }

    fun reset() {
        pos.set(Config.SCREEN_W / 2, Config.SCREEN_H - 90f)
        lives = 3
        alive = true
        specialCharges = SPECIAL_MAX
        fireCooldown = 0f
        invulnTimer = 1.5f
        weapon = WeaponType.BLASTER
        weaponTimer = 0f
        shieldTimer = 0f
    }

    /** A weapon power-up: grabbed again, it starts its time over. */
    fun giveWeapon(w: WeaponType) {
        weapon = w
        weaponTimer = WEAPON_TIME
    }

    fun grantShield() {
        shieldTimer = SHIELD_TIME
    }

    /** Spends a bomb, which brings three seconds of immunity. */
    fun useSpecial(): Boolean {
        if (specialCharges <= 0) return false
        specialCharges--
        invulnTimer = 3f
        return true
    }

    fun addSpecialCharge() {
        if (specialCharges < SPECIAL_MAX) specialCharges++
    }

    /** One tick: the ship moved by ([dx], [dy]), the timers run down, and the gun fires when it can. */
    fun update(dt: Float, dx: Float, dy: Float, firing: Boolean, bullets: BulletPool) {
        if (invulnTimer > 0f) invulnTimer -= dt
        if (shieldTimer > 0f) shieldTimer -= dt
        if (weaponTimer > 0f) {
            weaponTimer -= dt
            if (weaponTimer <= 0f) weapon = WeaponType.BLASTER
        }

        pos.x = (pos.x + dx).coerceIn(radius, Config.SCREEN_W - radius)
        pos.y = (pos.y + dy).coerceIn(radius, Config.SCREEN_H - radius)

        fireCooldown -= dt
        if (firing && fireCooldown <= 0f) fire(bullets)
    }

    private fun fire(bullets: BulletPool) {
        val mx = pos.x
        val my = pos.y - radius - 4f
        when (weapon) {
            WeaponType.SPREAD -> {
                fireCooldown = 0.20f
                for (i in -2..2) {
                    val ang = -1.5708f + i * 0.16f
                    bullets.spawn(mx, my, cos(ang) * 430f, sin(ang) * 430f, true, 5f, Palette.ORANGE)?.aoe = 26f
                }
                sounds.play(Sfx.SHOT_PLAYER, 0.06f, 0.7f)
            }
            WeaponType.RAIL -> {
                fireCooldown = 0.42f
                bullets.spawn(mx, my, 0f, -1150f, true, 4f, Palette.SKYBLUE)?.let {
                    it.pierce = 6
                    it.damage = 3f
                }
                sounds.play(Sfx.SHOT_PLAYER, 0.05f, 1.35f)
            }
            WeaponType.HOMING -> {
                fireCooldown = 0.22f
                for (i in intArrayOf(-1, 1)) {
                    bullets.spawn(mx + i * 8f, my, i * 70f, -300f, true, 4f, Palette.LIME)?.let {
                        it.missile = true
                        it.speed = 360f
                        it.turn = 6f
                        it.steerTime = 3f
                    }
                }
                sounds.play(Sfx.SHOT_PLAYER, 0.06f, 0.8f)
            }
            WeaponType.BLASTER -> {
                fireCooldown = FIRE_INTERVAL
                bullets.spawn(mx, my, 0f, -520f, true, 3f, Palette.YELLOW)
                sounds.play(Sfx.SHOT_PLAYER, 0.06f, 0.9f)
            }
        }
    }

    fun hit() {
        if (godmode || damageImmune) return
        lives -= 1
        invulnTimer = 2f
        // The power-up gun is lost with the ship.
        weapon = WeaponType.BLASTER
        weaponTimer = 0f
        pos.set(Config.SCREEN_W / 2, Config.SCREEN_H - 90f)
        if (lives <= 0) alive = false
    }

    /** Back to the start between levels and before a boss, keeping the lives. */
    fun recenter() {
        pos.set(Config.SCREEN_W / 2, Config.SCREEN_H - 90f)
        fireCooldown = 0f
        invulnTimer = 1.5f
    }

    companion object {
        const val SPECIAL_MAX = 2
        const val WEAPON_TIME = 15f
        const val SHIELD_TIME = 6f
        private const val FIRE_INTERVAL = 0.14f
    }
}
