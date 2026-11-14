package com.neilturner.musicfun.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.musicfun.data.listener.NotificationListenerPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MenuUiState(
	val hasNotificationListenerAccess: Boolean = false,
)

class MenuViewModel(application: Application) : AndroidViewModel(application) {

	private var hasAutoOpenedMusic = false

	private val hasAccess = MutableStateFlow(
		NotificationListenerPermission.isGranted(application)
	)

	val uiState: StateFlow<MenuUiState> = hasAccess
		.map(::MenuUiState)
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5_000),
			initialValue = MenuUiState(hasAccess.value),
		)

	/** Re-read the setting; it can only change while we are in the background. */
	fun refreshPermission() {
		val context = getApplication<Application>()
		hasAccess.value = NotificationListenerPermission.isGranted(context)
	}

	/**
	 * Whether the menu should now open the music screen: once per grant, but not again when the
	 * user navigates back from the music screen.
	 */
	fun onPermissionChanged(hasAccess: Boolean): Boolean {
		if (!hasAccess) {
			hasAutoOpenedMusic = false
			return false
		}
		if (hasAutoOpenedMusic) return false
		hasAutoOpenedMusic = true
		return true
	}
}