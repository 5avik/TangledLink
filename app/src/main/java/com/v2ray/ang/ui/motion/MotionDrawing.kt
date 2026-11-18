package com.v2ray.ang.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// Shared portal colors/clock adapted from tangledlink-motion-v1. Backgrounds use MP4 only.
internal data class MotionColors(val primary: Color, val secondary: Color)
internal fun MotionWorld.colors(): MotionColors = when (this) {
    MotionWorld.ORIGINAL -> MotionColors(Color(0xFF7CFC8A), Color(0xFF48F0A9))
    MotionWorld.SAKURA -> MotionColors(Color(0xFFAD75FF), Color(0xFFF28BC8))
    MotionWorld.AURORA -> MotionColors(Color(0xFF49F2C2), Color(0xFF56B8FF))
    MotionWorld.CYBERPUNK -> MotionColors(Color(0xFFFF3DB8), Color(0xFF45D9FF))
}

/** Disabled means no frame loop; resume starts from the last visible phase. */
@Composable
internal fun rememberLoopPhase(enabled: Boolean, fpsLimit: Int): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(enabled, fpsLimit) {
        if (!enabled) return@LaunchedEffect
        require(fpsLimit in 1..120)
        var last = 0L
        val interval = 1_000_000_000L / fpsLimit
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (last == 0L) last = now
                val delta = now - last
                if (delta >= interval) {
                    phase.floatValue = MotionMath.phase(phase.floatValue + delta / 12_000_000_000f)
                    last = now
                }
            }
        }
    }
    return phase
}

internal fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) {
    if (radius <= 0f) return
    drawCircle(Brush.radialGradient(
        colors=listOf(color.copy(alpha=alpha.coerceIn(0f,1f)),color.copy(alpha=(alpha*.48f).coerceIn(0f,1f)),Color.Transparent),
        center=center,radius=radius),radius=radius,center=center)
}
