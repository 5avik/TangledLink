package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Rule
import org.junit.Test

/** Exercises the production NavHost and bottom bar without a VPN, account or payment. */
class PremiumNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var inputMode: InputModeManager
    private val sequence = mutableLongStateOf(0)
    private val hasServer = mutableStateOf(true)
    private val server = ServerRowUiModel("qa-guid", ProfileItem(configType=EConfigType.VLESS),
        "QA server", "", "", 12, "")
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun tab(id: Int) = compose.onNode(
        hasText(label(id)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
    private fun page(route: String) = compose.onNodeWithTag("page_$route").assertIsDisplayed()

    @Composable private fun Fixture() {
        inputMode = LocalInputModeManager.current
        PremiumTheme {
            PremiumTabNavigation(sequence.longValue) { route, selectTab, padding, clearance ->
                Box(Modifier.fillMaxSize().testTag("page_$route")) {
                    when (route) {
                        "home" -> HomeScreen(ConnectionStatus.Disconnected, if(hasServer.value) server else null,
                            {}, {selectTab("servers")}, {selectTab("more")},
                            accountLogin="qa-account", contentInsets=padding, bottomPadding=clearance)
                        "more" -> MoreScreen(PremiumPreferences(), {}, {}, {selectTab("servers")},
                            contentInsets=padding, bottomPadding=clearance, account={selectTab("account")})
                        "account" -> AccountScreen("qa-account",null,{}, {selectTab("more")},padding,clearance)
                        "servers" -> {
                            var count by rememberSaveable { mutableIntStateOf(0) }
                            Column(Modifier.padding(padding)) {
                                Text("server-state:$count")
                                TextButton(onClick={count++}) { Text("increment-server-state") }
                            }
                        }
                        "subscription" -> SubscriptionScreen(null, 1, {},
                            contentInsets=padding, bottomPadding=clearance)
                    }
                }
            }
        }
    }

    @Test fun accountChildReturnsToSettingsAndNeverReopensFromHome() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent {Fixture()}
        repeat(2) {
            tab(R.string.premium_more).performClick()
            compose.onNodeWithText(label(R.string.premium_manage_account)).performScrollTo().performClick()
            page("account");tab(R.string.premium_more).assertIsSelected()
            compose.onNodeWithText(label(R.string.premium_password)).assertExists()
            restoration.emulateSavedInstanceStateRestore()
            page("account")
            compose.onNodeWithContentDescription(label(R.string.premium_account_back)).performClick()
            page("more")
            compose.onNodeWithText(label(R.string.premium_password)).assertDoesNotExist()
            compose.onNodeWithText(label(R.string.premium_manage_account)).performScrollTo().performClick()
            page("account")
            tab(R.string.premium_home).performClick();page("home")
            tab(R.string.premium_more).performClick();page("more")
            tab(R.string.premium_home).performClick();page("home")
        }
    }

    @Test fun settingsShortcutAndEveryBottomTabReturnToActualHome() {
        compose.setContent { Fixture() }
        repeat(3) {
            page("home")
            compose.onNodeWithContentDescription(label(R.string.premium_settings)).performClick()
            page("more")
            tab(R.string.premium_more).assertIsSelected()
            tab(R.string.premium_home).performClick()
            page("home")
            tab(R.string.premium_home).assertIsSelected()
        }
        for ((id, route) in listOf(R.string.premium_servers to "servers",
            R.string.premium_subscription to "subscription", R.string.premium_more to "more")) {
            repeat(2) {
                tab(id).performClick(); page(route); tab(id).assertIsSelected()
                tab(id).performClick(); page(route)
                tab(R.string.premium_home).performClick(); page("home")
            }
        }
    }

    @Test fun serverCardEmptySelectionAndProtocolShortcutUseTheSameTabNavigation() {
        compose.setContent { Fixture() }
        compose.onNodeWithText("QA server").performScrollTo().performClick()
        page("servers")
        compose.onNodeWithText("increment-server-state").performClick()
        tab(R.string.premium_home).performClick(); page("home")
        compose.onNodeWithContentDescription(label(R.string.premium_settings)).performClick()
        compose.onNodeWithText(label(R.string.premium_protocol)).performScrollTo().performClick()
        page("servers")
        compose.onNodeWithText("server-state:1").assertIsDisplayed()
        tab(R.string.premium_home).performClick(); page("home")
        compose.runOnIdle { hasServer.value = false }
        compose.onNodeWithText(label(R.string.premium_no_server)).performScrollTo().performClick()
        page("servers")
        tab(R.string.premium_home).performClick(); page("home")
    }

    @Test fun shortcutsAndHomeTabWorkWithKeyboardAndDpad() {
        compose.setContent { Fixture() }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        val settings = compose.onNodeWithContentDescription(label(R.string.premium_settings))
        settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        settings.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        page("more")
        val home = tab(R.string.premium_home)
        home.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        home.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        page("home")
        val card = compose.onNodeWithText("QA server").performScrollTo()
        card.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        card.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        page("servers")
        tab(R.string.premium_home).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        tab(R.string.premium_home).assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        page("home")
    }

    @Test fun handledBillingReturnDoesNotOverrideHomeAfterStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Fixture() }
        compose.runOnIdle { sequence.longValue = 1 }
        page("subscription")
        tab(R.string.premium_home).performClick(); page("home")
        restoration.emulateSavedInstanceStateRestore()
        page("home")
        tab(R.string.premium_home).assertIsSelected()
        compose.runOnIdle { sequence.longValue = 2 }
        page("subscription")
        tab(R.string.premium_more).performClick(); page("more")
        restoration.emulateSavedInstanceStateRestore()
        page("more")
        tab(R.string.premium_home).performClick(); page("home")
    }
}
