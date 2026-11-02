package com.agustingv.puzzles.ui.menu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.ui.common.GameSounds
import com.agustingv.puzzles.ui.common.ScreenTopBar
import com.agustingv.puzzles.ui.theme.Card
import kotlin.math.roundToInt

/** The app's settings: for now, its sound. */
@Composable
fun SettingsScreen(sounds: GameSounds, onExit: () -> Unit) {
    BackHandler(onBack = onExit)

    val shape = RoundedCornerShape(16.dp)
    val volumeLabel = stringResource(R.string.settings_volume)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenTopBar(title = stringResource(R.string.settings_title), onBack = onExit)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .clip(shape)
                .background(Card)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = sounds.enabled, role = Role.Switch, onValueChange = { sounds.toggle() }),
            ) {
                Text(
                    text = stringResource(R.string.settings_sound),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = sounds.enabled, onCheckedChange = null)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(volumeLabel, color = if (sounds.enabled) MaterialTheme.colorScheme.onSurface else muted)
                Text(
                    text = stringResource(R.string.settings_volume_value, (sounds.volume * 100).roundToInt()),
                    color = muted,
                )
            }
            Slider(
                value = sounds.volume,
                onValueChange = sounds::changeVolume,
                // A sample at the end of the gesture, so that a drag is not a burst of notes.
                onValueChangeFinished = sounds::playSample,
                enabled = sounds.enabled,
                modifier = Modifier.semantics { contentDescription = volumeLabel },
            )
            Text(
                text = stringResource(R.string.settings_volume_hint),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }
}
