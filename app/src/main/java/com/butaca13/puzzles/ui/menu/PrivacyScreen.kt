package com.butaca13.puzzles.ui.menu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.ui.common.ScreenTopBar

private val SECTIONS = listOf(
    R.string.privacy_summary_title to R.string.privacy_summary,
    R.string.privacy_collect_title to R.string.privacy_collect,
    R.string.privacy_server_title to R.string.privacy_server,
    R.string.privacy_device_title to R.string.privacy_device,
    R.string.privacy_backup_title to R.string.privacy_backup,
    R.string.privacy_delete_title to R.string.privacy_delete,
    R.string.privacy_play_title to R.string.privacy_play,
    R.string.privacy_children_title to R.string.privacy_children,
    R.string.privacy_changes_title to R.string.privacy_changes,
    R.string.privacy_contact_title to R.string.privacy_contact,
)

/** The policy inside the app, readable offline. docs/privacidad.html is the same text for Play. */
@Composable
fun PrivacyScreen(onExit: () -> Unit) {
    BackHandler(onBack = onExit)

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenTopBar(title = stringResource(R.string.privacy_title), onBack = onExit)

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.privacy_updated),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SECTIONS.forEach { (title, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = stringResource(body))
                }
            }
        }
    }
}
