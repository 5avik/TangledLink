package com.v2ray.ang.ui.compose

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import kotlin.math.sin

/** Observes system animation scale as well as lifecycle, including changes while this page is open. */
@Composable
fun sceneMotionAllowed(): Boolean {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val reduceEffects = LocalReduceEffects.current
    var systemEnabled by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        fun refresh() {
            systemEnabled = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        refresh()
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return !reduceEffects && systemEnabled && lifecycle == Lifecycle.State.RESUMED
}

@Composable
fun ThemedBackground(
    modifier: Modifier = Modifier,
    config: ThemeConfig = LocalThemeConfig.current,
    preview: Boolean = false,
    effects: Boolean = true,
) {
    val context = LocalContext.current
    val resource = if (preview) config.previewRes else config.backgroundRes
    val request = remember(context, resource, preview) {
        ImageRequest.Builder(context).data(resource)
            // The home screen retains only its current decoded scene; thumbnails can use Coil's cache.
            .memoryCachePolicy(if (preview) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .build()
    }
    Box(modifier.background(config.scene.background).clearAndSetSemantics {}) {
        AsyncImage(request, contentDescription = null, modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop)
        if (effects && config.effect != SceneEffect.None) SceneDecoration(config)
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.verticalGradient(listOf(
                config.scene.background.copy(alpha = .36f),
                Color.Transparent,
                config.scene.background.copy(alpha = .68f),
            )))
        }
    }
}

/** Only this small drawing node reads animation time; bitmap decoding and screen state do not. */
@Composable
private fun SceneDecoration(config: ThemeConfig) {
    if (!sceneMotionAllowed()) return
    val period = if (config.effect == SceneEffect.Rain) 2400 else 14000
    val progress = rememberInfiniteTransition(label = "scene")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing)), label = "scene phase")
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
        val phase = progress.value
        val scene = config.scene
        when (config.effect) {
            SceneEffect.Petals -> repeat(8) { i ->
                val travel = (phase + i / 8f) % 1f
                val x = size.width * ((i * .137f + .1f) % 1f) + sin(travel * 6.28f + i) * size.width * .035f
                val y = size.height * travel
                val r = 3f + i % 3
                val petal = Path().apply {
                    moveTo(x, y - r * 2)
                    quadraticTo(x + r * 2, y, x, y + r * 2)
                    quadraticTo(x - r, y, x, y - r * 2)
                }
                drawPath(petal, scene.portalSecondary.copy(alpha = .22f))
            }
            SceneEffect.Aurora -> {
                val x = size.width * (.35f + sin(phase * 6.28f) * .12f)
                val glowCenter = Offset(x, size.height * .18f)
                val glowRadius = size.width * .8f
                drawCircle(Brush.radialGradient(
                    listOf(scene.portalPrimary.copy(alpha = .12f), Color.Transparent),
                    center = glowCenter, radius = glowRadius,
                ), radius = glowRadius, center = glowCenter)
            }
            SceneEffect.Rain -> repeat(16) { i ->
                val edge = if (i % 2 == 0) .02f + (i % 5) * .025f else .89f + (i % 5) * .02f
                val y = size.height * ((phase + i * .061f) % 1f)
                drawLine(if (i % 2 == 0) scene.portalPrimary.copy(alpha = .12f) else scene.portalSecondary.copy(alpha = .12f),
                    Offset(size.width * edge, y), Offset(size.width * edge - 4f, y + 20f), strokeWidth = 1.5f)
            }
            SceneEffect.None -> Unit
        }
    }
}
