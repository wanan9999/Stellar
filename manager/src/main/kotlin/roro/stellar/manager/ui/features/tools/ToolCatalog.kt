package roro.stellar.manager.ui.features.tools

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import roro.stellar.manager.R
import roro.stellar.manager.carrier.CarrierController
import roro.stellar.manager.location.LocationController
import roro.stellar.manager.ui.features.carrier.CarrierScreen
import roro.stellar.manager.ui.features.location.LocationScreen

data class ToolSpec(
    val id: String,
    val route: String,
    val titleRes: Int,
    val icon: ImageVector,
    val subtitle: (Context) -> String,
    val content: @Composable (onBack: () -> Unit) -> Unit
)

object ToolCatalog {
    const val LIST = "tools"

    val Sim = ToolSpec(
        id = "sim",
        route = "tool_sim",
        titleRes = R.string.tool_sim,
        icon = Icons.Outlined.SimCard,
        subtitle = { context ->
            val overlays = runCatching {
                CarrierController.snapshots(context).filter { it.overlayIso.isNotEmpty() }
            }.getOrDefault(emptyList())
            if (overlays.isEmpty()) {
                context.getString(R.string.tool_sim_idle)
            } else {
                overlays.joinToString(" · ") { "SIM ${it.slot} ${it.overlayIso.uppercase()}" }
            }
        },
        content = { onBack -> CarrierScreen(onBack = onBack) }
    )

    val Location = ToolSpec(
        id = "location",
        route = "tool_location",
        titleRes = R.string.tool_location,
        icon = Icons.Outlined.MyLocation,
        subtitle = { context ->
            val snap = LocationController.snapshot.value
            if (snap.active) {
                context.getString(
                    R.string.tool_location_active,
                    snap.label.ifEmpty { context.getString(R.string.location_custom) }
                )
            } else {
                context.getString(R.string.tool_location_idle)
            }
        },
        content = { onBack -> LocationScreen(onBack = onBack) }
    )

    val all = listOf(Sim, Location)
}
