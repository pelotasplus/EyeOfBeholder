package pl.pelotasplus.eyeofbeholder.features.levels_debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.viewmodel.koinViewModel
import pl.pelotasplus.eyeofbeholder.data.model.LevelEntryPoint

@Composable
fun LevelsDebugScreen(
    onLevelSelected: (String, LevelEntryPoint?) -> Unit,
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
    levels: ImmutableList<LevelsDebugViewModel.Level> = persistentListOf(),
    modifier: Modifier = Modifier,
    onLevelSelected: (String, LevelEntryPoint?) -> Unit = { _, _ -> },
) {
    var expandedLevel by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(levels, key = { it.name }) { level ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { expandedLevel = level.name.takeIf { it != expandedLevel } }
                ) {
                    Text(text = level.name)
                }

                if (expandedLevel == level.name) {
                    Button(
                        onClick = { onLevelSelected(level.name, null) },
                        modifier = Modifier.padding(start = 16.dp),
                    ) {
                        Text(text = "Keep current position")
                    }

                    level.entryPoints.forEach { entryPoint ->
                        Button(
                            onClick = { onLevelSelected(level.name, entryPoint) },
                            modifier = Modifier.padding(start = 16.dp),
                        ) {
                            Text(text = entryPoint.label())
                        }
                    }
                }
            }
        }
    }
}

private fun LevelEntryPoint.label(): String {
    val facing = direction?.name ?: "same facing"
    val subLevelSuffix = if (subLevel == 0) "" else " sub $subLevel"
    return "${location.x}x${location.y}  $facing$subLevelSuffix  <- LEVEL$fromLevel"
}
