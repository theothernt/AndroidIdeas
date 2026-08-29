package com.neilturner.navstate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.neilturner.navstate.navigation.AppNavigation
import com.neilturner.navstate.ui.theme.NavStateTheme

class MainActivity : ComponentActivity() {
	@OptIn(ExperimentalTvMaterial3Api::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			NavStateTheme {
				Surface(
					modifier = Modifier.fillMaxSize(),
					shape = RectangleShape
				) {
					AppNavigation()
				}
			}
		}
	}
}