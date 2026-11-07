package com.butaca13.puzzles.ui.starfall

import androidx.activity.compose.BackHandler
import android.app.Activity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.starfall.Config
import com.butaca13.puzzles.game.starfall.Palette
import com.butaca13.puzzles.game.starfall.Phase
import com.butaca13.puzzles.ui.common.PrizeNote
import com.butaca13.puzzles.game.starfall.WeaponType
import java.text.DateFormat
import java.util.Date

private val Bg = Color(Palette.BG)
private val Pixel = FontFamily.Monospace

/** Where the title's ship bobs, between the subtitle and the story. */
private const val TITLE_SHIP_Y = 225f

/** The least room below the game worth making a panel of. */
private val MinPanel = 110.dp

@Composable
fun StarfallScreen(viewModel: StarfallViewModel, onExit: () -> Unit) {
    BackHandler {
        when {
            viewModel.stage == StarfallStage.PLAYING && viewModel.paused -> viewModel.resume()
            viewModel.stage == StarfallStage.PLAYING -> viewModel.pause()
            viewModel.stage == StarfallStage.SCORES -> viewModel.toTitle()
            else -> onExit()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.pause() }

    // The fixed-step clock: every frame runs as many 1/60 s ticks as the time since the last one holds.
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(viewModel) {
        var last = -1L
        var acc = 0f
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last >= 0) acc += ((now - last) / 1e9f).coerceAtMost(0.25f)
                last = now
                var ticks = 0
                while (acc >= Config.TICK) {
                    acc -= Config.TICK
                    ticks++
                }
                viewModel.advance(ticks)
                frame = now
            }
        }
    }

    val texts = rememberTexts()
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val density = LocalDensity.current
    val text = remember(measurer, density) { StarfallText(measurer, density) }

    // Light status bar icons over the night sky, back to the app's own on leaving.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (wasLight != null) controller.isAppearanceLightStatusBars = wasLight }
    }

    val statusBar = WindowInsets.statusBars.getTop(density)
    val navigationBar = WindowInsets.navigationBars.getBottom(density)
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Bg)) {
        // The game's 480 × 720 scaled to fit. On a tall phone it sits at the top, and the room left below
        // is a panel for the bomb button and the finger, which then never covers the action; otherwise it
        // is centred. Beyond it the sky goes on, stars and all, but nothing else: the play area is the
        // original's.
        val scale = minOf(constraints.maxWidth / Config.SCREEN_W, constraints.maxHeight / Config.SCREEN_H)
        val width = with(density) { (Config.SCREEN_W * scale).toDp() }
        val height = with(density) { (Config.SCREEN_H * scale).toDp() }
        val left = (constraints.maxWidth - Config.SCREEN_W * scale) / 2
        val leftover = constraints.maxHeight - Config.SCREEN_H * scale
        val minPanel = with(density) { MinPanel.toPx() }
        val withPanel = leftover >= statusBar + navigationBar + minPanel
        val top = if (withPanel) statusBar.toFloat() else leftover / 2
        val panelHeight = with(density) { (leftover - top - navigationBar).toDp() }
        val currentScale by rememberUpdatedState(scale)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(viewModel) {
                    // Any finger dragging anywhere moves the ship by as much: it never hides under it.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val world = viewModel.world
                            for (change in event.changes) {
                                if (!change.pressed || change.isConsumed) continue
                                val d = change.positionChange()
                                if (world != null && !viewModel.paused) world.drag(d.x / currentScale, d.y / currentScale)
                                if (!change.previousPressed) world?.skipEnding()
                            }
                        }
                    }
                },
        ) {
            // Read so that every frame draws again.
            if (frame < 0) return@Canvas
            translate(left, top) {
                scale(scale, scale, pivot = Offset.Zero) {
                    val world = viewModel.world
                    val playing = world != null && viewModel.stage == StarfallStage.PLAYING
                    val stars = if (playing) world!!.starfield else viewModel.backdrop
                    // The sky above and below, the same stars over again.
                    val above = top / scale
                    var y = -Config.SCREEN_H
                    while (y > -above - Config.SCREEN_H) {
                        translate(0f, y) { drawStars(stars) }
                        y -= Config.SCREEN_H
                    }
                    y = Config.SCREEN_H
                    while (y < Config.SCREEN_H + above) {
                        translate(0f, y) { drawStars(stars) }
                        y += Config.SCREEN_H
                    }
                    clipRect(0f, 0f, Config.SCREEN_W, Config.SCREEN_H) {
                        if (playing) {
                            drawWorld(world!!, viewModel.atlas, text, texts)
                            // Above the panel, things fade out rather than stop at a line.
                            if (withPanel) {
                                drawRect(
                                    Brush.verticalGradient(listOf(Color.Transparent, Bg), startY = Config.SCREEN_H - 24f, endY = Config.SCREEN_H),
                                    topLeft = Offset(0f, Config.SCREEN_H - 24f),
                                    size = androidx.compose.ui.geometry.Size(Config.SCREEN_W, 24f),
                                )
                            }
                        } else {
                            drawStars(stars)
                            if (viewModel.stage == StarfallStage.TITLE) {
                                val bob = kotlin.math.sin(viewModel.titleTime * 2f) * 5f
                                with(viewModel.atlas) { sprite("player", Config.SCREEN_W / 2, TITLE_SHIP_Y + bob, 70f) }
                            }
                        }
                    }
                }
            }
        }

        val unit = width / Config.SCREEN_W
        Box(modifier = Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }.size(width, height)) {
            when (viewModel.stage) {
                StarfallStage.TITLE -> Title(unit, onPlay = viewModel::play, onScores = viewModel::showScores, onExit = onExit)
                StarfallStage.PLAYING -> PlayControls(viewModel, unit, frame, bombHere = !withPanel)
                StarfallStage.SCORES -> Scores(viewModel, unit)
            }
        }
        if (withPanel && viewModel.stage == StarfallStage.PLAYING && !viewModel.paused) {
            Panel(viewModel, frame, modifier = Modifier.offset { IntOffset(0, (top + Config.SCREEN_H * scale).roundToInt()) }.fillMaxWidth().height(panelHeight))
        }
    }
}

