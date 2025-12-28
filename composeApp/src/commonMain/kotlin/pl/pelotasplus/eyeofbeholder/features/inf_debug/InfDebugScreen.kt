package pl.pelotasplus.eyeofbeholder.features.inf_debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
fun InfDebugScreen(
    viewModel: InfDebugViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    InfDebugContent(
        modifier = modifier,
        state = state,
        onInfSelected = {
            viewModel.onEvent(InfDebugViewModel.Event.OnInfSelected(it))
        }
    )
}

@Composable
private fun InfDebugContent(
    state: InfDebugViewModel.State,
    modifier: Modifier = Modifier,
    onInfSelected: (String) -> Unit = {},
) {
    if (state.isLoading) {
        CircularProgressIndicator()
    } else {
        Column(modifier = modifier) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(state.allInfs) { infName ->
                    Button(
                        onClick = { onInfSelected(infName) }
                    ) {
                        Text(text = infName)
                    }
                }
            }

            if (state.loadedLevel != null) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "File: ${state.loadedLevel.inf}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Number of sublevels: ${state.loadedLevel.subLevels.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    state.loadedLevel.subLevels.forEach {
                        Text(
                            text = "Sublevel: ${it.index}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Palette: ${it.palette}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Maz name: ${it.mazName}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "VMP: ${it.vmpData}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Sound: ${it.sound}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "Messages:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    state.loadedLevel.messages.forEach {
                        Text(
                            text = "\t$it",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewInfDebugContent() {
    InfDebugContent(
        state = InfDebugViewModel.State()
    )
}
