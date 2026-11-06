package com.butaca13.puzzles.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.FakeStore
import com.butaca13.puzzles.data.Products

/** Every product on sale: the paid games, then all of them at once. */
private val ALL_PRODUCTS: List<String> get() = PAID_GAME_PRODUCTS + Products.ALL_GAMES

@Composable
private fun productName(product: String): String =
    GameInfo.entries.firstOrNull { it.productId == product }?.let { stringResource(it.title) }
        ?: if (product == Products.ALL_GAMES) stringResource(R.string.settings_shop_all) else product

/** The simulated store's payment sheet, in place of Google Play's, with every way a payment can end. */
@Composable
fun FakeStoreSheet(store: FakeStore) {
    val product = store.sheet ?: return

    AlertDialog(
        onDismissRequest = { store.answer(FakeStore.Answer.CANCEL) },
        title = { Text(stringResource(R.string.fake_store_title)) },
        text = {
            Column {
                Text("${productName(product)}: ${store.price(product)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.fake_store_sheet_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { store.answer(FakeStore.Answer.PAY) }) { Text(stringResource(R.string.fake_store_pay)) }
                TextButton(onClick = { store.answer(FakeStore.Answer.PAY_LATER) }) {
                    Text(stringResource(R.string.fake_store_pay_later))
                }
                TextButton(onClick = { store.answer(FakeStore.Answer.FAIL) }) { Text(stringResource(R.string.fake_store_fail)) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { store.answer(FakeStore.Answer.CANCEL) }) { Text(stringResource(R.string.fake_store_cancel)) }
        },
    )
}

/**
 * In the settings of a build with the simulated store: what its account holds, and what Google Play would
 * do on its own, as completing a payment in cash, refunding, or not being reachable.
 */
@Composable
fun FakeStoreControls(store: FakeStore) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text = stringResource(R.string.fake_store_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(stringResource(R.string.fake_store_hint), style = MaterialTheme.typography.bodySmall, color = muted)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.fake_store_available), modifier = Modifier.weight(1f))
        Switch(checked = store.available, onCheckedChange = store::changeAvailable)
    }
    for (product in ALL_PRODUCTS) {
        val purchase = store.purchaseOf(product)
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(productName(product), style = MaterialTheme.typography.titleMedium)
            Text(
                text = when {
                    purchase == null -> stringResource(R.string.fake_store_not_bought)
                    !purchase.paid -> stringResource(R.string.fake_store_waiting)
                    purchase.acknowledged -> stringResource(R.string.fake_store_paid)
                    else -> stringResource(R.string.fake_store_paid_unacknowledged)
                },
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    purchase == null -> Unit
                    !purchase.paid -> {
                        TextButton(onClick = { store.completePayment(product) }) {
                            Text(stringResource(R.string.fake_store_complete))
                        }
                        TextButton(onClick = { store.remove(product) }) { Text(stringResource(R.string.fake_store_abandon)) }
                    }
                    else -> TextButton(onClick = { store.remove(product) }) { Text(stringResource(R.string.fake_store_refund)) }
                }
            }
        }
    }
}
