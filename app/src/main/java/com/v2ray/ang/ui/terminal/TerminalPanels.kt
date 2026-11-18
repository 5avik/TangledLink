package com.v2ray.ang.ui.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalThemeConfig
import com.v2ray.ang.ui.main.ServerRowUiModel
import com.v2ray.ang.ui.main.TrafficUiState
import com.v2ray.ang.ui.main.countryFlags
import com.v2ray.ang.ui.main.formatTrafficSpeed

@Composable
private fun TuiPanel(
    title: String,
    modifier: Modifier = Modifier,
    open: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scene = LocalThemeConfig.current.scene
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontFamily = TerminalFont, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, lineHeight = 16.sp, color = scene.portalSecondary)
            content()
        }
    }
    if (open == null) {
        Surface(modifier.fillMaxWidth(), color = scene.portalFill, contentColor = scene.onScene,
            shape = RectangleShape, border = BorderStroke(1.dp, scene.portalPrimary.copy(alpha = .7f)),
            content = body)
    } else {
        Surface(onClick = open, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
            color = scene.portalFill, contentColor = scene.onScene, shape = RectangleShape,
            border = BorderStroke(1.dp, scene.portalPrimary.copy(alpha = .7f)), content = body)
    }
}

@Composable
private fun TuiField(label: String, value: String, accent: Boolean = false) {
    val scene = LocalThemeConfig.current.scene
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 240.dp || LocalDensity.current.fontScale >= 1.5f
        val key: @Composable () -> Unit = {
            Text(label, fontFamily = TerminalFont, fontSize = 12.sp, lineHeight = 16.sp,
                color = scene.onScene.copy(alpha = .72f))
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                key()
                Text(value, fontFamily = TerminalFont, fontSize = 13.sp, lineHeight = 18.sp,
                    color = if (accent) scene.portalPrimary else scene.onScene)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { key() }
                Text(value, Modifier.weight(1.3f), fontFamily = TerminalFont, fontSize = 13.sp, lineHeight = 18.sp,
                    textAlign = TextAlign.End, color = if (accent) scene.portalPrimary else scene.onScene)
            }
        }
    }
}

@Composable
internal fun TerminalServerCard(server: ServerRowUiModel?, open: () -> Unit) {
    val scene = LocalThemeConfig.current.scene
    val label = server?.label
    val country = countryFlags[label?.countryFlag]
    val title = when {
        server == null -> stringResource(R.string.premium_no_server)
        label?.automatic == true -> stringResource(R.string.premium_auto_select)
        country != null -> stringResource(country)
        !label?.detail.isNullOrBlank() -> label.detail
        else -> stringResource(R.string.premium_server)
    }
    val detail = when {
        label?.automatic == true -> stringResource(R.string.premium_auto_select_note)
        country != null && !label?.detail.isNullOrBlank() -> label.detail
        else -> null
    }
    val choose = stringResource(R.string.premium_choose)
    TuiPanel(
        title = stringResource(R.string.terminal_server_title),
        modifier = Modifier.testTag("terminal_selected_server").semantics(mergeDescendants = true) {
            role = Role.Button
            selected = server != null
            onClick(label = choose, action = null)
        },
        open = open,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(">", fontFamily = TerminalFont, fontSize = 15.sp, lineHeight = 20.sp, color = scene.portalPrimary,
                modifier = Modifier.clearAndSetSemantics {})
            Text(title, fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp,
                color = scene.onScene)
        }
        detail?.let { TuiField(stringResource(R.string.terminal_tui_node), it) }
        if (server != null) {
            TuiField(stringResource(R.string.terminal_latency),
                if (server.testDelayMillis > 0) stringResource(R.string.premium_ping, server.testDelayMillis)
                else stringResource(R.string.premium_unknown_ping))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.terminal_tui_enter), fontFamily = TerminalFont, fontSize = 12.sp, lineHeight = 16.sp, color = scene.portalSecondary,
                modifier = Modifier.clearAndSetSemantics {})
            Text(choose, fontFamily = TerminalFont, fontSize = 12.sp, lineHeight = 16.sp, color = scene.portalPrimary)
        }
    }
}

@Composable
internal fun TerminalTraffic(traffic: TrafficUiState) {
    val latest = traffic.latest?.takeIf { it.isValid }
    val samples = if (latest == null) emptyList() else traffic.samples.filter { it.isFinite() && it >= 0f }.takeLast(60)
    val missing = stringResource(R.string.terminal_tui_missing)
    val scene = LocalThemeConfig.current.scene
    TuiPanel(stringResource(R.string.terminal_stats_title), Modifier.testTag("terminal_traffic")) {
        TuiField(stringResource(R.string.terminal_tui_rx),
            latest?.let { formatTrafficSpeed(it.download) } ?: missing, accent = true)
        TuiField(stringResource(R.string.terminal_tui_tx),
            latest?.let { formatTrafficSpeed(it.upload) } ?: missing, accent = true)
        val graphStyle = TextStyle(fontFamily = TerminalFont, fontSize = 12.sp, lineHeight = 14.sp)
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val cellWidth = remember(measurer, density) {
            measurer.measure(AnnotatedString("█"), style = graphStyle).size.width.coerceAtLeast(1)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = (with(density) { maxWidth.toPx() } / cellWidth).toInt().minus(2).coerceIn(1, 48)
            val grid = remember(samples, columns) { terminalTrafficGrid(samples, columns) }
            val lines = buildList {
                add("┌" + "─".repeat(columns) + "┐")
                addAll(grid.map { "│" + it + "│" })
                add("└" + "─".repeat(columns) + "┘")
            }.joinToString("\n")
            Text(lines, style = graphStyle, softWrap = false, color = scene.portalPrimary,
                modifier = Modifier.clearAndSetSemantics {})
            if (samples.isEmpty()) Text(stringResource(R.string.terminal_no_data),
                Modifier.align(androidx.compose.ui.Alignment.Center).padding(horizontal = 12.dp),
                fontFamily = TerminalFont, fontSize = 12.sp, lineHeight = 16.sp, color = scene.onScene)
        }
        TuiField(stringResource(R.string.terminal_tui_peak),
            samples.maxOrNull()?.let { formatTrafficSpeed(it.toDouble() * 1024.0) } ?: missing)
        val seconds = latest?.connectedMillis?.div(1000)
        TuiField(stringResource(R.string.terminal_uptime), seconds?.let {
            stringResource(R.string.terminal_tui_elapsed, it / 3600, it / 60 % 60, it % 60)
        } ?: missing)
    }
}