/** Sizes in the game's own units, so that the menus scale with it. */
@Composable
private fun Dp.sp(units: Float): TextUnit = with(LocalDensity.current) { (this@sp * units).toSp() }

@Composable
private fun Title(unit: Dp, onPlay: () -> Unit, onScores: () -> Unit, onExit: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        Spacer(Modifier.height(unit * 50))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(unit * 90)) {
            PixelText(stringResource(R.string.starfall_title), unit.sp(60f), Palette.RAYWHITE)
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(unit * 30)) {
            PixelText(stringResource(R.string.starfall_subtitle), unit.sp(18f), Palette.SKYBLUE)
        }
        // Room for the ship, drawn bobbing on the canvas at TITLE_SHIP_Y.
        Spacer(Modifier.height(unit * 110))
        PixelText(
            stringResource(R.string.starfall_story),
            unit.sp(15f),
            Palette.LIGHTGRAY,
            modifier = Modifier.padding(horizontal = unit * 28),
        )
        Spacer(Modifier.weight(1f))
        MenuButton(stringResource(R.string.starfall_play), unit, onPlay, primary = true)
        MenuButton(stringResource(R.string.starfall_scores), unit, onScores)
        MenuButton(stringResource(R.string.starfall_exit), unit, onExit)
        Spacer(Modifier.height(unit * 40))
    }
}

@Composable
private fun PixelText(text: String, size: TextUnit, color: Int, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color(color),
        fontSize = size,
        lineHeight = size * 1.5f,
        fontFamily = Pixel,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun MenuButton(label: String, unit: Dp, onClick: () -> Unit, primary: Boolean = false) {
    val shape = RoundedCornerShape(unit * 6)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(vertical = unit * 6)
            .width(unit * 260)
            .clip(shape)
            .background(Color.White.copy(alpha = if (primary) 0.16f else 0.06f))
            .border(unit * 1.5f, Color(if (primary) Palette.YELLOW else Palette.GRAY), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = unit * 12),
    ) {
        PixelText(label, unit.sp(24f), if (primary) Palette.YELLOW else Palette.LIGHTGRAY)
    }
}

