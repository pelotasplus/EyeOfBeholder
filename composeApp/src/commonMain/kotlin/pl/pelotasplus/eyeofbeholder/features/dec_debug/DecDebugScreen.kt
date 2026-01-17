package pl.pelotasplus.eyeofbeholder.features.dec_debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DecDebugScreen(
    viewModel: DecDebugViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DecDebugContent(
        modifier = modifier,
        state = state,
        onDecSelected = {
            viewModel.onEvent(DecDebugViewModel.Event.OnDecSelected(it))
        }
    )
}

@Composable
private fun DecDebugContent(
    state: DecDebugViewModel.State,
    modifier: Modifier = Modifier,
    onDecSelected: (String) -> Unit = {},
) {
    Column(modifier = modifier) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(state.allDecs) { decName ->
                Button(
                    onClick = { onDecSelected(decName) }
                ) {
                    Text(text = decName)
                }
            }
        }

        if (state.loadedDec != null) {
            Text(text = "Loaded: ${state.loadedDec.name}")
            Text(text = "Decorations: ${state.loadedDec.decorations.size}")
        }
    }
}

@Preview
@Composable
private fun PreviewDecDebugContent() {
    DecDebugContent(
        state = DecDebugViewModel.State()
    )
}
