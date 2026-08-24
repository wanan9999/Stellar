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
import roro.stellar.manager.carrier.CarrierClient
import roro.stellar.manager.carrier.CarrierKeys
import roro.stellar.manager.carrier.CarrierPresets

data class SimItem(
    val slot: Int,
    val subId: Int,
    val displayName: String,
    val mccMnc: String,
    val simIso: String,
    val overrideIso: String,
    val overrideName: String,
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
    val lastMessage: String = "",
    val lastStrategy: String = "",
    val lastPersistent: Boolean? = null,
    val verifiedIso: String = "",
    val verifiedOperator: String = "",
    val error: String = ""
)

class CarrierViewModel : ViewModel() {
    private val _state = MutableStateFlow(CarrierUiState())
    val state = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "", serviceRunning = Stellar.pingBinder()) }
            if (!Stellar.pingBinder()) {
                _state.update { it.copy(loading = false, error = "请先在「启动」页完成 ADB / Root 激活") }
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
                        verifiedOperator = peek.getString(CarrierKeys.VERIFIED_OPERATOR).orEmpty(),
                        error = if (sims.isEmpty()) "没有检测到已插入的 SIM" else ""
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
            _state.update { it.copy(error = "请选择 SIM 卡") }
            return
        }
        val iso = if (current.useCustomIso) current.customIso else current.selectedCountry
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "", lastMessage = "") }
            runCatching {
                withContext(Dispatchers.IO) {
                    CarrierClient.ensure().applyOverride(current.selectedSubId, iso, current.selectedCarrier)
                }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        loading = false,
                        lastMessage = result.getString(CarrierKeys.MESSAGE).orEmpty(),
                        lastStrategy = result.getString(CarrierKeys.STRATEGY).orEmpty(),
                        lastPersistent = if (result.containsKey(CarrierKeys.PERSISTENT)) result.getBoolean(CarrierKeys.PERSISTENT) else null,
                        verifiedIso = result.getString(CarrierKeys.VERIFIED_ISO).orEmpty(),
                        verifiedOperator = result.getString(CarrierKeys.VERIFIED_OPERATOR).orEmpty(),
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
                withContext(Dispatchers.IO) { CarrierClient.ensure().resetOverride(subId) }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        loading = false,
                        lastMessage = result.getString(CarrierKeys.MESSAGE).orEmpty(),
                        lastStrategy = result.getString(CarrierKeys.STRATEGY).orEmpty(),
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
                        verifiedOperator = peek.getString(CarrierKeys.VERIFIED_OPERATOR).orEmpty(),
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
        mccMnc = getString(CarrierKeys.MCC_MNC).orEmpty(),
        simIso = getString(CarrierKeys.ISO).orEmpty(),
        overrideIso = getString(CarrierKeys.OVERRIDE_ISO).orEmpty(),
        overrideName = getString(CarrierKeys.OVERRIDE_NAME).orEmpty(),
        runtimeIso = getString(CarrierKeys.CARRIER).orEmpty()
    )
}
