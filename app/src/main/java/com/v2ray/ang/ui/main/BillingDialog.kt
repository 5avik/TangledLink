package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    val order=state.snapshot?.order
    val emailForm=order == null || (order.status == "paid" && !state.hasCreatedOrder) ||
        (order.status == "pending" && order.paymentUrl == null)
    AlertDialog(
        onDismissRequest = { onAction(MainAction.DismissBilling) },
        title = { Text(stringResource(R.string.renew_subscription),
            style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.snapshot != null) {
                    if (emailForm) {
                        OutlinedTextField(value = state.email, onValueChange = { onAction(MainAction.BillingEmail(it)) },
                            enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.billing_email)) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email))
                        Text(stringResource(R.string.billing_email_note), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BrandButton(onClick = { onAction(MainAction.CreateBillingOrder) },
                            enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.billing_pay))
                        }
                    } else {
                        Text(stringResource(when {
                            order?.status == "paid" && order.pendingSync -> R.string.billing_syncing
                            order?.status == "paid" -> R.string.billing_paid
                            order?.status == "applying" || order?.status == "retry" -> R.string.billing_processing
                            else -> R.string.billing_pending
                        }))
                        if(order?.status == "pending") {
                            BrandButton(onClick = { onAction(MainAction.OpenBillingPayment) },
                                enabled = state.canPay, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.billing_pay))
                            }
                        }
                    }
                }
                state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (!emailForm || state.error != null) TextButton(enabled = !state.busy, onClick = { onAction(MainAction.RefreshBilling) }) {
                Text(stringResource(R.string.billing_check))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(MainAction.DismissBilling) }) { Text(stringResource(R.string.auth_cancel)) }
        }
    )
}
