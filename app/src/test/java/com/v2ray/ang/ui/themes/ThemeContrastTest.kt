package com.v2ray.ang.ui.themes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.v2ray.ang.ui.compose.ThemeCatalog
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThemeContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }

    @Test fun bodyLabelsAndActionsRemainReadableInBothWorldPalettes() {
        for (theme in ThemeCatalog.ordered) {
            for (palette in listOf(theme.light, theme.dark)) {
                val pairs = listOf(
                    palette.onSurface to palette.surface,
                    palette.onSurfaceVariant to palette.surface,
                    palette.onPrimary to palette.primary,
                    palette.onPrimary to palette.tertiary,
                    palette.onPrimaryContainer to palette.primaryContainer,
                    palette.onError to palette.error,
                )
                for ((text, background) in pairs) {
                    assertTrue(contrast(text, background) >= 4.5f,
                        "${theme.id}: text/background contrast must be at least 4.5:1")
                }
            }
            // Opaque scene panels provide the stable baseline for important text.
            assertTrue(contrast(theme.scene.onScene, theme.scene.background) >= 4.5f)
        }
    }
}
