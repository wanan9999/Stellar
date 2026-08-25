package roro.stellar.manager.ui.features.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import roro.stellar.Stellar
import roro.stellar.manager.R
import roro.stellar.manager.application
import roro.stellar.manager.location.LocationController
import roro.stellar.manager.location.LocationSearch
import roro.stellar.manager.location.SavedPlace
import roro.stellar.manager.location.SearchHit

data class LocationUiState(
    val serviceRunning: Boolean = false,
    val ready: Boolean = false,
    val needsPermission: Boolean = false,
    val active: Boolean = false,
    val lat: Double = 39.9042,
    val lng: Double = 116.4074,
    val label: String = "",
    val zoom: Double = 12.0,
    val cameraEpoch: Int = 0,
    val error: String = "",
    val loading: Boolean = false,
    val favorites: List<SavedPlace> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SearchHit> = emptyList(),
    val searching: Boolean = false,
    val reduceJump: Boolean = false
)

@OptIn(FlowPreview::class)
class LocationViewModel : ViewModel() {
    private val _state = MutableStateFlow(LocationUiState())
    val state = _state.asStateFlow()
    private val query = MutableStateFlow("")
    private val moves = MutableStateFlow<Move?>(null)
    private var searchJob: Job? = null

    init {
        LocationController.snapshot
            .onEach { snap ->
                _state.update {
                    it.copy(
                        active = snap.active,
                        lat = snap.lat,
                        lng = snap.lng,
                        label = snap.label,
                        zoom = snap.zoom,
                        reduceJump = snap.reduceJump,
                        error = if (snap.error.isNotEmpty()) mapError(snap.error) else it.error
                    )
                }
            }
            .launchIn(viewModelScope)
        moves
            .debounce(250)
            .onEach { move ->
                if (move != null) {
                    LocationController.remember(move.lat, move.lng, _state.value.label, move.zoom)
                }
            }
            .launchIn(viewModelScope)
        query
            .debounce(400)
            .map { it.trim() }
            .distinctUntilChanged()
            .filter { it.length >= 2 }
            .onEach { search(it) }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            val running = Stellar.pingBinder()
            _state.update {
                it.copy(
                    serviceRunning = running,
                    favorites = LocationController.favorites(),
                    error = if (running) it.error else application.getString(R.string.tools_service_missing)
                )
            }
            if (!running) {
                _state.update { it.copy(ready = false, loading = false) }
                return@launch
            }
            _state.update { it.copy(loading = true) }
            runCatching {
                withContext(Dispatchers.IO) { LocationController.prepare() }
            }.onSuccess { ready ->
                val injectError = LocationController.snapshot.value.error
                _state.update {
                    it.copy(
                        loading = false,
                        ready = ready,
                        needsPermission = !LocationController.hasLocationPermission(),
                        reduceJump = LocationController.snapshot.value.reduceJump,
                        error = when {
                            injectError.isNotEmpty() -> mapError(injectError)
                            ready -> ""
                            else -> application.getString(R.string.location_permission_needed)
                        }
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        loading = false,
                        ready = false,
                        error = when (e.message) {
                            "service" -> application.getString(R.string.tools_service_missing)
                            "mock_app" -> application.getString(R.string.location_mock_failed)
                            else -> e.message ?: e.javaClass.simpleName
                        }
                    )
                }
            }
        }
    }

    fun onSearchQuery(value: String) {
        query.value = value
        _state.update {
            it.copy(
                searchQuery = value,
                searchResults = if (value.isBlank()) emptyList() else it.searchResults
            )
        }
        if (value.trim().length < 2) {
            _state.update { it.copy(searching = false, searchResults = emptyList()) }
        }
    }

    fun pick(lat: Double, lng: Double, label: String, zoom: Double = 15.0) {
        LocationController.remember(lat, lng, label, zoom)
        _state.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                cameraEpoch = it.cameraEpoch + 1
            )
        }
    }

    fun onUserMoved(lat: Double, lng: Double, zoom: Double) {
        _state.update { it.copy(lat = lat, lng = lng, zoom = zoom) }
        moves.value = Move(lat, lng, zoom)
    }

    fun start() {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "") }
            runCatching {
                withContext(Dispatchers.IO) {
                    LocationController.start(current.lat, current.lng, current.label, current.zoom)
                }
            }.onSuccess {
                refresh()
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = when (e.message) {
                            "permission" -> application.getString(R.string.location_permission_needed)
                            "providers", "cmd_location", "inject", "timeout" ->
                                application.getString(R.string.location_inject_failed)
                            else -> e.message ?: e.javaClass.simpleName
                        }
                    )
                }
            }
        }
    }

    fun stop() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { LocationController.stop() }
        }
    }

    fun saveFavorite(name: String) {
        val current = _state.value
        LocationController.saveFavorite(name, current.lat, current.lng)
        _state.update { it.copy(favorites = LocationController.favorites()) }
    }

    fun removeFavorite(place: SavedPlace) {
        LocationController.removeFavorite(place)
        _state.update { it.copy(favorites = LocationController.favorites()) }
    }

    fun setReduceJump(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { LocationController.setReduceJump(enabled) }
        }
    }

    private fun mapError(code: String): String = when (code) {
        "service" -> application.getString(R.string.tools_service_missing)
        "mock_app" -> application.getString(R.string.location_mock_failed)
        "permission" -> application.getString(R.string.location_permission_needed)
        else -> application.getString(R.string.location_inject_failed)
    }

    private fun search(text: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            val hits = runCatching {
                withContext(Dispatchers.IO) { LocationSearch.query(text) }
            }.getOrDefault(emptyList())
            if (query.value.trim() == text) {
                _state.update { it.copy(searching = false, searchResults = hits) }
            }
        }
    }

    private data class Move(val lat: Double, val lng: Double, val zoom: Double)
}
