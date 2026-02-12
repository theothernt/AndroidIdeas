package com.neilturner.twopane.ui.theme

import android.content.pm.PackageManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TwoPaneTheme(
    isInDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    if (isTv) {
        val tvColorScheme = if (isInDarkTheme) {
            darkColorScheme(
                primary = Purple80,
                secondary = PurpleGrey80,
                tertiary = Pink80
            )
        } else {
            lightColorScheme(
                primary = Purple40,
                secondary = PurpleGrey40,
                tertiary = Pink40
            )
        }

        MaterialTheme(
            colorScheme = tvColorScheme,
            typography = Typography,
            content = content
        )
    } else {
        val mobileColorScheme = if (isInDarkTheme) {
            androidx.compose.material3.darkColorScheme(
                primary = Purple80,
                secondary = PurpleGrey80,
                tertiary = Pink80
            )
        } else {
            androidx.compose.material3.lightColorScheme(
                primary = Purple40,
                secondary = PurpleGrey40,
                tertiary = Pink40
            )
        }

        androidx.compose.material3.MaterialTheme(
            colorScheme = mobileColorScheme,
            typography = MobileTypography,
            content = content
        )
    }
}