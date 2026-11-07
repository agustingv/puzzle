package com.butaca13.puzzles.game.starfall

import com.butaca13.puzzles.data.KeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StarfallTest {

    private class MemoryStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) {
            map[key] = value
        }
        override fun remove(key: String) {
            map.remove(key)
        }
    }

    /** Steps [world] until [done] or [seconds] have gone by, and says whether it got there. */
    private fun run(world: World, seconds: Int, done: (World) -> Boolean): Boolean {
        repeat(seconds * 60) {
            world.step()
            if (done(world)) return true
        }
        return false
    }

    @Test
    fun `the whole campaign can be walked to the end, every boss falling to the cannon`() {
        val world = World(Random(1))
        world.player.godmode = true

        // A ship sweeping side to side, as a player would, so that the guns reach every boss.
        var t = 0f
        val won = run(world, 60 * 60) {
            t += Config.TICK
            it.drag(kotlin.math.sin(t * 0.8f) * 4f, 0f)
            it.phase == Phase.VICTORY
        }

        assertTrue("stuck in level ${world.level} at ${world.phase}", won)
        assertEquals(5, world.level)
        // Every boss pays its bonus: 1500 a level for the kill and the clear, at the least.
        assertTrue(world.score > (1..5).sumOf { 1500 * it })
    }

    @Test
    fun `a run opens on its level's intro, then the first section`() {
        val world = World(Random(2))

        assertEquals(Phase.LEVEL_INTRO, world.phase)
        run(world, 4) { it.phase == Phase.SECTION }
        assertEquals(Phase.SECTION, world.phase)
        assertEquals(1, world.section)
    }

    @Test
    fun `a ship that never moves is soon shot down, and the run ends`() {
        val world = World(Random(3))

        assertTrue(run(world, 600) { it.finished })
        assertFalse(world.player.alive)
        assertEquals(0, world.player.lives)
    }

    @Test
    fun `the bomb spends a charge, clears the enemy fire and sends missiles`() {
        val world = World(Random(4))
        run(world, 10) { it.enemies.activeCount() > 0 && it.bullets.items.any { b -> b.active && !b.fromPlayer } }

        world.askBomb()
        world.step()

        assertEquals(Player.SPECIAL_MAX - 1, world.player.specialCharges)
        assertTrue(world.bullets.items.none { it.active && !it.fromPlayer })
        assertTrue(world.missiles.items.any { it.active })
        assertTrue(world.player.damageImmune)
    }

    @Test
    fun `a bomb outside a fight is kept`() {
        val world = World(Random(5))

        world.askBomb()
        world.step()

        assertEquals(Player.SPECIAL_MAX, world.player.specialCharges)
    }

    @Test
    fun `the drag moves the ship, but never off the screen`() {
        val world = World(Random(6))
        run(world, 4) { it.phase == Phase.SECTION }
        val x = world.player.pos.x

        world.drag(30f, 0f)
        world.step()
        assertEquals(x + 30f, world.player.pos.x, 0.01f)

        world.drag(-10_000f, 10_000f)
        world.step()
        assertEquals(world.player.radius, world.player.pos.x, 0.01f)
        assertEquals(Config.SCREEN_H - world.player.radius, world.player.pos.y, 0.01f)
    }

    @Test
    fun `sections grow with the level, up to a cap`() {
        // Five waves: a V of 5, a row of 3, two columns of 3, a diamond of 4 and a row of 5.
        assertEquals(23, SectionScript.enemyCount(1, 1))
        assertTrue(SectionScript.enemyCount(5, 3) > SectionScript.enemyCount(1, 1))
        assertEquals(8, SectionScript.burstCount(5, 3))
        assertEquals(SectionScript.burstCount(4, 3), SectionScript.burstCount(5, 3))
    }

    @Test
    fun `bosses get tougher level by level`() {
        val hp = (1..5).map { level -> Boss(Random(0)) { _, _, _ -> }.apply { spawn(level) }.maxHp }

        assertEquals(listOf(75f, 120f, 155f, 230f, 210f), hp)
    }

    @Test
    fun `a beam hurts only once it has charged, and only along its line`() {
        val world = World(Random(7))
        val player = world.player
        val beam = Beam()
        beam.reset(null, player.pos.x, player.pos.y - 300f, player)

        assertFalse(beam.hits(player))
        beam.update(Beam.CHARGE + 0.01f) { _, _, _ -> }
        assertTrue(beam.hits(player))
        assertEquals(5f, Beam.segmentDistance(5f, 5f, 0f, 0f, 0f, 10f), 0.001f)
    }

    @Test
    fun `the table keeps the ten best runs, best first, and says where a new one went`() {
        val store = MemoryStore()
        val scores = HighScores(store)

        assertFalse(scores.qualifies(0))
        for (s in 1..10) scores.add(ScoreEntry(s * 100, 1, s.toLong()))
        assertEquals(1000, scores.entries.first().score)
        assertFalse(scores.qualifies(100))
        assertEquals(2, scores.add(ScoreEntry(850, 3, 99)))
        assertEquals(10, scores.entries.size)
        assertEquals(200, scores.entries.last().score)
        // A tie goes under the runs that were there first.
        assertEquals(2, scores.add(ScoreEntry(900, 2, 100)))
        assertEquals(listOf(1000, 900, 900, 850), scores.entries.take(4).map { it.score })
        assertEquals(1, scores.entries[1].level)
        assertEquals(2, scores.entries[2].level)

        assertEquals(scores.entries, HighScores(store).entries)
    }
}
