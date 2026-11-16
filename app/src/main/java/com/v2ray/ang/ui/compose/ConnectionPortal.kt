package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.v2ray.ang.R
import com.v2ray.ang.ui.main.ConnectionStatus

/** Pure presentation of the real UI state. No timers or animation callbacks change VPN state. */
@Composable
internal fun ConnectionPortal(
    status: ConnectionStatus,
    enabled: Boolean = true,
    diameter: Dp = 224.dp,
    interactive: Boolean = true,
    onClick: () -> Unit,
) {
    val config = LocalThemeConfig.current
    val scene = config.scene
    val motion = sceneMotionAllowed()
    val spinning = motion && status == ConnectionStatus.Connecting
    val angle = if (spinning) {
        rememberInfiniteTransition(label = "portal").animateFloat(0f, 360f,
            infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "portal rotation")
    } else rememberUpdatedState(0f)
    val target = if (status == ConnectionStatus.Connected) 1f else .12f
    val animated by animateFloatAsState(target, tween(if (motion) 300 else 0), label = "portal intensity")
    val intensity = if (motion) animated else target
    val label = stringResource(if (status == ConnectionStatus.Connected) R.string.premium_disconnect else R.string.premium_connect)
    val description = stringResource(when (status) {
        ConnectionStatus.Connected -> R.string.premium_on
        ConnectionStatus.Connecting -> R.string.premium_connecting
        ConnectionStatus.Disconnected -> R.string.premium_off
    })
    val drawing: @Composable () -> Unit = {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val radius = size.minDimension * .39f
            drawCircle(Brush.radialGradient(
                listOf(scene.portalPrimary.copy(alpha = .15f * intensity), Color.Transparent),
                radius = size.minDimension * .5f,
            ), size.minDimension * .5f)
            drawCircle(Brush.radialGradient(listOf(scene.portalFill, scene.background)), radius)
            val ring = if (config.id == ThemeId.Cyberpunk) Brush.sweepGradient(
                listOf(scene.portalPrimary, scene.portalSecondary, scene.portalPrimary),
            ) else Brush.sweepGradient(listOf(scene.portalPrimary, scene.portalPrimary))
            drawCircle(ring, radius, alpha = .28f + intensity * .7f, style = Stroke(3.dp.toPx()))
            drawCircle(scene.portalPrimary.copy(alpha = .12f * intensity), radius + 4.dp.toPx(), style = Stroke(6.dp.toPx()))
            if (status == ConnectionStatus.Connecting) drawArc(
                scene.portalPrimary, angle.value, 100f, false,
                topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
            )
            val power = size.minDimension * .10f
            drawArc(scene.onScene, -50f, 280f, false,
                topLeft = Offset(center.x - power, center.y - power), size = Size(power * 2, power * 2),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            drawLine(scene.onScene, Offset(center.x, center.y - power * 1.3f),
                Offset(center.x, center.y - power * .1f), 4.dp.toPx(), StrokeCap.Round)
        }
    }
    if (interactive) Surface(
        onClick = onClick, enabled = enabled && status != ConnectionStatus.Connecting,
        modifier = Modifier.size(diameter.coerceAtLeast(48.dp)).semantics {
            contentDescription = label
            stateDescription = description
            role = Role.Button
        },
        shape = CircleShape, color = Color.Transparent, content = drawing,
    ) else Box(Modifier.size(diameter).clearAndSetSemantics {}) { drawing() }
}
