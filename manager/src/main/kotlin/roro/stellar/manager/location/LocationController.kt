package roro.stellar.manager.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import roro.stellar.Stellar
import roro.stellar.manager.application
import roro.stellar.manager.compat.BuildUtils
import roro.stellar.manager.util.UserHandleCompat

object LocationController {
    private val store = LocationStore(application)
    private val _snapshot = MutableStateFlow(initialSnapshot())
    val snapshot = _snapshot.asStateFlow()

    fun favorites(): List<SavedPlace> = store.favorites()

    fun prepare(): Boolean {
        if (!Stellar.pingBinder()) return false
        grant(Manifest.permission.ACCESS_FINE_LOCATION)
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (BuildUtils.atLeast33) grant(Manifest.permission.POST_NOTIFICATIONS)
        MockLocationOps.ensureSelected()
        _snapshot.update { it.copy(mockAppReady = true, reduceJump = store.reduceJump()) }
        return hasLocationPermission()
    }

    fun isReady(): Boolean = Stellar.pingBinder() && hasLocationPermission() && runCatching {
        MockLocationOps.isSelected()
    }.getOrDefault(false)

    fun hasLocationPermission(context: Context = application): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun remember(lat: Double, lng: Double, label: String, zoom: Double = _snapshot.value.zoom) {
        persist(_snapshot.value.copy(lat = lat, lng = lng, label = label, zoom = zoom))
    }

    fun start(lat: Double, lng: Double, label: String, zoom: Double = _snapshot.value.zoom) {
        if (!prepare()) error("permission")
        persist(
            _snapshot.value.copy(
                active = false,
                lat = lat,
                lng = lng,
                label = label,
                zoom = zoom,
                error = ""
            )
        )
        store.save(StoredLocation(lat, lng, label, zoom, running = true))
        ContextCompat.startForegroundService(
            application,
            Intent(application, LocationMockService::class.java)
        )
    }

    fun markStarted() {
        persist(_snapshot.value.copy(active = true, error = ""))
        if (_snapshot.value.reduceJump) LocationScanGuard.apply()
    }

    fun fail(message: String) {
        persist(_snapshot.value.copy(active = false, error = message))
        LocationScanGuard.restore()
    }

    fun stop() {
        markStopped()
        LocationScanGuard.restore()
        ContextCompat.startForegroundService(
            application,
            Intent(application, LocationMockService::class.java).setAction(LocationMockService.ACTION_STOP)
        )
    }

    fun markStopped() {
        persist(_snapshot.value.copy(active = false))
    }

    fun setReduceJump(enabled: Boolean) {
        store.setReduceJump(enabled)
        persist(_snapshot.value.copy(reduceJump = enabled))
        if (!Stellar.pingBinder()) return
        if (enabled && _snapshot.value.active) LocationScanGuard.apply()
        if (!enabled) LocationScanGuard.restore()
    }

    fun shouldRun(): Boolean =
        _snapshot.value.active || store.load()?.running == true

    fun restoreIfRunning() {
        val stored = store.load() ?: return
        if (!stored.running) return
        persist(
            _snapshot.value.copy(
                active = true,
                lat = stored.lat,
                lng = stored.lng,
                label = stored.label,
                zoom = stored.zoom,
                error = ""
            )
        )
    }

    fun saveFavorite(name: String, lat: Double, lng: Double) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val next = listOf(SavedPlace(trimmed, lat, lng)) +
            favorites().filterNot { similar(it, lat, lng) || it.name == trimmed }
        store.saveFavorites(next)
    }

    fun removeFavorite(place: SavedPlace) {
        store.saveFavorites(favorites().filterNot { it == place })
    }

    private fun persist(next: LocationSnapshot) {
        _snapshot.value = next
        store.save(StoredLocation(next.lat, next.lng, next.label, next.zoom, next.active))
        store.setReduceJump(next.reduceJump)
    }

    private fun initialSnapshot(): LocationSnapshot {
        val stored = store.load()
        return LocationSnapshot(
            lat = stored?.lat ?: DEFAULT_LAT,
            lng = stored?.lng ?: DEFAULT_LNG,
            label = stored?.label.orEmpty(),
            zoom = stored?.zoom ?: DEFAULT_ZOOM,
            reduceJump = store.reduceJump()
        )
    }

    private fun grant(permission: String) {
        if (application.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return
        runCatching {
            Stellar.grantRuntimePermission(
                application.packageName,
                permission,
                UserHandleCompat.myUserId()
            )
        }
    }

    private fun similar(place: SavedPlace, lat: Double, lng: Double): Boolean =
        kotlin.math.abs(place.lat - lat) < 0.00015 && kotlin.math.abs(place.lng - lng) < 0.00015
}
