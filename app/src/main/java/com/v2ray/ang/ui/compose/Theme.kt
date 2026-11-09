package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
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

private val LightColor = lightColorScheme(
    primary = Color(0xFF006C4C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF416775),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F7FA),
    onSecondaryContainer = Color(0xFF001F24),
    tertiary = Color(0xFF52648C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCFCE7),
    onTertiaryContainer = Color(0xFF00210B),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8FAF9),
    onBackground = Color(0xFF121413),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121413),
    surfaceVariant = Color(0xFFE2E8E4),
    onSurfaceVariant = Color(0xFF404A44),
    outline = Color(0xFF707A74),
    outlineVariant = Color(0xFFC0CAC4),
    inverseSurface = Color(0xFF2C312E),
    inverseOnSurface = Color(0xFFF0F4F2),
    inversePrimary = Color(0xFF00FF66),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF006C4C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F6F4),
    surfaceContainer = Color(0xFFECEFEA),
    surfaceContainerHigh = Color(0xFFE6EAE5),
    surfaceContainerHighest = Color(0xFFE0E4DF),
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFF79DAB0),
    onPrimary = Color(0xFF101012), // Dark Obsidian
    primaryContainer = Color(0xFF183B25), // Deep Dark Green
    onPrimaryContainer = Color(0xFF94FFB4), // Soft Mint Green
    secondary = Color(0xFFA0CEDD),
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF004D56),
    onSecondaryContainer = Color(0xFFB8F8FF),
    tertiary = Color(0xFFB4C5F0),
    onTertiary = Color(0xFF101012),
    tertiaryContainer = Color(0xFF183B25),
    onTertiaryContainer = Color(0xFF94FFB4),
    error = Color(0xFFFF5252),
    errorContainer = Color(0xFF5C000B),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121212), // Dark Obsidian
    onBackground = Color(0xFFE2E8E4),
    surface = Color(0xFF1A1A1E), // Dark Card Surface
    onSurface = Color(0xFFE2E8E4),
    surfaceVariant = Color(0xFF24242A),
    onSurfaceVariant = Color(0xFFA0B4A8),
    outline = Color(0xFF2E3832),
    outlineVariant = Color(0xFF1E2822),
    inverseSurface = Color(0xFFE2E8E4),
    inverseOnSurface = Color(0xFF121212),
    inversePrimary = Color(0xFF00CC52),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF79DAB0),
    surfaceContainerLowest = Color(0xFF0D0D0F),
    surfaceContainerLow = Color(0xFF16161A),
    surfaceContainer = Color(0xFF1A1A1E),
    surfaceContainerHigh = Color(0xFF222228),
    surfaceContainerHighest = Color(0xFF2B2B32),
)

// Semantic Colors
val colorPing = Color(0xFF00FF66) // Neon Green
val colorPingRed = Color(0xFFFF3366) // Pink Red
val colorConfigType = Color(0xFF00E5FF) // Neon Cyan
val colorFabActive = Color(0xFF00FF66) // Neon Green Active
val colorFabInactiveLight = Color(0xFF9C9C9C)
val colorFabInactiveDark = Color(0xFF646464)
val dividerColorLight = Color(0xFFE0E0E0)
val dividerColorDark = Color(0xFF2E3832)

// Toast Colors
val toastNormalBgLight = Color(0xD9353A3E)
val toastNormalBgDark = Color(0xD91A1A1E)
val toastSuccessBg = Color(0xD900A846)
val toastErrorBg = Color(0xD9D50000)
val toastInfoBg = Color(0xD90088CC)
val toastIconCircleBg = Color(0x33FFFFFF)
val toastTextColor = Color.White

object ThemeManager {
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
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
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
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
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
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
