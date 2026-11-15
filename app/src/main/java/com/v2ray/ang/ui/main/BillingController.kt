package com.v2ray.ang.ui.main

import com.v2ray.ang.R
import com.v2ray.ang.handler.BillingGateway
import com.v2ray.ang.handler.BillingSnapshot
import com.v2ray.ang.handler.TnglAuthException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.receiveAsFlow

data class BillingUiState(
    val visible: Boolean = false,
    val busy: Boolean = false,
    val snapshot: BillingSnapshot? = null,
    val error: Int? = null,
    val email: String = "",
    val planId: String = "month30",
    val hasCreatedOrder: Boolean = false,
    val returnSequence: Long = 0
) {
    val canPay: Boolean
        get() = visible && !busy && error == null && snapshot?.order?.status == "pending" && snapshot.order.paymentUrl?.startsWith("https://") == true
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
    private val payments = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val paymentEvents = payments.receiveAsFlow()

    fun open(planId: String = "month30") {
        generation++
        job?.cancel()
        while (payments.tryReceive().isSuccess) { /* Drop cancelled navigation. */ }
        state.value = BillingUiState(visible = true, planId = planId)
        refresh()
    }
    fun returnFromPayment() {
        generation++
        job?.cancel()
        while (payments.tryReceive().isSuccess) { /* Returning must never reopen checkout. */ }
        state.value = BillingUiState(visible = true, hasCreatedOrder = true,
            returnSequence = state.value.returnSequence + 1)
        run(awaitConfirmation = true) { gateway.load() }
    }
    fun close() {
        generation++
        job?.cancel()
        while (payments.tryReceive().isSuccess) { /* Drop cancelled navigation. */ }
        state.update { it.copy(visible = false, busy = false) }
    }
    fun paymentUrl(): String? = if (state.value.canPay) {
        state.value.snapshot?.order?.paymentUrl
    } else null

    fun pay() {
        val value = state.value
        if (!value.canPay) return
        val snapshot = value.snapshot ?: return
        val order = snapshot.order ?: return
        // The backend rechecks this invoice and replaces only a verified failure.
        // Never reopen a cached checkout URL after a failed payment.
        run(openPayment = true) { snapshot.copy(order = gateway.resume(order.id)) }
    }

    fun email(value: String) { if (!state.value.busy) state.update { it.copy(email = value.take(254), error = null) } }
    fun selectPlan(value: String) { if (!state.value.busy) state.update { it.copy(planId = value, error = null) } }
    fun refresh() = run { gateway.load() }
    fun create() {
        val snapshot = state.value.snapshot ?: return
        val value = state.value
        val email = value.email.trim()
        if (!Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(email)) {
            state.update { it.copy(error = R.string.billing_invalid_email) }; return
        }
        if (snapshot.plans.none { it.id == value.planId }) {
            state.update { it.copy(error = R.string.billing_manual_review) }; return
        }
        run(openPayment = true) { snapshot.copy(order = gateway.create(value.planId, email)) }
    }
    private fun run(openPayment: Boolean = false, awaitConfirmation: Boolean = false, operation: () -> BillingSnapshot) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, error = null) }
        val currentGeneration = ++generation
        job = scope.launch {
            try {
                val snapshot = withContext(io) {
                    var result = operation()
                    if (awaitConfirmation) repeat(6) {
                        if (result.order?.status !in listOf("pending", "applying", "retry")) return@withContext result
                        delay(1500)
                        result = operation()
                    }
                    result
                }
                if (currentGeneration != generation) return@launch
                state.update { it.copy(snapshot = snapshot, hasCreatedOrder = it.hasCreatedOrder || openPayment) }
                if (openPayment) snapshot.order?.takeIf { it.status == "pending" }
                    ?.paymentUrl?.takeIf { it.startsWith("https://") }?.let { payments.trySend(it) }
                val paid = snapshot.order?.takeIf { it.status == "paid" && !it.pendingSync }
                if (paid != null && paid.id != appliedOrder) {
                    appliedOrder = paid.id
                    onPaid()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) {
                val error = when ((e as? TnglAuthException)?.errorCode) {
                    "invalid_email" -> R.string.billing_invalid_email
                    "invoice_requires_review", "existing_order_different_plan", "order_requires_review" -> R.string.billing_manual_review
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
