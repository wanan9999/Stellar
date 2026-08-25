package roro.stellar.manager.carrier

import android.content.ComponentName
import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.os.Process
import roro.stellar.manager.application
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object CarrierInstrument {
    fun write(subId: Int, iso: String?, name: String?, reset: Boolean): Bundle {
        val flags = HiddenAm.flagsForNoRestartInstrumentation()
            ?: error("当前系统没有 INSTR_FLAG_NO_RESTART，拒绝 Instrumentation，以免杀掉管理器进程")
        val am = HiddenAm.activityManager()
        val callback = ResultBinder()
        val args = Bundle().apply {
            putInt(PrivilegedProcess.EXTRA_PID, Process.myPid())
            putInt(PrivilegedProcess.EXTRA_SUB_ID, subId)
            putBoolean(PrivilegedProcess.EXTRA_RESET, reset)
            putString(PrivilegedProcess.EXTRA_ISO, iso)
            putString(PrivilegedProcess.EXTRA_NAME, name)
            putBinder(PrivilegedProcess.EXTRA_CALLBACK, callback)
        }
        val startError = runCatching {
            if (!HiddenAm.startInstrumentation(
                    am,
                    ComponentName(application.packageName, PrivilegedProcess::class.java.name),
                    flags,
                    args
                )
            ) {
                error("startInstrumentation returned false")
            }
        }.exceptionOrNull()
        if (!callback.latch.await(20, TimeUnit.SECONDS)) {
            throw startError ?: error("Instrumentation 超时")
        }
        if (!callback.ok) error(callback.message.ifEmpty { "Instrumentation 失败" })
        val overlay = if (reset) null else CarrierOverlay.build(iso, name)
        if (!CarrierConfigWriter.confirmOverride(application, subId, overlay)) {
            val actual = CarrierConfigWriter.currentOverride(application, subId).first
            error(
                if (reset) "清除覆盖后系统仍返回 $actual"
                else "写入后系统未读到覆盖 ${iso.orEmpty()}（当前 $actual）"
            )
        }
        val (overrideIso, _) = CarrierConfigWriter.currentOverride(application, subId)
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
        return Bundle().apply {
            putBoolean(CarrierKeys.OK, true)
            putString(CarrierKeys.STRATEGY, "instrumentation")
            putBoolean(CarrierKeys.PERSISTENT, false)
            putString(CarrierKeys.OVERRIDE_ISO, overrideIso)
            putString(CarrierKeys.MESSAGE, "已写入 (instrumentation)")
        }
    }

    fun completeIfNeeded(result: Bundle, subId: Int, iso: String?, name: String?, reset: Boolean): Bundle {
        if (result.getBoolean(CarrierKeys.OK) || !result.getBoolean(CarrierKeys.NEED_INSTRUMENT)) {
            return result
        }
        return write(subId, iso, name, reset)
    }

    private class ResultBinder : Binder() {
        val latch = CountDownLatch(1)
        @Volatile var ok = false
        @Volatile var message = ""
        @Volatile var persistent = false

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == 1) {
                data.enforceInterface(PrivilegedProcess.DESCRIPTOR)
                ok = data.readInt() == 1
                message = data.readString().orEmpty()
                persistent = data.readInt() == 1
                latch.countDown()
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }
}
