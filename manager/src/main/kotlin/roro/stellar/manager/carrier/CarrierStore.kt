package roro.stellar.manager.carrier

import android.content.Context
import java.io.File
import java.util.Properties

internal data class StoredOverlay(
    val subId: Int,
    val iso: String,
    val name: String,
    val autoReapply: Boolean
)

internal class CarrierStore(context: Context) {
    private val file = File(context.filesDir, "carrier_overlay.properties")
    private val tokenFile = File(context.filesDir, "carrier_apply.token")

    fun writeToken(token: String) {
        file.parentFile?.mkdirs()
        tokenFile.writeText(token)
    }

    fun readToken(): String? = runCatching { tokenFile.readText() }.getOrNull()

    fun clearToken() {
        if (tokenFile.exists()) tokenFile.delete()
    }

    fun load(): StoredOverlay? {
        if (!file.exists()) return null
        return try {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            val subId = props.getProperty("subId")?.toIntOrNull() ?: return null
            StoredOverlay(
                subId = subId,
                iso = props.getProperty("iso").orEmpty(),
                name = props.getProperty("name").orEmpty(),
                autoReapply = props.getProperty("autoReapply", "true").toBoolean()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(overlay: StoredOverlay) {
        file.parentFile?.mkdirs()
        val props = Properties()
        props["subId"] = overlay.subId.toString()
        props["iso"] = overlay.iso
        props["name"] = overlay.name
        props["autoReapply"] = overlay.autoReapply.toString()
        file.outputStream().use { props.store(it, "stellar-carrier") }
    }

    fun setAutoReapply(enabled: Boolean) {
        val current = load() ?: return
        save(current.copy(autoReapply = enabled))
    }

    fun clear() {
        if (file.exists()) file.delete()
    }
}
