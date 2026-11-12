package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import com.v2ray.ang.ui.compose.BrandButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

@Composable
fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    onAction: (MainAction) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val panelColor by animateColorAsState(
        if (isRunning) colors.primaryContainer else colors.surfaceContainerLow,
        animationSpec = tween(450), label = "connectionPanel"
    )
    Surface(color = colors.surface) {
        Surface(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, colors.outlineVariant),
            color = panelColor
        ) {
            Column(
                modifier = Modifier
                    .background(Brush.linearGradient(listOf(panelColor, colors.surface)))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = colors.surface.copy(alpha = 0.65f)) {
                        Icon(
                            painterResource(if (isRunning) R.drawable.ic_privacy_24dp else R.drawable.ic_qu_stop_24dp),
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp).size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable(enabled = isRunning) { onAction(MainAction.TestCurrentServer) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(if (isRunning) R.string.home_vpn_on else R.string.home_vpn_off),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            displayText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandButton(
                        onClick = { onAction(MainAction.ToggleService) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                    ) {
                        Icon(
                            painterResource(if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(if (isRunning) R.string.home_disconnect else R.string.home_connect),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { onAction(MainAction.TestCurrentServer) },
                        enabled = isRunning,
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_check_update_24dp),
                            contentDescription = stringResource(R.string.connection_test_pending)
                        )
                    }
                }
            }
        }
    }
}
