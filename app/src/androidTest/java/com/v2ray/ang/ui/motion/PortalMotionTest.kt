package com.v2ray.ang.ui.motion

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import org.junit.*
import org.junit.Assert.*

class PortalMotionTest {
    @get:Rule val compose=createComposeRule()
    private fun label(id:Int)=InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun animationNeverDispatchesAndOneButtonKeepsActionSeparateFromErrorState() {
        val world=mutableStateOf(MotionWorld.ORIGINAL)
        val state=mutableStateOf(PortalVisualState.DISCONNECTED)
        val running=mutableStateOf(false)
        var calls=0
        var mode: androidx.compose.ui.input.InputModeManager?=null
        compose.mainClock.autoAdvance=false
        compose.setContent {
            mode=LocalInputModeManager.current
            ConnectionPortal(world.value,state.value,
                stringResource(if(running.value) R.string.premium_disconnect else R.string.premium_connect),
                stringResource(if(state.value==PortalVisualState.ERROR) R.string.motion_service_error else
                    if(running.value) R.string.motion_service_running else R.string.motion_service_stopped),
                enabled=state.value!=PortalVisualState.CONNECTING,
                isAnimationEnabled=true,onClick={calls++})
        }
        for(w in MotionWorld.entries) for(s in PortalVisualState.entries) {
            compose.runOnUiThread {world.value=w;state.value=s;running.value=s==PortalVisualState.CONNECTED || s==PortalVisualState.ERROR}
            compose.mainClock.advanceTimeBy(12500)
            compose.runOnUiThread {assertEquals(0,calls)}
        }
        val stop=compose.onNodeWithContentDescription(label(R.string.premium_disconnect))
        stop.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,label(R.string.motion_service_error)))
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        stop.performClick()
        compose.runOnUiThread {assertEquals(1,calls);mode!!.requestInputMode(InputMode.Keyboard)}
        stop.performSemanticsAction(SemanticsActions.RequestFocus){it()}
        compose.mainClock.advanceTimeByFrame()
        stop.assertIsFocused().performKeyInput {pressKey(Key.Enter)}
        stop.performKeyInput {pressKey(Key.DirectionCenter)}
        compose.runOnUiThread {assertEquals(3,calls)}
        compose.onRoot().printToLog("MotionPortalSemantics")
    }

    @Test fun pendingIsDisabledAndStaticPortalStaysCompleteAtSmallSize() {
        var calls=0
        compose.setContent {
            ConnectionPortal(MotionWorld.CYBERPUNK,PortalVisualState.CONNECTING,label(R.string.premium_connect),
                label(R.string.motion_service_starting),enabled=false,isAnimationEnabled=false,onClick={calls++},
                modifier=Modifier.size(128.dp))
        }
        compose.onNodeWithContentDescription(label(R.string.premium_connect)).assertIsNotEnabled()
        compose.runOnIdle {assertEquals(0,calls)}
    }
}
