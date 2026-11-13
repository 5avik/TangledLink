package com.v2ray.ang.ui.main

import com.v2ray.ang.R
import com.v2ray.ang.handler.BillingGateway
import com.v2ray.ang.handler.BillingSnapshot
import com.v2ray.ang.handler.TnglAuthException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BillingUiState(
    val visible: Boolean = false,
    val busy: Boolean = false,
    val snapshot: BillingSnapshot? = null,
    val error: Int? = null
) {
    val canPay: Boolean
        get() = visible && !busy && error == null && snapshot?.order?.status == "pending"
}

/** Owned by MainViewModel. All server operations run on its IO dispatcher. */
internal class BillingController(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val gateway: BillingGateway,
    private val onPaid: () -> Unit
) {
    private val state = MutableStateFlow(BillingUiState())
    val uiState = state.asStateFlow()
    private var job: Job? = null
    private var appliedOrder: String? = null
    private var generation = 0

    fun open() {
        generation++
        job?.cancel()
        state.value = BillingUiState(visible = true)
        refresh()
    }
    fun close() {
        generation++
        job?.cancel()
        state.update { it.copy(visible = false, busy = false) }
    }
    fun paymentUrl(): String? = if (state.value.canPay) {
        com.v2ray.ang.handler.TnglBillingClient.PAYMENT_URL
    } else null

    fun refresh() = run { gateway.load() }
    fun create() {
        val snapshot = state.value.snapshot ?: return
        run { snapshot.copy(order = gateway.create(snapshot.plan.id)) }
    }
    private fun run(operation: () -> BillingSnapshot) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, error = null) }
        val currentGeneration = ++generation
        job = scope.launch {
            try {
                val snapshot = withContext(io) { operation() }
                state.update { it.copy(snapshot = snapshot) }
                val paid = snapshot.order?.takeIf { it.status == "paid" && !it.pendingSync }
                if (paid != null && paid.id != appliedOrder) {
                    appliedOrder = paid.id
                    onPaid()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) {
                val error = when ((e as? TnglAuthException)?.errorCode) {
                    "billing_unavailable" -> R.string.billing_unavailable
                    "session_expired" -> R.string.auth_session_expired
                    "client_disabled", "client_expiry_requires_review", "traffic_depleted",
                    "client_auto_renewal_requires_review", "panel_client_mismatch",
                    "panel_client_changed", "tunnel_client_requires_review" -> R.string.billing_manual_review
                    else -> R.string.auth_server_error
                }
                state.update { it.copy(error = error) }
            } finally {
                if (currentGeneration == generation) state.update { it.copy(busy = false) }
            }
        }
    }
}
