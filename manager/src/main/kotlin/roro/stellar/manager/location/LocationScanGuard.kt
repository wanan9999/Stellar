package roro.stellar.manager.location

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import roro.stellar.Stellar
import roro.stellar.manager.application

internal object LocationScanGuard {
    private const val WIFI = "wifi_scan_always_enabled"
    private const val BLE = "ble_scan_always_enabled"

    @Volatile private var applied = false

    fun apply() {
        if (applied) return
        val store = LocationStore(application)
        store.saveScanBackup(read(WIFI), read(BLE))
        write(WIFI, "0")
        write(BLE, "0")
        applied = true
    }

    fun restore() {
        val store = LocationStore(application)
        val backup = store.scanBackup() ?: run {
            applied = false
            return
        }
        write(WIFI, backup.first)
        write(BLE, backup.second)
        store.clearScanBackup()
        applied = false
    }

    private fun read(key: String): String {
        runCatching {
            Settings.Global.getString(application.contentResolver, key)
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        if (!Stellar.pingBinder()) return "1"
        return MockLocationOps.exec("settings get global $key").text.trim().ifBlank { "1" }.let {
            if (it.equals("null", true)) "1" else it
        }
    }

    private fun write(key: String, value: String) {
        if (application.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            Settings.Global.putString(application.contentResolver, key, value)
            return
        }
        if (Stellar.pingBinder()) {
            MockLocationOps.exec("settings put global $key $value")
        }
    }
}
