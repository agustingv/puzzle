package com.butaca13.puzzles.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.SecretClue
import com.butaca13.puzzles.data.SecretMessage
import com.butaca13.puzzles.ui.common.SecretBoard

/**
 * The secret message: the clues found so far, each in its place. Until every clue is found, it names the
 * games still to win; then the clues spell the message out, and [onReveal] uncovers it, with its prize.
 */
@Composable
fun SecretDialog(found: List<SecretClue>, onReveal: () -> Unit, onDismiss: () -> Unit) {
    val missing = SecretMessage.clues - found.toSet()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.secret_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.secret_intro))
                SecretBoard(found)
                Text(
                    text = stringResource(R.string.secret_found, found.size, SecretMessage.clues.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (missing.isEmpty()) {
                        stringResource(R.string.secret_complete)
                    } else {
                        pluralStringResource(R.plurals.secret_missing, missing.size, missing.size, missing.joinToString(", ") { it.title })
                    },
                )
            }
        },
        confirmButton = {
            if (missing.isEmpty()) {
                TextButton(onClick = onReveal) { Text(stringResource(R.string.secret_reveal)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.secret_close)) }
        },
    )
}
