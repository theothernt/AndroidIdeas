package com.neilturner.navstate.ui.screens

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neilturner.navstate.navigation.ScreenSix
import com.neilturner.navstate.ui.FocusableText
import com.neilturner.navstate.ui.TvScreenColumn
import com.neilturner.navstate.viewmodel.CounterViewModel

@Composable
fun ScreenSixContent(
    onNavigateToScreenFive: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CounterViewModel = viewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity,
        key = ScreenSix::class.qualifiedName!!,
        factory = viewModelFactory { initializer { CounterViewModel() } },
    ),
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    TvScreenColumn(modifier = modifier) {
        FocusableText(text = "Screen Six")
        FocusableText(text = "Counter: ${viewModel.counter}")
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onNavigateToScreenFive,
            modifier = Modifier.focusRequester(focusRequester),
        ) {
            Text(text = "Back to Screen 5")
        }
    }
}