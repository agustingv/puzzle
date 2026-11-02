package com.agustingv.puzzles.ui.common

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agustingv.puzzles.data.GameEvent
import com.agustingv.puzzles.data.KeyValueStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A test can not listen: this checks that every sound loads and starts, and that the switch works. */
@RunWith(AndroidJUnit4::class)
class GameSoundsTest {

    private class MemoryStore : KeyValueStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) {
            values[key] = value
        }
        override fun remove(key: String) {
            values.remove(key)
        }
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = MemoryStore()
    private val created = mutableListOf<GameSounds>()

    private fun sounds() = GameSounds(context, store).also { created += it }

    private fun startsWithin(sounds: GameSounds, event: GameEvent, millis: Long = 5000): Boolean {
        val end = SystemClock.elapsedRealtime() + millis
        while (SystemClock.elapsedRealtime() < end) {
            if (sounds.play(event)) return true
            SystemClock.sleep(50)
        }
        return false
    }

    @After
    fun releaseAll() = created.forEach { it.release() }

    @Test
    fun everySoundLoadsAndStarts() {
        val sounds = sounds()

        for (event in GameEvent.entries) {
            assertTrue("$event", startsWithin(sounds, event))
        }
    }

    @Test
    fun theVolumeIsKeptAndStaysInRange() {
        val sounds = sounds()
        assertEquals(1f, sounds.volume)

        sounds.changeVolume(0.4f)
        assertEquals(0.4f, sounds().volume)

        sounds.changeVolume(3f)
        assertEquals(1f, sounds.volume)
        sounds.changeVolume(-1f)
        assertEquals(0f, sounds.volume)
    }

    @Test
    fun aBadSavedVolumeIsStillUsable() {
        for (saved in listOf("nonsense", "7", "-2")) {
            store.values["sound-volume"] = saved
            assertTrue(saved, sounds().volume in 0f..1f)
        }
        store.values["sound-volume"] = "nonsense"
        assertEquals(1f, sounds().volume)
    }

    @Test
    fun turnedOffNothingPlaysAndTheChoiceIsKept() {
        val sounds = sounds()
        assertTrue(startsWithin(sounds, GameEvent.WIN))

        sounds.toggle()
        assertFalse(sounds.enabled)
        assertFalse(sounds.play(GameEvent.WIN))

        val reopened = sounds()
        assertFalse(reopened.enabled)
        assertFalse(reopened.play(GameEvent.WIN))

        reopened.toggle()
        assertTrue(sounds().enabled)
    }
}
