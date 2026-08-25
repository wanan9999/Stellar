package roro.stellar.manager.carrier

import android.app.Instrumentation
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import android.os.Process
import android.util.Log

class PrivilegedProcess : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val args = arguments ?: Bundle()
        val callback = args.getBinder(EXTRA_CALLBACK)
        val host = targetContext ?: context
        if (host == null || args.getInt(EXTRA_PID, -1) != Process.myPid()) {
            reply(callback, false, "invalid apply pid", false)
            finishQuietly()
            return
        }
        val am = HiddenAm.activityManager()
        try {
            HiddenAm.startDelegate(am, host.applicationInfo.uid)
            val subId = args.getInt(EXTRA_SUB_ID)
            val reset = args.getBoolean(EXTRA_RESET)
            val bundle = if (reset) {
                null
            } else {
                CarrierOverlay.build(
                    args.getString(EXTRA_ISO),
                    args.getString(EXTRA_NAME)
                )
            }
            CarrierConfigWriter.writeOverride(host, subId, bundle)
            if (Looper.myLooper() != Looper.getMainLooper()) {
                if (!CarrierConfigWriter.confirmOverride(host, subId, bundle)) {
                    error("写入后系统未确认覆盖")
                }
            }
            reply(callback, true, "instrumentation", false)
        } catch (e: Exception) {
            Log.e(TAG, "override failed", e)
            reply(callback, false, e.message ?: e.javaClass.simpleName, false)
        } finally {
            runCatching { HiddenAm.stopDelegate(am) }
        }
        finishQuietly()
    }

    private fun finishQuietly() {
        runCatching { finish(0, Bundle()) }
    }

    private fun reply(callback: android.os.IBinder?, ok: Boolean, message: String, persistent: Boolean) {
        if (callback == null) return
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(if (ok) 1 else 0)
            data.writeString(message)
            data.writeInt(if (persistent) 1 else 0)
            callback.transact(1, data, null, 0)
        } catch (e: Exception) {
            Log.e(TAG, "callback failed", e)
        } finally {
            data.recycle()
        }
    }

    companion object {
        private const val TAG = "StellarCarrier"
        const val DESCRIPTOR = "roro.stellar.manager.carrier.ApplyCallback"
        const val EXTRA_PID = "pid"
        const val EXTRA_SUB_ID = "subId"
        const val EXTRA_ISO = "iso"
        const val EXTRA_NAME = "name"
        const val EXTRA_RESET = "reset"
        const val EXTRA_CALLBACK = "callback"
    }
}
