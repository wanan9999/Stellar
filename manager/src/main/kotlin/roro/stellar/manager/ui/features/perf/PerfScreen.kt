package roro.stellar.manager.ui.features.perf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import roro.stellar.manager.R
import roro.stellar.manager.perf.PerfApp
import roro.stellar.manager.perf.PerfCollector
import roro.stellar.manager.perf.PerfKind
import roro.stellar.manager.perf.PerfSort
import roro.stellar.manager.perf.formatBytes
import roro.stellar.manager.perf.formatKb
import roro.stellar.manager.perf.formatPercent
import roro.stellar.manager.perf.formatSpeed
import roro.stellar.manager.ui.components.StellarSegmentedSelector
import roro.stellar.manager.ui.navigation.components.FixedTopAppBar
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing

private val CpuCol = 52.dp
private val RamCol = 64.dp
private val NetCol = 88.dp

@Composable
fun PerfScreen(
    onBack: () -> Unit,
    viewModel: PerfViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.start()
                Lifecycle.Event.ON_PAUSE -> viewModel.stop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.start()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stop()
        }
    }

    val rows = remember(state.apps, state.sort, state.kind) {
        val filtered = when (state.kind) {
            PerfKind.ALL -> state.apps
            PerfKind.USER -> state.apps.filter { !it.system }
            PerfKind.SYSTEM -> state.apps.filter { it.system }
        }
        when (state.sort) {
            PerfSort.CPU -> filtered.sortedByDescending { it.cpuPercent }
            PerfSort.NET -> filtered.sortedByDescending { it.downBytesPerSec + it.upBytesPerSec }
            PerfSort.RAM -> filtered
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            FixedTopAppBar(
                title = stringResource(R.string.tool_perf),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + AppSpacing.topBarContentSpacing,
                bottom = AppSpacing.screenBottomPadding,
                start = AppSpacing.screenHorizontalPadding,
                end = AppSpacing.screenHorizontalPadding
            )
        ) {
            item(key = "gauges") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Dial(
                        progress = state.gauges.cpuPercent / 100f,
                        value = formatPercent(state.gauges.cpuPercent),
                        label = stringResource(R.string.perf_cpu),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Dial(
                        progress = state.gauges.ramPercent / 100f,
                        value = formatPercent(state.gauges.ramPercent),
                        label = stringResource(R.string.perf_ram),
                        caption = "${formatBytes(state.gauges.ramUsedBytes)} / ${formatBytes(state.gauges.ramTotalBytes)}",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Dial(
                        progress = state.gauges.netProgress,
                        value = formatSpeed(state.gauges.downBytesPerSec),
                        label = stringResource(R.string.perf_net),
                        caption = stringResource(R.string.perf_net_up, formatSpeed(state.gauges.upBytesPerSec)),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            item(key = "kind") {
                val labels = mapOf(
                    PerfKind.ALL to stringResource(R.string.perf_filter_all),
                    PerfKind.USER to stringResource(R.string.perf_filter_user),
                    PerfKind.SYSTEM to stringResource(R.string.perf_filter_system)
                )
                StellarSegmentedSelector(
                    items = listOf(PerfKind.ALL, PerfKind.USER, PerfKind.SYSTEM),
                    selectedItem = state.kind,
                    onItemSelected = viewModel::setKind,
                    itemLabel = { labels[it] ?: "" },
                    modifier = Modifier.padding(top = AppSpacing.sectionSpacing),
                    itemHeight = AppSpacing.selectorItemHeightSmall
                )
            }

            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.perf_running, rows.size),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SortHead(stringResource(R.string.perf_cpu), state.sort == PerfSort.CPU, CpuCol) {
                        viewModel.setSort(PerfSort.CPU)
                    }
                    SortHead(stringResource(R.string.perf_ram), state.sort == PerfSort.RAM, RamCol) {
                        viewModel.setSort(PerfSort.RAM)
                    }
                    SortHead(stringResource(R.string.perf_net), state.sort == PerfSort.NET, NetCol) {
                        viewModel.setSort(PerfSort.NET)
                    }
                }
            }

            if (state.error.isNotEmpty() && rows.isEmpty()) {
                item(key = "error") {
                    Text(
                        state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else if (state.loaded && rows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.perf_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            items(rows, key = { it.packageName }, contentType = { "app" }) { app ->
                AppRow(app)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SortHead(text: String, selected: Boolean, width: Dp, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .width(width)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.End
    )
}

@Composable
private fun Dial(
    progress: Float,
    value: String,
    label: String,
    color: Color,
    caption: String? = null
) {
    val sweep = 240f
    val start = 150f
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "dial"
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                drawArc(track, start, sweep, false, style = stroke)
                drawArc(color, start, sweep * animated, false, style = stroke)
            }
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AppRow(app: PerfApp) {
    var expanded by remember(app.packageName) { mutableStateOf(false) }
    val canExpand = app.members.size > 1
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (canExpand) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { expanded = !expanded }
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(app.packageName)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppSpacing.iconTextSpacing)
            ) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                formatPercent(app.cpuPercent),
                modifier = Modifier.width(CpuCol),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.End
            )
            Text(
                formatKb(app.ramKb),
                modifier = Modifier.width(RamCol),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End
            )
            Column(Modifier.width(NetCol), horizontalAlignment = Alignment.End) {
                if (app.netKnown) {
                    Text("↓${formatSpeed(app.downBytesPerSec)}", style = MaterialTheme.typography.bodySmall)
                    Text("↑${formatSpeed(app.upBytesPerSec)}", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("—", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        AnimatedVisibility(visible = canExpand && expanded) {
            Column(Modifier.padding(start = 52.dp, bottom = 8.dp)) {
                app.members.forEach { proc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            proc.name.substringAfterLast('/'),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            formatPercent(proc.cpuPercent),
                            modifier = Modifier.width(CpuCol),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End
                        )
                        Text(
                            formatKb(proc.ramKb),
                            modifier = Modifier.width(RamCol),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End
                        )
                        Box(Modifier.width(NetCol))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            PerfCollector.icon(context, packageName)?.asImageBitmap()
        }
    }
    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(AppSpacing.iconContainerSize)
                .clip(AppShape.shapes.iconSmall),
            contentScale = ContentScale.Fit
        )
    } else {
        Icon(
            Icons.Outlined.Android,
            contentDescription = null,
            modifier = Modifier.size(AppSpacing.iconContainerSize),
            tint = MaterialTheme.colorScheme.outline
        )
    }
}
