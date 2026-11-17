package com.v2ray.ang.ui.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

internal const val DisconnectedPortalIconAlpha = .75f
internal val PortalCenterColor = Color(0xFF08101A).copy(alpha=.94f)

/** Stateless control: action and state are supplied by the actual application's owner. */
@Composable
fun ConnectionPortal(
    world: MotionWorld,
    state: PortalVisualState,
    actionLabel: String,
    statusLabel: String,
    enabled: Boolean,
    isAnimationEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val colors=world.colors()
    val moving=isAnimationEnabled && (state==PortalVisualState.CONNECTING || state==PortalVisualState.CONNECTED)
    val progress=rememberLoopPhase(moving,fpsLimit=60)
    val intensity by animateFloatAsState(
        targetValue=when(state) {
            PortalVisualState.CONNECTED -> 1f
            PortalVisualState.CONNECTING -> .55f
            PortalVisualState.DISCONNECTED -> .25f
            PortalVisualState.ERROR -> .50f
        },
        animationSpec=if(isAnimationEnabled) tween(300) else snap(),
        label="portal-intensity",
    )
    Box(modifier
        .size(224.dp)
        .clip(CircleShape)
        .then(if(interactive) Modifier.clickable(enabled=enabled,role=Role.Button,onClick=onClick)
            .semantics(mergeDescendants=true) {
                contentDescription=actionLabel
                stateDescription=statusLabel
            } else Modifier.clearAndSetSemantics {})) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val c=center
            val r=min(size.width,size.height)*.37f
            val q=(2*PI*progress.value).toFloat()
            val accent=if(state==PortalVisualState.ERROR) Color(0xFFFFB4AB) else colors.primary
            glow(c,r*1.34f,accent,.26f*intensity)
            drawCircle(PortalCenterColor,radius=r*.92f,center=c)
            drawCircle(accent.copy(alpha=.18f+intensity*.22f),radius=r,center=c,style=Stroke(width=r*.032f))
            if(state==PortalVisualState.CONNECTING) {
                val rotation=if(moving) progress.value*2160f else 0f
                drawArc(Brush.sweepGradient(listOf(accent.copy(alpha=.10f),accent,colors.secondary),center=c),
                    startAngle=rotation-90f,sweepAngle=261f,useCenter=false,
                    topLeft=c-Offset(r,r),size=Size(r*2,r*2),style=Stroke(width=r*.038f,cap=StrokeCap.Round))
                drawArc(colors.secondary.copy(alpha=.5f),startAngle=-rotation,sweepAngle=90f,useCenter=false,
                    topLeft=c-Offset(r*.91f,r*.91f),size=Size(r*1.82f,r*1.82f),style=Stroke(width=r*.016f,cap=StrokeCap.Round))
            } else if(state==PortalVisualState.CONNECTED) {
                val pulse=if(moving) .8f+.15f*sin(q*3) else .9f
                drawCircle(accent.copy(alpha=pulse),radius=r,center=c,style=Stroke(width=r*.038f))
                drawCircle(colors.secondary.copy(alpha=.60f),radius=r*.89f,center=c,style=Stroke(width=r*.012f))
            }
            val icon=accent.copy(alpha=if(state==PortalVisualState.DISCONNECTED) DisconnectedPortalIconAlpha else .90f)
            when(state) {
                PortalVisualState.CONNECTED -> {
                    drawLine(icon,c+Offset(-r*.22f,0f),c+Offset(-r*.055f,r*.15f),strokeWidth=r*.046f,cap=StrokeCap.Round)
                    drawLine(icon,c+Offset(-r*.055f,r*.15f),c+Offset(r*.25f,-r*.17f),strokeWidth=r*.046f,cap=StrokeCap.Round)
                }
                PortalVisualState.ERROR -> {
                    drawLine(icon,c+Offset(0f,-r*.24f),c+Offset(0f,r*.07f),strokeWidth=r*.046f,cap=StrokeCap.Round)
                    drawCircle(icon,radius=r*.027f,center=c+Offset(0f,r*.23f))
                }
                else -> {
                    drawArc(icon,startAngle=-48.6f,sweepAngle=277.2f,useCenter=false,
                        topLeft=c+Offset(-r*.24f,-r*.225f),size=Size(r*.48f,r*.48f),
                        style=Stroke(width=r*.046f,cap=StrokeCap.Round))
                    drawLine(icon,c+Offset(0f,-r*.30f),c+Offset(0f,-r*.02f),strokeWidth=r*.046f,cap=StrokeCap.Round)
                }
            }
        }
    }
}
