package roro.stellar.manager.location

import roro.stellar.Stellar
import roro.stellar.manager.application
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal data class ShellResult(val code: Int, val text: String) {
    val failed: Boolean
        get() {
            val t = text.lowercase()
            if (t.contains("already exists") || t.contains("already added")) return false
            return t.contains("unknown command") ||
                t.contains("can't find service") ||
                t.contains("not allowed") ||
                t.contains("securityexception") ||
                t.contains("denied") ||
                t.contains("error:") ||
                t.contains("exception:")
        }
}

internal object MockLocationOps {
    private const val OP = "android:mock_location"
    private val PACKAGE_RE = Regex("""package=([A-Za-z0-9._]+)""")

    fun ensureSelected() {
        if (!Stellar.pingBinder()) error("service")
        val pkg = application.packageName
        allowedPackages().filter { it != pkg && it != "com.android.shell" }.forEach { other ->
            exec("appops set $other $OP deny")
        }
        exec("appops set $pkg $OP allow")
        exec("appops set 2000 $OP allow")
        exec("appops set com.android.shell $OP allow")
        if (!isSelected()) error("mock_app")
    }

    fun isSelected(): Boolean {
        if (!Stellar.pingBinder()) return false
        val pkg = application.packageName
        val allowed = allowedPackages()
        if (allowed.isNotEmpty()) return pkg in allowed
        return exec("appops get $pkg $OP").text.contains("allow", ignoreCase = true)
    }

    fun exec(script: String, timeoutSec: Long = 8): ShellResult {
        val process = Stellar.newProcess(arrayOf("sh", "-c", script), null, null)
        val stdout = CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader().use { it.readText() }
        }
        val stderr = CompletableFuture.supplyAsync {
            process.errorStream.bufferedReader().use { it.readText() }
        }
        val finished = process.waitForTimeout(timeoutSec, TimeUnit.SECONDS)
        val out = runCatching { stdout.get(timeoutSec, TimeUnit.SECONDS) }.getOrDefault("")
        val err = runCatching { stderr.get(timeoutSec, TimeUnit.SECONDS) }.getOrDefault("")
        if (!finished) {
            process.destroy()
            error("timeout")
        }
        val code = runCatching { process.exitValue() }.getOrDefault(-1)
        return ShellResult(code, (out + err).trim())
    }

    private fun allowedPackages(): Set<String> {
        val out = exec("appops query-op $OP allow").text
        return PACKAGE_RE.findAll(out).map { it.groupValues[1] }.toSet()
    }
}