/** Over the running game: the pause button, the bomb button, and the pause menu when paused. */
@Composable
private fun PlayControls(viewModel: StarfallViewModel, unit: Dp, frame: Long, bombHere: Boolean) {
    val world = viewModel.world ?: return
    val pauseLabel = stringResource(R.string.starfall_pause)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(unit * PAUSE_ROOM)
                .semantics { contentDescription = pauseLabel }
                .clickable(role = Role.Button, onClick = viewModel::pause),
        ) {
            PixelText("II", unit.sp(22f), Palette.RAYWHITE)
        }

        // The world is not Compose state: read again on every frame.
        if (bombHere && frame > 0 && !viewModel.paused && world.phase != Phase.DEAD && world.phase != Phase.VICTORY) {
            BombButton(viewModel, frame, unit * 76, Modifier.align(Alignment.BottomEnd).padding(unit * 16))
        }

        if (viewModel.paused) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
            ) {
                PixelText(stringResource(R.string.starfall_paused), unit.sp(44f), Palette.RAYWHITE)
                Spacer(Modifier.height(unit * 40))
                MenuButton(stringResource(R.string.starfall_resume), unit, viewModel::resume, primary = true)
                MenuButton(stringResource(R.string.starfall_quit), unit, viewModel::toTitle)
            }
        }
    }
}

/** Below the game on a tall phone: room for the finger, and the bomb button. */
@Composable
private fun Panel(viewModel: StarfallViewModel, frame: Long, modifier: Modifier) {
    // Nothing to steer or throw once the run is over. The world is not Compose state: [frame] has it read again.
    val phase = viewModel.world?.phase
    if (frame < 0 || phase == Phase.DEAD || phase == Phase.VICTORY) return
    BoxWithConstraints(modifier = modifier.padding(horizontal = 24.dp)) {
        val size = min(maxHeight * 0.7f, 120.dp)
        Text(
            text = stringResource(R.string.starfall_drag_hint),
            color = Color(Palette.GRAY),
            fontFamily = Pixel,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(0.55f),
        )
        if (frame > 0) BombButton(viewModel, frame, size, Modifier.align(Alignment.CenterEnd))
    }
}

/** The bomb, while there is one and a fight to throw it in: how many are left shows on it. */
@Composable
private fun BombButton(viewModel: StarfallViewModel, frame: Long, size: Dp, modifier: Modifier) {
    val world = viewModel.world ?: return
    // The world is not Compose state: [frame] has this read again on every frame.
    if (frame < 0) return
    val ready = world.inFight && world.player.specialCharges > 0
    val label = stringResource(R.string.starfall_bomb)
    val colour = Color(if (ready) Palette.YELLOW else Palette.GRAY)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colour.copy(alpha = if (ready) 0.16f else 0.06f))
            .border(size * 0.03f, colour.copy(alpha = 0.7f), CircleShape)
            .semantics { contentDescription = label }
            .clickable(enabled = ready, role = Role.Button, onClick = world::askBomb),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.starfall_bomb_short), color = colour, fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = with(LocalDensity.current) { (size * 0.18f).toSp() })
            Text("×${world.player.specialCharges}", color = colour, fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = with(LocalDensity.current) { (size * 0.16f).toSp() })
        }
    }
}

/** The table of the ten best runs, with the one just played lit up. */
@Composable
private fun Scores(viewModel: StarfallViewModel, unit: Dp) {
    val entries = viewModel.scores.entries
    val dates = remember { DateFormat.getDateInstance(DateFormat.SHORT) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
    ) {
        Spacer(Modifier.height(unit * 60))
        PixelText(stringResource(R.string.starfall_high_scores), unit.sp(34f), Palette.YELLOW)
        if (viewModel.lastScore > 0 || viewModel.lastRow >= 0) {
            Spacer(Modifier.height(unit * 8))
            PixelText(stringResource(R.string.starfall_your_score, viewModel.lastScore), unit.sp(18f), Palette.LIGHTGRAY)
        }
        viewModel.prize?.let { piece ->
            Spacer(Modifier.height(unit * 16))
            PrizeNote(piece, Modifier.padding(horizontal = unit * 28))
        }
        Spacer(Modifier.height(unit * 28))
        if (entries.isEmpty()) {
            PixelText(stringResource(R.string.starfall_no_scores), unit.sp(16f), Palette.GRAY)
        }
        entries.forEachIndexed { i, entry ->
            val lit = i == viewModel.lastRow
            val color = if (lit) Palette.YELLOW else Palette.RAYWHITE
            val level = stringResource(R.string.starfall_level_short, entry.level)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = unit * 36, vertical = unit * 5)
                    .semantics(mergeDescendants = true) {},
            ) {
                ScoreCell("${i + 1}.", unit, color, Modifier.width(unit * 44), TextAlign.End)
                ScoreCell("%08d".format(entry.score), unit, color, Modifier.weight(1f))
                ScoreCell(level, unit, Palette.SKYBLUE, Modifier.width(unit * 90))
                ScoreCell(dates.format(Date(entry.playedAt)), unit, Palette.GRAY, Modifier.width(unit * 110), TextAlign.End, small = true)
            }
        }
        Spacer(Modifier.weight(1f))
        MenuButton(stringResource(R.string.starfall_play_again), unit, viewModel::play, primary = true)
        MenuButton(stringResource(R.string.starfall_back), unit, viewModel::toTitle)
        Spacer(Modifier.height(unit * 40))
    }
}

