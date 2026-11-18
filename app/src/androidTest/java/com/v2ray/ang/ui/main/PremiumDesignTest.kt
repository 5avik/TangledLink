package com.v2ray.ang.ui.main
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import kotlinx.coroutines.flow.first
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
class PremiumDesignTest {
    @get:Rule val compose=createComposeRule()
    @Test fun homeRendersReceivedTrafficRatesAndDuration() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val traffic=androidx.compose.runtime.mutableStateOf(TrafficUiState())
        compose.setContent { PremiumTheme {
            HomeScreen(ConnectionStatus.Connected,null,{},{},{},traffic=traffic.value)
        } }
        compose.onNodeWithText("Нет измерений").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            traffic.value=traffic.value.receive(com.v2ray.ang.dto.TrafficSnapshot(
                "test",100000,65000,1024.0,2048.0,0.0,0.0))
        }
        compose.onNodeWithText("Нет измерений").assertDoesNotExist()
        val down=context.getString(com.v2ray.ang.R.string.premium_rate,2.0,context.getString(com.v2ray.ang.R.string.premium_kbytes_second))
        val up=context.getString(com.v2ray.ang.R.string.premium_rate,1.0,context.getString(com.v2ray.ang.R.string.premium_kbytes_second))
        compose.onNodeWithText(down).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(up).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("00:01:05").performScrollTo().assertIsDisplayed()
    }
    @Test fun homeDisplaysCurrentAccountLogin() {
        val login=androidx.compose.runtime.mutableStateOf<String?>(null)
        compose.setContent { PremiumTheme {
            HomeScreen(ConnectionStatus.Disconnected,null,{},{},{},accountLogin=login.value)
        } }
        compose.onNodeWithText("svk").assertDoesNotExist()
        compose.runOnIdle { login.value="svk" }
        val account=compose.onNodeWithText("svk").assertIsDisplayed()
        compose.onNodeWithText("Логин:",substring=true).assertDoesNotExist()
        val button=compose.onNodeWithContentDescription("Подключить VPN")
        org.junit.Assert.assertTrue(account.fetchSemanticsNode().boundsInRoot.bottom <= button.fetchSemanticsNode().boundsInRoot.top)
        compose.runOnIdle { login.value="other-user" }
        compose.onNodeWithText("svk").assertDoesNotExist()
        compose.onNodeWithText("other-user").assertIsDisplayed()
        compose.runOnIdle { login.value=null }
        compose.onNodeWithText("other-user").assertDoesNotExist()
    }
    @Test fun subscriptionHasRealPricesAndNoPaymentSideEffect() {
        val months=androidx.compose.runtime.mutableIntStateOf(1)
        var renewals=0
        compose.setContent { PremiumTheme { SubscriptionScreen(null,months.intValue,{months.intValue=it},onRenew={renewals++}) } }
        compose.onNodeWithText("TangledLink Pro").assertExists()
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(com.v2ray.ang.R.string.premium_benefits)).assertDoesNotExist()
        compose.onNodeWithText("900 ₽").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(6,months.intValue) }
        compose.onNodeWithText("6 мес.").assertIsSelected()
        compose.onNodeWithText("150 ₽").performScrollTo().performClick()
        compose.onNodeWithText("1 мес.").assertIsSelected()
        compose.onNodeWithText("450 ₽").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(3,months.intValue) }
        compose.onNodeWithText("3 мес.").assertIsSelected()
        compose.onNodeWithText("1800 ₽").performScrollTo().assertExists()
        compose.onNodeWithText("Продлить подписку").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1,renewals) }
    }
    @Test fun subscriptionRendersUnknownActiveExpiredAndDepleted() {
        val info=androidx.compose.runtime.mutableStateOf<com.v2ray.ang.dto.entities.SubscriptionUsageInfo?>(null)
        compose.setContent { PremiumTheme { SubscriptionScreen(info.value,1,{}) } }
        compose.onNodeWithText("Данные подписки ещё не загружены").assertExists()
        compose.runOnIdle { info.value=com.v2ray.ang.dto.entities.SubscriptionUsageInfo() }
        compose.onNodeWithText("Подписка активна").assertExists()
        compose.runOnIdle { info.value=com.v2ray.ang.dto.entities.SubscriptionUsageInfo(expire=1) }
        compose.onNodeWithText("Подписка истекла").assertExists()
        compose.runOnIdle { info.value=com.v2ray.ang.dto.entities.SubscriptionUsageInfo(total=100,download=100) }
        compose.onNodeWithText("Трафик подписки исчерпан").assertExists()
    }
    @Test fun connectionButtonDispatchesActualUserIntent() {
        var calls=0
        val status=androidx.compose.runtime.mutableStateOf(ConnectionStatus.Disconnected)
        compose.setContent { PremiumTheme { ConnectionButton(status.value,true){calls++} } }
        compose.onNodeWithContentDescription("Подключить VPN").performClick()
        compose.runOnIdle { assertEquals(1,calls);status.value=ConnectionStatus.Connected }
        compose.onNodeWithContentDescription("Отключить VPN").performClick()
        compose.runOnIdle { assertEquals(2,calls) }
    }
    @Test fun settingsSwitchDispatchesOneAction() {
        val actions=mutableListOf<MainAction>()
        lateinit var inputMode: androidx.compose.ui.input.InputModeManager
        compose.setContent {
            inputMode=androidx.compose.ui.platform.LocalInputModeManager.current
            PremiumTheme { MoreScreen(PremiumPreferences(), {}, {actions.add(it)}, {}) }
        }
        compose.onNodeWithText("Подключение при запуске").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(MainAction.SetConnectOnLaunch(true)),actions) }
        compose.runOnIdle { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
        val toggle=compose.onNodeWithText("Подключение при запуске")
        toggle.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        toggle.assertIsFocused()
        toggle.performKeyInput { pressKey(Key.Enter) }
        toggle.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(List(3) { MainAction.SetConnectOnLaunch(true) },actions) }
    }
    @Test fun preferencesPersistAcrossRepositoryRecreation() = kotlinx.coroutines.runBlocking {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val store=PremiumStore(context)
        val original=store.state.first()
        val guid="premium-instrumented-check"
        try {
            store.toggle(guid)
            store.select(12)
            store.setConnectOnLaunch(!original.connectOnLaunch)
            val restored=PremiumStore(context).state.first()
            assertEquals(guid !in original.favorites,guid in restored.favorites)
            assertEquals(12,restored.months)
            assertEquals(!original.connectOnLaunch,restored.connectOnLaunch)
        } finally {
            store.toggle(guid)
            store.select(original.months)
            store.setConnectOnLaunch(original.connectOnLaunch)
        }
    }
    @Test fun connectingButtonDoesNotDispatchAnotherStart() {
        var calls=0
        compose.setContent { PremiumTheme { ConnectionButton(ConnectionStatus.Connecting,true){calls++} } }
        compose.onNodeWithContentDescription("Подключить VPN").assertIsNotEnabled()
        compose.runOnIdle {assertEquals(0,calls)}
    }
}
