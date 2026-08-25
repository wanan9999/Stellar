package roro.stellar.manager.location

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Properties

internal class LocationStore(context: Context) {
    private val file = File(context.filesDir, "location_mock.properties")
    private val gson = Gson()

    fun load(): StoredLocation? {
        if (!file.exists()) return null
        return try {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            val lat = props.getProperty("lat")?.toDoubleOrNull() ?: return null
            val lng = props.getProperty("lng")?.toDoubleOrNull() ?: return null
            StoredLocation(
                lat = lat,
                lng = lng,
                label = props.getProperty("label").orEmpty(),
                zoom = props.getProperty("zoom")?.toDoubleOrNull() ?: DEFAULT_ZOOM,
                running = props.getProperty("running").toBoolean()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(location: StoredLocation) {
        persist(location, favorites())
    }

    fun favorites(): List<SavedPlace> {
        if (!file.exists()) return emptyList()
        return try {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            val json = props.getProperty("favorites").orEmpty()
            if (json.isEmpty()) emptyList()
            else gson.fromJson(json, object : TypeToken<List<SavedPlace>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveFavorites(places: List<SavedPlace>) {
        persist(load(), places.take(12))
    }

    private fun persist(location: StoredLocation?, places: List<SavedPlace>) {
        file.parentFile?.mkdirs()
        val current = if (file.exists()) {
            Properties().also { file.inputStream().use { stream -> it.load(stream) } }
        } else {
            Properties()
        }
        if (location != null) {
            current["lat"] = location.lat.toString()
            current["lng"] = location.lng.toString()
            current["label"] = location.label
            current["zoom"] = location.zoom.toString()
            current["running"] = location.running.toString()
        }
        current["favorites"] = gson.toJson(places)
        file.outputStream().use { current.store(it, "stellar-location") }
    }

    fun reduceJump(): Boolean = props()["reduceJump"] == "true"

    fun setReduceJump(enabled: Boolean) {
        val current = props()
        current["reduceJump"] = enabled.toString()
        writeAll(current)
    }

    fun saveScanBackup(wifi: String, ble: String) {
        val current = props()
        current["wifiScanWas"] = wifi
        current["bleScanWas"] = ble
        writeAll(current)
    }

    fun scanBackup(): Pair<String, String>? {
        val current = props()
        val wifi = current.getProperty("wifiScanWas") ?: return null
        val ble = current.getProperty("bleScanWas") ?: return null
        return wifi to ble
    }

    fun clearScanBackup() {
        val current = props()
        current.remove("wifiScanWas")
        current.remove("bleScanWas")
        writeAll(current)
    }

    private fun props(): Properties {
        if (!file.exists()) return Properties()
        return Properties().also { file.inputStream().use { stream -> it.load(stream) } }
    }

    private fun writeAll(props: Properties) {
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, "stellar-location") }
    }
}
