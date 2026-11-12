@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.v2ray.ang.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

private val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)
private val Unbounded = FontFamily(Font(R.font.unbounded, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))))
private val defaults = Typography()
val BrandTypography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = Unbounded),
    displayMedium = defaults.displayMedium.copy(fontFamily = Unbounded),
    displaySmall = defaults.displaySmall.copy(fontFamily = Unbounded),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = Unbounded),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = Unbounded),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = Unbounded),
    titleLarge = defaults.titleLarge.copy(fontFamily = Unbounded),
    titleMedium = defaults.titleMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    titleSmall = defaults.titleSmall.copy(fontFamily = Manrope),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = Manrope),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = Manrope),
    bodySmall = defaults.bodySmall.copy(fontFamily = Manrope),
    labelLarge = defaults.labelLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    labelMedium = defaults.labelMedium.copy(fontFamily = Manrope),
    labelSmall = defaults.labelSmall.copy(fontFamily = Manrope)
)
val BrandShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
val BrandGradient = Brush.linearGradient(
    listOf(Color(0xFFC3FF92), Color(0xFF6EF5A8), Color(0xFF21D4A4))
)

@Composable
fun BrandButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFF04140C),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.heightIn(min = 52.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().background(if (enabled) BrandGradient else Brush.linearGradient(
                listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerHighest)
            )).padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            content = content
        )
    }
}
