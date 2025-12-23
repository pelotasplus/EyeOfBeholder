package pl.pelotasplus.eyeofbeholder.features.cps_debug

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CpsDebugScreen(
    viewModel: CpsDebugViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CpsDebugContent(
        modifier = modifier,
        state = state
    )
}

@Composable
private fun CpsDebugContent(
    state: CpsDebugViewModel.State,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
    ) {
        Text("Loaded PAL is ${state.loadedPal}")
    }
}

@Preview
@Composable
private fun PreviewCpsDebugContent() {
    CpsDebugContent(
        state = CpsDebugViewModel.State(
            isLoading = true
        )
    )
}
