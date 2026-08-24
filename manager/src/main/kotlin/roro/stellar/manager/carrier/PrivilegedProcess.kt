package roro.stellar.manager.carrier

import android.app.Instrumentation
import android.os.Bundle
import android.os.Parcel
import android.util.Log

class PrivilegedProcess : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val args = arguments ?: Bundle()
        val callback = args.getBinder(EXTRA_CALLBACK)
        val token = args.getString(EXTRA_TOKEN).orEmpty()
        val expected = runCatching { CarrierStore(context).readToken() }.getOrNull().orEmpty()
        if (token.isEmpty() || token != expected) {
            reply(callback, false, "invalid apply token", false)
            finish(0, Bundle())
            return
        }
        try {
            val context = context
            val subId = args.getInt(EXTRA_SUB_ID)
            val reset = args.getBoolean(EXTRA_RESET)
            val persistent = if (reset) {
                CarrierConfigWriter.applyWithPersistentFallback(context, subId, null)
            } else {
                val bundle = CarrierOverlay.build(
                    args.getString(EXTRA_ISO),
                    args.getString(EXTRA_NAME)
                )
                CarrierConfigWriter.applyWithPersistentFallback(context, subId, bundle)
            }
            reply(callback, true, "instrumentation", persistent)
        } catch (e: Exception) {
            Log.e(TAG, "override failed", e)
            reply(callback, false, e.message ?: e.javaClass.simpleName, false)
        }
        finish(0, Bundle())
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
        const val EXTRA_TOKEN = "token"
        const val EXTRA_SUB_ID = "subId"
        const val EXTRA_ISO = "iso"
        const val EXTRA_NAME = "name"
        const val EXTRA_RESET = "reset"
        const val EXTRA_CALLBACK = "callback"
    }
}
