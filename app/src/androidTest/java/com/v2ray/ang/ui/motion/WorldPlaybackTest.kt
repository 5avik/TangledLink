package com.v2ray.ang.ui.motion

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.*
import org.junit.*
import org.junit.Assert.*

class WorldPlaybackTest {
    @get:Rule val compose=createComposeRule()

    @Test fun sessionIsUniqueAndReleasedForHideReduceLifecycleWorldChangeAndRecreation() {
        val owner=object:LifecycleOwner {
            val registry=LifecycleRegistry(this)
            override val lifecycle:Lifecycle get()=registry
        }
        val visible=mutableStateOf(true)
        val reduce=mutableStateOf(false)
        val world=mutableStateOf(ThemeId.Original)
        var active=0
        var created=0
        var lastRaw=0
        val factory=BackgroundSessionFactory { _, _, raw, event ->
            active++;created++;lastRaw=raw
            check(active==1)
            event(BackgroundPlaybackEvent.FirstFrame)
            BackgroundSession {active--}
        }
        val restore=StateRestorationTester(compose)
        restore.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner,LocalReduceEffects provides reduce.value,
                LocalMotionVisible provides visible.value) {
                AnimatedWorldBackground(WorldMotionAssets[world.value]!!,sceneMotionAllowed(),Modifier.fillMaxSize(),factory)
            }
        }
        compose.runOnIdle {owner.registry.currentState=Lifecycle.State.RESUMED}
        compose.waitUntil(5000){active==1}
        compose.runOnIdle {visible.value=false}
        compose.runOnIdle {assertEquals(0,active);visible.value=true}
        compose.waitUntil(5000){active==1}
        compose.runOnIdle {reduce.value=true}
        compose.runOnIdle {assertEquals(0,active);reduce.value=false}
        compose.waitUntil(5000){active==1}
        compose.runOnIdle {owner.registry.currentState=Lifecycle.State.STARTED}
        compose.runOnIdle {assertEquals(0,active);owner.registry.currentState=Lifecycle.State.RESUMED}
        compose.waitUntil(5000){active==1}
        for(id in listOf(ThemeId.Sakura,ThemeId.Aurora,ThemeId.Cyberpunk)) {
            compose.runOnIdle {world.value=id}
            compose.waitUntil(5000){active==1 && lastRaw==WorldMotionAssets[id]!!.video}
        }
        val beforeRestore=created
        restore.emulateSavedInstanceStateRestore()
        compose.waitUntil(5000){active==1 && created>beforeRestore}
        compose.runOnIdle {assertEquals(1,active);assertTrue(created>=7)}
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.runOnIdle {visible.value=false}
        compose.runOnIdle {assertEquals(0,active)}
    }

    @Test fun realVideosRenderLoopAndReleaseAndUiStaysAboveTexture() {
        val world=mutableStateOf(ThemeId.Original)
        val visible=mutableStateOf(true)
        val events=mutableListOf<BackgroundPlaybackEvent>()
        var clicks=0
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                AnimatedWorldBackground(WorldMotionAssets[world.value]!!,visible.value,Modifier.fillMaxSize(),
                    onPlaybackEvent={events.add(it)})
                androidx.compose.material3.Button({clicks++},Modifier.testTag("overlay")) {
                    androidx.compose.material3.Text("QA overlay")
                }
            }
        }
        for(id in listOf(ThemeId.Original,ThemeId.Sakura,ThemeId.Aurora,ThemeId.Cyberpunk)) {
            compose.runOnIdle {visible.value=false}
            compose.waitForIdle()
            compose.runOnIdle {world.value=id;events.clear();visible.value=true}
            compose.waitUntil(15000){BackgroundPlaybackEvent.FirstFrame in events || BackgroundPlaybackEvent.Failed in events}
            compose.runOnIdle {assertFalse(events.contains(BackgroundPlaybackEvent.Failed))}
            compose.onNodeWithTag("overlay").performClick()
            compose.waitUntil(16000){BackgroundPlaybackEvent.Loop in events}
            compose.runOnIdle {visible.value=false}
            compose.waitUntil(5000){BackgroundPlaybackEvent.Released in events}
        }
        compose.runOnIdle {assertEquals(4,clicks)}
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test fun decoderErrorRetainsStaticPosterAndDoesNotRetryOnRecomposition() {
        val asset=WorldMotionAssets[ThemeId.Sakura]!!.copy(video=0)
        val events=mutableListOf<BackgroundPlaybackEvent>()
        val recompose=mutableStateOf(false)
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag(if(recompose.value) "after" else "before")) {
                AnimatedWorldBackground(asset,true,Modifier.fillMaxSize(),onPlaybackEvent={events.add(it)})
            }
        }
        compose.waitUntil(15000){BackgroundPlaybackEvent.Failed in events}
        compose.waitUntil(5000){BackgroundPlaybackEvent.Released in events}
        compose.runOnIdle {recompose.value=true}
        compose.runOnIdle {
            assertEquals(1,events.count{it==BackgroundPlaybackEvent.Created})
            assertEquals(0,events.count{it==BackgroundPlaybackEvent.FirstFrame})
            assertEquals(1,events.count{it==BackgroundPlaybackEvent.Failed})
        }
        compose.onNodeWithTag("after").assertIsDisplayed()
        assertTrue(events.contains(BackgroundPlaybackEvent.PosterReady))
        val pixels=compose.onRoot().captureToImage().toPixelMap()
        assertTrue((1..8).any {i ->
            val color=pixels[pixels.width*i/9,pixels.height/8]
            color.red+color.green+color.blue>.03f
        })
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
