package com.neilturner.dreamcompose.dream

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.service.dreams.DreamService
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.neilturner.dreamcompose.ui.screens.DreamScreen
import com.neilturner.dreamcompose.ui.theme.DreamComposeTheme

/**
 * The screensaver for this app.
 *
 * The system binds to this service when the screen saver is triggered (or when the user asks for
 * a preview). It hosts a ComposeView in the service window, which means the lifecycle, saved state
 * and view model owners have to be provided manually - see [DreamCompositionHost].
 */
class DreamService : DreamService() {
	private var host: DreamCompositionHost? = null
	private var composeView: ComposeView? = null

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		isInteractive = false
		isFullscreen = true
		isScreenBright = false

		window.setBackgroundDrawable(ColorDrawable(Color.BLACK))

		val host = DreamCompositionHost().also { this.host = it }
		val composeView =
			ComposeView(this)
				.apply {
					setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
					setViewTreeLifecycleOwner(host)
					setViewTreeSavedStateRegistryOwner(host)
					setViewTreeViewModelStoreOwner(host)
				}.also { this.composeView = it }

		setContentView(
			composeView,
			ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT,
			),
		)
		composeView.setContent {
			DreamComposeTheme {
				DreamScreen()
			}
		}
		host.start()
	}

	override fun onDetachedFromWindow() {
		composeView?.setContent { }
		composeView = null
		host?.stop()
		host = null
		super.onDetachedFromWindow()
	}
}
