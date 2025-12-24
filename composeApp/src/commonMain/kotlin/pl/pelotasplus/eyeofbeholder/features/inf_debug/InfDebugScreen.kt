package pl.pelotasplus.eyeofbeholder.features.inf_debug

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
import androidx.compose.ui.text.font.FontFamily
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
        BoxWithConstraints(modifier = modifier) {
            val isLandscape = maxWidth > maxHeight

            val infList: @Composable (Modifier) -> Unit = { listModifier ->
                LazyColumn(listModifier) {
                    items(state.allInfs) { infName ->
                        Button(
                            onClick = { onInfSelected(infName) }
                        ) {
                            Text(text = infName)
                        }
                    }
                }
            }

            val infDetails: @Composable (Modifier) -> Unit = { detailsModifier ->
                if (state.loadedInf != null) {
                    Column(
                        modifier = detailsModifier
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "File: ${state.loadedInf.name}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Size: ${state.loadedInf.data.size} bytes",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Hex dump (first 256 bytes):",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = formatHexDump(state.loadedInf.data.take(256)),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (isLandscape) {
                Row {
                    infList(Modifier.weight(1f).fillMaxHeight())
                    infDetails(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column {
                    infList(Modifier.weight(1f).fillMaxWidth())
                    infDetails(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

private fun formatHexDump(bytes: List<UByte>): String {
    return bytes.chunked(16).mapIndexed { lineIndex, line ->
        val offset = String.format("%04X", lineIndex * 16)
        val hex = line.joinToString(" ") { String.format("%02X", it.toInt()) }
        val ascii = line.map { byte ->
            val char = byte.toInt().toChar()
            if (char.isLetterOrDigit() || char in " !\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~") char else '.'
        }.joinToString("")
        "$offset  ${hex.padEnd(48)}  $ascii"
    }.joinToString("\n")
}

@Preview
@Composable
private fun PreviewInfDebugContent() {
    InfDebugContent(
        state = InfDebugViewModel.State(
            isLoading = true
        )
    )
}
