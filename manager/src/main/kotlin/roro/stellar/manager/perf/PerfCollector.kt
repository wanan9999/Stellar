package roro.stellar.manager.perf

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.TrafficStats
import android.os.Parcel
import android.os.Process
import android.os.SystemClock
import androidx.core.graphics.drawable.toBitmap
import roro.stellar.Stellar
import roro.stellar.StellarApiConstants
import roro.stellar.server.ServerConstants
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal object PerfCollector {
    private val meminfoLine = Regex("""^(\w+):\s+(\d+)\s+kB""")
    private val cpuLock = Any()
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastNetAt = 0L
    private var snapIdle = 0L
    private var snapTotal = 0L
    @Volatile private var lastCpu = 0f

    @Volatile private var pkgByName = emptyMap<String, PkgMeta>()
    @Volatile private var pkgsByUid = emptyMap<Int, List<String>>()
    private var pkgIndexAt = 0L

    private val icons = ConcurrentHashMap<String, Bitmap>()
    private val labels = ConcurrentHashMap<String, String>()
    private val prevJiffies = HashMap<Int, Long>()
    private var prevCpuTotal = 0L
    private val prevUidRx = HashMap<Int, Long>()
    private val prevUidTx = HashMap<Int, Long>()
    private var prevUidAt = 0L

    private class PkgMeta(
        val info: ApplicationInfo?,
        val system: Boolean
    )

    fun gauges(): PerfGauges {
        val (used, total) = readRam()
        val (down, up) = readNet()
        return PerfGauges(lastCpu, used, total, down, up)
    }

    fun snapshot(context: Context): PerfSnapshot {
        if (!Stellar.pingBinder()) error("service")
        val pm = context.packageManager
        refreshPackages(pm)
        val raw = pull()
        val cpuTotal = parseCpuTotal(raw.cpuLine)
        val gaugeCpu = cpuPercent(raw.cpuLine)
        val apps = group(raw.procs, cpuTotal, raw.net, pm)
        val (used, total) = readRam()
        val (down, up) = readNet()
        val gauges = PerfGauges(
            cpuPercent = if (gaugeCpu > 0f) gaugeCpu else lastCpu,
            ramUsedBytes = used,
            ramTotalBytes = total,
            downBytesPerSec = down,
            upBytesPerSec = up
        )
        if (gauges.cpuPercent > 0f) lastCpu = gauges.cpuPercent
        return PerfSnapshot(gauges, apps)
    }

    fun icon(context: Context, pkg: String): Bitmap? {
        icons[pkg]?.let { return it }
        val pm = context.packageManager
        val drawable = runCatching {
            pkgByName[pkg]?.info?.loadIcon(pm) ?: pm.getApplicationIcon(pkg)
        }.getOrNull() ?: return null
        val bmp = runCatching { drawable.toBitmap(96, 96) }.getOrNull() ?: return null
        icons.putIfAbsent(pkg, bmp)
        return icons[pkg] ?: bmp
    }

    private class RawProc(
        val pid: Int,
        val uid: Int,
        val rssKb: Long,
        val jiffies: Long,
        val cmd: String
    )

    private class RawSample(
        val cpuLine: String,
        val procs: List<RawProc>,
        val net: Map<Int, Pair<Long, Long>>?
    )

    private fun pull(): RawSample {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(StellarApiConstants.BINDER_DESCRIPTOR)
            if (Stellar.binder?.transact(ServerConstants.BINDER_TRANSACTION_perfSnapshot, data, reply, 0) != true) {
                error("snapshot")
            }
            reply.readException()
            val cpuLine = reply.readString().orEmpty()
            val n = reply.readInt().coerceAtLeast(0)
            val procs = ArrayList<RawProc>(n)
            repeat(n) {
                procs.add(
                    RawProc(
                        pid = reply.readInt(),
                        uid = reply.readInt(),
                        rssKb = reply.readLong(),
                        jiffies = reply.readLong(),
                        cmd = reply.readString().orEmpty()
                    )
                )
            }
            val netKnown = reply.readInt() == 1
            val net = if (!netKnown) {
                null
            } else {
                val count = reply.readInt().coerceAtLeast(0)
                val map = HashMap<Int, Pair<Long, Long>>(count)
                repeat(count) {
                    map[reply.readInt()] = reply.readLong() to reply.readLong()
                }
                map
            }
            RawSample(cpuLine, procs, net)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun refreshPackages(pm: PackageManager) {
        val now = SystemClock.elapsedRealtime()
        if (pkgByName.isNotEmpty() && now - pkgIndexAt < 60_000L) return
        val byName = LinkedHashMap<String, PkgMeta>()
        val byUid = HashMap<Int, MutableList<String>>()
        val installed = runCatching {
            pm.getInstalledApplications(PackageManager.MATCH_ALL)
        }.getOrElse {
            runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
        }
        installed.forEach { ai ->
            val pkg = ai.packageName ?: return@forEach
            if (pkg.isEmpty() || pkg == "android") return@forEach
            val system = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
            byName[pkg] = PkgMeta(ai, system)
            byUid.getOrPut(ai.uid) { ArrayList() }.add(pkg)
        }
        if (byName.isNotEmpty()) {
            pkgByName = byName
            pkgsByUid = byUid
            pkgIndexAt = now
        }
    }

    private fun group(
        procs: List<RawProc>,
        cpuTotal: Long,
        uidNet: Map<Int, Pair<Long, Long>>?,
        pm: PackageManager
    ): List<PerfApp> {
        val sysDelta = synchronized(cpuLock) {
            val d = if (prevCpuTotal == 0L) 0L else (cpuTotal - prevCpuTotal).coerceAtLeast(0L)
            prevCpuTotal = cpuTotal
            d
        }
        val now = SystemClock.elapsedRealtime()
        val seen = HashSet<Int>()
        val buckets = LinkedHashMap<String, ArrayList<PerfProc>>()
        val uidOf = HashMap<String, Int>()
        procs.forEach { raw ->
            val pkg = ownerOf(raw) ?: return@forEach
            val prev = prevJiffies[raw.pid]
            val cpu = if (prev != null && sysDelta > 0L) {
                ((raw.jiffies - prev).coerceAtLeast(0L).toFloat() / sysDelta * 100f)
            } else {
                0f
            }
            buckets.getOrPut(pkg) { ArrayList() }.add(PerfProc(raw.cmd, raw.pid, raw.rssKb, cpu))
            uidOf[pkg] = raw.uid
            seen += raw.pid
        }
        prevJiffies.keys.retainAll(seen)
        procs.forEach { prevJiffies[it.pid] = it.jiffies }

        val netKnown = uidNet != null
        val netSec = if (prevUidAt == 0L) 0.0 else (now - prevUidAt).coerceAtLeast(1L) / 1000.0
        val downMap = HashMap<String, Long>()
        val upMap = HashMap<String, Long>()
        if (uidNet != null && netSec > 0.0) {
            uidOf.entries.groupBy({ it.value }, { it.key }).forEach { (uid, pkgs) ->
                val cur = uidNet[uid] ?: return@forEach
                val pRx = prevUidRx[uid] ?: return@forEach
                val pTx = prevUidTx[uid] ?: return@forEach
                val down = ((cur.first - pRx).coerceAtLeast(0L) / netSec).toLong()
                val up = ((cur.second - pTx).coerceAtLeast(0L) / netSec).toLong()
                val n = pkgs.size.coerceAtLeast(1)
                pkgs.forEach { pkg ->
                    downMap[pkg] = down / n
                    upMap[pkg] = up / n
                }
            }
        }
        if (uidNet != null) {
            prevUidRx.clear()
            prevUidTx.clear()
            uidNet.forEach { (uid, bytes) ->
                prevUidRx[uid] = bytes.first
                prevUidTx[uid] = bytes.second
            }
            prevUidAt = now
        }

        return buckets.map { (pkg, members) ->
            val meta = pkgByName[pkg]
            PerfApp(
                packageName = pkg,
                label = labelOf(pkg, meta, pm),
                system = meta?.system == true,
                ramKb = members.sumOf { it.ramKb },
                cpuPercent = members.sumOf { it.cpuPercent.toDouble() }.toFloat().coerceAtLeast(0f),
                downBytesPerSec = downMap[pkg] ?: 0L,
                upBytesPerSec = upMap[pkg] ?: 0L,
                netKnown = netKnown,
                members = members.sortedWith(
                    compareByDescending<PerfProc> { it.ramKb }.thenByDescending { it.cpuPercent }
                )
            )
        }.sortedByDescending { it.ramKb }
    }

    private fun labelOf(pkg: String, meta: PkgMeta?, pm: PackageManager): String {
        labels[pkg]?.let { return it }
        val label = meta?.info?.loadLabel(pm)?.toString()
            ?: runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrNull()
            ?: pkg
        labels[pkg] = label
        return label
    }

    private fun ownerOf(raw: RawProc): String? {
        val cmd = raw.cmd.replace('\u0000', ' ').trim()
        matchName(cmd)?.let { return it }
        cmd.split(' ', ':').forEach { token ->
            matchName(token)?.let { return it }
        }
        if (raw.uid < Process.FIRST_APPLICATION_UID) return null
        val pkgs = pkgsByUid[raw.uid] ?: return null
        pkgs.singleOrNull()?.let { return it }
        return pkgs.filter { cmd.startsWith(it) }.maxByOrNull { it.length }
    }

    private fun matchName(name: String): String? {
        if (name.isEmpty()) return null
        val base = name.substringBefore(':').substringAfterLast('/')
        if (base in pkgByName) return base
        var candidate = base
        while (candidate.contains('.')) {
            if (candidate in pkgByName) return candidate
            candidate = candidate.substringBeforeLast('.')
        }
        return null
    }

    private fun parseCpuTotal(text: String): Long {
        val parts = cpuParts(text) ?: return 0L
        return cpuTotalOf(parts)
    }

    private fun cpuPercent(text: String): Float {
        val parts = cpuParts(text) ?: return 0f
        val idle = parts.getOrElse(3) { 0L } + parts.getOrElse(4) { 0L }
        val total = cpuTotalOf(parts)
        synchronized(cpuLock) {
            val first = snapTotal == 0L
            val dIdle = idle - snapIdle
            val dTotal = total - snapTotal
            snapIdle = idle
            snapTotal = total
            if (first || dTotal <= 0L) return lastCpu
            val value = ((dTotal - dIdle).toFloat() / dTotal * 100f).coerceIn(0f, 100f)
            lastCpu = value
            return value
        }
    }

    private fun cpuParts(text: String): List<Long>? =
        text.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("cpu") || it.firstOrNull()?.isDigit() == true }
            ?.split(Regex("\\s+"))
            ?.mapNotNull { it.toLongOrNull() }
            ?.takeIf { it.size >= 4 }

    private fun cpuTotalOf(parts: List<Long>): Long {
        val n = minOf(parts.size, 8)
        var sum = 0L
        for (i in 0 until n) sum += parts[i]
        return sum
    }

    private fun readRam(): Pair<Long, Long> {
        var totalKb = 0L
        var availKb = 0L
        var freeKb = 0L
        var cachedKb = 0L
        var reclaimKb = 0L
        var shmemKb = 0L
        runCatching {
            File("/proc/meminfo").forEachLine { line ->
                val m = meminfoLine.find(line) ?: return@forEachLine
                val kb = m.groupValues[2].toLong()
                when (m.groupValues[1]) {
                    "MemTotal" -> totalKb = kb
                    "MemAvailable" -> availKb = kb
                    "MemFree" -> freeKb = kb
                    "Cached" -> cachedKb = kb
                    "SReclaimable" -> reclaimKb = kb
                    "Shmem" -> shmemKb = kb
                }
            }
        }
        if (totalKb <= 0L) return 0L to 0L
        if (availKb <= 0L) {
            availKb = (freeKb + cachedKb + reclaimKb - shmemKb).coerceAtLeast(0L)
        }
        return (totalKb - availKb).coerceAtLeast(0L) * 1024 to totalKb * 1024
    }

    private fun readNet(): Pair<Long, Long> {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = SystemClock.elapsedRealtime()
        if (rx < 0L || tx < 0L) return 0L to 0L
        synchronized(cpuLock) {
            val prevRx = lastRx
            val prevTx = lastTx
            val prevAt = lastNetAt
            lastRx = rx
            lastTx = tx
            lastNetAt = now
            if (prevAt == 0L || prevRx < 0L) return 0L to 0L
            val sec = (now - prevAt).coerceAtLeast(1L) / 1000.0
            return ((rx - prevRx).coerceAtLeast(0L) / sec).toLong() to
                ((tx - prevTx).coerceAtLeast(0L) / sec).toLong()
        }
    }
}
