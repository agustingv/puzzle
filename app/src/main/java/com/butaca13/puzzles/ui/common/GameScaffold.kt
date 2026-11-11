package com.butaca13.puzzles.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.GameSession
import kotlinx.coroutines.delay

private val MaxBoardSide = 480.dp

/**
 * Screen shared by every game: a top bar with title, status and clock, a square board area and an
 * actions area. Portrait stacks them, with the board centred in the space left between the bar and
 * the actions; landscape puts the board on the right at full height. [board] receives
 * the side of the largest square available, and nothing here scrolls in portrait, so boards
 * driven by drag gestures never compete with a scroll container.
 */
@Composable
fun GameScaffold(
    title: String,
    session: GameSession<*>,
    status: String,
    onExit: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    board: @Composable (side: Dp) -> Unit,
) {
    BackHandler(onBack = onExit)

    val visuals = remember { FeedbackVisuals() }
    GameFeedbackEffect(session.feedback, visuals)

    // Inside a layer: the shake moves pixels only, so it never disturbs layout or a drag in progress.
    val shaking = Modifier.graphicsLayer { translationX = visuals.shakeX }

    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val topBar: @Composable () -> Unit = {
        val running = session.saved != null

        // Compact, for the board's sake: the way back, the title and the clock on one line, and
        // under it the status with the way to give up.
        ScreenTopBar(
            title = title,
            subtitle = status,
            appHeader = false,
            onBack = onExit,
            trailing = { TimerPill(session.elapsedSeconds) },
            subtitleAction = {
                if (running) {
                    TextButton(onClick = { confirmDiscard = true }) { Text(stringResource(R.string.end_game)) }
                }
            },
        )
    }

    val actionsWithClue: @Composable ColumnScope.() -> Unit = {
        session.lastClue?.let { SecretClueNote(it, completes = session.lastClueCompletes) }
        session.lastPrizes.forEach { PrizeNote(it) }
        actions()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        if (maxWidth > maxHeight) {
            // Top bar and actions on the left, the board at full height on the right.
            val side = min(maxHeight - 24.dp, maxWidth * 0.6f)
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    topBar()
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = actionsWithClue,
                    )
                }
                Box(
                    modifier = Modifier.fillMaxHeight().padding(end = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = shaking) { board(side) }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                topBar()
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val side = min(min(maxWidth, maxHeight), MaxBoardSide)
                    Box(modifier = shaking) { board(side) }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = actionsWithClue,
                )
            }
        }
    }

    Confetti(visuals.confettiBursts)

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.end_game_title)) },
            text = { Text(stringResource(R.string.end_game_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    session.discard()
                    onExit()
                }) { Text(stringResource(R.string.end_game)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.keep_playing)) }
            },
        )
    }
}

/** Ticks once per second, only while the screen is resumed: backgrounding pauses the clock. */
@Composable
fun GameClock(onTick: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val tick by rememberUpdatedState(onTick)

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(1000)
                tick()
            }
        }
    }
}

fun formatTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
