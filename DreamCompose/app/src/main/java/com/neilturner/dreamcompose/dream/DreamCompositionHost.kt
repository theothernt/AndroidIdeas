package com.neilturner.dreamcompose.dream

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * A [DreamService] is a [android.app.Service], not an activity, so it does not provide the
 * lifecycle, saved state and view model owners that Compose requires in order to host a
 * composition. This class provides the minimum set of owners for a ComposeView that is hosted
 * by a service window.
 */
internal class DreamCompositionHost :
	LifecycleOwner,
	SavedStateRegistryOwner,
	ViewModelStoreOwner {
	private val lifecycleRegistry = LifecycleRegistry(this)
	private val savedStateRegistryController = SavedStateRegistryController.create(this)
	private val store = ViewModelStore()

	override val lifecycle: Lifecycle
		get() = lifecycleRegistry

	override val savedStateRegistry: SavedStateRegistry
		get() = savedStateRegistryController.savedStateRegistry

	override val viewModelStore: ViewModelStore
		get() = store

	fun start() {
		savedStateRegistryController.performAttach()
		savedStateRegistryController.performRestore(null)
		lifecycleRegistry.currentState = Lifecycle.State.RESUMED
	}

	fun stop() {
		lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
		store.clear()
	}
}
