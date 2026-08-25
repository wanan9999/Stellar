package roro.stellar.server.query

import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.ServiceManager
import java.io.File
import java.io.FileInputStream

internal object PerfProbe {
    private val qtaguid = File("/proc/net/xt_qtaguid/stats")
    private var netMode = 0

    class Sample(
        val cpuLine: String,
        val procs: ArrayList<Proc>,
        val net: Map<Int, Pair<Long, Long>>?,
    )

    class Proc(
        val pid: Int,
        val uid: Int,
        val rssKb: Long,
        val jiffies: Long,
        val cmd: String,
    )

    fun collect(): Sample = Sample(readCpuLine(), readProcs(), readUidNet())

    private fun readCpuLine(): String =
        runCatching { File("/proc/stat").bufferedReader().use { it.readLine().orEmpty() } }
            .getOrDefault("")

    private fun readProcs(): ArrayList<Proc> {
        val names = File("/proc").list() ?: return ArrayList()
        val out = ArrayList<Proc>(64)
        for (name in names) {
            val pid = name.toIntOrNull() ?: continue
            val cmd = cmdline(pid) ?: continue
            if (cmd.isEmpty() || cmd[0] == '/') continue
            val uidRss = uidRss(pid) ?: continue
            out.add(Proc(pid, uidRss.first, uidRss.second, jiffies(pid), cmd))
        }
        return out
    }

