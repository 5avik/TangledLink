package com.v2ray.ang.ui.main

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccountScreenTest {
    @get:Rule val compose=createComposeRule()
    private fun label(id:Int)=InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    @Test fun accountActionsUseExistingContractAndDisplayOnlyCurrentUser() {
        val login=mutableStateOf<String?>("svk")
        val actions=mutableListOf<MainAction>()
        var backs=0
        compose.setContent {PremiumTheme {AccountScreen(login.value,null,{actions.add(it)},{backs++})}}
        compose.onNodeWithText("svk").assertExists()
        compose.onNodeWithText(label(R.string.premium_password)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.premium_account)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(MainAction.RefreshEmail,MainAction.ShowPasswordDialog,MainAction.ChangeAccount),actions)
            login.value="other-user"
        }
        compose.onNodeWithText("svk").assertDoesNotExist()
        compose.onNodeWithText("other-user").assertExists()
        compose.runOnIdle {login.value=null}
        compose.onNodeWithText("other-user").assertDoesNotExist()
        compose.onNodeWithText(label(R.string.premium_password)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription(label(R.string.premium_account_back)).performScrollTo().performClick()
        compose.runOnIdle {assertEquals(1,backs)}
    }
    @Test fun confirmedAddressAndBindingActionComeFromServerState() {
        val address=mutableStateOf<String?>(null)
        val actions=mutableListOf<MainAction>()
        compose.setContent {
            PremiumTheme {AccountScreen("member",null,{actions.add(it)},{},
                verifiedEmail=address.value,emailLoaded=true)}
        }
        compose.onNodeWithText(label(R.string.email_bind)).performScrollTo().performClick()
        compose.runOnIdle {assertEquals(MainAction.ShowBindEmail,actions.last());address.value="verified@example.com"}
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(
            R.string.email_confirmed,"verified@example.com")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.email_bind)).assertDoesNotExist()
    }
    @Test fun subscriptionCardShowsUnknownActiveExpiredAndDepletedData() {
        val info=mutableStateOf<SubscriptionUsageInfo?>(null)
        compose.setContent {PremiumTheme {AccountScreen("svk",info.value,{},{})}}
        compose.onNodeWithText(label(R.string.premium_unknown_subscription)).assertExists()
        compose.runOnIdle {info.value=SubscriptionUsageInfo()}
        compose.onNodeWithText(label(R.string.premium_active)).assertExists()
        compose.runOnIdle {info.value=SubscriptionUsageInfo(expire=1)}
        compose.onNodeWithText(label(R.string.premium_expired)).assertExists()
        compose.runOnIdle {info.value=SubscriptionUsageInfo(total=100,download=100)}
        compose.onNodeWithText(label(R.string.premium_depleted)).assertExists()
    }
}
