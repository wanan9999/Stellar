package roro.stellar.manager.perf

import java.util.Locale

data class PerfGauges(
    val cpuPercent: Float = 0f,
    val ramUsedBytes: Long = 0L,
    val ramTotalBytes: Long = 0L,
    val downBytesPerSec: Long = 0L,
    val upBytesPerSec: Long = 0L
) {
    val ramPercent: Float
        get() = if (ramTotalBytes <= 0L) 0f else ramUsedBytes.toFloat() / ramTotalBytes * 100f

    val netProgress: Float
        get() {
            val cap = 8L * 1024 * 1024
            return ((downBytesPerSec + upBytesPerSec).toFloat() / cap).coerceIn(0f, 1f)
        }
}

data class PerfProc(
    val name: String,
    val pid: Int,
    val ramKb: Long,
    val cpuPercent: Float
)

data class PerfApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val ramKb: Long,
    val cpuPercent: Float,
    val downBytesPerSec: Long,
    val upBytesPerSec: Long,
    val netKnown: Boolean,
    val members: List<PerfProc>
)

data class PerfSnapshot(
    val gauges: PerfGauges,
    val apps: List<PerfApp>
)

enum class PerfSort { RAM, CPU, NET }

enum class PerfKind { ALL, USER, SYSTEM }

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (value >= 10) {
        "${value.toInt()} ${units[unit]}"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unit])
    }
}

fun formatKb(kb: Long): String = formatBytes(kb * 1024)

fun formatSpeed(bytesPerSec: Long): String = "${formatBytes(bytesPerSec.coerceAtLeast(0L))}/s"

fun formatPercent(value: Float): String =
    if (value < 10f) String.format(Locale.US, "%.1f%%", value) else "${value.toInt()}%"
