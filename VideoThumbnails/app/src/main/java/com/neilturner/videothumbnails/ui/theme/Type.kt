package com.neilturner.videothumbnails.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Typography
import com.neilturner.videothumbnails.R

val GoogleSans =
    FontFamily(
        Font(R.font.google_sans_regular, FontWeight.Normal),
        Font(R.font.google_sans_medium, FontWeight.Medium),
        Font(R.font.google_sans_semibold, FontWeight.SemiBold),
        Font(R.font.google_sans_bold, FontWeight.Bold),
    )

private fun TextStyle.googleSans(): TextStyle = copy(fontFamily = GoogleSans)

@OptIn(ExperimentalTvMaterial3Api::class)
private fun Typography.withGoogleSans(): Typography =
    copy(
        displayLarge = displayLarge.googleSans(),
        displayMedium = displayMedium.googleSans(),
        displaySmall = displaySmall.googleSans(),
        headlineLarge = headlineLarge.googleSans(),
        headlineMedium = headlineMedium.googleSans(),
        headlineSmall = headlineSmall.googleSans(),
        titleLarge = titleLarge.googleSans(),
        titleMedium = titleMedium.googleSans(),
        titleSmall = titleSmall.googleSans(),
        bodyLarge =
            TextStyle(
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            ),
        bodyMedium = bodyMedium.googleSans(),
        bodySmall = bodySmall.googleSans(),
        labelLarge = labelLarge.googleSans(),
        labelMedium = labelMedium.googleSans(),
        labelSmall = labelSmall.googleSans(),
    )

// Set of Material typography styles to start with
@OptIn(ExperimentalTvMaterial3Api::class)
val Typography = Typography().withGoogleSans()
