package com.butaca13.puzzles.ui.garden

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.game.garden.GardenCell
import com.butaca13.puzzles.game.garden.GardenGame
import com.butaca13.puzzles.ui.common.BoardZoomState
import com.butaca13.puzzles.ui.common.GameActionButton
import com.butaca13.puzzles.ui.common.GameClock
import com.butaca13.puzzles.ui.common.GameLoading
import com.butaca13.puzzles.ui.common.GameResult
import com.butaca13.puzzles.ui.common.GameScaffold
import com.butaca13.puzzles.ui.common.ZoomableBoard
import com.butaca13.puzzles.ui.theme.Accent
import com.butaca13.puzzles.ui.theme.Danger
import com.butaca13.puzzles.ui.theme.GardenBeds
import kotlin.math.ceil
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.DrawScope

@Composable
fun GardenScreen(viewModel: GardenViewModel, onExit: () -> Unit) {
    GameClock(onTick = viewModel.session::tick)

    val title = stringResource(R.string.garden_title)
    val game = viewModel.session.game

    if (game == null) {
        GameLoading(title, onExit)
        return
    }

    GameScaffold(
        title = title,
        session = viewModel.session,
        status = stringResource(R.string.garden_status, game.flowers.size, game.size),
        onExit = onExit,
        actions = {
            if (game.isComplete) {
                GameResult(
                    message = stringResource(R.string.garden_won),
                    record = viewModel.session.lastRecord,
                    onPlayAgain = { viewModel.newGame(game.difficulty) },
                )
            } else {
                GameActionButton(stringResource(R.string.path_reset), viewModel::reset)
            }
        },
        board = { side -> Board(game, side, viewModel) },
    )
}

