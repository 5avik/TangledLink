package com.v2ray.ang.ui.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class TnglLoginDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dialogFocusesInputAndRejectsEmptyToken() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var submitted: String? = null
        compose.setContent {
            MaterialTheme {
                TnglLoginDialog(onConfirm = { submitted = it })
            }
        }

        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.onNodeWithText(context.getString(R.string.tngl_login_button)).performClick()
        compose.onNodeWithText(context.getString(R.string.tngl_login_empty_error)).assertExists()
        compose.runOnIdle { assertNull(submitted) }

        compose.onNode(hasSetTextAction()).performTextInput("  local-test-token  ")
        compose.onNodeWithText(context.getString(R.string.tngl_login_button)).performClick()
        compose.runOnIdle { assertEquals("local-test-token", submitted) }
    }
}
