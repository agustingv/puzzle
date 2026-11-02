package com.agustingv.puzzles.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.agustingv.puzzles.R
import kotlinx.coroutines.delay

private val MaxBoardSide = 480.dp

/**
 * Screen shared by every game: header and stats, a square board area and an actions area.
 * Portrait stacks them; landscape puts the board on the right at full height. [board] receives
 * the side of the largest square available, and nothing here scrolls in portrait, so boards
 * driven by drag gestures never compete with a scroll container.
 */
@Composable
fun GameScaffold(
    title: String,
    elapsedSeconds: Int,
    status: String,
    onExit: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    board: @Composable (side: Dp) -> Unit,
) {
    BackHandler(onBack = onExit)

    val info: @Composable () -> Unit = {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onExit) { Text(stringResource(R.string.exit)) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.time_value, formatTime(elapsedSeconds)))
            Text(status)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        if (maxWidth > maxHeight) {
            val side = min(maxHeight, maxWidth * 0.6f)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    info()
                    actions()
                }
                board(side)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                info()
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    board(min(min(maxWidth, maxHeight), MaxBoardSide))
                }
                actions()
            }
        }
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
