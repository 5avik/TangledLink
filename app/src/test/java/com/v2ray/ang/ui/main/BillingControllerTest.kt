package com.v2ray.ang.ui.main

import com.v2ray.ang.R
import com.v2ray.ang.handler.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BillingControllerTest {
    private val plan = BillingPlan("month30", 15000, 30)
    private val pending = BillingOrder("TL-test", "svk", "pending", null, false, "https://pay.lava.top/test")
    private class Fake(var value: BillingSnapshot) : BillingGateway {
        var failure: String? = null
        var created = 0
        var resumed = 0
        override fun resume(orderId: String): BillingOrder {
            check(orderId == value.order?.id)
            failure?.let { throw TnglAuthException(it) }
            resumed++
            return requireNotNull(value.order)
        }
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
    @Test fun payRechecksWithServerAndUsesOnlyItsFreshLink() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val fake=Fake(BillingSnapshot(plan,"",pending))
        var paid=0
        val controller=BillingController(scope,Dispatchers.Unconfined,fake){paid++}
        val urls=mutableListOf<String>()
        scope.launch {controller.paymentEvents.collect {urls.add(it)}}
        try {
            controller.pay()
            assertEquals(0,fake.resumed)
            controller.open()
            fake.value=fake.value.copy(order=pending.copy(paymentUrl="https://pay.lava.top/fresh"))
            controller.pay()
            assertEquals(listOf("https://pay.lava.top/fresh"),urls)
            assertEquals(1,fake.resumed);assertEquals(0,fake.created)
            assertEquals("pending",controller.uiState.value.snapshot?.order?.status)
            controller.refresh()
            assertEquals(1,urls.size)
            fake.failure="invoice_requires_review"
            controller.pay()
            assertEquals(R.string.billing_manual_review,controller.uiState.value.error)
            assertEquals(1,urls.size);assertFalse(controller.uiState.value.busy)
            fake.failure=null;controller.refresh()
            fake.value=fake.value.copy(order=pending.copy(status="paid",paymentUrl=null))
            controller.pay()
            assertEquals(2,fake.resumed);assertEquals(1,paid)
            assertEquals(1,urls.size)
            controller.pay();assertEquals(2,fake.resumed)
            controller.close();controller.pay();assertEquals(2,fake.resumed)
        } finally {scope.cancel()}
    }
    @Test fun browserReturnOnlyLoadsServerStatusAndNeverCreatesPayment() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val fake=Fake(BillingSnapshot(plan,"",pending.copy(status="paid")))
        var paid=0
        val controller=BillingController(scope,Dispatchers.Unconfined,fake){paid++}
        val urls=mutableListOf<String>()
        scope.launch {controller.paymentEvents.collect {urls.add(it)}}
        try {
            controller.returnFromPayment()
            assertTrue(controller.uiState.value.visible)
            assertTrue(controller.uiState.value.hasCreatedOrder)
            assertEquals("paid",controller.uiState.value.snapshot?.order?.status)
            assertEquals(1,controller.uiState.value.returnSequence)
            assertEquals(1,paid)
            assertEquals(0,fake.created)
            assertTrue(urls.isEmpty())
            controller.returnFromPayment()
            assertEquals(2,controller.uiState.value.returnSequence)
            assertEquals(1,paid)
            fake.failure="session_expired"
            controller.returnFromPayment()
            assertEquals(R.string.auth_session_expired,controller.uiState.value.error)
            assertNull(controller.uiState.value.snapshot)
            assertEquals(0,fake.created)
            assertTrue(urls.isEmpty())
        } finally {scope.cancel()}
    }
    @Test fun pendingBrowserReturnWaitsForServerConfirmation() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        var loads=0
        val gateway=object:BillingGateway {
            override fun load():BillingSnapshot {
                loads++
                return BillingSnapshot(plan,"",pending.copy(status=if(loads==1)"pending" else "paid"))
            }
            override fun create(planId:String):BillingOrder=error("Must not create from a return link")
        }
        var paid=0
        val controller=BillingController(scope,Dispatchers.Unconfined,gateway){paid++}
        try {
            controller.returnFromPayment()
            assertTrue(controller.uiState.value.busy)
            assertEquals(0,paid)
            withTimeout(4000) { controller.uiState.first {it.visible&&!it.busy} }
            assertEquals(2,loads)
            assertEquals(1,paid)
        } finally {scope.cancel()}
    }
    @Test fun selectedPlanCreatesPaymentAndEmitsNavigationExactlyOnce() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val selected=BillingPlan("month90",45000,90)
        val fake=Fake(BillingSnapshot(selected,"",pending,listOf(plan,selected)))
        val controller=BillingController(scope,Dispatchers.Unconfined,fake){}
        val urls=mutableListOf<String>()
        scope.launch {controller.paymentEvents.collect {urls.add(it)}}
        try {
            controller.open("month90")
            assertEquals("month90",controller.uiState.value.planId)
            assertTrue(urls.isEmpty())
            controller.email("buyer@example.com");controller.create()
            assertEquals(listOf("https://pay.lava.top/test"),urls)
            controller.refresh();controller.close();controller.open("month90")
            assertEquals(1,urls.size)
        } finally {scope.cancel()}
    }
    @Test fun invalidEmailAndFailedRequestsNeverOpenBrowser() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val fake=Fake(BillingSnapshot(plan,"",null))
        val controller=BillingController(scope,Dispatchers.Unconfined,fake){}
        val urls=mutableListOf<String>()
        scope.launch {controller.paymentEvents.collect {urls.add(it)}}
        try {
            controller.open();controller.create()
            assertTrue(urls.isEmpty())
            controller.email("buyer@example.com");fake.failure="server_error";controller.create()
            assertTrue(urls.isEmpty())
            assertEquals(R.string.auth_server_error,controller.uiState.value.error)
        } finally {scope.cancel()}
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
            controller.email("buyer@example.com")
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
            assertEquals("https://pay.lava.top/test", controller.paymentUrl())
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
            controller.email("buyer@example.com")
            controller.create()
            assertEquals(pending, controller.uiState.value.snapshot?.order)
            assertEquals(0, refreshed)
        } finally { scope.cancel() }
    }
    @Test fun invalidEmailAndUnknownPlanNeverCreatePayment() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val fake = Fake(BillingSnapshot(plan, "Instructions", null))
        val controller = BillingController(scope, Dispatchers.Unconfined, fake) {}
        try {
            controller.open()
            for (email in listOf("", "svk", "bad@", "a b@example.com")) {
                controller.email(email); controller.create()
                assertEquals(R.string.billing_invalid_email, controller.uiState.value.error)
                assertEquals(0, fake.created)
            }
            controller.email("buyer@example.com"); controller.selectPlan("unknown"); controller.create()
            assertEquals(R.string.billing_manual_review, controller.uiState.value.error)
            assertEquals(0, fake.created)
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
