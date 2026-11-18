package com.v2ray.ang.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

/** Decorative only. Frame state is read during drawing, never by the form composition. */
@Composable
fun TangledAnimatedBackground(
    modifier: Modifier = Modifier,
    animationEnabled: Boolean = true,
    intensity: Float = 1f,
    background: Color = Color(0xFF020B09),
    green: Color = Color(0xFF65FF85),
    teal: Color = Color(0xFF00DDB5),
) {
    val allowed = sceneMotionAllowed() && animationEnabled
    val phase = remember { mutableFloatStateOf(0f) }
    val particles = remember {
        val random = Random(42)
        FloatArray(28 * 4) { i -> when (i % 4) {
            0, 1 -> random.nextFloat()
            2 -> random.nextFloat() * 6.283185f
            else -> 0.4f + random.nextFloat() * 0.6f
        } }
    }
    LaunchedEffect(allowed) {
        if (!allowed) { phase.floatValue = 0f; return@LaunchedEffect }
        var start = 0L
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                if (now - last >= 33_333_333L) {
                    phase.floatValue = ((now - start) % 24_000_000_000L) / 24_000_000_000f * (2f * PI.toFloat())
                    last = now
                }
            }
        }
    }
    Canvas(modifier.drawWithCache {
        val strength = intensity.coerceIn(0f, 1f)
        val colors = Array(49) { i -> lerp(teal, green, i / 48f) }
        val path = Path()
        val glowTop = Brush.radialGradient(listOf(teal.copy(alpha = .075f * strength), Color.Transparent),
            center = Offset(size.width * .25f, 0f), radius = size.maxDimension * .45f)
        val glowBottom = Brush.radialGradient(listOf(green.copy(alpha = .06f * strength), Color.Transparent),
            center = Offset(size.width * .8f, size.height), radius = size.maxDimension * .4f)
        onDrawBehind {
            drawRect(background)
            drawRect(glowTop)
            drawRect(glowBottom)
            val t = phase.floatValue
            for (side in 0..1) {
                for (row in 0..6) {
                    path.reset()
                    for (col in 0..48) {
                        val x = col / 48f
                        val ripple = sin(x * 6.283185f + t + row * .24f) * .035f +
                            sin(x * 12.56637f - t) * .012f
                        val band = .055f + row * .025f + ripple
                        val y = size.height * if (side == 0) band else 1f - band * .85f
                        val point = Offset(x * size.width, y)
                        drawCircle(colors[col], radius = (if(row == 6) 1f else .75f) * density,
                            center = point, alpha = (.12f + row * .035f) * strength)
                        if(col == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    if(row == 6) drawPath(path, teal, alpha = .25f * strength,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(.6f * density))
                }
            }
            for(i in 0 until 28) {
                val base = i * 4
                val p = particles[base + 2]
                val x = (particles[base] + sin(t + p) * .012f) * size.width
                val y = (particles[base + 1] + cos(t + p) * .009f) * size.height
                val a = (.08f + .09f * (sin(t + p) + 1f) * .5f) * strength
                val radius = particles[base + 3] * density
                drawCircle(teal, radius * 3f, Offset(x,y), alpha = a * .12f)
                drawCircle(green, radius, Offset(x,y), alpha = a)
            }
        }
    }) {}
}
