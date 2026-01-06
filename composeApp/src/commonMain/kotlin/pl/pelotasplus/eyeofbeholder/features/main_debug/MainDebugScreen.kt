package pl.pelotasplus.eyeofbeholder.features.main_debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainDebugScreen(
    modifier: Modifier = Modifier,
    onCpsDebugClick: () -> Unit,
    onPalDebugClick: () -> Unit,
    onInfDebugClick: () -> Unit,
    onMazDebugClick: () -> Unit,
    onVcnDebugClick: () -> Unit,
    onViewConeDebugClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = onCpsDebugClick,
        ) {
            Text("CPS Debug")
        }
        Button(
            onClick = onPalDebugClick,
        ) {
            Text("PAL Debug")
        }
        Button(
            onClick = onInfDebugClick,
        ) {
            Text("INF Debug")
        }
        Button(
            onClick = onMazDebugClick,
        ) {
            Text("MAZ Debug")
        }
        Button(
            onClick = onVcnDebugClick,
        ) {
            Text("VCN Debug")
        }
        Button(
            onClick = onViewConeDebugClick,
        ) {
            Text("View Cone Debug")
        }
    }
}
