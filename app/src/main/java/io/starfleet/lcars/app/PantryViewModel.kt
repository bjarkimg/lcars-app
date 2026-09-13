package io.starfleet.lcars.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.starfleet.lcars.app.data.BAYS
import io.starfleet.lcars.app.data.PantryApi
import io.starfleet.lcars.app.data.PantryItem
import io.starfleet.lcars.app.data.bayLabel
import io.starfleet.lcars.app.scan.normalizeRetailBarcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PantryUiState(
    val location: String = "",
    val mode: String = "in",
    val items: List<PantryItem> = emptyList(),
    val lastItem: PantryItem? = null,
    val status: String = "VELDU STAÐSETNINGU",
    val online: Boolean = false,
    val cameraOn: Boolean = true,
    val holding: Boolean = false,
    val holdLeftSec: Int = 0,
    val manualName: String = "",
)

class PantryViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("lcars_pantry", 0)
    private val api = PantryApi()

    private val _state = MutableStateFlow(
        PantryUiState(location = prefs.getString(KEY_LOCATION, "") ?: ""),
    )
    val state: StateFlow<PantryUiState> = _state

    private var lastCode = ""
    private var inFlight = false
    private var holdJob: Job? = null

    init {
        val saved = _state.value.location
        if (saved.isNotBlank() && BAYS.any { it.id == saved }) {
            _state.update { it.copy(status = "STAÐSETNING: ${bayLabel(saved)} · READY") }
        }
        refresh()
    }

    fun pickLocation(id: String) {
        prefs.edit().putString(KEY_LOCATION, id).apply()
        _state.update { it.copy(location = id, status = "STAÐSETNING: ${bayLabel(id)} · READY") }
    }

    fun setMode(mode: String) {
        _state.update { it.copy(mode = mode) }
    }

    fun setManualName(value: String) {
        _state.update { it.copy(manualName = value) }
    }

    fun setCameraOn(on: Boolean) {
        _state.update { it.copy(cameraOn = on) }
    }

    fun onBarcode(code: String) {
        val clean = normalizeRetailBarcode(code) ?: return
        if (_state.value.holding || inFlight) return
        if (clean == lastCode) return
        scan(clean)
    }

    fun scan(code: String) {
        val location = _state.value.location
        if (location.isBlank()) {
            _state.update { it.copy(status = "VELDU STAÐSETNINGU FYRST") }
            return
        }
        if (inFlight || _state.value.holding) return
        inFlight = true
        lastCode = code
        viewModelScope.launch {
            _state.update { it.copy(status = "LOGGING $code") }
            val result = withContext(Dispatchers.IO) {
                runCatching { api.scan(code, location, _state.value.mode) }
            }
            result.fold(
                onSuccess = { scan ->
                    if (!scan.success || scan.item == null) {
                        lastCode = ""
                        _state.update { it.copy(status = scan.error ?: "SCAN REJECTED") }
                    } else {
                        upsert(scan.item)
                        _state.update {
                            it.copy(
                                lastItem = scan.item,
                                status = "LOGGED ${scan.item.qty} ${scan.item.unit} ${scan.item.name} · ${bayLabel(scan.item.location)}",
                            )
                        }
                        startHold()
                    }
                },
                onFailure = { err ->
                    lastCode = ""
                    _state.update { it.copy(online = false, status = "LINK OFFLINE · ${err.message}") }
                },
            )
            inFlight = false
        }
    }

    private fun startHold() {
        holdJob?.cancel()
        _state.update { it.copy(holding = true, holdLeftSec = HOLD_SEC) }
        holdJob = viewModelScope.launch {
            val total = HOLD_SEC
            for (left in total downTo 1) {
                _state.update { it.copy(holdLeftSec = left, status = "HOLD ${left}s · MOVE THE PACK") }
                delay(1000)
            }
            lastCode = ""
            _state.update {
                it.copy(
                    holding = false,
                    holdLeftSec = 0,
                    status = "READY · ${bayLabel(it.location)}",
                )
            }
        }
    }

    fun commitManual() {
        val name = _state.value.manualName.trim()
        val location = _state.value.location
        if (location.isBlank()) {
            _state.update { it.copy(status = "VELDU STAÐSETNINGU FYRST") }
            return
        }
        if (name.isBlank()) {
            _state.update { it.copy(status = "NAME REQUIRED") }
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.manual(name, location) }
            }
            result.fold(
                onSuccess = { scan ->
                    if (scan.item != null) {
                        upsert(scan.item)
                        _state.update {
                            it.copy(
                                lastItem = scan.item,
                                manualName = "",
                                status = "LOGGED ${scan.item.name} · ${bayLabel(scan.item.location)}",
                            )
                        }
                    } else {
                        _state.update { it.copy(status = scan.error ?: "ENTRY REJECTED") }
                    }
                },
                onFailure = { err ->
                    _state.update { it.copy(status = "LINK OFFLINE · ${err.message}") }
                },
            )
        }
    }

    fun bump(item: PantryItem, delta: Double) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.adjust(item.id, delta) }
            }.getOrNull() ?: return@launch
            result.item?.let { upsert(it) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.snapshot() }
            }
            result.fold(
                onSuccess = { items ->
                    _state.update { it.copy(items = sortItems(items), online = true) }
                },
                onFailure = {
                    _state.update { it.copy(online = false, status = "PROVISIONS LINK OFFLINE") }
                },
            )
        }
    }

    private fun upsert(item: PantryItem) {
        _state.update { current ->
            current.copy(items = sortItems(listOf(item) + current.items.filter { it.id != item.id }))
        }
    }

    private fun sortItems(items: List<PantryItem>): List<PantryItem> =
        items.sortedByDescending { it.lastScanAt ?: "" }

    companion object {
        private const val KEY_LOCATION = "location"
        private const val HOLD_SEC = 4
    }
}
