package com.v2ray.ang.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.v2ray.ang.dto.TrafficSnapshot
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdaptiveHomeLayoutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var inputMode: androidx.compose.ui.input.InputModeManager

    @Composable
    private fun Fixture(width: Int, height: Int, fontScale: Float = 1f) {
        DeviceConfigurationOverride(
            DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp)) then
                DeviceConfigurationOverride.FontScale(fontScale),
        ) {
            inputMode=androidx.compose.ui.platform.LocalInputModeManager.current
            PremiumTheme {
                FloatingNavigationLayout("home", {}) { insets, clearance ->
                    HomeScreen(
                        ConnectionStatus.Connected,
                        ServerRowUiModel("server-guid", ProfileItem(configType = EConfigType.VLESS),
                            "🇬🇧 Лондон", "", "", 25, ""),
                        {}, {}, {}, accountLogin = "svk",
                        traffic = TrafficUiState().receive(TrafficSnapshot("test", 100000, 65000,
                            1024.0, 2048.0, 0.0, 0.0)),
                        contentInsets = insets,
                        bottomPadding = clearance,
                    )
                }
            }
        }
    }

    private fun assertTimerAboveNavigation(scroll: Boolean) {
        val timer = compose.onNodeWithText("00:01:05")
        if (scroll) {
            timer.performScrollTo()
            val menu=compose.onNodeWithTag("floating_navigation").fetchSemanticsNode().boundsInRoot
            if (timer.fetchSemanticsNode().boundsInRoot.bottom > menu.top) {
                compose.onNodeWithTag("home_scroll").performSemanticsAction(
                    androidx.compose.ui.semantics.SemanticsActions.ScrollBy,
                ) { it(0f, menu.height + 32f) }
            }
        }
        timer.assertIsDisplayed()
        val timeBounds = timer.fetchSemanticsNode().boundsInRoot
        val navigationBounds = compose.onNodeWithTag("floating_navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("Timer $timeBounds must be above floating menu $navigationBounds", timeBounds.bottom <= navigationBounds.top + 1f)
        compose.onNodeWithText("Главная").assertIsDisplayed()
        compose.onNodeWithText("Ещё").assertIsDisplayed()
    }

    @Test fun mediumPhoneFitsAllHomeContent() {
        compose.setContent { Fixture(411, 891) }
        compose.onNodeWithText("TangledLink").assertIsDisplayed()
        compose.onNodeWithText("svk").assertIsDisplayed()
        compose.onNodeWithContentDescription("Отключить VPN").assertIsDisplayed()
        assertTimerAboveNavigation(scroll = false)
    }

    @Test fun landscapeUsesAvailableWidth() {
        compose.setContent { Fixture(840, 411) }
        val power=compose.onNodeWithContentDescription("Отключить VPN").fetchSemanticsNode().boundsInRoot
        val server=compose.onNodeWithText("Великобритания").fetchSemanticsNode().boundsInRoot
        assertTrue("Landscape must put connection and server panels side by side", power.right <= server.left)
        assertTimerAboveNavigation(scroll = true)
    }

    @Test fun focusedServerStaysAboveFloatingNavigation() {
        compose.setContent { Fixture(320, 480, 1.3f) }
        val server=compose.onNodeWithText("Великобритания").performScrollTo()
        compose.runOnIdle { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
        server.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        server.assertIsFocused()
        compose.waitForIdle()
        val bounds=server.fetchSemanticsNode().boundsInRoot
        val menu=compose.onNodeWithTag("floating_navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("Focused server $bounds must be above floating menu $menu", bounds.bottom <= menu.top + 1f)
    }

    @Test fun smallPhoneCanScrollAllContentClearOfOverlay() {
        compose.setContent { Fixture(320, 568) }
        assertTimerAboveNavigation(scroll = true)
    }

    @Test fun largeTextRemainsReachableWithoutClippingBehindNavigation() {
        compose.setContent { Fixture(360, 640, 1.6f) }
        assertTimerAboveNavigation(scroll = true)
    }
}
