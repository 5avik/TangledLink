package com.v2ray.ang.ui.terminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.dto.TrafficSnapshot
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.*
import com.v2ray.ang.ui.main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TerminalPanelsTest {
    @get:Rule val compose = createComposeRule()
    private val before = ThemeManager.appearance.value
    private val dynamic = ThemeManager.dynamicColorEnabled.value
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private val selected = ServerRowUiModel("selected-guid",
        ProfileItem(configType = EConfigType.VLESS, server = "hidden-host.invalid", password = "hidden-credential"),
        "🇷🇴 №3 gRPC", "hidden-statistics", "VLESS / reality", 43, "")

    @After fun restore() = runBlocking(Dispatchers.IO) {
        check(ThemeManager.saveAppearance(before))
        ThemeManager.setDynamicColorEnabled(dynamic)
    }

    @Test fun tuiServerIsOneActionAndWorksWithTouchKeyboardAndDpad() {
        var clicks = 0
        var input: androidx.compose.ui.input.InputModeManager? = null
        compose.setContent {
            AppTheme {
                input = LocalInputModeManager.current
                Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
                    TerminalServerCard(selected) { clicks++ }
                }
            }
        }
        val card = compose.onNodeWithTag("terminal_selected_server")
        card.assertHasClickAction().assertIsSelected().performClick()
        compose.runOnIdle { input!!.requestInputMode(InputMode.Keyboard) }
        card.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        card.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        card.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(3, clicks) }
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithText("hidden", substring = true).assertDoesNotExist()
        compose.onNodeWithText("VLESS", substring = true).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.premium_ro), substring = true).assertExists()
        compose.onRoot().printToLog("TerminalTuiSemantics")
    }

    @Test fun panelsHandleEmptyAndLiveDataAcrossPalettesLargeTextAndLayouts() {
        val populated = mutableStateOf(false)
        val dark = mutableStateOf(true)
        val wide = mutableStateOf(false)
        val snapshot = TrafficSnapshot("test-session", 1000, 65000, 512.0, 1536.0, 0.0, 0.0)
        val traffic = TrafficUiState(snapshot, listOf(0f, 1f, 2f, 1f))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(
                if (wide.value) DpSize(640.dp, 360.dp) else DpSize(320.dp, 568.dp)
            ) then DeviceConfigurationOverride.FontScale(1.6f)) {
                AppTheme(darkTheme = dark.value) {
                    FloatingNavigationLayout("home", {}) { padding, bottom ->
                        TerminalHome(MainUiState(traffic = if (populated.value) traffic else TrafficUiState()),
                            if (populated.value) selected else null, {}, {}, {}, padding, bottom)
                    }
                }
            }
        }
        for (palette in TerminalPaletteId.entries) for (isDark in listOf(false, true))
            for (useDynamic in listOf(false, true)) {
                runBlocking(Dispatchers.IO) {
                    check(ThemeManager.saveAppearance(AppearanceSelection(ThemeId.Terminal, true, palette)))
                    ThemeManager.setDynamicColorEnabled(useDynamic)
                }
                compose.runOnIdle { dark.value = isDark; wide.value = useDynamic; populated.value = false }
                compose.onNodeWithTag("terminal_selected_server").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(label(R.string.premium_no_server), substring = true).assertExists()
                compose.onNodeWithText(label(R.string.terminal_no_data)).performScrollTo().assertIsDisplayed()
                compose.runOnIdle { populated.value = true }
                compose.onNodeWithText("00:01:05", substring = true).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(label(R.string.terminal_no_data)).assertDoesNotExist()
                compose.onNodeWithText(label(R.string.terminal_console_title)).assertIsDisplayed()
            }
    }
}
