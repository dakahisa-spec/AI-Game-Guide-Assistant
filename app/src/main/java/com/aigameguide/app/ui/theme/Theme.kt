package com.aigameguide.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GuideBlue = Color(0xFF4F63F5)
val GuidePurple = Color(0xFF7759F5)
val GuideCyan = Color(0xFF22BFF4)
val GuidePink = Color(0xFFE852C4)
val Ink = Color(0xFF20202A)
val SoftBackground = Color(0xFFFAF9FF)
val CardBackground = Color(0xFFFFFFFF)
val SoftBorder = Color(0xFFE8E6F1)

private val GuideColors = lightColorScheme(
    primary = GuideBlue,
    secondary = GuidePurple,
    tertiary = GuidePink,
    background = SoftBackground,
    surface = CardBackground,
    surfaceVariant = Color(0xFFF1F0F8),
    onPrimary = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    outline = SoftBorder,
    error = Color(0xFFBC315F)
)

@Composable
fun AIGameGuideTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GuideColors, content = content)
}
