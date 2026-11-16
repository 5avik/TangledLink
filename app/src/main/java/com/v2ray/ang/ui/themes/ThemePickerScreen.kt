package com.v2ray.ang.ui.themes

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.*
import com.v2ray.ang.ui.main.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemePickerScreen(state: ThemePickerState, action: (ThemePickerAction) -> Unit) {
    val config = if(state.draft.themeId == ThemeId.Terminal) com.v2ray.ang.ui.terminal.TerminalThemes.config(state.draft.terminalPalette) else ThemeCatalog[state.draft.themeId]
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.themes_title), maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { action(ThemePickerAction.Cancel) }, enabled = !state.saving) {
                        Icon(painterResource(R.drawable.ic_arrow_back_24dp), stringResource(R.string.theme_cancel))
                    }
                })
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.saveFailed) Text(stringResource(R.string.theme_save_error),
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { action(ThemePickerAction.Cancel) }, enabled = !state.saving,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.theme_cancel))
                        }
                        Button(onClick = { action(ThemePickerAction.Apply) }, enabled = !state.saving,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("theme_apply")) {
                            if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(stringResource(R.string.theme_apply))
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().selectableGroup().testTag("theme_list"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "preview") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.theme_preview), style = MaterialTheme.typography.titleMedium)
                    ThemeLocalPreview(config, state.draft.reduceEffects)
                }
            }
            items(ThemeCatalog.ordered, key = { it.id.storageId }) { theme ->
                ThemeChoice(theme, state.draft.themeId == theme.id, !state.saving) {
                    action(ThemePickerAction.Preview(theme.id))
                }
            }
            if(state.draft.themeId == ThemeId.Terminal) items(com.v2ray.ang.ui.terminal.TerminalPaletteId.entries, key={it.stableId}) { palette ->
                val paletteConfig = com.v2ray.ang.ui.terminal.TerminalThemes.config(palette)
                ThemeChoice(paletteConfig.copy(nameRes = when(palette) { com.v2ray.ang.ui.terminal.TerminalPaletteId.MATRIX_GREEN->R.string.terminal_palette_matrix_green;com.v2ray.ang.ui.terminal.TerminalPaletteId.CATPPUCCIN_MOCHA->R.string.terminal_palette_catppuccin_mocha;com.v2ray.ang.ui.terminal.TerminalPaletteId.TOKYO_NIGHT->R.string.terminal_palette_tokyo_night;else->R.string.terminal_palette_gruvbox_dark }), state.draft.terminalPalette==palette, !state.saving, tag = "palette_"+palette.stableId) {
                    action(ThemePickerAction.Palette(palette))
                }
            }
            item(key = "effects") {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().toggleable(
                        value = state.draft.reduceEffects, enabled = !state.saving, role = Role.Switch,
                        onValueChange = { action(ThemePickerAction.ReduceEffects(it)) },
                    ).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.theme_reduce_effects), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.theme_reduce_effects_note),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(state.draft.reduceEffects, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeChoice(config: ThemeConfig, selected: Boolean, enabled: Boolean, tag: String = "theme_"+config.id.storageId, choose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceContainer,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().testTag(tag)
                .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = choose)
                .semantics(mergeDescendants = true) {}.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AsyncImage(config.previewRes, null, Modifier.width(64.dp).height(112.dp).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(config.nameRes), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(config.descriptionRes), style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
            }
            RadioButton(selected, onClick = null, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun ThemeLocalPreview(config: ThemeConfig, reduced: Boolean) {
    val dark = LocalDarkTheme.current
    val dynamic by ThemeManager.dynamicColorEnabled.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = when {
        dynamic && android.os.Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> config.dark
        else -> config.light
    }
    CompositionLocalProvider(LocalThemeConfig provides config, LocalReduceEffects provides reduced) {
        MaterialTheme(colorScheme = colors) {
            Box(Modifier.fillMaxWidth().heightIn(min = 310.dp).clip(RoundedCornerShape(26.dp))) {
                ThemedBackground(Modifier.matchParentSize(), config, preview = config.id != ThemeId.Terminal)
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsyncImage(R.drawable.tngl_brand_mark, null, Modifier.width(30.dp).aspectRatio(256f / 240f),
                        contentScale = ContentScale.Fit)
                    if(config.id == ThemeId.Terminal) com.v2ray.ang.ui.terminal.AsciiPenguin(false,true,neutral=true)
                    else ConnectionPortal(ConnectionStatus.Disconnected, diameter = 136.dp, interactive = false, onClick = {})
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.theme_preview_card), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.theme_preview_note), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
