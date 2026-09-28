package com.neilturner.navstate.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neilturner.navstate.R
import com.neilturner.navstate.ui.ScreenText
import com.neilturner.navstate.ui.TvScreenColumn
import com.neilturner.navstate.viewmodel.CounterViewModel

/** A numbered screen in the chain, paired with the action that navigates to it. */
class ScreenTarget(val number: Int, val onNavigate: () -> Unit)

/**
 * A screen in the numbered chain, identified by its 1 based position [number].
 *
 * The [CounterViewModel] is resolved from the current NavEntry's `ViewModelStore`, which
 * `rememberViewModelStoreNavEntryDecorator` provides. Every distinct `NavKey` therefore gets its own
 * store, its own view model instance and its own counter, and the store is cleared when the entry is
 * popped off the back stack.
 */
@Composable
fun CounterScreen(
    number: Int,
    next: ScreenTarget? = null,
    previous: ScreenTarget? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: CounterViewModel = viewModel(factory = CounterViewModel.Factory)
    val counter by viewModel.counter.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(number) {
        focusRequester.requestFocus()
    }

    TvScreenColumn(modifier = modifier) {
        ScreenText(text = stringResource(R.string.screen_title, number))
        ScreenText(text = stringResource(R.string.counter_value, counter))

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = viewModel::increment,
            modifier = Modifier.focusRequester(focusRequester),
        ) {
            Text(text = stringResource(R.string.increment))
        }

        if (next != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = next.onNavigate) {
                Text(text = stringResource(R.string.go_to_screen, next.number))
            }
        }

        if (previous != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = previous.onNavigate) {
                Text(text = stringResource(R.string.back_to_screen, previous.number))
            }
        }
    }
}
