package com.agustingv.puzzles.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agustingv.puzzles.R
import com.agustingv.puzzles.data.AppStorage
import com.agustingv.puzzles.data.GameEvent
import com.agustingv.puzzles.data.KeyValueStore

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
 * The game's sound effects, made by tools/generate_sounds.py. They play as media, so they follow the
 * phone's media volume. Loading takes a moment and they are small, so they stay loaded once asked for.
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

    fun toggle() {
        enabled = !enabled
        store.put(ENABLED_KEY, if (enabled) ON else OFF)
        // Turning it on answers with a sound: it also tells how loud the phone is.
        play(GameEvent.HIT)
    }

    override fun play(event: GameEvent): Boolean {
        if (!enabled) return false
        return pool.play(sounds.getValue(event), 1f, 1f, 1, 0, 1f) != 0
    }

    internal fun release() = pool.release()

    companion object {
        private const val MAX_STREAMS = 4
        private const val ENABLED_KEY = "sound-enabled"
        private const val ON = "on"
        private const val OFF = "off"

        @Volatile
        private var instance: GameSounds? = null

        fun of(context: Context): GameSounds = instance ?: synchronized(this) {
            instance ?: GameSounds(context.applicationContext, AppStorage.of(context)).also { instance = it }
        }
    }
}
