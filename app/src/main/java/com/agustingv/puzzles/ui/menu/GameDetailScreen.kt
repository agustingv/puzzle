package com.agustingv.puzzles.ui.menu

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.data.KeyValueStore
import com.agustingv.puzzles.data.Records
import com.agustingv.puzzles.ui.common.ScreenTopBar
import com.agustingv.puzzles.ui.common.formatTime
import com.agustingv.puzzles.ui.theme.Card

private const val LAST_VARIANT_KEY = "last-variant"

/** A game's page: what it is, which level to play, the best times on that level, and how to start. */
@Composable
fun GameDetailScreen(
    info: GameInfo,
    records: Records,
    storage: KeyValueStore,
    onPlay: (variant: String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    // Opens on the last level played, so a repeat game takes one tap.
    val lastKey = "$LAST_VARIANT_KEY/${info.id}"
    var selected by rememberSaveable(info) {
        mutableStateOf(storage.get(lastKey)?.takeIf { it in info.variants } ?: info.variants.first())
    }

    fun play() {
        storage.put(lastKey, selected)
        onPlay(selected)
    }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenTopBar(title = stringResource(info.title), onBack = onBack, titleInTiles = true)

        val intro: @Composable () -> Unit = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(info.gradient)),
            ) {
                Text(text = info.icon, fontSize = 56.sp, modifier = Modifier.clearAndSetSemantics {})
            }

            Text(text = stringResource(info.description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val options: @Composable () -> Unit = {
            VariantSelector(info, selected, onSelect = { selected = it })
            HallOfFame(records.top(info.id, selected))
        }
        val spacing = Arrangement.spacedBy(16.dp)
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (landscape) {
            // Little height: the intro goes beside the options, and each side scrolls on its own.
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = spacing,
                ) { intro() }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = spacing,
                ) { options() }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = spacing,
            ) {
                intro()
                options()
            }
        }

        Button(
            onClick = ::play,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp),
        ) {
            Text(stringResource(R.string.play), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariantSelector(info: GameInfo, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(if (info.usesWordLengths) R.string.option_letters else R.string.option_difficulty),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            info.variants.forEachIndexed { index, variant ->
                SegmentedButton(
                    selected = variant == selected,
                    onClick = { onSelect(variant) },
                    shape = SegmentedButtonDefaults.itemShape(index, info.variants.size),
                    colors = SegmentedButtonDefaults.colors(inactiveContainerColor = Card),
                    icon = {},
                    label = {
                        Text(
                            text = variantShortLabel(variant),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun HallOfFame(times: List<Int>) {
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.hall_of_fame),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        if (times.isEmpty()) {
            Text(
                text = stringResource(R.string.records_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        times.forEachIndexed { index, seconds ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${index + 1}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatTime(seconds), fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
