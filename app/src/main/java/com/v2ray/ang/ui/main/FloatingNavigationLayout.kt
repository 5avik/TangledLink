package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.LocalThemeConfig
import com.v2ray.ang.ui.compose.ThemedBackground

/** The bar overlays the scrollable viewport; screens reserve its measured height at list end. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FloatingNavigationLayout(
    selected: String,
    onSelect: (String) -> Unit,
    content: @Composable (PaddingValues, Dp) -> Unit,
) {
    var barHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val fallback = 92.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val clearance = if (barHeightPx > 0) with(density) { barHeightPx.toDp() } else fallback
    val reservedPixels = with(density) { clearance.toPx() }
    val reservedTopPixels = WindowInsets.safeDrawing.getTop(density).toFloat()
    val focusScrolling = remember(reservedPixels, reservedTopPixels) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                if (offset < reservedTopPixels) return offset - reservedTopPixels
                val visibleHeight = (containerSize - reservedPixels).coerceAtLeast(reservedTopPixels)
                return (offset + size - visibleHeight).coerceAtLeast(0f)
                    .coerceAtMost((offset - reservedTopPixels).coerceAtLeast(0f))
            }
        }
    }
    val sceneVisible = selected == "home"
    val view = LocalView.current
    val dark = LocalDarkTheme.current
    val config = LocalThemeConfig.current
    // The scene beneath system bars stays dark even when Material cards use the light palette.
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !sceneVisible && !dark
                isAppearanceLightNavigationBars = !sceneVisible && !dark
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (sceneVisible) ThemedBackground(Modifier.fillMaxSize(), config)
        Scaffold(
            containerColor = if (sceneVisible) Color.Transparent else MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        ) { insets ->
            Box(Modifier.fillMaxSize().consumeWindowInsets(insets).imePadding()) {
                CompositionLocalProvider(LocalBringIntoViewSpec provides focusScrolling) {
                    content(insets, clearance)
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .height(clearance)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, (if (sceneVisible) config.scene.background else MaterialTheme.colorScheme.background).copy(alpha = .9f)))),
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(start=insets.calculateStartPadding(direction),end=insets.calculateEndPadding(direction))
                        .onSizeChanged { barHeightPx = it.height }
                        .testTag("floating_navigation"),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomNavigationBar(selected, onSelect)
                }
            }
        }
    }
}

@Composable
internal fun floatingContentPadding(
    insets: PaddingValues,
    bottomClearance: Dp,
    horizontal: Dp,
    vertical: Dp,
): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = insets.calculateStartPadding(direction) + horizontal,
        top = insets.calculateTopPadding() + vertical,
        end = insets.calculateEndPadding(direction) + horizontal,
        bottom = bottomClearance + vertical,
    )
}