    private fun cmdline(pid: Int): String? {
        val bytes = runCatching { File("/proc/$pid/cmdline").readBytes() }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes[0] == 0.toByte()) return ""
        return String(bytes, 0, bytes.size).replace('\u0000', ' ').trim()
    }

    private fun uidRss(pid: Int): Pair<Int, Long>? {
        var uid: Int? = null
        var rss = 0L
        runCatching {
            File("/proc/$pid/status").bufferedReader().use { br ->
                while (true) {
                    val line = br.readLine() ?: break
                    when {
                        line.startsWith("Uid:") -> {
                            uid = line.substring(4).trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()
                        }
                        line.startsWith("VmRSS:") -> {
                            rss = line.substringAfter(':').trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L
                            break
                        }
                    }
                }
            }
        }
        val u = uid ?: return null
        if (rss <= 0L) return null
        return u to rss
    }

    private fun jiffies(pid: Int): Long {
        val stat = runCatching { File("/proc/$pid/stat").readText() }.getOrNull() ?: return 0L
        val close = stat.lastIndexOf(')')
        val fields = (if (close >= 0) stat.substring(close + 1) else stat).trim().split(Regex("\\s+"))
        val ut = fields.getOrNull(11)?.toLongOrNull() ?: 0L
        val st = fields.getOrNull(12)?.toLongOrNull() ?: 0L
        return ut + st
    }

    private fun readUidNet(): Map<Int, Pair<Long, Long>>? {
        when (netMode) {
            1 -> return qtaguidNet()
            2 -> return nmsNet()
            3 -> return trafficControllerNet()
        }
        qtaguidNet()?.takeIf { it.isNotEmpty() }?.also { netMode = 1 }?.let { return it }
        nmsNet()?.takeIf { it.isNotEmpty() }?.also { netMode = 2 }?.let { return it }
        trafficControllerNet()?.takeIf { it.isNotEmpty() }?.also { netMode = 3 }?.let { return it }
        return null
    }

    private fun qtaguidNet(): Map<Int, Pair<Long, Long>>? {
        if (!qtaguid.canRead()) return null
        val map = HashMap<Int, Pair<Long, Long>>()
        runCatching {
            qtaguid.forEachLine { line ->
                val p = line.trim().split(Regex("\\s+"))
                if (p.size < 8 || p[0] == "idx") return@forEachLine
                val uid = p[3].toIntOrNull() ?: return@forEachLine
                val rx = p[5].toLongOrNull() ?: return@forEachLine
                val tx = p[7].toLongOrNull() ?: return@forEachLine
                val old = map[uid] ?: (0L to 0L)
                map[uid] = old.first + rx to old.second + tx
            }
        }
        return map
    }

    private fun nmsNet(): Map<Int, Pair<Long, Long>>? = runCatching {
        val binder = ServiceManager.getService("network_management") ?: return null
        val stub = Class.forName("android.os.INetworkManagementService\$Stub")
        val nms = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder) ?: return null
        val method = nms.javaClass.methods.firstOrNull { m ->
            (m.name == "getNetworkStatsUidDetail" || m.name == "getNetworkStatsDetail") &&
                (m.parameterCount == 0 || (m.parameterCount == 1 && m.parameterTypes[0] == Int::class.javaPrimitiveType))
        } ?: return null
        val stats = if (method.parameterCount == 0) method.invoke(nms) else method.invoke(nms, -1)
        foldStats(stats)
    }.getOrNull()

    private fun foldStats(stats: Any?): Map<Int, Pair<Long, Long>>? {
        if (stats == null) return null
        val size = stats.javaClass.getMethod("size").invoke(stats) as Int
        val entryClass = Class.forName("android.net.NetworkStats\$Entry")
        val entry = entryClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val getValues = stats.javaClass.getMethod("getValues", Int::class.javaPrimitiveType, entryClass)
        val uidF = entryClass.getField("uid")
        val tagF = entryClass.getField("tag")
        val rxF = entryClass.getField("rxBytes")
        val txF = entryClass.getField("txBytes")
        val map = HashMap<Int, Pair<Long, Long>>()
        for (i in 0 until size) {
            getValues.invoke(stats, i, entry)
            if (tagF.getInt(entry) != 0) continue
            val uid = uidF.getInt(entry)
            if (uid < 0) continue
            val old = map[uid] ?: (0L to 0L)
            map[uid] = old.first + rxF.getLong(entry) to old.second + txF.getLong(entry)
        }
        return map
    }

    private fun trafficControllerNet(): Map<Int, Pair<Long, Long>>? {
        val text = dumpService("connectivity", arrayOf("trafficcontroller")).ifEmpty {
            dumpService("netd", arrayOf("trafficcontroller"))
        }
        if (text.isEmpty()) return null
        parseAppUidStats(text)?.takeIf { it.isNotEmpty() }?.let { return it }
        return parseStatsMap(text)
    }

    private fun parseAppUidStats(text: String): Map<Int, Pair<Long, Long>>? {
        val map = HashMap<Int, Pair<Long, Long>>()
        var inMap = false
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("mAppUidStatsMap") || line.contains("AppUidStatsMap")) {
                inMap = true
                return@forEach
            }
            if (!inMap) return@forEach
            if (line.endsWith(':') && line.startsWith("m") && !line.contains("AppUidStats")) {
                inMap = false
                return@forEach
            }
            if (line.startsWith("uid ")) return@forEach
            val p = line.split(Regex("\\s+"))
            if (p.size < 5) return@forEach
            val uid = p[0].toIntOrNull() ?: return@forEach
            val rx = p[1].toLongOrNull() ?: return@forEach
            val tx = p[3].toLongOrNull() ?: return@forEach
            map[uid] = rx to tx
        }
        return map.ifEmpty { null }
    }

    private fun parseStatsMap(text: String): Map<Int, Pair<Long, Long>>? {
        val map = HashMap<Int, Pair<Long, Long>>()
        var inMap = false
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("mStatsMap")) {
                inMap = true
                return@forEach
            }
            if (!inMap) return@forEach
            if (line.endsWith(':') && line.startsWith("m") && !line.startsWith("mStatsMap")) {
                inMap = line.startsWith("mStatsMap")
                return@forEach
            }
            val p = line.split(Regex("\\s+"))
            if (p.size < 9) return@forEach
            if (p[2] != "0x0" && p[2] != "0") return@forEach
            val uid = p[3].toIntOrNull() ?: return@forEach
            val rx = p[5].toLongOrNull() ?: return@forEach
            val tx = p[7].toLongOrNull() ?: return@forEach
            val old = map[uid] ?: (0L to 0L)
            map[uid] = old.first + rx to old.second + tx
        }
        return map.ifEmpty { null }
    }

    private fun dumpService(name: String, args: Array<String>): String {
        val binder = ServiceManager.getService(name) ?: return ""
        val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull() ?: return ""
        val readFd = pipe[0]
        val writeFd = pipe[1]
        val buf = StringBuilder()
        val reader = Thread({
            runCatching {
                FileInputStream(readFd.fileDescriptor).bufferedReader().use { br ->
                    val tmp = CharArray(8192)
                    while (true) {
                        val n = br.read(tmp)
                        if (n < 0) break
                        buf.appendRange(tmp, 0, n)
                    }
                }
            }
        }, "perf-dump")
        reader.start()
        return try {
            binder.dump(writeFd.fileDescriptor, args)
            runCatching { writeFd.close() }
            reader.join(2000)
            buf.toString()
        } catch (_: Throwable) {
            runCatching { writeFd.close() }
            ""
        } finally {
            runCatching { readFd.close() }
        }
    }
}
