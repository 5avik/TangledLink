package com.v2ray.ang.ui.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MainSettingsHubTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hubDispatchesAccountAndNavigationActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var action: MainAction? = null
        var destination: MainDestination? = null
        compose.setContent {
            MaterialTheme {
                MainSettingsHub(onBack = {}, onAction = { action = it }, onNavigate = { destination = it })
            }
        }
        compose.onNodeWithText(context.getString(R.string.auth_change_password)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(MainAction.ShowPasswordDialog, action) }
        compose.onNodeWithText(context.getString(R.string.routing_settings_title)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(MainDestination.Routing, destination) }
        compose.onNodeWithText(context.getString(R.string.title_about)).performScrollTo().assertExists()
    }

    @Test fun renewalDispatchesOnlyBillingAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var operations = 0
        var renewals = 0
        compose.setContent {
            MaterialTheme {
                SubscriptionInfoCard(info = null, fallbackTitle = "test",
                    onOpenLink = { operations++ }, onRefresh = { operations++ }, onTestPing = { operations++ },
                    onRenew = { renewals++ })
            }
        }
        compose.onNodeWithText(context.getString(R.string.renew_subscription)).performClick()
        compose.runOnIdle { assertEquals(0, operations); assertEquals(1, renewals) }
    }
}
