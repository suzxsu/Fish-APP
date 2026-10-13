package com.guanyji.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
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
fun FishAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FishColorScheme,
        typography = Typography(),
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(18.dp),
        ),
        content = content,
    )
}
