package pl.pelotasplus.eyeofbeholder.features.levels_debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LevelsDebugScreen(
    onLevelSelected: (String) -> Unit,
    viewModel: LevelsDebugViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LevelsDebugContent(
        modifier = modifier,
        levels = state.levels,
        onLevelSelected = onLevelSelected
    )
}

@Composable
private fun LevelsDebugContent(
    levels: ImmutableList<String> = persistentListOf(),
    modifier: Modifier = Modifier,
    onLevelSelected: (String) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(levels) { levelName ->
            Button(onClick = { onLevelSelected(levelName) }) {
                Text(text = levelName)
            }
        }
    }
}
