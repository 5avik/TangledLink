package com.v2ray.ang.ui.themes

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ThemePickerLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun smallAndLandscapeWindowsWithLargeTextKeepEveryChoiceAndActionReachable() {
        val window = mutableStateOf(DpSize(320.dp, 480.dp))
        val state = mutableStateOf(ThemePickerState(AppearanceSelection()))
        var applies = 0
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(window.value) then DeviceConfigurationOverride.FontScale(1.6f),
            ) {
                AppTheme {
                    ThemePickerScreen(state.value) { action ->
                        when (action) {
                            is ThemePickerAction.Preview -> state.value = state.value.copy(
                                draft = state.value.draft.copy(themeId = action.id))
                            is ThemePickerAction.ReduceEffects -> state.value = state.value.copy(
                                draft = state.value.draft.copy(reduceEffects = action.enabled))
                            ThemePickerAction.Apply -> applies++
                            ThemePickerAction.Cancel -> Unit
                        }
                    }
                }
            }
        }
        val reduce = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.theme_reduce_effects)
        for (size in listOf(DpSize(320.dp, 480.dp), DpSize(640.dp, 360.dp))) {
            compose.runOnIdle { window.value = size }
            for (id in ThemeId.entries) {
                compose.onNodeWithTag("theme_list").performScrollToNode(hasTestTag("theme_" + id.storageId))
                compose.onNodeWithTag("theme_" + id.storageId).performClick().assertIsSelected()
            }
            compose.onNodeWithTag("theme_list").performScrollToNode(hasText(reduce))
            compose.onNodeWithText(reduce).assertIsDisplayed().performClick()
            compose.onNodeWithTag("theme_apply").assertIsDisplayed().performClick()
        }
        compose.runOnIdle { assertEquals(2, applies) }
    }
}
