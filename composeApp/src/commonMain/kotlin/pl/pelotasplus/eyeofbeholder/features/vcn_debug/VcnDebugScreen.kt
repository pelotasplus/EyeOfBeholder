package pl.pelotasplus.eyeofbeholder.features.vcn_debug

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
fun VcnDebugScreen(
    viewModel: VcnDebugViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    VcnDebugContent(
        modifier = modifier,
        state = state,
        onVcnSelected = {
            viewModel.onEvent(VcnDebugViewModel.Event.OnVcnSelected(it))
        }
    )
}

@Composable
private fun VcnDebugContent(
    state: VcnDebugViewModel.State,
    modifier: Modifier = Modifier,
    onVcnSelected: (String) -> Unit = {},
) {
    if (state.isLoading) {
        CircularProgressIndicator()
    } else {
        BoxWithConstraints(modifier = modifier) {
            val isLandscape = maxWidth > maxHeight

            val vcnList: @Composable (Modifier) -> Unit = { listModifier ->
                LazyColumn(listModifier) {
                    items(state.allVcns) { vcnName ->
                        Button(
                            onClick = { onVcnSelected(vcnName) }
                        ) {
                            Text(text = vcnName)
                        }
                    }
                }
            }

            val vcnDetails: @Composable (Modifier) -> Unit = { detailsModifier ->
                if (state.selectedVcn != null) {
                    Column(
                        modifier = detailsModifier
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "File: ${state.selectedVcn.name}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Tiles count: ${state.selectedVcn.tilesCount}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Wall palette size: ${state.selectedVcn.wallPalette.size}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Backdrop palette size: ${state.selectedVcn.wallPalette.size}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            if (isLandscape) {
                Row {
                    vcnList(Modifier.weight(1f).fillMaxHeight())
                    vcnDetails(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column {
                    vcnList(Modifier.weight(1f).fillMaxWidth())
                    vcnDetails(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}


@Preview
@Composable
private fun PreviewVcnDebugContent() {
    VcnDebugContent(
        state = VcnDebugViewModel.State()
    )
}
