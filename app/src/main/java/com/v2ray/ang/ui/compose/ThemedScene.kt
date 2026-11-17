package com.v2ray.ang.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import com.v2ray.ang.ui.motion.AnimatedWorldBackground
import com.v2ray.ang.ui.motion.WorldMotionAssets

@Composable
fun ThemedBackground(
    modifier: Modifier = Modifier,
    config: ThemeConfig = LocalThemeConfig.current,
    preview: Boolean = false,
    effects: Boolean = true,
    animated: Boolean = false,
) {
    val context = LocalContext.current
    val resource = if (preview) config.previewRes else config.backgroundRes
    val request = remember(context, resource, preview) {
        ImageRequest.Builder(context).data(resource)
            .memoryCachePolicy(if (preview) CachePolicy.ENABLED else CachePolicy.DISABLED).build()
    }
    val asset=WorldMotionAssets[config.id]
    val motion=if(animated && !preview && effects && asset!=null) sceneMotionAllowed() else false
    Box(modifier.background(config.scene.background).clearAndSetSemantics {}) {
        when {
            config.id==ThemeId.Terminal && !preview ->
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(config.backgroundRes),null,
                    Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            animated && !preview && asset!=null ->
                if(com.v2ray.ang.ui.motion.worldPlaybackBackend(android.os.Build.HARDWARE)==com.v2ray.ang.ui.motion.WorldPlaybackBackend.NativeCanvas)
                    com.v2ray.ang.ui.motion.NativeWorldBackground(asset,allowMotion=effects && motion,Modifier.fillMaxSize())
                else AnimatedWorldBackground(asset,allowMotion=effects && motion,Modifier.fillMaxSize())
            else -> AsyncImage(request,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        }
        // Readable system bars and content over a dark scene, independently of Material light/dynamic.
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.verticalGradient(listOf(
                config.scene.background.copy(alpha=.36f),Color.Transparent,config.scene.background.copy(alpha=.68f)
            )))
        }
    }
}
