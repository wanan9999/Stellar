package roro.stellar.manager.ui.features.carrier

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import roro.stellar.Stellar
import roro.stellar.manager.R
import roro.stellar.manager.application
import roro.stellar.manager.carrier.CarrierController
import roro.stellar.manager.carrier.CarrierPresets

internal const val CUSTOM_COUNTRY_CODE = "_"

data class SimItem(
    val slot: Int,
    val subId: Int,
    val displayName: String,
    val nativeIso: String,
    val overlayIso: String
)

data class CarrierUiState(
    val serviceRunning: Boolean = false,
    val loading: Boolean = false,
    val sims: List<SimItem> = emptyList(),
    val selectedSubId: Int = -1,
    val selectedCountry: String = "JP",
    val selectedCarrier: String = "NTT docomo",
    val customIso: String = "",
    val useCustomIso: Boolean = false,
    val autoReapply: Boolean = true,
    val error: String = ""
)

class CarrierViewModel : ViewModel() {
    private val _state = MutableStateFlow(CarrierUiState())
    val state = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val running = Stellar.pingBinder()
            _state.update { it.copy(loading = true, error = "", serviceRunning = running) }
            if (!running) {
                _state.update {
                    it.copy(loading = false, error = application.getString(R.string.carrier_service_missing))
                }
                return@launch
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    CarrierController.snapshots().map {
                        SimItem(it.slot, it.subId, it.displayName, it.nativeIso, it.overlayIso)
                    }
                }
            }.onSuccess { sims ->
                val selected = _state.value.selectedSubId.takeIf { id -> sims.any { it.subId == id } }
                    ?: sims.firstOrNull()?.subId
                    ?: -1
                _state.update {
                    it.copy(
                        loading = false,
                        sims = sims,
                        selectedSubId = selected,
                        autoReapply = CarrierController.autoReapply(),
                        error = if (sims.isEmpty()) application.getString(R.string.carrier_no_sim) else ""
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun selectSim(subId: Int) {
        _state.update { it.copy(selectedSubId = subId) }
    }

    fun selectCountry(code: String) {
        if (code == CUSTOM_COUNTRY_CODE) {
            _state.update { it.copy(useCustomIso = true, selectedCarrier = "") }
            return
        }
        val firstCarrier = CarrierPresets.carriersFor(code).firstOrNull()?.name.orEmpty()
        _state.update {
            it.copy(
                selectedCountry = code,
                selectedCarrier = firstCarrier,
                useCustomIso = false
            )
        }
    }

    fun selectCarrier(name: String) {
        _state.update { it.copy(selectedCarrier = name) }
    }

    fun setCustomIso(value: String) {
        _state.update { it.copy(customIso = value, useCustomIso = true) }
    }

    fun setAutoReapply(enabled: Boolean) {
        _state.update { it.copy(autoReapply = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { CarrierController.setAutoReapply(enabled = enabled) }
        }
    }

    fun apply() {
        val current = _state.value
        if (current.selectedSubId <= 0) {
            _state.update { it.copy(error = application.getString(R.string.carrier_select_sim)) }
            return
        }
        val iso = if (current.useCustomIso) current.customIso else current.selectedCountry
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "") }
            runCatching {
                withContext(Dispatchers.IO) {
                    CarrierController.apply(current.selectedSubId, iso, current.selectedCarrier)
                }
            }.onSuccess {
                refresh()
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun reset() {
        val subId = _state.value.selectedSubId
        if (subId <= 0) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "") }
            runCatching {
                withContext(Dispatchers.IO) {
                    CarrierController.reset(subId)
                }
            }.onSuccess {
                refresh()
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }
}
