package roro.stellar.manager.ui.features.perf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import roro.stellar.Stellar
import roro.stellar.manager.R
import roro.stellar.manager.application
import roro.stellar.manager.perf.PerfApp
import roro.stellar.manager.perf.PerfCollector
import roro.stellar.manager.perf.PerfGauges
import roro.stellar.manager.perf.PerfKind
import roro.stellar.manager.perf.PerfSort

data class PerfUiState(
    val serviceRunning: Boolean = false,
    val gauges: PerfGauges = PerfGauges(),
    val apps: List<PerfApp> = emptyList(),
    val sort: PerfSort = PerfSort.RAM,
    val kind: PerfKind = PerfKind.ALL,
    val loaded: Boolean = false,
    val error: String = ""
)

class PerfViewModel : ViewModel() {
    private val _state = MutableStateFlow(PerfUiState())
    val state = _state.asStateFlow()
    private val dumpLock = Mutex()
    private var loop: Job? = null

    fun start() {
        if (loop?.isActive == true) return
        loop = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                refresh()
                delay(1000)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
    }

    fun setSort(sort: PerfSort) {
        _state.update { it.copy(sort = sort) }
    }

    fun setKind(kind: PerfKind) {
        _state.update { it.copy(kind = kind) }
    }

    private suspend fun refresh() {
        if (!dumpLock.tryLock()) return
        try {
            val running = Stellar.pingBinder()
            if (!running) {
                _state.update {
                    it.copy(
                        serviceRunning = false,
                        apps = emptyList(),
                        loaded = true,
                        error = application.getString(R.string.tools_service_missing)
                    )
                }
                return
            }
            runCatching { PerfCollector.snapshot(application) }
                .onSuccess { snap ->
                    _state.update {
                        it.copy(
                            serviceRunning = true,
                            gauges = snap.gauges,
                            apps = snap.apps,
                            loaded = true,
                            error = ""
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update {
                        it.copy(
                            serviceRunning = true,
                            loaded = true,
                            error = e.message ?: e.javaClass.simpleName
                        )
                    }
                }
        } finally {
            dumpLock.unlock()
        }
    }
}
