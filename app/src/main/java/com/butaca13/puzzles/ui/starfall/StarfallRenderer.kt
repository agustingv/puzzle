package com.butaca13.puzzles.ui.starfall

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import com.butaca13.puzzles.game.starfall.Beam
import com.butaca13.puzzles.game.starfall.Config
import com.butaca13.puzzles.game.starfall.Palette
import com.butaca13.puzzles.game.starfall.Phase
import com.butaca13.puzzles.game.starfall.Starfield
import com.butaca13.puzzles.game.starfall.WeaponType
import com.butaca13.puzzles.game.starfall.World
import com.butaca13.puzzles.game.starfall.EnemyKind
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** The words drawn over the game, in the app's language: resolved once, outside drawing. */
class StarfallTexts(
    val score: (Int) -> String,
    val levelTag: (Int, Int) -> String,
    val bossTag: (Int) -> String,
    val weapon: (WeaponType) -> String,
    val shield: String,
    val levelNumber: (Int) -> String,
    val levelTitle: (Int) -> String,
    val levelTagline: (Int) -> String,
    val bossName: (Int) -> String,
    val sectionClear: (Int, Int) -> String,
    val levelClear: (Int) -> String,
    val warning: String,
    val gameOver: String,
    val gameOverHint: String,
    val victory: String,
    val victoryHint: String,
)

private fun c(argb: Int, alpha: Float = 1f) = Color(argb).copy(alpha = Color(argb).alpha * alpha)

/** Draws text in the world's units, the pixel font's stand-in: bold monospace. */
class StarfallText(private val measurer: TextMeasurer, private val density: Density) {
    private fun size(px: Float): TextUnit = with(density) { px.toSp() }

