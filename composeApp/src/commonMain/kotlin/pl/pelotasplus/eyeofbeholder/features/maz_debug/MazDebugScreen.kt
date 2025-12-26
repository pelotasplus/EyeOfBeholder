package pl.pelotasplus.eyeofbeholder.features.maz_debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MazDebugScreen(
    viewModel: MazDebugViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MazDebugContent(
        modifier = modifier,
        state = state,
        onMazSelected = {
            viewModel.onEvent(MazDebugViewModel.Event.OnMazSelected(it))
        }
    )
}

@Composable
private fun MazDebugContent(
    state: MazDebugViewModel.State,
    modifier: Modifier = Modifier,
    onMazSelected: (String) -> Unit = {},
) {
    if (state.isLoading) {
        CircularProgressIndicator()
    } else {
        BoxWithConstraints(modifier = modifier) {
            val isLandscape = maxWidth > maxHeight

            val mazList: @Composable (Modifier) -> Unit = { listModifier ->
                LazyColumn(listModifier) {
                    items(state.allMazs) { mazName ->
                        Button(
                            onClick = { onMazSelected(mazName) }
                        ) {
                            Text(text = mazName)
                        }
                    }
                }
            }

            val mazDetails: @Composable (Modifier) -> Unit = { detailsModifier ->
                if (state.loadedMaz != null) {
                    Column(
                        modifier = detailsModifier
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "File: ${state.loadedMaz.name}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            if (isLandscape) {
                Row {
                    mazList(Modifier.weight(1f).fillMaxHeight())
                    mazDetails(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column {
                    mazList(Modifier.weight(1f).fillMaxWidth())
                    mazDetails(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewMazDebugContent() {
    MazDebugContent(
        state = MazDebugViewModel.State()
    )
}
