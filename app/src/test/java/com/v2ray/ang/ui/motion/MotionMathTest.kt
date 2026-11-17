package com.v2ray.ang.ui.motion

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MotionMathTest {
    @Test fun invalidAndWrappedPhasesAreSafeAndParticlesLoopContinuously() {
        assertEquals(0f,MotionMath.phase(Float.NaN))
        assertEquals(0f,MotionMath.phase(Float.POSITIVE_INFINITY))
        assertEquals(.75f,MotionMath.phase(-.25f))
        for(i in 0 until 1000) assertTrue(MotionMath.seed(i) in 0f..<1f)
        for(i in 0 until 56) {
            assertEquals(MotionMath.petal(i,0f),MotionMath.petal(i,1f))
            assertEquals(MotionMath.rain(i,0f),MotionMath.rain(i,1f))
            for(frame in 0 until 288) {
                val p=frame/288f
                val rain=MotionMath.rain(i,p)
                val petal=MotionMath.petal(i,p)
                assertTrue(rain.x in 0f..1f && rain.y in -.051f..1.051f && rain.alpha in 0f..1f && rain.radius>0f)
                assertTrue(petal.x in -.036f..1.036f && petal.y in -.081f..1.081f && petal.alpha in 0f..1f &&
                    petal.radius>0f && petal.angle.isFinite())
            }
        }
    }

    @Test fun everyPolicyGateAndInvalidSystemScaleStopsMotion() {
        assertTrue(MotionMath.allowed(true,true,false,1f,false))
        assertTrue(MotionMath.allowed(true,true,false,.5f,false))
        assertFalse(MotionMath.allowed(false,true,false,1f,false))
        assertFalse(MotionMath.allowed(true,false,false,1f,false))
        assertFalse(MotionMath.allowed(true,true,true,1f,false))
        assertFalse(MotionMath.allowed(true,true,false,1f,true))
        for(scale in listOf(0f,-1f,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY))
            assertFalse(MotionMath.allowed(true,true,false,scale,false))
    }

    @Test fun emulatorsAvoidSoftwareVideoWhilePhysicalDevicesRetainTheVideoPathAndTerminalHasNoVideo() {
        assertEquals(WorldPlaybackBackend.NativeCanvas,worldPlaybackBackend("ranchu"))
        assertEquals(WorldPlaybackBackend.NativeCanvas,worldPlaybackBackend("goldfish"))
        for(hardware in listOf("qcom","tensor","exynos","unknown",""))
            assertEquals(WorldPlaybackBackend.Video,worldPlaybackBackend(hardware))
        assertNull(WorldMotionAssets[com.v2ray.ang.ui.compose.ThemeId.Terminal])
    }

    @Test fun disconnectedPowerSymbolHasNonTextContrastEvenOverABrightScene() {
        val center=PortalCenterColor.compositeOver(Color.White)
        for(world in MotionWorld.entries) {
            val icon=world.colors().primary.copy(alpha=DisconnectedPortalIconAlpha).compositeOver(center)
            val ratio=(icon.luminance()+.05f)/(center.luminance()+.05f)
            assertTrue(ratio>=3f,"$world power icon contrast $ratio")
        }
    }

    @Test fun cropPreservesAspectRatioAndCentersWithClippingInsteadOfStretching() {
        assertEquals(1f to 1f,centerCropScale(720,1600,720f,1600f))
        val portrait=centerCropScale(1080,2400,720f,1600f)
        assertEquals(1f to 1f,portrait)
        val wide=centerCropScale(1600,720,720f,1600f)
        assertEquals(1f,wide.first,.00001f)
        assertTrue(wide.second>1f)
        assertEquals(720f/1600f,wide.first*1600/(wide.second*720),.00001f)
        for(dimensions in listOf(0 to 800,800 to 0))
            assertEquals(1f to 1f,centerCropScale(dimensions.first,dimensions.second,720f,1600f))
        assertEquals(1f to 1f,centerCropScale(720,1600,Float.NaN,1600f))
    }
}
