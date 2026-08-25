package roro.stellar.manager.ui.features.location

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import roro.stellar.manager.R
import roro.stellar.manager.compat.BuildUtils
import roro.stellar.manager.compat.ClipboardUtils
import roro.stellar.manager.location.LocationPresets
import roro.stellar.manager.location.SavedPlace
import roro.stellar.manager.ui.components.StellarDialog
import roro.stellar.manager.ui.navigation.components.FixedTopAppBar
import roro.stellar.manager.ui.theme.AppSpacing
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocationScreen(
    onBack: () -> Unit,
    viewModel: LocationViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showSave by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SavedPlace?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refresh() }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            FixedTopAppBar(
                title = stringResource(R.string.tool_location),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            LocationMap(
                modifier = Modifier.fillMaxSize(),
                lat = state.lat,
                lng = state.lng,
                zoom = state.zoom,
                cameraEpoch = state.cameraEpoch,
                onUserMoved = viewModel::onUserMoved
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::onSearchQuery,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.location_search)) }
                        )
                        if (state.searchResults.isNotEmpty()) {
                            state.searchResults.forEach { hit ->
                                Text(
                                    text = hit.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.pick(hit.lat, hit.lng, hit.name) }
                                        .padding(vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else if (state.searching) {
                            Text(
                                stringResource(R.string.location_searching),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LocationPresets.cities, key = { it.nameRes }) { city ->
                        val name = stringResource(city.nameRes)
                        FilterChip(
                            selected = state.label == name,
                            onClick = { viewModel.pick(city.lat, city.lng, name, city.zoom) },
                            label = { Text(name) }
                        )
                    }
                    items(state.favorites, key = { "${it.name}-${it.lat}-${it.lng}" }) { place ->
                        FilterChip(
                            selected = state.label == place.name,
                            onClick = {},
                            modifier = Modifier.combinedClickable(
                                onClick = { viewModel.pick(place.lat, place.lng, place.name) },
                                onLongClick = { pendingDelete = place }
                            ),
                            label = { Text(place.name) }
                        )
                    }
                }

                if (state.error.isNotEmpty()) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = AppSpacing.screenHorizontalPadding,
                        end = AppSpacing.screenHorizontalPadding,
                        top = 12.dp,
                        bottom = AppSpacing.screenBottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val status = when {
                        state.active -> stringResource(
                            R.string.location_status_active,
                            state.label.ifEmpty { stringResource(R.string.location_custom) }
                        )
                        else -> stringResource(R.string.location_disclaimer)
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.location_reduce_jump),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.reduceJump,
                            onCheckedChange = viewModel::setReduceJump
                        )
                    }
                    Text(
                        stringResource(R.string.location_reduce_jump_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        String.format(Locale.US, "%.5f, %.5f", state.lat, state.lng),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            ClipboardUtils.put(context, "${state.lat}, ${state.lng}")
                            Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                        }
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.active) {
                            Button(onClick = viewModel::stop, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.location_stop))
                            }
                        } else if (state.needsPermission) {
                            Button(
                                onClick = {
                                    val perms = buildList {
                                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        if (BuildUtils.atLeast33) add(Manifest.permission.POST_NOTIFICATIONS)
                                    }.toTypedArray()
                                    permissionLauncher.launch(perms)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.location_grant_permission))
                            }
                        } else {
                            Button(
                                onClick = viewModel::start,
                                enabled = state.serviceRunning && !state.loading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.location_start))
                            }
                        }
                        FilledTonalButton(onClick = { showSave = true }) {
                            Icon(Icons.Outlined.BookmarkAdd, contentDescription = stringResource(R.string.location_save_favorite))
                        }
                    }
                }
            }
        }
    }

    if (showSave) {
        var name by remember { mutableStateOf(state.label) }
        StellarDialog(
            onDismissRequest = { showSave = false },
            title = stringResource(R.string.location_save_favorite),
            confirmEnabled = name.isNotBlank(),
            onConfirm = {
                viewModel.saveFavorite(name)
                showSave = false
            }
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.location_favorite_name)) }
            )
        }
    }

    pendingDelete?.let { place ->
        StellarDialog(
            onDismissRequest = { pendingDelete = null },
            title = stringResource(R.string.location_delete_favorite),
            onConfirm = {
                viewModel.removeFavorite(place)
                pendingDelete = null
            }
        ) {
            Text(place.name)
        }
    }
}
