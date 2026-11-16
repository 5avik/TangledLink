package com.v2ray.ang.ui.themes

import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.ui.compose.LocalReduceEffects
import com.v2ray.ang.ui.compose.sceneMotionAllowed
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SceneMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun decorationsRequireResumedLifecycleAndRespectReducedEffects() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val reduce = mutableStateOf(false)
        var allowed = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val systemEnabled = Settings.Global.getFloat(context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalReduceEffects provides reduce.value) {
                val animate = sceneMotionAllowed()
                SideEffect { allowed = animate }
            }
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(systemEnabled, allowed); reduce.value = true }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(allowed); reduce.value = false }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(systemEnabled, allowed); owner.registry.currentState = Lifecycle.State.STARTED }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(allowed); owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(systemEnabled, allowed); owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(allowed) }
    }
}
