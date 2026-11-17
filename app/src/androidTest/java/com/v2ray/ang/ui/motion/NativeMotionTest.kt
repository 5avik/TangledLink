package com.v2ray.ang.ui.motion

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.*
import org.junit.Assert.*

class NativeMotionTest {
    @get:Rule val compose=createComposeRule()

    @Test fun nativeWorldsMoveOnlyWhenAllowedAndHaveNoAccessibleDecorations() {
        val enabled=mutableStateOf(false)
        val world=mutableStateOf(MotionWorld.ORIGINAL)
        compose.mainClock.autoAdvance=false
        compose.setContent {
            NativeMotionBackground(ColorPainter(Color.Black),world.value,enabled.value,Modifier.fillMaxSize())
        }
        for(w in MotionWorld.entries) {
            compose.runOnUiThread {world.value=w;enabled.value=false}
            compose.mainClock.advanceTimeByFrame()
            val static=compose.onRoot().captureToImage().toPixelMap()
            compose.runOnUiThread {enabled.value=true}
            compose.mainClock.advanceTimeBy(3000)
            val animated=compose.onRoot().captureToImage().toPixelMap()
            assertTrue((1..8).any {x -> (1..8).any {y ->
                static[static.width*x/9,static.height*y/9]!=animated[animated.width*x/9,animated.height*y/9]
            }})
            compose.runOnUiThread {enabled.value=false}
            compose.mainClock.advanceTimeByFrame()
            val stopped=compose.onRoot().captureToImage().toPixelMap()
            compose.mainClock.advanceTimeBy(3000)
            val later=compose.onRoot().captureToImage().toPixelMap()
            for(x in 1..8) for(y in 1..8)
                assertEquals(stopped[stopped.width*x/9,stopped.height*y/9],later[later.width*x/9,later.height*y/9])
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
