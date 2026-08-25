package roro.stellar.manager.location

import roro.stellar.Stellar
import roro.stellar.manager.application
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal object MockLocationOps {
    private const val OP = "android:mock_location"
    private val PACKAGE_RE = Regex("""package=([A-Za-z0-9._]+)""")

    fun isSelected(): Boolean {
        if (!Stellar.pingBinder()) return false
        val pkg = application.packageName
        val allowed = allowedPackages()
        if (allowed.isNotEmpty()) return pkg in allowed
        return shell("appops get $pkg $OP").contains("allow", ignoreCase = true)
    }

    fun ensureSelected() {
        if (!Stellar.pingBinder()) error("service")
        val pkg = application.packageName
        allowedPackages().filter { it != pkg }.forEach { other ->
            shell("appops set $other $OP deny")
        }
        shell("appops set $pkg $OP allow")
        if (!isSelected()) error("mock_app")
    }

    private fun allowedPackages(): Set<String> {
        val out = shell("appops query-op $OP allow")
        return PACKAGE_RE.findAll(out).map { it.groupValues[1] }.toSet()
    }

    private fun shell(command: String): String {
        val process = Stellar.newProcess(arrayOf("sh", "-c", command), null, null)
        val stdout = CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader().use { it.readText() }
        }
        val stderr = CompletableFuture.supplyAsync {
            process.errorStream.bufferedReader().use { it.readText() }
        }
        val finished = process.waitForTimeout(8, TimeUnit.SECONDS)
        val out = runCatching { stdout.get(8, TimeUnit.SECONDS) }.getOrDefault("")
        val err = runCatching { stderr.get(8, TimeUnit.SECONDS) }.getOrDefault("")
        if (!finished) {
            process.destroy()
            error("timeout")
        }
        return (out + err).trim()
    }
}
