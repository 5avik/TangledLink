package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Legacy accent consumers now resolve the active Material palette.
val colorPing: Color @Composable get() = MaterialTheme.colorScheme.primary
val colorPingRed: Color @Composable get() = MaterialTheme.colorScheme.error
val colorConfigType: Color @Composable get() = MaterialTheme.colorScheme.secondary
val colorFabActive: Color @Composable get() = MaterialTheme.colorScheme.primary
val colorFabInactiveLight: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val colorFabInactiveDark: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val dividerColorLight: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
val dividerColorDark: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant

// Toast Colors
val toastNormalBgLight = Color(0xD9353A3E)
val toastNormalBgDark = Color(0xD91A1A1E)
val toastSuccessBg = Color(0xD900A846)
val toastErrorBg = Color(0xD9D50000)
val toastInfoBg = Color(0xD90088CC)
val toastIconCircleBg = Color(0x33FFFFFF)
val toastTextColor = Color.White

object ThemeManager : AppearanceStore {
    private const val PALETTE_KEY = "pref_tngl_terminal_palette"
    private const val WORLD_KEY = "pref_tngl_theme_id"
    private const val REDUCE_EFFECTS_KEY = "pref_tngl_reduce_effects"

    private fun readAppearance() = AppearanceSelection(
        terminalPalette = com.v2ray.ang.ui.terminal.TerminalPaletteId.restore(runCatching { MmkvManager.decodeSettingsString(PALETTE_KEY) }.getOrNull()),
        themeId = ThemeId.fromStorage(runCatching { MmkvManager.decodeSettingsString(WORLD_KEY) }.getOrNull()),
        reduceEffects = runCatching { MmkvManager.decodeSettingsBool(REDUCE_EFFECTS_KEY, false) }.getOrDefault(false),
    )
    private val _appearance = MutableStateFlow(readAppearance())
    override val appearance: StateFlow<AppearanceSelection> = _appearance.asStateFlow()

    /** Publish only after MMKV confirms both writes. ID is written exactly once on success. */
    @Synchronized
    override fun saveAppearance(selection: AppearanceSelection): Boolean {
        val previous = _appearance.value
        return try {
            if (!MmkvManager.encodeSettings(PALETTE_KEY, selection.terminalPalette.stableId)) return false
            if (!MmkvManager.encodeSettings(REDUCE_EFFECTS_KEY, selection.reduceEffects)) {
                MmkvManager.encodeSettings(PALETTE_KEY, previous.terminalPalette.stableId)
                return false
            }
            if (!MmkvManager.encodeSettings(WORLD_KEY, selection.themeId.storageId)) {
                MmkvManager.encodeSettings(PALETTE_KEY, previous.terminalPalette.stableId)
                MmkvManager.encodeSettings(REDUCE_EFFECTS_KEY, previous.reduceEffects)
                return false
            }
            _appearance.value = selection
            true
        } catch (_: Exception) {
            runCatching { MmkvManager.encodeSettings(PALETTE_KEY, previous.terminalPalette.stableId) }
            runCatching { MmkvManager.encodeSettings(REDUCE_EFFECTS_KEY, previous.reduceEffects) }
            false
        }
    }

    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _appearance.value = readAppearance()
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsStateWithLifecycle()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appearance by ThemeManager.appearance.collectAsStateWithLifecycle()
    val config = if (appearance.themeId == ThemeId.Terminal) com.v2ray.ang.ui.terminal.TerminalThemes.config(appearance.terminalPalette) else ThemeCatalog[appearance.themeId]
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> config.dark
        else -> config.light
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalThemeConfig provides config,
        LocalReduceEffects provides appearance.reduceEffects,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BrandTypography,
            shapes = BrandShapes
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
