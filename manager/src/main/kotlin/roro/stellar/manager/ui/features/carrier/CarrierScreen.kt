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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import roro.stellar.manager.R
import roro.stellar.manager.carrier.CarrierPresets
import roro.stellar.manager.carrier.CountryPreset
import roro.stellar.manager.ui.navigation.components.FixedTopAppBar
import roro.stellar.manager.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarrierScreen(
    viewModel: CarrierViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val selectedSim = state.sims.firstOrNull { it.subId == state.selectedSubId }
    val carriers = CarrierPresets.carriersFor(state.selectedCountry)
    val otherLabel = stringResource(R.string.carrier_country_other)
    val countries = remember(otherLabel) {
        CarrierPresets.countries + CountryPreset(CUSTOM_COUNTRY_CODE, otherLabel)
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            FixedTopAppBar(title = stringResource(R.string.nav_carrier))
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

            if (!state.serviceRunning || state.error.isNotEmpty()) {
                item {
                    Text(
                        if (!state.serviceRunning) {
                            stringResource(R.string.carrier_service_missing)
                        } else {
                            state.error
                        },
                        color = MaterialTheme.colorScheme.error
                    )
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
                    supportingText = selectedSim?.let { sim ->
                        val iso = sim.overrideIso.ifEmpty { sim.runtimeIso.ifEmpty { sim.simIso } }
                        if (sim.overrideIso.isNotEmpty()) {
                            stringResource(R.string.carrier_sim_overlay, iso)
                        } else if (iso.isNotEmpty()) {
                            stringResource(R.string.carrier_sim_current, iso)
                        } else {
                            null
                        }
                    }
                )
            }

            item {
                DropdownField(
                    label = stringResource(R.string.carrier_section_country),
                    selectedText = when {
                        state.useCustomIso -> state.customIso.ifEmpty { otherLabel }
                        else -> CarrierPresets.countries
                            .firstOrNull { it.code == state.selectedCountry }
                            ?.let { "${it.name} ${it.code}" }
                            ?: state.selectedCountry
                    },
                    options = countries,
                    optionText = { if (it.code == CUSTOM_COUNTRY_CODE) it.name else "${it.name} ${it.code}" },
                    onSelect = { viewModel.selectCountry(it.code) }
                )
            }

            if (state.useCustomIso) {
                item {
                    OutlinedTextField(
                        value = state.customIso,
                        onValueChange = viewModel::setCustomIso,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.carrier_custom_iso)) },
                        singleLine = true
                    )
                }
            }

            if (carriers.isNotEmpty()) {
                item {
                    DropdownField(
                        label = stringResource(R.string.carrier_section_name),
                        selectedText = state.selectedCarrier,
                        options = carriers,
                        optionText = { it.name },
                        onSelect = { viewModel.selectCarrier(it.name) }
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.carrier_auto_reapply),
                        modifier = Modifier.weight(1f)
                    )
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

            if (state.verifiedIso.isNotEmpty() || state.lastPersistent == false) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.verifiedIso.isNotEmpty()) {
                            Text(stringResource(R.string.carrier_status_active, state.verifiedIso))
                        }
                        if (state.lastPersistent == false) {
                            Text(
                                stringResource(R.string.carrier_non_persistent),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
