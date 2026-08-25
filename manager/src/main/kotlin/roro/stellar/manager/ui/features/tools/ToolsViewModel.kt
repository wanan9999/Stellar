package roro.stellar.manager.ui.features.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import roro.stellar.manager.application
import roro.stellar.manager.location.LocationController

data class ToolRow(
    val spec: ToolSpec,
    val subtitle: String
)

class ToolsViewModel : ViewModel() {
    private val _rows = MutableStateFlow(ToolCatalog.all.map { ToolRow(it, "") })
    val rows = _rows.asStateFlow()

    init {
        LocationController.snapshot
            .map { it.active to it.label }
            .distinctUntilChanged()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            val context = application
            val subtitles = withContext(Dispatchers.IO) {
                ToolCatalog.all.associate { it.route to it.subtitle(context) }
            }
            _rows.update {
                ToolCatalog.all.map { spec ->
                    ToolRow(spec, subtitles[spec.route].orEmpty())
                }
            }
        }
    }
}