@Composable
private fun Board(game: GardenGame, side: Dp, viewModel: GardenViewModel) {
    val size = game.size
    val zoom = remember(size) { BoardZoomState() }

    fun cellAt(position: Offset): Int {
        val row = (position.y * size).toInt().coerceIn(0, size - 1)
        val col = (position.x * size).toInt().coerceIn(0, size - 1)
        return row * size + col
    }

    // The cell of the last single tap: a double tap plants only where its first tap fell, and two quick
    // taps on neighbouring cells are two single ones.
    var lastTap by remember { mutableIntStateOf(-1) }
    // Where the finger was at the last report of a drag, as a fraction of the board.
    var dragAt by remember { mutableStateOf<Offset?>(null) }

    ZoomableBoard(
        side = side,
        state = zoom,
        zoomEnabled = false,
        onTap = {
            lastTap = cellAt(it)
            viewModel.tap(lastTap)
        },
        onDoubleTap = {
            val cell = cellAt(it)
            if (cell == lastTap) viewModel.toggleFlower(cell) else viewModel.tap(cell)
            lastTap = -1
        },
        onDragStart = {
            dragAt = it
            viewModel.startDrag(cellAt(it))
        },
        onDrag = { to ->
            // A quick finger moves more than a cell between two reports: every cell on the way counts.
            val from = dragAt ?: to
            val steps = ceil((to - from).getDistance() * size * DRAG_SAMPLES_PER_CELL).toInt().coerceAtLeast(1)
            for (step in 1..steps) viewModel.drag(cellAt(lerp(from, to, step.toFloat() / steps)))
            dragAt = to
        },
        onDragEnd = { dragAt = null },
        onDragCancel = { dragAt = null },
    ) { boardSide ->
        val cell = boardSide / size
        val colors = remember(game.beds) { bedColors(game.beds, size) }

        // One lawn for the whole board, each bed faintly tinted with its own colour, and a hedge around
        // every bed: the hedges are what tell the beds apart, the tint only helps.
        Box(
            modifier = Modifier
                .size(boardSide)
                .clip(RoundedCornerShape(cell * 0.18f))
                .drawWithCache {
                    val step = this.size.width / size
                    val tints = game.beds.map { GardenBeds[colors[it]].copy(alpha = BedTint).compositeOver(Grass) }
                    val turfs = List(size * size) { turfOf(it, step) }
                    val blades = Stroke(width = step * 0.016f, cap = StrokeCap.Round)
                    val hedges = hedgesOf(game.beds, size, step)
                    val hedge = Stroke(width = step * HEDGE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val leaves = leavesOf(hedges.second, step)
                    onDrawBehind {
                        for (index in 0 until size * size) {
                            val topLeft = Offset((index % size) * step, (index / size) * step)
                            drawRect(tints[index], topLeft, Size(step, step))
                            val turf = turfs[index]
                            translate(topLeft.x, topLeft.y) {
                                turf.patches.forEach { (center, radius) -> drawCircle(GrassDark.copy(alpha = 0.14f), radius, center) }
                                drawPath(turf.darkBlades, GrassDark.copy(alpha = 0.7f), style = blades)
                                drawPath(turf.lightBlades, GrassLight.copy(alpha = 0.8f), style = blades)
                            }
                        }
                        // The cells, faintly, within each bed.
                        for (line in 1 until size) {
                            drawLine(GrassDark.copy(alpha = 0.22f), Offset(line * step, 0f), Offset(line * step, this.size.height), step * 0.012f)
                            drawLine(GrassDark.copy(alpha = 0.22f), Offset(0f, line * step), Offset(this.size.width, line * step), step * 0.012f)
                        }
                        drawPath(hedges.first, Hedge, style = hedge)
                        leaves.forEach { (center, radius) -> drawCircle(HedgeLeaf, radius, center) }
                    }
                },
        ) {
            Column {
                for (row in 0 until size) {
                    Row {
                        for (col in 0 until size) {
                            GardenCellView(
                                game, row * size + col, row, col, cell,
                                onTap = { viewModel.tap(row * size + col) },
                                onToggleFlower = { viewModel.toggleFlower(row * size + col) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GardenCellView(
    game: GardenGame,
    index: Int,
    row: Int,
    col: Int,
    side: Dp,
    onTap: () -> Unit,
    onToggleFlower: () -> Unit,
) {
    val state = game.cells[index]
    val clashing = index in game.conflicts
    val bed = game.beds[index]
    val density = LocalDensity.current
    val flowerSize = with(density) { (side * 0.62f).toSp() }

    // Screen readers have no double tap of their own to spare: planting is an action of the cell.
    val markLabel = stringResource(if (state == GardenCell.MARK) R.string.garden_action_unmark else R.string.garden_action_mark)
    val flowerLabel = stringResource(if (state == GardenCell.FLOWER) R.string.garden_action_unplant else R.string.garden_action_plant)
    val description = stringResource(
        when {
            clashing -> R.string.garden_cell_clash
            state == GardenCell.FLOWER -> R.string.garden_cell_flower
            state == GardenCell.MARK -> R.string.garden_cell_mark
            else -> R.string.garden_cell_empty
        },
        row + 1,
        col + 1,
        bed + 1,
    )

    val overlay = when {
        game.isComplete && state == GardenCell.FLOWER -> Accent.copy(alpha = 0.45f)
        clashing -> Danger.copy(alpha = 0.4f)
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(side * 0.16f)

    // The lawn and its hedges are drawn under the board: a cell shows only what is on it.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(side)
            // Clear of the hedges, which take the edges of the cells they run along.
            .padding(side * (HEDGE_WIDTH / 2 + 0.02f))
            .background(overlay, shape)
            .then(if (clashing) Modifier.border(2.dp, Danger, shape) else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
                if (state != GardenCell.FLOWER) onClick(label = markLabel) { onTap(); true }
                customActions = listOf(CustomAccessibilityAction(flowerLabel) { onToggleFlower(); true })
            },
    ) {
        when (state) {
            GardenCell.FLOWER -> Text(text = "🌻", fontSize = flowerSize)
            // A cell ruled out has a bush on it: drawn, so that it looks the same on every phone.
            GardenCell.MARK -> Canvas(modifier = Modifier.fillMaxSize(0.92f)) { drawBush(this.size.width) }
            GardenCell.EMPTY -> Unit
        }
    }
}

/** How often a drag's way is looked at, per cell crossed: enough not to miss one it only clips. */
private const val DRAG_SAMPLES_PER_CELL = 4

/** The lawn: its grass, the blades darker and lighter on it, and how much of its bed's colour each cell takes. */
private val Grass = Color(0xFF9FCB72)
private val GrassDark = Color(0xFF5E8F3A)
private val GrassLight = Color(0xFFD3EBB0)
private const val BedTint = 0.5f

/** The hedges around the beds, with their leaves, and how wide they are, as a share of a cell. */
private val Hedge = Color(0xFF3B6E2E)
private val HedgeLeaf = Color(0xFF5C9A47)
private const val HEDGE_WIDTH = 0.14f

/** The bush that rules out a cell: rounder and brighter than the hedges, so that it is not taken for one. */
private val BushShadow = Color(0x33203A18)
private val BushOutline = Color(0xFF2C5A22)
private val Bush = Color(0xFF4F9A3C)
private val BushLight = Color(0xFF7CC160)

/** Leaves of the bush, as (x, y, radius) shares of its side: a round clump, fuller at the bottom. */
private val BushLeaves = listOf(
    Triple(0.5f, 0.36f, 0.2f),
    Triple(0.32f, 0.48f, 0.19f),
    Triple(0.68f, 0.48f, 0.19f),
    Triple(0.4f, 0.64f, 0.19f),
    Triple(0.6f, 0.64f, 0.19f),
)

/** A bush seen from above, on a cell [side] wide: a clump of leaves with its shadow and a lit side. */
private fun DrawScope.drawBush(side: Float) {
    fun leaf(color: Color, grow: Float, shift: Offset = Offset.Zero, scale: Float = 1f) =
        BushLeaves.forEach { (x, y, r) -> drawCircle(color, side * r * scale + grow, Offset(side * x, side * y) + shift) }

    leaf(BushShadow, 0f, Offset(side * 0.04f, side * 0.06f))
    leaf(BushOutline, side * 0.025f)
    leaf(Bush, 0f)
    // Light from the top left, on each leaf.
    leaf(BushLight, 0f, Offset(-side * 0.05f, -side * 0.05f), scale = 0.45f)
}

/**
 * The hedges of a board whose cells are [step] wide: where two cells of different beds meet, and all
 * around the board. As one path to draw, and as the segments that it is made of, to put leaves on.
 */
private fun hedgesOf(beds: List<Int>, size: Int, step: Float): Pair<Path, List<Pair<Offset, Offset>>> {
    val segments = mutableListOf<Pair<Offset, Offset>>()
    for (row in 0 until size) for (col in 0 until size) {
        val bed = beds[row * size + col]
        if (col + 1 < size && beds[row * size + col + 1] != bed) {
            segments += Offset((col + 1) * step, row * step) to Offset((col + 1) * step, (row + 1) * step)
        }
        if (row + 1 < size && beds[(row + 1) * size + col] != bed) {
            segments += Offset(col * step, (row + 1) * step) to Offset((col + 1) * step, (row + 1) * step)
        }
    }
    // The outer hedge inside the board, so that none of it is cut off.
    val inset = step * HEDGE_WIDTH / 2
    val far = size * step - inset
    segments += Offset(inset, inset) to Offset(far, inset)
    segments += Offset(far, inset) to Offset(far, far)
    segments += Offset(far, far) to Offset(inset, far)
    segments += Offset(inset, far) to Offset(inset, inset)

    val path = Path()
    for ((from, to) in segments) {
        path.moveTo(from.x, from.y)
        path.lineTo(to.x, to.y)
    }
    return path to segments
}

/** Lighter leaves along the hedges, a few per cell, so that they read as a hedge and not as a line. */
private fun leavesOf(segments: List<Pair<Offset, Offset>>, step: Float): List<Pair<Offset, Float>> {
    val random = Random(segments.size)
    return segments.flatMap { (from, to) ->
        List(LEAVES_PER_CELL) {
            val along = (it + random.nextFloat()) / LEAVES_PER_CELL
            val across = (random.nextFloat() - 0.5f) * step * HEDGE_WIDTH * 0.5f
            val horizontal = from.y == to.y
            val point = lerp(from, to, along)
            Offset(point.x + if (horizontal) 0f else across, point.y + if (horizontal) across else 0f) to
                step * (0.018f + random.nextFloat() * 0.014f)
        }
    }
}

private const val LEAVES_PER_CELL = 5

/** One cell's ground: blades of grass in two shades, over a few soft patches where it grows thicker. */
private class Turf(val darkBlades: Path, val lightBlades: Path, val patches: List<Pair<Offset, Float>>)

/**
 * The ground of the cell at [index], on a tile [side] wide: the same for a cell every time it is drawn,
 * and different from its neighbours', so that the board reads as one garden and not as a pattern.
 */
private fun turfOf(index: Int, side: Float): Turf {
    val random = Random(index * 7919 + 17)
    fun spot() = Offset(side * (0.06f + random.nextFloat() * 0.88f), side * (0.06f + random.nextFloat() * 0.88f))

    val patches = List(PATCHES) { spot() to side * (0.14f + random.nextFloat() * 0.12f) }
    val dark = Path()
    val light = Path()
    repeat(BLADES) { blade ->
        // Seen from above, each blade a short stroke leaning any way, most of them roughly upright.
        val root = spot()
        val angle = -PI.toFloat() / 2 + (random.nextFloat() - 0.5f) * 2.2f
        val length = side * (0.025f + random.nextFloat() * 0.035f)
        val path = if (blade % 3 == 0) light else dark
        path.moveTo(root.x, root.y)
        path.lineTo(root.x + cos(angle) * length, root.y + sin(angle) * length)
    }
    return Turf(dark, light, patches)
}

private const val BLADES = 48
private const val PATCHES = 3
