package com.v2ray.ang.ui.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.handler.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BillingDialogTest {
    @get:Rule val compose = createComposeRule()
    private val plan = BillingPlan("month30",15000,30)
    @Test fun emailAndOptionalPromoCanBeSubmittedWithImeOpen() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val state=mutableStateOf(BillingUiState(visible=true,planId="month90",
            snapshot=BillingSnapshot(plan,"Must not repeat",null,listOf(plan,BillingPlan("month90",45000,90)))))
        var action:MainAction?=null
        compose.setContent { MaterialTheme { BillingDialog(state.value) {
            action=it
            if(it is MainAction.BillingEmail)state.value=state.value.copy(email=it.value)
            if(it is MainAction.BillingPromoCode)state.value=state.value.copy(promoCode=it.value)
        } } }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onNodeWithText("Must not repeat").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.billing_plan,450,90)).assertDoesNotExist()
        val field=compose.onNodeWithText(context.getString(R.string.billing_email))
        field.performScrollTo().performClick()
        field.performTextInput("buyer@example.com")
        compose.onNodeWithText(context.getString(R.string.billing_promo)).performScrollTo().performClick().performTextInput("SOTKA")
        compose.onNodeWithText(context.getString(R.string.billing_pay)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("buyer@example.com",state.value.email)
            assertEquals("SOTKA",state.value.promoCode)
            assertEquals("month90",state.value.planId)
            assertEquals(MainAction.CreateBillingOrder,action)
        }
    }
    @Test fun pendingOrderHasOneStatusAndNoRepeatedInstructions() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        var action:MainAction?=null
        compose.setContent { MaterialTheme { BillingDialog(BillingUiState(visible=true,
            snapshot=BillingSnapshot(plan,"Must not repeat",
                BillingOrder("TL-example","svk","pending",null,false,"https://pay.lava.top/test",10001,"SOTKA")))) {action=it} } }
        compose.onNodeWithText(context.getString(R.string.billing_total,"100.01")).assertExists()
        compose.onNodeWithText(context.getString(R.string.billing_promo_value,"SOTKA")).assertExists()
        compose.onNodeWithText("Must not repeat").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.billing_lava_instructions)).assertDoesNotExist()
        compose.onAllNodesWithText(context.getString(R.string.billing_pending)).assertCountEquals(1)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.billing_pay)).performClick()
        compose.runOnIdle {assertEquals(MainAction.OpenBillingPayment,action)}
        compose.onNodeWithText(context.getString(R.string.billing_check)).performClick()
        compose.runOnIdle {assertEquals(MainAction.RefreshBilling,action)}
    }
    @Test fun loadingAndFailureDoNotEnableSubmission() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val state=mutableStateOf(BillingUiState(visible=true,busy=true,
            snapshot=BillingSnapshot(plan,"",null)))
        compose.setContent { MaterialTheme {BillingDialog(state.value){}} }
        compose.onNodeWithText(context.getString(R.string.billing_pay)).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {state.value=state.value.copy(busy=false,error=R.string.auth_server_error)}
        compose.onNodeWithText(context.getString(R.string.auth_server_error)).performScrollTo().assertIsDisplayed()
    }
}
