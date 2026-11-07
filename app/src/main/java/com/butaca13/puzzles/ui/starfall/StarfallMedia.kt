package com.butaca13.puzzles.ui.starfall

import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.butaca13.puzzles.game.starfall.Sfx
import com.butaca13.puzzles.game.starfall.SoundSink
import com.butaca13.puzzles.ui.common.GameSounds
import com.butaca13.puzzles.ui.common.loudness
import kotlin.random.Random

/** One region of the atlas: where it lies, and how many frames it holds side by side. */
class SpriteRegion(val x: Int, val y: Int, val w: Int, val h: Int, val frames: Int)

/**
 * The original's sprite sheet, assets/starfall/atlas.png, with the regions its atlas.txt names: drawn
 * centred, at a height, rotated and tinted, crisp as pixel art.
 */
class StarfallAtlas private constructor(private val image: ImageBitmap, private val regions: Map<String, SpriteRegion>) {

    /** Tints, made once each: drawing is every frame. */
    private val tints = HashMap<Int, ColorFilter>()

    fun frames(name: String) = regions[name]?.frames ?: 1

    /**
     * Draws [name] centred on ([cx], [cy]), scaled to [height] with the sprite's own proportions, turned
     * [degrees] clockwise, and multiplied by [tint] unless it is white.
     */
    fun DrawScope.sprite(name: String, cx: Float, cy: Float, height: Float, degrees: Float = 0f, tint: Int = WHITE, alpha: Float = 1f, frame: Int = 0) {
        val r = regions[name] ?: return
        val fw = r.w / r.frames
        val k = height / r.h
        translate(cx, cy) {
            rotate(degrees, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                    drawImage(
                        image = image,
                        srcOffset = IntOffset(r.x + (frame % r.frames) * fw, r.y),
                        srcSize = IntSize(fw, r.h),
                        dstOffset = IntOffset(-fw / 2, -r.h / 2),
                        dstSize = IntSize(fw, r.h),
                        alpha = alpha,
                        colorFilter = if (tint == WHITE) null else tints.getOrPut(tint) { ColorFilter.tint(Color(tint), BlendMode.Modulate) },
                        filterQuality = FilterQuality.None,
                    )
                }
            }
        }
    }

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()

        fun load(context: Context): StarfallAtlas {
            val assets = context.assets
            val image = assets.open("starfall/atlas.png").use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })!!.asImageBitmap()
            }
            val regions = assets.open("starfall/atlas.txt").bufferedReader().readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val f = line.split(Regex("\\s+"))
                    if (f.size < 5) return@mapNotNull null
                    f[0] to SpriteRegion(f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), f.getOrNull(5)?.toIntOrNull()?.coerceAtLeast(1) ?: 1)
                }
                .toMap()
            return StarfallAtlas(image, regions)
        }
    }
}

/**
 * The original's sound effects, assets/starfall/\*.wav, played through a pool so that rapid fire overlaps.
 * They follow the app's own sound setting and volume.
 */
class StarfallAudio(context: Context, private val settings: GameSounds) : SoundSink {
    private val pool = SoundPool.Builder()
        .setMaxStreams(12)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = Sfx.entries.associateWith { sfx ->
        context.assets.openFd("starfall/${sfx.file}.wav").use { pool.load(it, 1) }
    }

    override fun play(sfx: Sfx, pitchJitter: Float, volume: Float) {
        if (!settings.enabled) return
        val rate = if (pitchJitter > 0f) 1f + Random.nextFloat() * 2 * pitchJitter - pitchJitter else 1f
        val level = (volume * loudness(settings.volume)).coerceIn(0f, 1f)
        pool.play(ids.getValue(sfx), level, level, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun release() = pool.release()
}
