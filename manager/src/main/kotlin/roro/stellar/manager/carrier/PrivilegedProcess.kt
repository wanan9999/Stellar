package roro.stellar.manager.carrier

import android.app.Instrumentation
import android.os.Bundle
import android.os.Process
import android.util.Log

class PrivilegedProcess : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val args = arguments ?: Bundle()
        val host = targetContext ?: context
        if (host == null || args.getInt(EXTRA_PID, -1) != Process.myPid()) {
            CarrierApplySession.complete(false, "invalid apply pid")
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
            CarrierApplySession.complete(true, "instrumentation")
        } catch (e: Exception) {
            Log.e(TAG, "override failed", e)
            CarrierApplySession.complete(false, e.message ?: e.toString())
        } finally {
            runCatching { HiddenAm.stopDelegate(am) }
        }
        finishQuietly()
    }

    private fun finishQuietly() {
        runCatching { finish(0, Bundle()) }
    }

    companion object {
        private const val TAG = "StellarCarrier"
        const val EXTRA_PID = "pid"
        const val EXTRA_SUB_ID = "subId"
        const val EXTRA_ISO = "iso"
        const val EXTRA_NAME = "name"
        const val EXTRA_RESET = "reset"
    }
}
