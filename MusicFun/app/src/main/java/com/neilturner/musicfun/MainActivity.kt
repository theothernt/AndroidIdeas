package com.neilturner.musicfun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.neilturner.musicfun.data.listener.NotificationListenerPermission
import com.neilturner.musicfun.navigation.AppNavigation
import com.neilturner.musicfun.ui.theme.MusicFunTheme

class MainActivity : ComponentActivity() {
	@OptIn(ExperimentalTvMaterial3Api::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			MusicFunTheme {
				Surface(
					modifier = Modifier.fillMaxSize(),
					shape = RectangleShape
				) {
					AppNavigation(
						onRequestNotificationAccess = {
							NotificationListenerPermission.openSettings(this@MainActivity)
						},
					)
				}
			}
		}
	}
}