package com.guanyji.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FishColorScheme = lightColorScheme(
    primary = OceanBlue,
    onPrimary = Color.White,
    secondary = AquaGreen,
    onSecondary = Color.White,
    background = Mist,
    onBackground = Ink,
    surface = SurfaceWhite,
    onSurface = Ink,
    onSurfaceVariant = MutedInk,
    outline = SoftBorder,
    error = Color(0xFFB3261E),
)

@Composable
fun FishAppTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val colors = if (dark) darkColorScheme(primary = Color(0xFF80B8FF), secondary = Color(0xFF7BD4C8), background = Color(0xFF101820), surface = Color(0xFF17232D)) else FishColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(18.dp),
        ),
        content = content,
    )
}
