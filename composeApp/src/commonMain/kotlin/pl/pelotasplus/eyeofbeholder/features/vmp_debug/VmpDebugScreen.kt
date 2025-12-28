package pl.pelotasplus.eyeofbeholder.features.vmp_debug

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
fun VmpDebugScreen(
    viewModel: VmpDebugViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    VmpDebugContent(
        modifier = modifier,
        state = state,
        onVmpSelected = {
            viewModel.onEvent(VmpDebugViewModel.Event.OnVmpSelected(it))
        }
    )
}

@Composable
private fun VmpDebugContent(
    state: VmpDebugViewModel.State,
    modifier: Modifier = Modifier,
    onVmpSelected: (String) -> Unit = {},
) {
    if (state.isLoading) {
        CircularProgressIndicator()
    } else {
        BoxWithConstraints(modifier = modifier) {
            val isLandscape = maxWidth > maxHeight

            val vmpList: @Composable (Modifier) -> Unit = { listModifier ->
                LazyColumn(listModifier) {
                    items(state.allVmps) { vmpName ->
                        Button(
                            onClick = { onVmpSelected(vmpName) }
                        ) {
                            Text(text = vmpName)
                        }
                    }
                }
            }

            val vmpDetails: @Composable (Modifier) -> Unit = { detailsModifier ->
                if (state.selectedVmp != null) {
                    Column(
                        modifier = detailsModifier
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "File: ${state.selectedVmp.name}",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "Tiles count: ${state.selectedVmp.tileIndexes.size}",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "Wall types count: ${state.selectedVmp.wallTypesCount}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            if (isLandscape) {
                Row {
                    vmpList(Modifier.weight(1f).fillMaxHeight())
                    vmpDetails(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column {
                    vmpList(Modifier.weight(1f).fillMaxWidth())
                    vmpDetails(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}


@Preview
@Composable
private fun PreviewVmpDebugContent() {
    VmpDebugContent(
        state = VmpDebugViewModel.State()
    )
}
