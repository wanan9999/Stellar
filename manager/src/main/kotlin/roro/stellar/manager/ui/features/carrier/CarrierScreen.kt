package roro.stellar.manager.ui.features.carrier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CarrierScreen(
    topAppBarState: TopAppBarState,
    viewModel: CarrierViewModel = viewModel()
) {
    val scrollBehavior = createTopAppBarScrollBehavior(topAppBarState)
    val state by viewModel.state.collectAsState()

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
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + AppSpacing.topBarContentSpacing,
                bottom = AppSpacing.screenBottomPadding,
                start = AppSpacing.screenHorizontalPadding,
                end = AppSpacing.screenHorizontalPadding
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.itemSpacing)
        ) {
            if (state.loading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            item {
                StatusCard(state)
            }

            item {
                Text(stringResource(R.string.carrier_section_sim), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(state.sims.size, span = { GridItemSpan(1) }) { index ->
                val sim = state.sims[index]
                SimCard(
                    sim = sim,
                    selected = sim.subId == state.selectedSubId,
                    onClick = { viewModel.selectSim(sim.subId) }
                )
            }

            item {
                Text(stringResource(R.string.carrier_section_country), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CarrierPresets.countries.forEach { country ->
                        FilterChip(
                            selected = !state.useCustomIso && state.selectedCountry == country.code,
                            onClick = { viewModel.selectCountry(country.code) },
                            label = { Text("${country.name} ${country.code}") }
                        )
                    }
                }
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
                Text(stringResource(R.string.carrier_section_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CarrierPresets.carriersFor(state.selectedCountry).forEach { carrier ->
                        FilterChip(
                            selected = state.selectedCarrier == carrier.name,
                            onClick = { viewModel.selectCarrier(carrier.name) },
                            label = { Text(carrier.name) }
                        )
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.carrier_auto_reapply), fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.carrier_auto_reapply_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.autoReapply, onCheckedChange = viewModel::setAutoReapply)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
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
                item { ResultCard(state) }
            }

            item {
                Text(
                    stringResource(R.string.carrier_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun StatusCard(state: CarrierUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(AppSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (state.serviceRunning) stringResource(R.string.carrier_service_ready) else stringResource(R.string.carrier_service_missing),
                fontWeight = FontWeight.SemiBold
            )
            if (state.error.isNotEmpty()) {
                Text(state.error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SimCard(sim: SimItem, selected: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
    ) {
        Column(Modifier.padding(AppSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SIM ${sim.slot}  ·  ${sim.displayName.ifEmpty { "subId ${sim.subId}" }}", fontWeight = FontWeight.SemiBold)
            Text("MCC/MNC ${sim.mccMnc.ifEmpty { "-" }}  ·  SIM ISO ${sim.simIso.ifEmpty { "-" }}")
            Text("覆盖 ISO ${sim.overrideIso.ifEmpty { "-" }}  ·  运行时 ${sim.runtimeIso.ifEmpty { "-" }}")
            if (sim.overrideName.isNotEmpty()) {
                Text("覆盖名称 ${sim.overrideName}")
            }
        }
    }
}

@Composable
private fun ResultCard(state: CarrierUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(AppSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.carrier_section_verify), fontWeight = FontWeight.SemiBold)
            if (state.lastMessage.isNotEmpty()) Text(state.lastMessage)
            if (state.lastStrategy.isNotEmpty()) Text("策略: ${state.lastStrategy}")
            state.lastPersistent?.let { Text(if (it) stringResource(R.string.carrier_persistent) else stringResource(R.string.carrier_non_persistent)) }
            Text("getSimCountryIso: ${state.verifiedIso.ifEmpty { "-" }}")
            Text("MCC/MNC: ${state.verifiedOperator.ifEmpty { "-" }}")
        }
    }
}
