package roro.stellar.manager.carrier

import android.content.ComponentName
import android.os.Bundle
import android.os.Process
import roro.stellar.manager.application
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object CarrierApplySession {
    @Volatile private var latch = CountDownLatch(1)
    @Volatile var ok = false
    @Volatile var message = ""

    @Synchronized
    fun begin() {
        ok = false
        message = ""
        latch = CountDownLatch(1)
    }

    fun complete(success: Boolean, detail: String) {
        ok = success
        message = detail
        latch.countDown()
    }

    fun await(timeoutSec: Long = 20): Boolean = latch.await(timeoutSec, TimeUnit.SECONDS)
}

internal object CarrierInstrument {
    fun write(subId: Int, iso: String?, name: String?, reset: Boolean) {
        val flags = HiddenAm.flagsForNoRestartInstrumentation()
            ?: error("当前系统没有 INSTR_FLAG_NO_RESTART，拒绝 Instrumentation，以免杀掉管理器进程")
        val am = HiddenAm.activityManager()
        val connection = HiddenAm.uiAutomationConnection()
        CarrierApplySession.begin()
        val args = Bundle().apply {
            putInt(PrivilegedProcess.EXTRA_PID, Process.myPid())
            putInt(PrivilegedProcess.EXTRA_SUB_ID, subId)
            putBoolean(PrivilegedProcess.EXTRA_RESET, reset)
            putString(PrivilegedProcess.EXTRA_ISO, iso)
            putString(PrivilegedProcess.EXTRA_NAME, name)
        }
        val startError = runCatching {
            if (!HiddenAm.startInstrumentation(
                    am,
                    ComponentName(application.packageName, PrivilegedProcess::class.java.name),
                    flags,
                    args,
                    connection
                )
            ) {
                error("startInstrumentation returned false")
            }
        }.exceptionOrNull()
        if (!CarrierApplySession.await()) {
            throw startError ?: error("Instrumentation 超时")
        }
        if (!CarrierApplySession.ok) {
            error(CarrierApplySession.message.ifEmpty { startError?.message ?: "Instrumentation 失败" })
        }
        val overlay = if (reset) null else CarrierOverlay.build(iso, name)
        if (!CarrierConfigWriter.confirmOverride(application, subId, overlay)) {
            val actual = CarrierConfigWriter.currentOverride(application, subId).first
            error(
                if (reset) "清除覆盖后系统仍返回 $actual"
                else "写入后系统未读到覆盖 ${iso.orEmpty()}（当前 $actual）"
            )
        }
        val store = CarrierStore(application)
        if (reset) {
            store.clear()
        } else {
            store.save(
                StoredOverlay(
                    subId = subId,
                    iso = iso.orEmpty(),
                    name = name.orEmpty(),
                    autoReapply = store.load()?.autoReapply ?: true
                )
            )
        }
    }
}
