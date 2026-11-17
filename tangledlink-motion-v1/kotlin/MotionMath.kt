package com.v2ray.ang.ui.motion

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

enum class MotionWorld(val stableId: String) {
    ORIGINAL("original"), SAKURA("sakura"), AURORA("aurora"), CYBERPUNK("cyberpunk")
}

/** Presentation only. A host must map actual app state; this enum owns no VPN state. */
enum class PortalVisualState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class MotionParticle(val x: Float, val y: Float, val radius: Float, val angle: Float, val alpha: Float)

object MotionMath {
    const val LOOP_SECONDS = 12f
    fun phase(value: Float): Float = if (value.isFinite()) value - floor(value) else 0f
    fun seed(index: Int): Float {
        val v = sin(index * 127.1 + 311.7) * 43758.5453
        return (v - floor(v)).toFloat().coerceAtMost(0.99999994f)
    }

    fun petal(index: Int, progress: Float): MotionParticle {
        val p = phase(progress)
        val q = (2 * PI * p).toFloat()
        val u = phase(p + seed(index + 8))
        return MotionParticle(
            x = seed(index + 51) + .035f * sin(q + index * .73f),
            y = -.08f + 1.16f * u,
            radius = .009f + .007f * seed(index + 100),
            angle = q * (1 + index % 2) + index,
            alpha = .52f * minOf(1f, u * 8f, (1f - u) * 8f),
        )
    }

    fun rain(index: Int, progress: Float): MotionParticle {
        val p = phase(progress)
        val side = index % 3
        val x = when (side) {
            0 -> .025f + .23f * seed(index + 91)
            1 -> .75f + .23f * seed(index + 91)
            else -> .25f + .5f * seed(index + 91)
        }
        val u = phase(4f * p + seed(index + 60))
        return MotionParticle(x, -.05f + 1.10f * u, .01f + .013f * seed(index + 300), 0f,
            (if (side < 2) .30f else .07f) * minOf(1f, u * 12f, (1f - u) * 12f))
    }

    fun allowed(isResumed: Boolean, isVisible: Boolean, reduceMotion: Boolean, systemScale: Float, powerSave: Boolean): Boolean =
        isResumed && isVisible && !reduceMotion && systemScale.isFinite() && systemScale > 0f && !powerSave
}
