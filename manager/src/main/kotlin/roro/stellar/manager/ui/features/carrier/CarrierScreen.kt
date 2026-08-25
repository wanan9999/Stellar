package roro.stellar.manager.ui.features.carrier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import roro.stellar.manager.R
import roro.stellar.manager.carrier.CarrierPresets
import roro.stellar.manager.ui.navigation.components.StandardLargeTopAppBar
import roro.stellar.manager.ui.navigation.components.createTopAppBarScrollBehavior
import roro.stellar.manager.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarrierScreen(
    topAppBarState: TopAppBarState,
    viewModel: CarrierViewModel = viewModel()
) {
    val scrollBehavior = createTopAppBarScrollBehavior(topAppBarState)
    val state by viewModel.state.collectAsState()
    val selectedSim = state.sims.firstOrNull { it.subId == state.selectedSubId }
    val carriers = CarrierPresets.carriersFor(state.selectedCountry)

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            StandardLargeTopAppBar(
                title = stringResource(R.string.nav_carrier),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + AppSpacing.topBarContentSpacing,
                bottom = AppSpacing.screenBottomPadding,
                start = AppSpacing.screenHorizontalPadding,
                end = AppSpacing.screenHorizontalPadding
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionSpacing)
        ) {
            if (state.loading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (state.serviceRunning) {
                            stringResource(R.string.carrier_service_ready)
                        } else {
                            stringResource(R.string.carrier_service_missing)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (state.error.isNotEmpty()) {
                        Text(state.error, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item {
                DropdownField(
                    label = stringResource(R.string.carrier_section_sim),
                    selectedText = selectedSim?.let { simLabel(it) }.orEmpty(),
                    options = state.sims,
                    optionText = ::simLabel,
                    onSelect = { viewModel.selectSim(it.subId) },
                    enabled = state.sims.isNotEmpty(),
                    supportingText = selectedSim?.let(::simDetails)
                )
            }

            item {
                DropdownField(
                    label = stringResource(R.string.carrier_section_country),
                    selectedText = CarrierPresets.countries
                        .firstOrNull { !state.useCustomIso && it.code == state.selectedCountry }
                        ?.let { "${it.name} ${it.code}" }
                        ?: state.selectedCountry,
                    options = CarrierPresets.countries,
                    optionText = { "${it.name} ${it.code}" },
                    onSelect = { viewModel.selectCountry(it.code) }
                )
            }

            item {
                OutlinedTextField(
                    value = if (state.useCustomIso) state.customIso else state.selectedCountry,
                    onValueChange = viewModel::setCustomIso,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.carrier_custom_iso)) },
                    singleLine = true
                )
            }

            item {
                DropdownField(
                    label = stringResource(R.string.carrier_section_name),
                    selectedText = state.selectedCarrier,
                    options = carriers,
                    optionText = { it.name },
                    onSelect = { viewModel.selectCarrier(it.name) },
                    enabled = carriers.isNotEmpty()
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.carrier_auto_reapply), fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.carrier_auto_reapply_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.autoReapply, onCheckedChange = viewModel::setAutoReapply)
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = viewModel::apply,
                        enabled = !state.loading && state.serviceRunning && state.selectedSubId > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.carrier_apply))
                    }
                    OutlinedButton(
                        onClick = viewModel::reset,
                        enabled = !state.loading && state.serviceRunning && state.selectedSubId > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.carrier_reset))
                    }
                }
            }

            if (state.lastMessage.isNotEmpty() || state.verifiedIso.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.carrier_section_verify), fontWeight = FontWeight.Medium)
                        if (state.lastMessage.isNotEmpty()) Text(state.lastMessage)
                        if (state.lastStrategy.isNotEmpty()) Text("策略: ${state.lastStrategy}")
                        state.lastPersistent?.let {
                            Text(
                                if (it) stringResource(R.string.carrier_persistent)
                                else stringResource(R.string.carrier_non_persistent)
                            )
                        }
                        Text("getSimCountryIso: ${state.verifiedIso.ifEmpty { "-" }}")
                        Text("MCC/MNC: ${state.verifiedOperator.ifEmpty { "-" }}")
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.carrier_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun simLabel(sim: SimItem) =
    "SIM ${sim.slot}  ·  ${sim.displayName.ifEmpty { "subId ${sim.subId}" }}"

private fun simDetails(sim: SimItem): String {
    val override = buildString {
        append("覆盖 ISO ${sim.overrideIso.ifEmpty { "-" }}")
        if (sim.overrideName.isNotEmpty()) append("  ·  ${sim.overrideName}")
        append("  ·  运行时 ${sim.runtimeIso.ifEmpty { "-" }}")
    }
    return "MCC/MNC ${sim.mccMnc.ifEmpty { "-" }}  ·  SIM ISO ${sim.simIso.ifEmpty { "-" }}\n$override"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownField(
    label: String,
    selectedText: String,
    options: List<T>,
    optionText: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) },
            supportingText = supportingText?.let { { Text(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionText(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
