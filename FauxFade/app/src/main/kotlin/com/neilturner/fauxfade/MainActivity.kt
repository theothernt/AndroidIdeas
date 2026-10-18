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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.neilturner.fauxfade.core.theme.FauxFadeTheme
import com.neilturner.fauxfade.feature.pixelcopy.PixelCopyScreen
import com.neilturner.fauxfade.feature.pixelcopy.PixelCopyViewModel
import com.neilturner.fauxfade.ui.player.AppPhase
import com.neilturner.fauxfade.ui.player.PlayerViewModel
import com.neilturner.fauxfade.ui.player.StillCaptureMode
import com.neilturner.fauxfade.ui.player.VideoPlayerScreen
import com.neilturner.fauxfade.ui.startup.StartupScreen

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
 * Where the app is.
 *
 * A sealed type rather than a navigation graph: two destinations and no back stack worth the name.
 * Routing them behind a single `when` makes a missing case a compile error rather than a route that
 * silently does nothing, and it keeps the app free of a navigation dependency it would gain nothing
 * from.
 *
 * [Shared] is the app's entry point and covers the startup screen as well as playback, because
 * those two are the same feature - one view model decides which of them is up, and Back during a
 * run is answered by stopping it rather than by navigating. [PixelCopy] is the one method that has
 * been given a screen and a view model of its own; the other four are still run by [Shared], and
 * each will join [PixelCopy] as it is pulled out.
 */
private sealed interface Screen {
	/** The startup picker, and the playback screen behind it once a run has been started. */
	data object Shared : Screen

	/** The PixelCopy method's own screen, picker and playback and all. */
	data object PixelCopy : Screen
}

/**
 * Chooses between the two, and gives each its own view models.
 *
 * The startup screen is where the app begins and where Back from a run returns to, so [Screen.Shared]
 * is not remembered underneath a feature either - it is rebuilt on the way back. That costs a
 * re-parse of the catalogue, which is asynchronous and which the picker does not wait for, and it
 * buys a screen that is in the state the user left it in rather than one restored from a run they
 * have already finished with.
 *
 * Both routes go through [FeatureHost], so leaving either releases what it built. Only leaving
 * [Screen.PixelCopy] needs that: stopping a run on [Screen.Shared] releases the players itself and
 * the view model stays put, which is what lets the two choices on the picker survive into the next
 * run.
 */
@Composable
private fun FauxFadeApp() {
	var screen by remember { mutableStateOf<Screen>(Screen.Shared) }
	val toStartup: () -> Unit = { screen = Screen.Shared }

	when (screen) {
		Screen.Shared -> {
			FeatureHost {
				SharedMethod(onGoToPixelCopy = { screen = Screen.PixelCopy })
			}
		}

		Screen.PixelCopy -> {
			FeatureHost {
				// Leaves for the startup screen, which unmounts this feature's store and so
				// releases its player. A picker panel gets Back first and closes itself - see
				// the `onPreviewKeyEvent` on the screens that have one - and key events only
				// reach the dispatcher when nothing in Compose wanted them.
				BackHandler(onBack = toStartup)
				val viewModel: PixelCopyViewModel = viewModel()
				PixelCopyScreen(viewModel = viewModel)
			}
		}
	}
}

/**
 * The startup picker and the playback screen it hands over to, both driven by one view model.
 *
 * The Go button does not simply start a run: it reports which capture method was picked, and this
 * is where that turns into a screen. Four of the five run on this path. [StillCaptureMode.PIXEL_COPY]
 * is the odd one - it has a view model, a loop and a capture class of its own, so the button is
 * answered by going to [Screen.PixelCopy] instead of by building two players that were never going
 * to be used. The route is the only place the two halves meet, which is why the shared view model
 * can carry the other four without knowing PixelCopy exists at all.
 *
 * Because the picker and playback share a route, Back during a run cannot navigate anywhere and
 * does not try. It stops the run instead - see [PlayerViewModel.stopRun] - which is what the same
 * key does on the PixelCopy screen, where stopping means unmounting the feature.
 */
@Composable
private fun SharedMethod(onGoToPixelCopy: () -> Unit) {
	val viewModel: PlayerViewModel = viewModel()
	val phase by viewModel.phase.collectAsStateWithLifecycle()

	if (phase == AppPhase.PLAYING) {
		// Registered only once there is a run to stop: on the picker Back has nowhere to go but
		// out of the app, which is what the activity's own handler does when nothing has claimed it.
		BackHandler(onBack = viewModel::stopRun)
		VideoPlayerScreen(viewModel = viewModel)
	} else {
		StartupScreen(
			viewModel = viewModel,
			onGo = { picked ->
				if (picked == StillCaptureMode.PIXEL_COPY) {
					onGoToPixelCopy()
				} else {
					viewModel.skipCountdown()
				}
			},
		)
	}
}

/**
 * Gives one feature its own [ViewModelStore], so leaving it releases everything it built.
 *
 * Without this every feature would share the activity's store, and the player built by the last one
 * would survive a trip back to the startup screen and the choice of a different method: a decoder
 * held against its will under a list of buttons, and a view model whose phase is already `PLAYING`,
 * so the next entry would resume a finished run rather than start one. Clearing the store calls
 * `onCleared()` on each view model in it, which is where the player is released.
 *
 * Remembered without a key on purpose: this composable is only in the tree while one feature is,
 * so entering a different feature is a fresh instance with nothing in it to inherit.
 */
@Composable
private fun FeatureHost(content: @Composable () -> Unit) {
	// Captured before this replaces it - see [FeatureStoreOwner] for why it matters that it is.
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

/**
 * A [ViewModelStoreOwner] that belongs to one feature and nothing else.
 *
 * Not the activity, because the activity's store is where the activity's own view models live, and
 * borrowing it is exactly what would make a feature's state outlive its own screen.
 *
 * It does borrow the activity's *construction* rules, and has to: `viewModel()` asks the owner how
 * to make things, and an owner that only implements [ViewModelStoreOwner] is answered with
 * [CreationExtras.Empty] and a reflective default factory. Both view models here take an
 * `Application`, so that answer is a lookup for a no-arg constructor that does not exist, and the
 * first time anything asked for one the app died with "Cannot create an instance of class
 * PixelCopyViewModel". Passing the parent's factory and its extras through carries the application
 * the same way the activity would have.
 */
private class FeatureStoreOwner(
	private val parent: HasDefaultViewModelProviderFactory,
) : ViewModelStoreOwner,
	HasDefaultViewModelProviderFactory {
	override val viewModelStore = ViewModelStore()

	override val defaultViewModelProviderFactory: ViewModelProvider.Factory = parent.defaultViewModelProviderFactory

	override val defaultViewModelCreationExtras: CreationExtras = parent.defaultViewModelCreationExtras
}
