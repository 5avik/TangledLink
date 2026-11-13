package com.v2ray.ang.ui.main

import com.v2ray.ang.R
import com.v2ray.ang.handler.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BillingControllerTest {
    private val plan = BillingPlan("month30", 15000, 30)
    private val pending = BillingOrder("TL-test", "svk", "pending", null, false)
    private class Fake(var value: BillingSnapshot) : BillingGateway {
        var failure: String? = null
        var created = 0
        override fun load(): BillingSnapshot {
            failure?.let { throw TnglAuthException(it) }
            return value
        }
        override fun create(planId: String): BillingOrder {
            check(planId == value.plan.id)
            failure?.let { throw TnglAuthException(it) }
            created++
            return requireNotNull(value.order)
        }
    }
    @Test fun emptyOrderCreationAndPaidRefreshAreSeparate() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val fake = Fake(BillingSnapshot(plan, "Instructions", null))
        var refreshed = 0
        val controller = BillingController(scope, Dispatchers.Unconfined, fake) { refreshed++ }
        try {
            assertEquals(BillingUiState(), controller.uiState.value)
            controller.open()
            assertTrue(controller.uiState.value.visible)
            assertFalse(controller.uiState.value.busy)
            assertNull(controller.uiState.value.snapshot?.order)
            fake.value = fake.value.copy(order = pending)
            controller.create()
            assertEquals(1, fake.created)
            assertEquals(pending, controller.uiState.value.snapshot?.order)
            assertEquals(0, refreshed)
            fake.value = fake.value.copy(order = pending.copy(status = "paid"))
            controller.refresh(); controller.refresh()
            assertEquals(1, refreshed)
            controller.close()
            assertFalse(controller.uiState.value.visible)
        } finally { scope.cancel() }
    }
    @Test fun paymentLinkRequiresVisibleReadyPendingOrderAndNeverChangesItsStatus() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val fake = Fake(BillingSnapshot(plan, "Instructions", null))
        var refreshed = 0
        val controller = BillingController(scope, Dispatchers.Unconfined, fake) { refreshed++ }
        try {
            assertNull(controller.paymentUrl())
            controller.open()
            assertNull(controller.paymentUrl())
            fake.value = fake.value.copy(order = pending)
            controller.refresh()
            val before = controller.uiState.value
            assertEquals("https://choco.tips/qr/50156198", controller.paymentUrl())
            assertEquals(before, controller.uiState.value)
            assertEquals(0, refreshed)
            assertEquals(0, fake.created)
            assertFalse(before.copy(busy = true).canPay)
            assertFalse(before.copy(error = R.string.auth_server_error).canPay)
            controller.close()
            assertNull(controller.paymentUrl())
            for (status in listOf("paid", "applying", "retry", "unknown")) {
                fake.value = fake.value.copy(order = pending.copy(status = status))
                controller.open()
                assertNull(controller.paymentUrl())
            }
            fake.value = fake.value.copy(order = pending)
            controller.open()
            fake.failure = "server_error"
            controller.refresh()
            assertNull(controller.paymentUrl())
            assertEquals(pending, controller.uiState.value.snapshot?.order)
        } finally { scope.cancel() }
    }

    @Test fun errorsAreLocalizedAndDoNotMarkAnOrderPaid() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val fake = Fake(BillingSnapshot(plan, "Instructions", pending))
        var refreshed = 0
        val controller = BillingController(scope, Dispatchers.Unconfined, fake) { refreshed++ }
        try {
            for ((code, expected) in listOf(
                "billing_unavailable" to R.string.billing_unavailable,
                "session_expired" to R.string.auth_session_expired,
                "client_disabled" to R.string.billing_manual_review,
                "traffic_depleted" to R.string.billing_manual_review,
                "client_expiry_requires_review" to R.string.billing_manual_review,
                "client_auto_renewal_requires_review" to R.string.billing_manual_review,
                "panel_client_mismatch" to R.string.billing_manual_review,
                "panel_client_changed" to R.string.billing_manual_review,
                "tunnel_client_requires_review" to R.string.billing_manual_review,
                "server_error" to R.string.auth_server_error
            )) {
                fake.failure = code
                controller.open()
                assertEquals(expected, controller.uiState.value.error)
                assertFalse(controller.uiState.value.busy)
                assertNull(controller.uiState.value.snapshot)
                assertEquals(0, refreshed)
            }
            fake.failure = null
            controller.refresh()
            assertNull(controller.uiState.value.error)
            fake.failure = "server_error"
            controller.create()
            assertEquals(pending, controller.uiState.value.snapshot?.order)
            assertEquals(0, refreshed)
        } finally { scope.cancel() }
    }
    @Test fun pendingNodeSyncDoesNotRefreshSubscriptionOrLoseAccountIsolation() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val fake = Fake(BillingSnapshot(plan, "Instructions", pending.copy(status = "paid", pendingSync = true)))
        var refreshed = 0
        val controller = BillingController(scope, Dispatchers.Unconfined, fake) { refreshed++ }
        try {
            controller.open()
            assertEquals(0, refreshed)
            controller.close()
            fake.failure = "session_expired"
            controller.open()
            assertNull(controller.uiState.value.snapshot)
            assertEquals(0, refreshed)
        } finally { scope.cancel() }
    }
}
