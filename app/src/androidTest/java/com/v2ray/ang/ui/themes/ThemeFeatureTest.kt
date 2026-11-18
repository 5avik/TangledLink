package com.v2ray.ang.ui.themes

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.*
import com.v2ray.ang.ui.main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*

class ThemeFeatureTest {
    @get:Rule val compose = createComposeRule()
    private val previousAppearance = ThemeManager.appearance.value
    private val previousMode = ThemeManager.themeMode.value
    private val previousDynamic = ThemeManager.dynamicColorEnabled.value

    @After fun restoreSettings() {
        runBlocking(Dispatchers.IO) {
            check(ThemeManager.saveAppearance(previousAppearance))
            ThemeManager.setThemeMode(previousMode)
            ThemeManager.setDynamicColorEnabled(previousDynamic)
        }
    }

    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun save(id: ThemeId, reduced: Boolean = true) = runBlocking(Dispatchers.IO) {
        check(ThemeManager.saveAppearance(AppearanceSelection(id, reduced)))
    }

    @Test fun allWorldsRenderAndInteractAcrossPalettesAndColumnLayouts() {
        val dark = mutableStateOf(true)
        val wide = mutableStateOf(false)
        var actualPrimary = Color.Unspecified
        var expectedPrimary = Color.Unspecified
        var sceneId = ThemeId.Original
        var settingsClicks = 0
        var serverClicks = 0
        var connects = 0
        var input: androidx.compose.ui.input.InputModeManager? = null
        var dynamicExpected = false
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(
                if (wide.value) DpSize(840.dp, 411.dp) else DpSize(411.dp, 891.dp),
            ) then DeviceConfigurationOverride.FontScale(1.3f)) {
                AppTheme(darkTheme = dark.value) {
                    input = LocalInputModeManager.current
                    val config = LocalThemeConfig.current
                    val context = LocalContext.current
                    val expected = if (dynamicExpected && Build.VERSION.SDK_INT >= 31)
                        if (dark.value) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    else if (dark.value) config.dark else config.light
                    val primary = MaterialTheme.colorScheme.primary
                    SideEffect {
                        actualPrimary = primary
                        expectedPrimary = expected.primary
                        sceneId = config.id
                    }
                    FloatingNavigationLayout("home", {}) { insets, clearance ->
                        HomeScreen(ConnectionStatus.Disconnected,
                            ServerRowUiModel("stable-guid", ProfileItem(configType = EConfigType.VLESS),
                                "🇷🇴 №3 gRPC", "", "", 25, ""),
                            { connects++ }, { serverClicks++ }, { settingsClicks++ },
                            accountLogin = "theme-check", contentInsets = insets, bottomPadding = clearance)
                    }
                }
            }
        }
        for (id in ThemeId.entries.filter {it!=ThemeId.Terminal}) for (isDark in listOf(false, true))
            for (dynamic in listOf(false, true)) for (isWide in listOf(false, true)) {
                dynamicExpected = dynamic
                runBlocking(Dispatchers.IO) { ThemeManager.setDynamicColorEnabled(dynamic) }
                save(id)
                compose.runOnIdle { dark.value = isDark; wide.value = isWide }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertEquals(id, sceneId)
                    assertEquals(expectedPrimary, actualPrimary)
                    input!!.requestInputMode(InputMode.Keyboard)
                }
                val portal = compose.onNodeWithContentDescription(label(R.string.premium_connect))
                portal.performScrollTo().assertHasClickAction()
                portal.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                portal.assertIsFocused()
                portal.performKeyInput { pressKey(Key.Enter) }
                val server = compose.onNodeWithText(label(R.string.premium_ro))
                server.performScrollTo().performClick()
                compose.onNodeWithContentDescription(label(R.string.premium_settings)).performScrollTo().performClick()
            }
        compose.runOnIdle {
            assertEquals((ThemeId.entries.size-1) * 8, connects)
            assertEquals((ThemeId.entries.size-1) * 8, serverClicks)
            assertEquals((ThemeId.entries.size-1) * 8, settingsClicks)
        }
    }

    @Test fun pickerHasSingleRadioTargetAndSupportsKeyboardDpadApplyCancelAndFailure() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val stored = MutableStateFlow(AppearanceSelection())
        var fail = true
        var writes = 0
        val store = object : AppearanceStore {
            override val appearance: StateFlow<AppearanceSelection> = stored
            override fun saveAppearance(selection: AppearanceSelection): Boolean {
                writes++
                if (fail) return false
                stored.value = selection
                return true
            }
        }
        val controller = ThemeSelectionController(scope, Dispatchers.Unconfined, store, SavedStateHandle())
        var input: androidx.compose.ui.input.InputModeManager? = null
        try {
            compose.setContent {
                input = LocalInputModeManager.current
                AppTheme {
                    val state by controller.state.collectAsState()
                    ThemePickerScreen(state, controller::onAction)
                }
            }
            for (id in listOf(ThemeId.Sakura, ThemeId.Aurora, ThemeId.Cyberpunk, ThemeId.Terminal)) {
                compose.onNodeWithTag("theme_list").performScrollToNode(hasTestTag("theme_" + id.storageId))
                val target = compose.onNodeWithTag("theme_" + id.storageId)
                target.performScrollTo()
                compose.runOnIdle { input!!.requestInputMode(InputMode.Keyboard) }
                target.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                target.assertIsFocused()
                target.performKeyInput { pressKey(if (id == ThemeId.Aurora) Key.DirectionCenter else Key.Spacebar) }
                target.assertIsSelected()
                assertEquals(0, writes)
                assertEquals(ThemeId.Original, stored.value.themeId)
            }
            compose.onNodeWithTag("theme_list").performScrollToNode(hasTestTag("palette_tokyo_night"))
            val palette = compose.onNodeWithTag("palette_tokyo_night")
            palette.performScrollTo()
            palette.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            palette.assertIsFocused()
            palette.performKeyInput { pressKey(Key.DirectionCenter) }
            palette.assertIsSelected()
            assertEquals(0, writes)
            compose.onNodeWithTag("theme_apply").performClick()
            compose.onNodeWithText(label(R.string.theme_save_error)).assertIsDisplayed()
            assertFalse(controller.state.value.applied)
            fail = false
            compose.onNodeWithTag("theme_apply").performClick()
            compose.runOnIdle {
                assertTrue(controller.state.value.applied)
                assertEquals(ThemeId.Terminal, stored.value.themeId)
                assertEquals(com.v2ray.ang.ui.terminal.TerminalPaletteId.TOKYO_NIGHT, stored.value.terminalPalette)
                assertEquals(2, writes)
            }
        } finally { scope.cancel() }
    }

    @Test fun localPreviewSurvivesRecreationAndBackLeavesSavedChoice() {
        save(ThemeId.Original)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val scenario = androidx.test.core.app.ActivityScenario.launch<ThemePickerActivity>(
            android.content.Intent(context, ThemePickerActivity::class.java),
        )
        try {
            compose.onNodeWithTag("theme_list").performScrollToNode(hasTestTag("theme_terminal"))
            compose.onNodeWithTag("theme_terminal").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("theme_list").performScrollToNode(hasTestTag("palette_gruvbox_dark"))
            compose.onNodeWithTag("palette_gruvbox_dark").performScrollTo().performClick().assertIsSelected()
            scenario.recreate()
            compose.onNodeWithTag("theme_list").performScrollToNode(hasTestTag("theme_terminal"))
            compose.onNodeWithTag("theme_terminal").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("theme_list").performScrollToNode(hasTestTag("palette_gruvbox_dark"))
            compose.onNodeWithTag("palette_gruvbox_dark").performScrollTo().assertIsSelected()
            assertEquals(ThemeId.Original, ThemeManager.appearance.value.themeId)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(5000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
            assertEquals(ThemeId.Original, ThemeManager.appearance.value.themeId)
        } finally { scenario.close() }
    }
}
