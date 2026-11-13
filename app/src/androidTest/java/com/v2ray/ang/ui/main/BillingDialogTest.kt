package com.v2ray.ang.ui.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.handler.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BillingDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun orderCanBeCreatedAndStatusRefreshedWithoutClaimingPayment() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var action: MainAction? = null
        compose.setContent {
            MaterialTheme {
                BillingDialog(BillingUiState(visible = true, snapshot = BillingSnapshot(
                    BillingPlan("month30", 15000, 30), "Test payment instructions", null
                ))) { action = it }
            }
        }
        compose.onNodeWithText(context.getString(R.string.billing_create)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(MainAction.CreateBillingOrder, action) }
        compose.onNodeWithText(context.getString(R.string.billing_check)).performClick()
        compose.runOnIdle { assertEquals(MainAction.RefreshBilling, action) }
        compose.onNodeWithText(context.getString(R.string.billing_paid)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.billing_pay)).assertDoesNotExist()
    }
    @Test fun pendingOrderShowsLoginAndPaymentActionWithoutConfirmingIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var action: MainAction? = null
        compose.setContent {
            MaterialTheme {
                BillingDialog(BillingUiState(visible = true, snapshot = BillingSnapshot(
                    BillingPlan("month30", 15000, 30), "Test payment instructions",
                    BillingOrder("TL-example", "svk", "pending", null, false)
                ))) { action = it }
            }
        }
        compose.onNodeWithText(context.getString(R.string.billing_payment_instructions, "svk")).performScrollTo().assertExists()
        compose.onNodeWithText(context.getString(R.string.billing_pay)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(MainAction.OpenBillingPayment, action) }
        compose.onNodeWithText(context.getString(R.string.billing_order, "TL-example", "svk")).performScrollTo().assertExists()
        compose.onNodeWithText(context.getString(R.string.billing_pending)).performScrollTo().assertExists()
        compose.onNodeWithText(context.getString(R.string.billing_create)).assertDoesNotExist()
    }
}
