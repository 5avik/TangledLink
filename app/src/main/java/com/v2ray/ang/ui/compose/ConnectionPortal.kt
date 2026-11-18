package com.v2ray.ang.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import com.v2ray.ang.R
import com.v2ray.ang.ui.main.ConnectionStatus
import com.v2ray.ang.ui.motion.PortalVisualState
import com.v2ray.ang.ui.motion.WorldMotionAssets

/** Adapter to the existing screens. Animation has no access to a VPN action or state writer. */
@Composable
internal fun ConnectionPortal(
    status: ConnectionStatus,
    enabled: Boolean = true,
    diameter: Dp = 224.dp,
    interactive: Boolean = true,
    onClick: () -> Unit,
) {
    val world=WorldMotionAssets[LocalThemeConfig.current.id]?.world ?: return
    com.v2ray.ang.ui.motion.ConnectionPortal(
        world=world,
        state=when(status) {
            ConnectionStatus.Disconnected -> PortalVisualState.DISCONNECTED
            ConnectionStatus.Connecting -> PortalVisualState.CONNECTING
            ConnectionStatus.Connected -> PortalVisualState.CONNECTED
        },
        actionLabel=stringResource(if(status==ConnectionStatus.Connected) R.string.premium_disconnect else R.string.premium_connect),
        statusLabel=stringResource(status.serviceStatusLabel()),
        enabled=enabled && status!=ConnectionStatus.Connecting,
        isAnimationEnabled=interactive && sceneMotionAllowed(),
        onClick=onClick,
        modifier=Modifier.size(diameter.coerceAtLeast(48.dp)),
        interactive=interactive,
    )
}

internal fun ConnectionStatus.serviceStatusLabel(): Int = when(this) {
    ConnectionStatus.Disconnected -> R.string.motion_service_stopped
    ConnectionStatus.Connecting -> R.string.motion_service_starting
    ConnectionStatus.Connected -> R.string.motion_service_running
}
