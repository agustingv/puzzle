package com.butaca13.puzzles.ui.menu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.Products
import com.butaca13.puzzles.data.PlayerAccount
import com.butaca13.puzzles.data.SyncStatus
import com.butaca13.puzzles.ui.common.AvatarTile
import com.butaca13.puzzles.ui.common.GameSounds
import com.butaca13.puzzles.ui.common.ScreenTopBar
import com.butaca13.puzzles.ui.theme.Card
import com.butaca13.puzzles.ui.theme.Danger
import kotlin.math.roundToInt

/** The app's settings: the player on this phone, the sound, and the privacy policy. */
@Composable
fun SettingsScreen(
    sounds: GameSounds,
    account: PlayerAccount,
    serverConfigured: Boolean,
    onRename: (String) -> Boolean,
    onOfflineChange: (Boolean) -> Unit,
    onAvatar: () -> Unit,
    /** Deletes the player from the server; whether it worked. */
    onDeletePlayer: suspend () -> Boolean,
    onPrivacy: () -> Unit,
    onExit: () -> Unit,
    shop: GameShop = GameShop.Free,
) {
    BackHandler(onBack = onExit)

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenTopBar(title = stringResource(R.string.settings_title), onBack = onExit)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard { PlayerSettings(account, serverConfigured, onRename, onOfflineChange, onAvatar, onDeletePlayer) }
            SettingsCard { SoundSettings(sounds) }
            // Only once there are games to buy.
            if (PAID_GAME_PRODUCTS.isNotEmpty()) SettingsCard { ShopSettings(shop) }
            SettingsCard { LinkRow(stringResource(R.string.privacy_title), onPrivacy) }
        }
    }
}

/** A group of settings; the avatar screen uses them too. */
@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A setting that opens another screen. */
@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun PlayerSettings(
    account: PlayerAccount,
    serverConfigured: Boolean,
    onRename: (String) -> Boolean,
    onOfflineChange: (Boolean) -> Unit,
    onAvatar: () -> Unit,
    onDeletePlayer: suspend () -> Boolean,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val avatarLabel = stringResource(R.string.avatar_change)
    var renaming by rememberSaveable { mutableStateOf(false) }
    val status = account.status(serverConfigured)

    Text(
        text = stringResource(R.string.settings_player),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        AvatarTile(
            avatar = account.avatar,
            size = 56.dp,
            modifier = Modifier
                .padding(top = 8.dp, end = 16.dp)
                .clickable(onClick = onAvatar)
                .semantics {
                    contentDescription = avatarLabel
                    role = Role.Button
                },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = account.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.settings_avatar_hint),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        TextButton(onClick = { renaming = true }) { Text(stringResource(R.string.settings_rename)) }
    }
    Text(
        text = stringResource(
            when (status) {
                SyncStatus.SYNCED -> R.string.sync_synced
                SyncStatus.PENDING -> R.string.sync_pending
                SyncStatus.FAILED -> R.string.sync_failed
                SyncStatus.OFFLINE -> R.string.sync_offline
                SyncStatus.NO_SERVER -> R.string.sync_no_server
                SyncStatus.NAME_REJECTED -> R.string.sync_rejected
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = if (status == SyncStatus.NAME_REJECTED) Danger else muted,
    )

    SwitchRow(stringResource(R.string.settings_offline), account.offline, onOfflineChange)
    Text(
        text = stringResource(R.string.settings_offline_hint),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )

    // Only a build with a server has a player there to delete.
    var deleting by rememberSaveable { mutableStateOf(false) }
    var deleted by rememberSaveable { mutableStateOf(false) }
    if (serverConfigured) {
        TextButton(onClick = { deleting = true }) {
            Text(stringResource(R.string.settings_delete_player), color = Danger)
        }
        if (deleted) {
            Text(
                text = stringResource(R.string.settings_delete_done),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }

    if (deleting) {
        DeletePlayerDialog(
            onDelete = onDeletePlayer,
            onDeleted = {
                deleting = false
                deleted = true
            },
            onDismiss = { deleting = false },
        )
    }

    if (renaming) {
        RenameDialog(
            current = account.name,
            onRename = { name -> onRename(name).also { if (it) renaming = false } },
            onDismiss = { renaming = false },
        )
    }
}

/** Asks before deleting the player from the server, and waits there for the answer. */
@Composable
private fun DeletePlayerDialog(onDelete: suspend () -> Boolean, onDeleted: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.settings_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_delete_text))
                if (failed) Text(stringResource(R.string.settings_delete_failed), color = Danger)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working,
                onClick = {
                    working = true
                    failed = false
                    scope.launch {
                        val gone = onDelete()
                        working = false
                        if (gone) onDeleted() else failed = true
                    }
                },
            ) {
                Text(
                    text = stringResource(if (working) R.string.settings_delete_working else R.string.settings_delete_confirm),
                    color = if (working) Color.Unspecified else Danger,
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !working, onClick = onDismiss) { Text(stringResource(R.string.settings_delete_cancel)) }
        },
    )
}

@Composable
private fun RenameDialog(current: String, onRename: (String) -> Boolean, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val save = { invalid = !onRename(name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    invalid = false
                },
                singleLine = true,
                isError = invalid,
                supportingText = {
                    Text(
                        text = stringResource(R.string.settings_name_rule),
                        color = if (invalid) Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = save) { Text(stringResource(R.string.settings_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.secret_close)) } },
    )
}

@Composable
private fun ShopSettings(shop: GameShop) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val allPrice = shop.allPrice

    Text(
        text = stringResource(R.string.settings_shop),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.settings_shop_all), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.settings_shop_all_hint),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        when {
            shop.ownsAll() -> Text(stringResource(R.string.settings_shop_owned), color = muted)
            shop.pending(Products.ALL_GAMES) -> Text(stringResource(R.string.settings_shop_pending), color = muted)
            allPrice != null -> TextButton(onClick = { shop.buy(Products.ALL_GAMES) }) { Text(allPrice) }
            else -> Text(stringResource(R.string.settings_shop_unavailable), color = muted)
        }
    }
    TextButton(onClick = shop.restore) { Text(stringResource(R.string.settings_shop_restore)) }
    Text(
        text = stringResource(R.string.settings_shop_restore_hint),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
}

@Composable
private fun SoundSettings(sounds: GameSounds) {
    val volumeLabel = stringResource(R.string.settings_volume)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    SwitchRow(stringResource(R.string.settings_sound), sounds.enabled) { sounds.toggle() }

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
