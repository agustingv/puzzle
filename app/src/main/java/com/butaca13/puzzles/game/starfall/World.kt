package com.butaca13.puzzles.game.starfall

import kotlin.random.Random

/** Where a run is: each level's intro, its three sections and the boss, then the next level or the end. */
enum class Phase { LEVEL_INTRO, SECTION, SECTION_CLEAR, BOSS_INTRO, BOSS, LEVEL_CLEAR, DEAD, VICTORY }

/**
 * A run of the campaign: the player, the enemies, every shot and the boss, and the phases that take each
 * level through its three sections and its boss, then on to the next level or to victory; out of lives,
 * the run is over. [step] moves it on by one tick, with the ship dragged as far as [drag] has gathered.
 */
class World(
    private val rng: Random = Random.Default,
    private val sounds: SoundSink = SoundSink { _, _, _ -> },
    /** Where the run starts: always the first level, but for tests. */
    startLevel: Int = 1,
) {
    val player = Player(sounds)
    val bullets = BulletPool()
    val enemies = EnemyPool()
    val boss = Boss(rng, sounds)
    val starfield = Starfield(rng)
    val planet = Planet()
    val beams = BeamPool()
    val fx = ExplosionPool()
    val missiles = MissilePool()
    val pickups = PickupPool()

    var level = startLevel.coerceIn(1, Config.LEVELS)
        private set
    var section = 1
        private set
    var score = 0
        private set
    var phase = Phase.LEVEL_INTRO
        private set

    /** Seconds in the current phase. */
    var phaseT = 0f
        private set

    /** Seconds since the run began, for blinking and pulsing. */
    var time = 0f
        private set

    /** Whether the run is over and its score can be filed: shortly after dying, or once the ending is read. */
    var finished = false
        private set

    private var script = SectionScript(1, 1, rng)
    private var fired = 0
    private var sectionT = 0f

    private var dragX = 0f
    private var dragY = 0f
    private var bombAsked = false

    init {
        beginLevelIntro()
    }

    val inFight get() = phase == Phase.SECTION || phase == Phase.BOSS

    /** The finger moved the ship this far, in the world's units, since the last tick. */
    fun drag(dx: Float, dy: Float) {
        dragX += dx
        dragY += dy
    }

    /** The bomb button: fired at the next tick, if there is a bomb left and a fight to use it in. */
    fun askBomb() {
        bombAsked = true
    }

    /** A tap on the ending, or on the game over once it has been seen a moment: on to the scores. */
    fun skipEnding() {
        if ((phase == Phase.DEAD && phaseT >= 0.5f) || (phase == Phase.VICTORY && phaseT >= 1f)) finished = true
    }

    private fun setPhase(p: Phase) {
        phase = p
        phaseT = 0f
    }

    private fun beginLevelIntro() {
        bullets.clear()
        enemies.clear()
        beams.clear()
        fx.clear()
        missiles.clear()
        pickups.clear()
        boss.active = false
        planet.setLevel(level)
        player.recenter()
        setPhase(Phase.LEVEL_INTRO)
    }

    private fun beginSection(n: Int) {
        section = n
        script = SectionScript(level, n, rng)
        fired = 0
        sectionT = 0f
        setPhase(Phase.SECTION)
    }

    private fun beginBossIntro() {
        bullets.clear()
        enemies.clear()
        beams.clear()
        missiles.clear()
        pickups.clear()
        player.recenter()
        setPhase(Phase.BOSS_INTRO)
    }

    fun step(dt: Float = Config.TICK) {
        time += dt
        starfield.update(dt)
        planet.update(dt)
        phaseT += dt

        if (bombAsked) {
            bombAsked = false
            if (inFight && player.useSpecial()) launchSpecial()
        }

        when (phase) {
            Phase.LEVEL_INTRO -> if (phaseT >= 3f) beginSection(1)
            Phase.SECTION -> runSection(dt)
            Phase.SECTION_CLEAR -> if (phaseT >= 1.6f) {
                if (section >= Config.SECTIONS_PER_LEVEL) beginBossIntro() else beginSection(section + 1)
            }
            Phase.BOSS_INTRO -> if (phaseT >= 2.2f) {
                boss.spawn(level)
                setPhase(Phase.BOSS)
            }
            Phase.BOSS -> runBoss(dt)
            Phase.LEVEL_CLEAR -> if (phaseT >= 2.6f) {
                if (level >= Config.LEVELS) {
                    setPhase(Phase.VICTORY)
                    sounds.play(Sfx.FANFARE)
                } else {
                    level++
                    section = 1
                    beginLevelIntro()
                }
            }
            Phase.DEAD -> {
                fx.update(dt)
                if (phaseT >= 2.4f) finished = true
            }
            Phase.VICTORY -> Unit
        }
        // A drag outside a fight is not saved up for the next one.
        dragX = 0f
        dragY = 0f
    }

    private fun runSection(dt: Float) {
        sectionT += dt
        while (fired < script.events.size && sectionT >= script.events[fired].at) {
            val e = script.events[fired]
            Formations.spawn(enemies, e.formation, e.kind, rng)
            fired++
        }
        stepWorld(dt)
        if (player.alive && fired >= script.events.size && enemies.activeCount() == 0 && sectionT >= script.lastAt + 2.5f) {
            setPhase(Phase.SECTION_CLEAR)
        }
    }

    private fun runBoss(dt: Float) {
        boss.update(dt, bullets, beams, player)
        stepWorld(dt)
        if (player.alive && !boss.active) {
            score += 1000 * level
            // The boss's last shots go with it, rather than hang still over the banner.
            bullets.clearEnemy()
            beams.clear()
            setPhase(Phase.LEVEL_CLEAR)
        }
    }

    /** The player, the shots and the collisions: what every fight shares. */
    private fun stepWorld(dt: Float) {
        player.update(dt, dragX, dragY, firing = true, bullets)
        enemies.update(dt, bullets, beams, player, sounds)
        bullets.update(dt, player, enemies, boss)
        beams.update(dt, sounds)
        missiles.update(dt)
        pickups.update(dt)
        fx.update(dt)

        val wasAlive = player.alive
        val livesBefore = player.lives
        resolveCollisions()
        if (wasAlive && !player.alive) {
            fx.spawn(player.pos.x, player.pos.y, player.radius * 4f)
            sounds.play(Sfx.EXPLOSION_BIG)
            setPhase(Phase.DEAD)
        } else if (player.lives < livesBefore) {
            sounds.play(Sfx.HIT)
        }
    }

    /** The bomb: every hostile shot wiped out, and a missile sent after each enemy, plus a few more. */
    private fun launchSpecial() {
        sounds.play(Sfx.SPECIAL)
        bullets.clearEnemy()
        beams.clear()
        var launched = 0
        for (e in enemies.items) {
            if (!e.active) continue
            val ang = rng.value(-70, 70) / 100f
            missiles.launch(player.pos.x, player.pos.y, ang, e) ?: break
            launched++
        }
        val extra = minOf(6, MissilePool.CAP - launched)
        for (i in 0 until extra) {
            val ang = if (extra > 1) -0.9f + 1.8f * i / (extra - 1) else 0f
            missiles.launch(player.pos.x, player.pos.y, ang, null)
        }
    }

    private fun resolveCollisions() {
        // The player's shots against the boss, then the enemies.
        for (b in bullets.items) {
            if (!b.active || !b.fromPlayer) continue
            if (boss.hittable() && circles(b.pos, b.radius, boss.pos, boss.radius)) {
                boss.damage(b.damage)
                fx.spawn(b.pos.x, b.pos.y, 14f)
                if (!boss.active) {
                    score += 500 * level
                    repeat(6) {
                        fx.spawn(boss.pos.x + rng.value(-30, 30), boss.pos.y + rng.value(-24, 24), boss.radius * 1.4f)
                    }
                    sounds.play(Sfx.EXPLOSION_BIG)
                }
                // One big target: even the railgun's slug stops there.
                b.active = false
                continue
            }
            for (e in enemies.items) {
                if (!e.active || !circles(b.pos, b.radius, e.pos, e.radius)) continue
                hurtEnemy(e, b.damage)
                if (b.aoe > 0f) {
                    // The spread battery splashes the neighbours.
                    for (e2 in enemies.items) {
                        if (e2.active && e2 !== e && circles(b.pos, b.aoe, e2.pos, e2.radius)) hurtEnemy(e2, 1f)
                    }
                    fx.spawn(b.pos.x, b.pos.y, b.aoe * 1.5f)
                    b.active = false
                    break
                }
                if (b.pierce > 0) {
                    b.pierce--
                    continue
                }
                b.active = false
                break
            }
        }

        // The bomb's missiles against the boss and the enemies.
        for (m in missiles.items) {
            if (!m.active) continue
            if (boss.hittable() && circles(m.pos, m.radius, boss.pos, boss.radius)) {
                m.deactivate()
                boss.damage(1f)
                fx.spawn(m.pos.x, m.pos.y, 16f)
                continue
            }
            for (e in enemies.items) {
                if (e.active && circles(m.pos, m.radius, e.pos, e.radius)) {
                    m.deactivate()
                    e.active = false
                    score += e.scoreValue
                    fx.spawn(e.pos.x, e.pos.y, e.radius * 3.2f)
                    sounds.play(Sfx.EXPLOSION_SMALL, 0.16f)
                    break
                }
            }
        }

        // The player's fire can shoot down the guided missiles coming at it.
        for (mb in bullets.items) {
            if (!mb.active || mb.fromPlayer || !mb.missile) continue
            var killed = false
            for (b in bullets.items) {
                if (b.active && b.fromPlayer && circles(b.pos, b.radius, mb.pos, mb.radius)) {
                    b.active = false
                    killed = true
                    break
                }
            }
            if (!killed) {
                for (m in missiles.items) {
                    if (m.active && circles(m.pos, m.radius, mb.pos, mb.radius)) {
                        m.deactivate()
                        killed = true
                        break
                    }
                }
            }
            if (killed) {
                mb.active = false
                fx.spawn(mb.pos.x, mb.pos.y, 14f)
                score += 25
                sounds.play(Sfx.HIT, 0.16f, 0.45f)
            }
        }

        // Power-ups, which can be picked up even while immune.
        for (p in pickups.items) {
            if (!p.active || !circles(p.pos, p.radius, player.pos, player.radius + 6f)) continue
            p.active = false
            collect(p.kind)
        }

        if (player.damageImmune) return

        if (beams.strike(player)) {
            player.hit()
            return
        }
        for (b in bullets.items) {
            if (b.active && !b.fromPlayer && circles(b.pos, b.radius, player.pos, player.radius)) {
                b.active = false
                player.hit()
                return
            }
        }
        for (e in enemies.items) {
            if (e.active && circles(e.pos, e.radius, player.pos, player.radius)) {
                e.active = false
                fx.spawn(e.pos.x, e.pos.y, e.radius * 3.2f)
                player.hit()
                return
            }
        }
        if (boss.hittable() && circles(boss.pos, boss.radius, player.pos, player.radius)) player.hit()
    }

    /** Takes [dmg] off an enemy; finished off, it scores, blows up, and sometimes leaves a power-up. */
    private fun hurtEnemy(e: Enemy, dmg: Float) {
        e.hp -= dmg
        if (e.hp > 0f) return
        e.active = false
        score += e.scoreValue
        fx.spawn(e.pos.x, e.pos.y, e.radius * 3.2f)
        sounds.play(Sfx.EXPLOSION_SMALL, 0.12f)
        if (rng.value(0, 99) < Config.POWERUP_DROP_PCT) pickups.spawn(e.pos.x, e.pos.y, PickupKind.random(rng), rng)
    }

    private fun collect(kind: PickupKind) {
        when (kind) {
            PickupKind.SHIELD -> player.grantShield()
            PickupKind.W_SPREAD -> player.giveWeapon(WeaponType.SPREAD)
            PickupKind.W_RAIL -> player.giveWeapon(WeaponType.RAIL)
            PickupKind.W_HOMING -> player.giveWeapon(WeaponType.HOMING)
            PickupKind.BOMB -> if (player.specialCharges >= Player.SPECIAL_MAX) {
                // Bombs full: points instead.
                score += 200
                sounds.play(Sfx.POWERUP, 0f, 0.6f)
                return
            } else {
                player.addSpecialCharge()
            }
        }
        sounds.play(Sfx.POWERUP)
    }
}
