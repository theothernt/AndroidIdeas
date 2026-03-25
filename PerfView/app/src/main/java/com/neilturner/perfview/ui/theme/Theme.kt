package com.neilturner.perfview.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = PerfBlue,
    secondary = PerfMint,
    tertiary = PerfSky,
    background = PerfInk,
    surface = PerfTeal,
    surfaceVariant = PerfInkMid,
    onPrimary = PerfInk,
    onSecondary = PerfInk,
    onBackground = PerfMist,
    onSurface = PerfMist,
    outline = PerfSlate,
    error = PerfDangerOutline,
)

private val LightColors = lightColorScheme(
    primary = PerfBlue,
    secondary = PerfMint,
    tertiary = PerfSky,
    background = PerfMist,
    surface = Color.White,
    surfaceVariant = Color(0xFFDDECEF),
    onPrimary = PerfInk,
    onSecondary = PerfInk,
    onBackground = PerfInk,
    onSurface = PerfInk,
    outline = PerfSlate,
)

@Composable
fun PerfViewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content
    )
}