    private fun style(px: Float, color: Int, alpha: Float) =
        TextStyle(color = c(color, alpha), fontSize = size(px), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

    fun width(text: String, px: Float) = measurer.measure(text, style(px, Palette.WHITE, 1f)).size.width.toFloat()

    fun DrawScope.left(text: String, x: Float, y: Float, px: Float, color: Int, alpha: Float = 1f) {
        drawText(measurer, text, Offset(x, y), style(px, color, alpha))
    }

    /** Centred across the screen, smaller if it would not fit. */
    fun DrawScope.centered(text: String, y: Float, px: Float, color: Int, alpha: Float = 1f) {
        var size = px
        var layout = measurer.measure(text, style(size, color, alpha))
        val room = Config.SCREEN_W - 24f
        if (layout.size.width > room) {
            size *= room / layout.size.width
            layout = measurer.measure(text, style(size, color, alpha))
        }
        drawText(layout, topLeft = Offset((Config.SCREEN_W - layout.size.width) / 2, y + (px - size) / 2))
    }
}

/** Every star of [field] as the small square it is. */
fun DrawScope.drawStars(field: Starfield) {
    for (s in field.stars) {
        drawRect(Color(s.bright, s.bright, (s.bright + 30).coerceAtMost(255)), Offset(s.x, s.y), Size(s.size, s.size))
    }
}

/** The whole world at its logical size, as the original's PlayScreen draws it. */
fun DrawScope.drawWorld(world: World, atlas: StarfallAtlas, text: StarfallText, texts: StarfallTexts) = with(atlas) {
    val t = world.time
    drawRect(Color(Palette.BG), size = Size(Config.SCREEN_W, Config.SCREEN_H))

    val planet = world.planet
    sprite("planet_${planet.level}", planet.x, planet.y, planet.size, alpha = 0.92f)
    drawStars(world.starfield)

    for (p in world.pickups.items) {
        if (!p.active) continue
        val pulse = 0.85f + 0.15f * sin(t * 8f)
        sprite("pickup", p.pos.x, p.pos.y, p.radius * 2.4f * pulse, tint = p.kind.colour)
        with(text) { left(p.kind.glyph, p.pos.x - text.width(p.kind.glyph, 14f) / 2, p.pos.y - 9f, 14f, Palette.BLACK) }
    }

    for (e in world.enemies.items) {
        if (!e.active) continue
        val (name, scale) = when (e.kind) {
            EnemyKind.GRUNT -> "enemy_grunt" to 2.7f
            EnemyKind.DARTER -> "enemy_darter" to 2.7f
            EnemyKind.WEAVER -> "enemy_weaver" to 2.6f
            EnemyKind.SENTINEL -> "enemy_sentinel" to 2.4f
            EnemyKind.BRUTE -> "enemy_brute" to 2.4f
            EnemyKind.HUNTER -> "enemy_hunter" to 2.6f
            EnemyKind.RACER -> "enemy_racer" to 2.8f
            EnemyKind.WARDEN -> "enemy_warden" to 2.3f
        }
        sprite(name, e.pos.x, e.pos.y, e.radius * scale)
        // The sentinel's ring while it charges.
        if (e.kind == EnemyKind.SENTINEL && e.holding) drawCircle(c(Palette.YELLOW, 0.18f), e.radius + 4f, Offset(e.pos.x, e.pos.y))
        if (e.hpMax > 1f && e.hp < e.hpMax) {
            drawRect(Color(Palette.YELLOW), Offset(e.pos.x - e.radius, e.pos.y - e.radius - 8f), Size(e.radius * 2f * (e.hp / e.hpMax), 3f))
        }
    }

    val boss = world.boss
    if (world.phase == Phase.BOSS && boss.active) {
        // The first boss's sprite is unusually wide, so it takes a smaller height to look as big.
        sprite("boss_${boss.level}", boss.pos.x, boss.pos.y, boss.radius * if (boss.level == 1) 1.5f else 2.6f)
    }

    for (b in world.beams.items) if (b.active) drawBeam(b, t)

    for (b in world.bullets.items) {
        if (!b.active) continue
        when {
            b.missile -> sprite("missile", b.pos.x, b.pos.y, b.radius * 5f, degrees = heading(b.vel.x, b.vel.y), tint = b.color)
            b.pierce > 0 -> {
                // The railgun's slug: a bright streak.
                drawRect(c(Palette.SKYBLUE, 0.7f), Offset(b.pos.x - 2.5f, b.pos.y - 15f), Size(5f, 30f))
                drawRect(Color(Palette.RAYWHITE), Offset(b.pos.x - 1f, b.pos.y - 17f), Size(2f, 34f))
            }
            b.aoe > 0f -> {
                drawCircle(Color(Palette.ORANGE), b.radius, Offset(b.pos.x, b.pos.y))
                drawCircle(Color(Palette.YELLOW), b.radius * 0.5f, Offset(b.pos.x, b.pos.y))
            }
            b.fromPlayer -> sprite("bullet_player", b.pos.x, b.pos.y, b.radius * 4.5f)
            else -> sprite("bullet_enemy", b.pos.x, b.pos.y, b.radius * 3.2f)
        }
    }

    for (m in world.missiles.items) {
        if (m.active) sprite("missile", m.pos.x, m.pos.y, m.radius * 4.5f, degrees = heading(m.vel.x, m.vel.y))
    }

    val player = world.player
    if (player.alive) {
        val p = player.pos
        val r = player.radius
        if (player.shielded) {
            val pulse = 0.85f + 0.15f * sin(t * 8f)
            val a = (player.shieldFrac + 0.15f).coerceIn(0f, 1f)
            drawCircle(c(Palette.SKYBLUE, 0.10f * a), r * 2.4f * pulse, Offset(p.x, p.y))
            drawCircle(c(Palette.SKYBLUE, 0.14f * a), r * 2f * pulse, Offset(p.x, p.y))
        }
        // It blinks while it can't be hit.
        if (!(player.invulnerable && (t * 20f).toInt() % 2 == 0)) {
            val flick = if ((t * 60f) % 2 < 1) 1f else 0.6f
            val flame = Path().apply {
                moveTo(p.x - r * 0.4f, p.y + r)
                lineTo(p.x, p.y + r + 10f * flick)
                lineTo(p.x + r * 0.4f, p.y + r)
                close()
            }
            drawPath(flame, Color(Palette.ORANGE))
            sprite("player", p.x, p.y, r * 3.1f)
        }
    }

    val frames = frames("explosion")
    for (e in world.fx.items) {
        if (!e.active) continue
        val frame = ((e.t / e.life) * frames).toInt().coerceAtMost(frames - 1)
        sprite("explosion", e.pos.x, e.pos.y, e.size, frame = frame)
    }

    drawHud(world, atlas, text, texts)
    drawPhase(world, text, texts)
}

/** The angle a sprite pointing up turns to head along (vx, vy). */
private fun heading(vx: Float, vy: Float) = Math.toDegrees(atan2(vy, vx).toDouble()).toFloat() + 90f

private fun DrawScope.drawBeam(b: Beam, t: Float) {
    val o = Offset(b.origin.x, b.origin.y)
    val end = Offset(b.origin.x + cos(b.angle) * Beam.LEN, b.origin.y + sin(b.angle) * Beam.LEN)
    if (b.charging) {
        val k = 1f - b.chargeT / Beam.CHARGE
        drawLine(c(Palette.YELLOW, 0.20f + 0.45f * k), o, end, 1f + 2f * k)
        drawCircle(c(Palette.RAYWHITE, 0.2f + 0.6f * k), 3f + 4f * k, o)
        return
    }
    val flick = 0.82f + 0.18f * sin(t * 55f)
    drawLine(c(Palette.RED, 0.55f), o, end, Beam.HALF_W * 2f * flick)
    drawLine(c(Palette.ORANGE, 0.85f), o, end, Beam.HALF_W * 1.1f * flick)
    drawLine(Color(Palette.RAYWHITE), o, end, Beam.HALF_W * 0.45f)
    drawCircle(c(Palette.RAYWHITE, 0.9f), Beam.HALF_W * 1.6f, o)
}

/** Room left at the top right for the pause button, which the screen lays over the game. */
const val PAUSE_ROOM = 44f

private fun DrawScope.drawHud(world: World, atlas: StarfallAtlas, text: StarfallText, texts: StarfallTexts) = with(atlas) {
    val player = world.player
    val score = texts.score(world.score)
    with(text) { left(score, 12f, 10f, 20f, Palette.RAYWHITE) }

    val tag = if (world.phase == Phase.BOSS || world.phase == Phase.BOSS_INTRO) texts.bossTag(world.level) else texts.levelTag(world.level, world.section)
    val scoreRight = 12f + text.width(score, 20f)
    with(text) { left(tag, scoreRight + 10f, 13f, 16f, Palette.SKYBLUE) }

    // The lives, under the pause button.
    for (i in 0 until player.lives) sprite("player", Config.SCREEN_W - 22f - i * 20f, PAUSE_ROOM + 14f, 20f)

    if (player.weapon != WeaponType.BLASTER) {
        with(text) { left(texts.weapon(player.weapon), 20f, 60f, 14f, Palette.ORANGE) }
        drawRect(c(Palette.WHITE, 0.2f), Offset(20f, 80f), Size(104f, 4f))
        drawRect(Color(Palette.ORANGE), Offset(20f, 80f), Size(104f * player.weaponFrac, 4f))
    }
    if (player.shielded) {
        with(text) { left(texts.shield, Config.SCREEN_W - 20f - 104f, 76f, 14f, Palette.SKYBLUE) }
        drawRect(c(Palette.WHITE, 0.2f), Offset(Config.SCREEN_W - 124f, 96f), Size(104f, 4f))
        drawRect(Color(Palette.SKYBLUE), Offset(Config.SCREEN_W - 124f, 96f), Size(104f * player.shieldFrac, 4f))
    }

    // The bombs left, at the bottom left.
    for (i in 0 until player.specialCharges) sprite("missile", 20f + i * 22f, Config.SCREEN_H - 20f, 18f)
}

private fun DrawScope.drawPhase(world: World, text: StarfallText, texts: StarfallTexts) = with(text) {
    val mid = Config.SCREEN_H / 2
    when (world.phase) {
        Phase.LEVEL_INTRO -> {
            drawRect(c(Palette.BLACK, 0.55f), size = Size(Config.SCREEN_W, Config.SCREEN_H))
            centered(texts.levelNumber(world.level), mid - 70f, 30f, Palette.LIGHTGRAY)
            centered(texts.levelTitle(world.level), mid - 30f, 40f, Palette.RAYWHITE)
            centered(texts.levelTagline(world.level), mid + 24f, 15f, Palette.SKYBLUE)
        }
        Phase.SECTION_CLEAR -> banner(texts.sectionClear(world.level, world.section), text)
        Phase.BOSS_INTRO -> {
            val on = (world.phaseT * 4f).toInt() % 2 == 0
            banner(if (on) "${texts.warning} - ${texts.bossName(world.level)}" else texts.warning, text)
        }
        Phase.BOSS -> {
            val boss = world.boss
            if (boss.hittable()) {
                val w = Config.SCREEN_W - 40f
                drawRect(c(Palette.WHITE, 0.15f), Offset(20f, 40f), Size(w, 10f))
                drawRect(Color(Palette.RED), Offset(20f, 40f), Size(w * (boss.hp / boss.maxHp).coerceIn(0f, 1f), 10f))
                drawRect(c(Palette.WHITE, 0.4f), Offset(20f, 40f), Size(w, 10f), style = Stroke(1f))
            }
        }
        Phase.LEVEL_CLEAR -> banner(texts.levelClear(world.level), text)
        Phase.DEAD -> overlay(texts.gameOver, texts.gameOverHint, text)
        Phase.VICTORY -> {
            overlay(texts.victory, texts.victoryHint, text)
            // The medal the ending wins, over the title.
            centered(MEDAL, Config.SCREEN_H / 2 - 150f, 80f, Palette.WHITE)
        }
        Phase.SECTION -> Unit
    }
}

private const val MEDAL = "🎖️"

private fun DrawScope.banner(message: String, text: StarfallText) = with(text) {
    val stripY = Config.SCREEN_H / 2 - 34f
    drawRect(c(Palette.BLACK, 0.55f), Offset(0f, stripY), Size(Config.SCREEN_W, 68f))
    centered(message, stripY + 15f, 34f, Palette.RAYWHITE)
}

/** How dark the game over and the ending make the screen; the screen dims beyond the play area by as much. */
const val END_DIM = 0.65f

/** Whether the run is over, with the game over or the ending showing. */
val World.ended: Boolean get() = phase == Phase.DEAD || phase == Phase.VICTORY

private fun DrawScope.overlay(title: String, hint: String, text: StarfallText) = with(text) {
    drawRect(c(Palette.BLACK, END_DIM), size = Size(Config.SCREEN_W, Config.SCREEN_H))
    centered(title, Config.SCREEN_H / 2 - 40f, 44f, Palette.RAYWHITE)
    centered(hint, Config.SCREEN_H / 2 + 18f, 18f, Palette.LIGHTGRAY)
}