@Composable
private fun ScoreCell(text: String, unit: Dp, color: Int, modifier: Modifier, align: TextAlign = TextAlign.Center, small: Boolean = false) {
    Text(
        text = text,
        color = Color(color),
        fontSize = unit.sp(if (small) 14f else 20f),
        fontFamily = Pixel,
        fontWeight = FontWeight.Bold,
        textAlign = align,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun rememberTexts(): StarfallTexts {
    val score = stringResource(R.string.starfall_hud_score)
    val levelTag = stringResource(R.string.starfall_hud_level)
    val bossTag = stringResource(R.string.starfall_hud_boss)
    val spread = stringResource(R.string.starfall_weapon_spread)
    val rail = stringResource(R.string.starfall_weapon_rail)
    val homing = stringResource(R.string.starfall_weapon_homing)
    val blaster = stringResource(R.string.starfall_weapon_blaster)
    val shield = stringResource(R.string.starfall_shield)
    val levelNumber = stringResource(R.string.starfall_level)
    val titles = (1..5).map { stringResource(levelTitles[it - 1]) }
    val taglines = (1..5).map { stringResource(levelTaglines[it - 1]) }
    val bosses = (1..5).map { stringResource(bossNames[it - 1]) }
    val sectionClear = stringResource(R.string.starfall_section_clear)
    val levelClear = stringResource(R.string.starfall_level_clear)
    val warning = stringResource(R.string.starfall_warning)
    val gameOver = stringResource(R.string.starfall_game_over)
    val gameOverHint = stringResource(R.string.starfall_game_over_hint)
    val victory = stringResource(R.string.starfall_victory)
    val victoryHint = stringResource(R.string.starfall_victory_hint)
    return remember(score, levelTag) {
        StarfallTexts(
            score = { score.format(it) },
            levelTag = { l, s -> levelTag.format(l, s) },
            bossTag = { bossTag.format(it) },
            weapon = {
                when (it) {
                    WeaponType.SPREAD -> spread
                    WeaponType.RAIL -> rail
                    WeaponType.HOMING -> homing
                    WeaponType.BLASTER -> blaster
                }
            },
            shield = shield,
            levelNumber = { levelNumber.format(it) },
            levelTitle = { titles[it - 1] },
            levelTagline = { taglines[it - 1] },
            bossName = { bosses[it - 1] },
            sectionClear = { l, s -> sectionClear.format(l, s) },
            levelClear = { levelClear.format(it) },
            warning = warning,
            gameOver = gameOver,
            gameOverHint = gameOverHint,
            victory = victory,
            victoryHint = victoryHint,
        )
    }
}

private val levelTitles = listOf(
    R.string.starfall_level1_title, R.string.starfall_level2_title, R.string.starfall_level3_title,
    R.string.starfall_level4_title, R.string.starfall_level5_title,
)
private val levelTaglines = listOf(
    R.string.starfall_level1_tagline, R.string.starfall_level2_tagline, R.string.starfall_level3_tagline,
    R.string.starfall_level4_tagline, R.string.starfall_level5_tagline,
)
private val bossNames = listOf(
    R.string.starfall_boss1, R.string.starfall_boss2, R.string.starfall_boss3, R.string.starfall_boss4, R.string.starfall_boss5,
)
