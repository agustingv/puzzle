package com.butaca13.puzzles.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.KeyValueStore

/** An interface so that tests can check what is asked for without any audio. */
fun interface SoundPlayer {
    /** Whether a sound started: not if sound is off, or while the sounds are still loading. */
    fun play(event: GameEvent): Boolean
}

@RawRes
internal fun soundOf(event: GameEvent): Int = when (event) {
    GameEvent.TICK -> R.raw.sound_tick
    GameEvent.HIT -> R.raw.sound_hit
    GameEvent.MISS -> R.raw.sound_miss
    GameEvent.WIN -> R.raw.sound_win
    GameEvent.LOSE -> R.raw.sound_lose
}

/**
 * How loud a slider position plays. Ears hear loudness on a curve: with a straight line, most of the
 * slider's travel would sound nearly the same.
 */
internal fun loudness(volume: Float): Float = volume * volume

/**
 * The game's sound effects, made by tools/generate_sounds.py. They play as media, so their [volume]
 * is a share of the phone's media volume. Loading takes a moment and they are small, so they stay
 * loaded once asked for.
 */
@Stable
class GameSounds internal constructor(context: Context, private val store: KeyValueStore) : SoundPlayer {

    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val sounds = GameEvent.entries.associateWith { pool.load(context, soundOf(it), 1) }

    var enabled by mutableStateOf(store.get(ENABLED_KEY) != OFF)
        private set

    /** From 0 to 1. */
    var volume by mutableFloatStateOf(store.get(VOLUME_KEY)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f)
        private set

    fun changeVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        store.put(VOLUME_KEY, volume.toString())
    }

    fun playSample() {
        play(GameEvent.HIT)
    }

    fun toggle() {
        enabled = !enabled
        store.put(ENABLED_KEY, if (enabled) ON else OFF)
        // Turning it on answers with a sound: it also tells how loud the phone is.
        playSample()
    }

    override fun play(event: GameEvent): Boolean {
        if (!enabled) return false
        val level = loudness(volume)
        return pool.play(sounds.getValue(event), level, level, 1, 0, 1f) != 0
    }

    internal fun release() = pool.release()

    companion object {
        private const val MAX_STREAMS = 4
        private const val ENABLED_KEY = "sound-enabled"
        private const val VOLUME_KEY = "sound-volume"
        private const val ON = "on"
        private const val OFF = "off"

        @Volatile
        private var instance: GameSounds? = null

        fun of(context: Context): GameSounds = instance ?: synchronized(this) {
            instance ?: GameSounds(context.applicationContext, AppStorage.of(context)).also { instance = it }
        }
    }
}
