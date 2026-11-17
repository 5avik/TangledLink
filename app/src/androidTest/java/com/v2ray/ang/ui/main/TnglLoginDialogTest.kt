package com.v2ray.ang.ui.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@androidx.compose.runtime.Composable
private fun StaticAuthTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(com.v2ray.ang.ui.compose.LocalReduceEffects provides true) {
        MaterialTheme(content = content)
    }
}

class TnglLoginDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun registrationIsVisibleAndDisabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { StaticAuthTheme { TnglLoginDialog(onConfirm = { _, _, _ -> }) } }
        compose.onNodeWithText(context.getString(R.string.auth_registration))
            .performScrollTo().assertIsNotEnabled()
    }

    @Test fun registrationSubmitsOnlyToRegistrationAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var submitted: Pair<String,String>? = null
        compose.setContent {
            StaticAuthTheme {
                TnglLoginDialog(onRegister={id,password->submitted=id to password},
                    onConfirm={_,_,_->throw AssertionError("Registration must not log in or activate")})
            }
        }
        compose.onNodeWithText(context.getString(R.string.auth_registration)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.auth_id)).performTextInput("new-user")
        compose.onNodeWithText(context.getString(R.string.auth_password)).performTextInput("test-password-123")
        compose.onNodeWithText(context.getString(R.string.registration_submit)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("new-user" to "test-password-123",submitted) }
    }

    @Test fun loginSubmitsCredentialsWithoutActivationCode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var submitted: Triple<String, String, String?>? = null
        compose.setContent {
            StaticAuthTheme {
                TnglLoginDialog(onConfirm = { id, password, code -> submitted = Triple(id, password, code) })
            }
        }
        compose.onNodeWithText(context.getString(R.string.auth_id)).performTextInput("example123")
        compose.onNodeWithText(context.getString(R.string.auth_password)).performTextInput("local-password")
        compose.onNodeWithText(context.getString(R.string.tngl_login_button)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("example123", submitted?.first)
            assertEquals("local-password", submitted?.second)
            assertNull(submitted?.third)
        }
    }

    @Test fun activationSubmitsOneTimeCode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var submitted: String? = null
        compose.setContent {
            StaticAuthTheme { TnglLoginDialog(onConfirm = { _, _, code -> submitted = code }) }
        }
        compose.onNodeWithText(context.getString(R.string.auth_activate)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.auth_id)).performTextInput("example123")
        compose.onNodeWithText(context.getString(R.string.auth_activation_code)).performTextInput("one-time-code")
        compose.onNodeWithText(context.getString(R.string.auth_new_password)).performScrollTo().performTextInput("local-password")
        compose.onNodeWithText(context.getString(R.string.auth_activate_submit)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("one-time-code", submitted) }
    }
}
