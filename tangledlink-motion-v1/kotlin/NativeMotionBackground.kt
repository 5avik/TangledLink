package com.v2ray.ang.ui.motion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
            withFrameNanos { now ->
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

/**
 * Pass a Painter loaded with the project's existing async image loader.
 * Host owns lifecycle, visibility, system scale, reduced motion and power-save policy.
 * Do not combine this layer with an animated MP4 of the same scene.
 */
@Composable
fun NativeMotionBackground(
    painter: Painter,
    world: MotionWorld,
    isAnimationEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress = rememberLoopPhase(isAnimationEnabled, fpsLimit = 24)
    val colors = world.colors()
    val petal = remember {
        Path().apply {
            moveTo(-.75f, 0f)
            cubicTo(-.45f, -.8f, .6f, -.7f, .85f, 0f)
            cubicTo(.45f, .65f, -.4f, .8f, -.75f, 0f)
            close()
        }
    }
    val ribbons = remember { List(6) { Path() } }
    Box(modifier.graphicsLayer { clip = true }) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val q = (2 * PI * progress.value).toFloat()
                val zoom = if (isAnimationEnabled) 1.014f + .004f * sin(q) else 1f
                scaleX = zoom
                scaleY = zoom
                translationX = if (isAnimationEnabled) 1.4f * sin(q) * size.width / 720f else 0f
                translationY = if (isAnimationEnabled) 1.8f * sin(q) * size.height / 1600f else 0f
            },
        )
        Canvas(Modifier.fillMaxSize()) {
            if (!isAnimationEnabled) return@Canvas
            val p = progress.value
            val q = (2 * PI * p).toFloat()
            val w = size.width
            val h = size.height
            when (world) {
                MotionWorld.ORIGINAL -> {
                    glow(Offset(.88f*w,.10f*h), .40f*w, colors.primary, .17f+.07f*sin(q))
                    glow(Offset(.14f*w,.61f*h), .42f*w, colors.secondary, .13f+.06f*cos(q))
                    repeat(18) { i ->
                        val u = MotionMath.phase(p + MotionMath.seed(i+2))
                        val x = (if (i%2==0) .05f+.18f*MotionMath.seed(i+101) else .78f+.18f*MotionMath.seed(i+101))*w + .015f*w*sin(q+i)
                        val a = .32f*sin(PI.toFloat()*u)*sin(PI.toFloat()*u)
                        glow(Offset(x,(.04f+.88f*u)*h),w*.008f,colors.primary,a)
                    }
                }
                MotionWorld.SAKURA -> {
                    glow(Offset(.86f*w,.08f*h),.24f*w,colors.primary,.08f+.035f*sin(q))
                    repeat(12) { i ->
                        val s = MotionMath.petal(i,p)
                        withTransform({
                            translate(s.x*w,s.y*h)
                            rotate(s.angle*180f/PI.toFloat(),Offset.Zero)
                            scale(s.radius*w,s.radius*w,Offset.Zero)
                        }) {
                            drawPath(petal,Color(0xFFF2A5DA).copy(alpha=s.alpha))
                        }
                    }
                }
                MotionWorld.AURORA -> {
                    glow(Offset(.05f*w,.19f*h),.75f*w,colors.primary,.11f+.06f*sin(q))
                    glow(Offset(.94f*w,.13f*h),.70f*w,colors.secondary,.10f+.05f*cos(q))
                    repeat(6) { n ->
                        val side=n/3
                        val band=n%3
                        val x=(if(side==0) .015f+.065f*band else .985f-.065f*band)*w
                        val bend=.018f*w*sin(q+band*.9f)
                        val path=ribbons[n]
                        path.reset()
                        path.moveTo(x-.065f*w,-.08f*h)
                        path.cubicTo(x+.08f*w+bend,.08f*h,x-.05f*w+bend,.22f*h,x+.03f*w,.35f*h)
                        path.lineTo(x+.08f*w,.36f*h)
                        path.cubicTo(x+.01f*w+bend,.22f*h,x+.14f*w+bend,.08f*h,x+.065f*w,-.08f*h)
                        path.close()
                        drawPath(path,Brush.horizontalGradient(
                            listOf(Color.Transparent,colors.primary.copy(alpha=.11f+.035f*sin(q+band)),Color.Transparent),
                            startX=x-.065f*w,endX=x+.065f*w))
                    }
                }
                MotionWorld.CYBERPUNK -> {
                    glow(Offset(.11f*w,.80f*h),.32f*w,colors.primary,.13f+.065f*sin(q))
                    glow(Offset(.91f*w,.72f*h),.28f*w,colors.secondary,.12f+.055f*cos(q))
                    repeat(56) { i ->
                        val s=MotionMath.rain(i,p)
                        drawLine((if(i%2==0) Color(0xFFF49ED5) else colors.secondary).copy(alpha=s.alpha),
                            start=Offset(s.x*w,s.y*h),end=Offset((s.x-.003f)*w,(s.y+s.radius)*h),
                            strokeWidth=w*(.0008f+.0006f*MotionMath.seed(i+444)),cap=StrokeCap.Round)
                    }
                }
            }
        }
    }
}

internal fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) {
    if (radius <= 0f) return
    drawCircle(Brush.radialGradient(
        colors=listOf(color.copy(alpha=alpha.coerceIn(0f,1f)),color.copy(alpha=(alpha*.48f).coerceIn(0f,1f)),Color.Transparent),
        center=center,radius=radius),radius=radius,center=center)
}
