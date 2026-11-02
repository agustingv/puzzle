package com.agustingv.puzzles.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.agustingv.puzzles.R
import com.agustingv.puzzles.data.SecretClue
import com.agustingv.puzzles.data.SecretMessage
import com.agustingv.puzzles.ui.common.SecretBoard
import com.agustingv.puzzles.ui.theme.Danger

/** Where the message is solved: the clues found so far, each in its place, and the player's guess. */
@Composable
fun SecretDialog(found: List<SecretClue>, onGuess: (String) -> Boolean, onDismiss: () -> Unit) {
    var guess by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }

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
                OutlinedTextField(
                    value = guess,
                    onValueChange = {
                        guess = it
                        wrong = false
                    },
                    label = { Text(stringResource(R.string.secret_guess)) },
                    singleLine = true,
                    isError = wrong,
                    supportingText = if (wrong) {
                        { Text(stringResource(R.string.secret_wrong), color = Danger) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    // With the keyboard up on a phone held sideways, the dialog's buttons are hidden.
                    keyboardActions = KeyboardActions(onDone = { wrong = !onGuess(guess) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { wrong = !onGuess(guess) }) { Text(stringResource(R.string.secret_check)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.secret_close)) }
        },
    )
}
