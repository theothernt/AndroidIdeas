package com.neilturner.perfview.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme

/**
 * TV Material 3 color scheme.
 *
 * android.compose.material3.MaterialTheme and androidx.tv.material3.MaterialTheme keep separate
 * CompositionLocals, so the TV components need their own theme to inherit from. Without this,
 * a TV Button nested in the phone theme silently falls back to the TV library's light defaults,
 * which reads as a washed-out button on this dark dashboard.
 *
 * The values mirror the phone scheme's roles so both layers stay visually consistent.
 */
private val TvDarkColors = darkColorScheme(
    primary = PerfBlue,
    onPrimary = PerfInk,
    secondary = PerfMint,
    onSecondary = PerfInk,
    tertiary = PerfSky,
    onTertiary = PerfInk,
    background = PerfInk,
    onBackground = PerfMist,
    surface = PerfTeal,
    onSurface = PerfMist,
    surfaceVariant = PerfInkMid,
    onSurfaceVariant = PerfSlate,
    inverseSurface = PerfMist,
    inverseOnSurface = PerfInk,
    error = PerfDangerOutline,
    onError = PerfInk,
)

/**
 * Supplies the TV Material 3 theme used by TV components such as [androidx.tv.material3.Button].
 *
 * Provided as a separate wrapper rather than replacing [PerfViewTheme], because the screens still
 * use phone Material 3 components (for example CircularProgressIndicator, which TV Material 3
 * does not ship) that read the phone theme.
 */
@Composable
fun PerfViewTvTheme(content: @Composable () -> Unit) {
    androidx.tv.material3.MaterialTheme(
        colorScheme = TvDarkColors,
        content = content,
    )
}