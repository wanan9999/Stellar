package roro.stellar.manager.ui.features.carrier

import android.os.Bundle
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
import roro.stellar.manager.carrier.CarrierClient
import roro.stellar.manager.carrier.CarrierInstrument
import roro.stellar.manager.carrier.CarrierKeys
import roro.stellar.manager.carrier.CarrierPresets

internal const val CUSTOM_COUNTRY_CODE = "_"

data class SimItem(
    val slot: Int,
    val subId: Int,
    val displayName: String,
    val simIso: String,
    val overrideIso: String,
    val runtimeIso: String
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
    val lastPersistent: Boolean? = null,
    val verifiedIso: String = "",
    val error: String = ""
)

class CarrierViewModel : ViewModel() {
    private val _state = MutableStateFlow(CarrierUiState())
    val state = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "", serviceRunning = Stellar.pingBinder()) }
            if (!Stellar.pingBinder()) {
                _state.update {
                    it.copy(loading = false, error = application.getString(R.string.carrier_service_missing))
                }
                return@launch
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val service = CarrierClient.ensure()
                    val sims = service.listSims().map { it.toSimItem() }
                    val selected = _state.value.selectedSubId.takeIf { id -> sims.any { it.subId == id } }
                        ?: sims.firstOrNull()?.subId
                        ?: -1
                    val peek = if (selected > 0) service.peek(selected) else Bundle()
                    Triple(sims, selected, peek)
                }
            }.onSuccess { (sims, selected, peek) ->
                _state.update {
                    it.copy(
                        loading = false,
                        sims = sims,
                        selectedSubId = selected,
                        autoReapply = peek.getBoolean(CarrierKeys.AUTO_REAPPLY, true),
                        verifiedIso = peek.getString(CarrierKeys.VERIFIED_ISO).orEmpty(),
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
        refreshPeek(subId)
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
            runCatching { CarrierClient.ensure().setAutoReapply(enabled) }
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
                    val service = CarrierClient.ensure()
                    val remote = service.applyOverride(current.selectedSubId, iso, current.selectedCarrier)
                    CarrierInstrument.completeIfNeeded(
                        remote,
                        current.selectedSubId,
                        iso,
                        current.selectedCarrier,
                        reset = false
                    )
                }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        loading = false,
                        lastPersistent = if (result.containsKey(CarrierKeys.PERSISTENT)) {
                            result.getBoolean(CarrierKeys.PERSISTENT)
                        } else {
                            null
                        },
                        verifiedIso = result.getString(CarrierKeys.VERIFIED_ISO).orEmpty(),
                        error = if (result.getBoolean(CarrierKeys.OK)) "" else result.getString(CarrierKeys.MESSAGE).orEmpty()
                    )
                }
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
                    val remote = CarrierClient.ensure().resetOverride(subId)
                    CarrierInstrument.completeIfNeeded(remote, subId, null, null, reset = true)
                }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        loading = false,
                        lastPersistent = null,
                        verifiedIso = result.getString(CarrierKeys.VERIFIED_ISO).orEmpty(),
                        error = if (result.getBoolean(CarrierKeys.OK)) "" else result.getString(CarrierKeys.MESSAGE).orEmpty()
                    )
                }
                refresh()
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    private fun refreshPeek(subId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val peek = CarrierClient.ensure().peek(subId)
                _state.update {
                    it.copy(
                        verifiedIso = peek.getString(CarrierKeys.VERIFIED_ISO).orEmpty(),
                        autoReapply = peek.getBoolean(CarrierKeys.AUTO_REAPPLY, it.autoReapply)
                    )
                }
            }
        }
    }

    private fun Bundle.toSimItem() = SimItem(
        slot = getInt(CarrierKeys.SLOT),
        subId = getInt(CarrierKeys.SUB_ID),
        displayName = getString(CarrierKeys.DISPLAY_NAME).orEmpty(),
        simIso = getString(CarrierKeys.ISO).orEmpty(),
        overrideIso = getString(CarrierKeys.OVERRIDE_ISO).orEmpty(),
        runtimeIso = getString(CarrierKeys.CARRIER).orEmpty()
    )
}
