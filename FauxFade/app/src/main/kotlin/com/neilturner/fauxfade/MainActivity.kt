package com.neilturner.fauxfade

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.neilturner.fauxfade.core.media.Quality
import com.neilturner.fauxfade.core.player.StillCaptureMode
import com.neilturner.fauxfade.core.theme.FauxFadeTheme
import com.neilturner.fauxfade.feature.pixelcopy.PixelCopyScreen
import com.neilturner.fauxfade.feature.pixelcopy.PixelCopyViewModel
import com.neilturner.fauxfade.feature.preloadedframe.PreloadedFrameScreen
import com.neilturner.fauxfade.feature.preloadedframe.PreloadedFrameViewModel
import com.neilturner.fauxfade.feature.retriever.RetrieverScreen
import com.neilturner.fauxfade.feature.retriever.RetrieverViewModel
import com.neilturner.fauxfade.feature.startup.StartupScreen
import com.neilturner.fauxfade.feature.tap.TapScreen
import com.neilturner.fauxfade.feature.tap.TapViewModel
import com.neilturner.fauxfade.feature.timedeffect.TimedEffectScreen
import com.neilturner.fauxfade.feature.timedeffect.TimedEffectViewModel

class MainActivity : ComponentActivity() {
	@OptIn(ExperimentalTvMaterial3Api::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			FauxFadeTheme {
				Surface(
					modifier = Modifier.fillMaxSize(),
					shape = RectangleShape,
				) {
					FauxFadeApp()
				}
			}
		}
	}
}

/**
 * Where the app is. One destination per capture method, plus the menu that selects one.
 *
 * A sealed type rather than a navigation graph: there is no back stack worth the name, and making
 * the route a compile-time case means a missing destination is an error rather than a route that
 * silently does nothing. Each method owns its screen, view model and capture utilities in its own
 * `feature/` package; the only thing shared between them is the menu's choice of quality.
 */
private sealed interface Screen {
	data object Startup : Screen

	data object Tap : Screen

	data object TimedEffect : Screen

	data object Retriever : Screen

	data object PreloadedFrame : Screen

	data object PixelCopy : Screen
}

private fun StillCaptureMode.screen(): Screen =
	when (this) {
		StillCaptureMode.ALWAYS_ON_TAP -> Screen.Tap
		StillCaptureMode.SCHEDULED_EFFECT -> Screen.TimedEffect
		StillCaptureMode.METADATA_RETRIEVER -> Screen.Retriever
		StillCaptureMode.PRELOADED_FRAME -> Screen.PreloadedFrame
		StillCaptureMode.PIXEL_COPY -> Screen.PixelCopy
	}

@Composable
private fun FauxFadeApp() {
	var screen by remember { mutableStateOf<Screen>(Screen.Startup) }
	var quality by remember { mutableStateOf(Quality.FHD_SDR) }
	val toStartup: () -> Unit = { screen = Screen.Startup }

	when (screen) {
		Screen.Startup -> {
			FeatureHost {
				StartupScreen(
					onGo = { pickedQuality, picked ->
						quality = pickedQuality
						screen = picked.screen()
					},
				)
			}
		}

		Screen.Tap -> {
			FeatureHost {
				BackHandler(onBack = toStartup)
				val viewModel: TapViewModel = viewModel()
				TapScreen(viewModel = viewModel, initialQuality = quality)
			}
		}

		Screen.TimedEffect -> {
			FeatureHost {
				BackHandler(onBack = toStartup)
				val viewModel: TimedEffectViewModel = viewModel()
				TimedEffectScreen(viewModel = viewModel, initialQuality = quality)
			}
		}

		Screen.Retriever -> {
			FeatureHost {
				BackHandler(onBack = toStartup)
				val viewModel: RetrieverViewModel = viewModel()
				RetrieverScreen(viewModel = viewModel, initialQuality = quality)
			}
		}

		Screen.PreloadedFrame -> {
			FeatureHost {
				BackHandler(onBack = toStartup)
				val viewModel: PreloadedFrameViewModel = viewModel()
				PreloadedFrameScreen(viewModel = viewModel, initialQuality = quality)
			}
		}

		Screen.PixelCopy -> {
			FeatureHost {
				BackHandler(onBack = toStartup)
				val viewModel: PixelCopyViewModel = viewModel()
				PixelCopyScreen(viewModel = viewModel, initialQuality = quality)
			}
		}
	}
}

/**
 * Gives one feature its own [ViewModelStore], so leaving it releases everything it built.
 *
 * Without this every feature would share the activity's store, and the player built by the last
 * one would survive a trip back to the menu and the choice of a different method: a decoder held
 * against its will under a list of buttons. Clearing the store calls `onCleared()` on each view
 * model in it, which is where the player is released.
 */
@Composable
private fun FeatureHost(content: @Composable () -> Unit) {
	val parent =
		checkNotNull(LocalViewModelStoreOwner.current as? HasDefaultViewModelProviderFactory) {
			"The activity has to be a ViewModelStoreOwner that knows how to build view models"
		}
	val owner = remember { FeatureStoreOwner(parent) }

	CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
		content()
	}

	DisposableEffect(owner) {
		onDispose { owner.viewModelStore.clear() }
	}
}

/** A [ViewModelStoreOwner] that belongs to one feature and borrows the activity's factory. */
private class FeatureStoreOwner(
	private val parent: HasDefaultViewModelProviderFactory,
) : ViewModelStoreOwner,
	HasDefaultViewModelProviderFactory {
	override val viewModelStore = ViewModelStore()

	override val defaultViewModelProviderFactory: ViewModelProvider.Factory = parent.defaultViewModelProviderFactory

	override val defaultViewModelCreationExtras: CreationExtras = parent.defaultViewModelCreationExtras
}
