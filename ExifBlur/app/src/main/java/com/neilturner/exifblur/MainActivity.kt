package com.neilturner.exifblur

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.neilturner.exifblur.ui.navigation.ExifBlurNavGraph
import com.neilturner.exifblur.ui.theme.ExifBlurTheme
import androidx.tv.material3.ExperimentalTvMaterial3Api

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExifBlurTheme {
                ExifBlurNavGraph()
            }
        }
    }
}