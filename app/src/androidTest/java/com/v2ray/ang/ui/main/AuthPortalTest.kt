package com.v2ray.ang.ui.main

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalReduceEffects
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuthPortalTest {
    @get:Rule val compose=createComposeRule()

    @Test fun confirmationRevealsOneHomeAndDoesNotReplay() {
        val login=mutableStateOf(true)
        val success=mutableLongStateOf(0)
        val reduce=mutableStateOf(false)
        var starts=0
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            CompositionLocalProvider(LocalReduceEffects provides reduce.value) {
                AuthPortalHost(login.value,success.longValue,home={
                    DisposableEffect(Unit){starts++;onDispose{}}
                    Text("Home fixture")
                }) { Text("Login fixture") }
            }
        }
        compose.onNodeWithText("Home fixture").assertDoesNotExist()
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread { success.longValue++; login.value=false }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText(context.getString(R.string.auth_portal_welcome)).assertExists()
        compose.onNodeWithText("Home fixture").assertExists()
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithText("Login fixture").assertDoesNotExist()
        compose.onNodeWithText("Home fixture").assertExists()
        compose.runOnUiThread { reduce.value=true }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertEquals(1,starts) }
    }
}
