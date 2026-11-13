package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.BrandButton

@Composable
internal fun BillingDialog(state: BillingUiState, onAction: (MainAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(MainAction.DismissBilling) },
        title = { Text(stringResource(R.string.renew_subscription), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.snapshot?.let { snapshot ->
                    Text(stringResource(R.string.billing_plan, snapshot.plan.amountKopecks / 100, snapshot.plan.days),
                        style = MaterialTheme.typography.titleMedium)
                    Text(snapshot.instructions)
                    snapshot.order?.let { order ->
                        SelectionContainer {
                            Text(stringResource(R.string.billing_order, order.id, order.account))
                        }
                        if (order.status == "pending") {
                            SelectionContainer {
                                Text(stringResource(R.string.billing_payment_instructions, order.account))
                            }
                            BrandButton(onClick = { onAction(MainAction.OpenBillingPayment) },
                                enabled = state.canPay, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.billing_pay))
                            }
                        }
                        Text(stringResource(when {
                            order.status == "paid" && order.pendingSync -> R.string.billing_syncing
                            order.status == "paid" -> R.string.billing_paid
                            order.status == "applying" || order.status == "retry" -> R.string.billing_processing
                            else -> R.string.billing_pending
                        }))
                    }
                    if (snapshot.order == null || snapshot.order.status == "paid") {
                        BrandButton(onClick = { onAction(MainAction.CreateBillingOrder) },
                            enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.billing_create))
                        }
                    }
                }
                state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                if (state.snapshot != null) {
                    Text(stringResource(R.string.billing_manual_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !state.busy, onClick = { onAction(MainAction.RefreshBilling) }) {
                Text(stringResource(R.string.billing_check))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(MainAction.DismissBilling) }) { Text(stringResource(R.string.auth_cancel)) }
        }
    )
}
