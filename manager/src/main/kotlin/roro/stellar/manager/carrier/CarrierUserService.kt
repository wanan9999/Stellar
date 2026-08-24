package roro.stellar.manager.carrier

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.telephony.SubscriptionManager
import android.util.Log
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CarrierUserService : ICarrierOverrideService.Stub {
    private val context: Context
    private val store: CarrierStore

    constructor() : this(currentApplication())

    constructor(context: Context?) {
        val resolved = context?.applicationContext ?: context
            ?: error("CarrierUserService requires Context")
        this.context = resolved
        this.store = CarrierStore(resolved)
    }

    companion object {
        private const val TAG = "CarrierUserService"

        private fun currentApplication(): Context? {
            return runCatching {
                Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null) as? Context
            }.getOrNull()
        }
    }

    @SuppressLint("MissingPermission")
    override fun listSims(): MutableList<Bundle> {
        val result = mutableListOf<Bundle>()
        val subs = CarrierConfigWriter.activeSubscriptions(context)
        subs.forEach { info ->
            val subId = info.subscriptionId
            val (overrideIso, overrideName) = runCatching {
                CarrierConfigWriter.currentOverride(context, subId)
            }.getOrDefault("" to "")
            result += Bundle().apply {
                putInt(CarrierKeys.SLOT, info.simSlotIndex + 1)
                putInt(CarrierKeys.SUB_ID, subId)
                putString(CarrierKeys.DISPLAY_NAME, info.displayName?.toString().orEmpty())
                putString(
                    CarrierKeys.MCC_MNC,
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        info.mccString.orEmpty() + info.mncString.orEmpty()
                    } else {
                        @Suppress("DEPRECATION")
                        "${info.mcc}${info.mnc}"
                    }
                )
                putString(CarrierKeys.ISO, info.countryIso.orEmpty())
                putString(CarrierKeys.OVERRIDE_ISO, overrideIso)
                putString(CarrierKeys.OVERRIDE_NAME, overrideName)
                putString(
                    CarrierKeys.CARRIER,
                    CarrierConfigWriter.simCountryIso(context, subId)
                )
            }
        }
        return result
    }

    override fun applyOverride(subId: Int, countryIso: String?, carrierName: String?): Bundle {
        val iso = countryIso?.trim()?.lowercase().orEmpty()
        val name = carrierName?.trim().orEmpty()
        if (iso.isNotEmpty() && iso.length != 2) {
            return failure("国家码必须是 2 位 ISO")
        }
        val bundle = CarrierOverlay.build(iso, name)
        if (CarrierOverlay.isEmpty(bundle)) {
            return failure("没有可写入的覆盖项")
        }
        val outcome = write(subId, bundle, reset = false)
        if (outcome.getBoolean(CarrierKeys.OK)) {
            store.save(
                StoredOverlay(
                    subId = subId,
                    iso = iso,
                    name = name,
                    autoReapply = store.load()?.autoReapply ?: true
                )
            )
            verifyInto(outcome, subId, iso)
        }
        return outcome
    }

    override fun resetOverride(subId: Int): Bundle {
        val outcome = write(subId, null, reset = true)
        if (outcome.getBoolean(CarrierKeys.OK)) {
            store.clear()
            verifyInto(outcome, subId, null)
        }
        return outcome
    }

    override fun peek(subId: Int): Bundle {
        val (overrideIso, overrideName) = runCatching {
            CarrierConfigWriter.currentOverride(context, subId)
        }.getOrDefault("" to "")
        return Bundle().apply {
            putBoolean(CarrierKeys.OK, true)
            putString(CarrierKeys.OVERRIDE_ISO, overrideIso)
            putString(CarrierKeys.OVERRIDE_NAME, overrideName)
            putString(CarrierKeys.VERIFIED_ISO, CarrierConfigWriter.simCountryIso(context, subId))
            putString(CarrierKeys.VERIFIED_OPERATOR, CarrierConfigWriter.operator(context, subId))
            putBoolean(CarrierKeys.AUTO_REAPPLY, store.load()?.autoReapply ?: true)
        }
    }

    override fun reapplyStored(): Bundle {
        val stored = store.load() ?: return failure("没有已保存的覆盖")
        if (!stored.autoReapply) return failure("自动重放已关闭")
        return applyOverride(stored.subId, stored.iso, stored.name)
    }

    override fun setAutoReapply(enabled: Boolean) {
        val current = store.load()
        if (current != null) {
            store.save(current.copy(autoReapply = enabled))
        } else {
            store.save(StoredOverlay(SubscriptionManager.INVALID_SUBSCRIPTION_ID, "", "", enabled))
        }
    }

    private fun write(subId: Int, bundle: android.os.PersistableBundle?, reset: Boolean): Bundle {
        val direct = runCatching {
            val persistent = CarrierConfigWriter.applyWithPersistentFallback(context, subId, bundle)
            success("direct", persistent)
        }
        if (direct.isSuccess) return direct.getOrThrow()
        val error = direct.exceptionOrNull()
        Log.w(TAG, "direct write failed", error)
        if (error is SecurityException) {
            val instrumented = runCatching { writeViaInstrumentation(subId, bundle, reset) }
            if (instrumented.isSuccess) return instrumented.getOrThrow()
            Log.w(TAG, "instrumentation write failed", instrumented.exceptionOrNull())
        }
        val cmd = runCatching { writeViaCmd(subId, bundle, reset) }
        if (cmd.isSuccess) return cmd.getOrThrow()
        return failure(error?.message ?: cmd.exceptionOrNull()?.message ?: "写入失败")
    }

    private fun writeViaInstrumentation(
        subId: Int,
        bundle: android.os.PersistableBundle?,
        reset: Boolean
    ): Bundle {
        val am = HiddenAm.activityManager()
        val appUid = context.applicationInfo.uid
        val callback = ResultBinder()
        val token = UUID.randomUUID().toString()
        store.writeToken(token)
        val args = Bundle().apply {
            putString(PrivilegedProcess.EXTRA_TOKEN, token)
            putInt(PrivilegedProcess.EXTRA_SUB_ID, subId)
            putBoolean(PrivilegedProcess.EXTRA_RESET, reset)
            putString(PrivilegedProcess.EXTRA_ISO, bundle?.getString(CarrierKeys.SIM_COUNTRY_ISO))
            putString(PrivilegedProcess.EXTRA_NAME, bundle?.getString(CarrierKeys.CARRIER_NAME))
            putBinder(PrivilegedProcess.EXTRA_CALLBACK, callback)
        }

        HiddenAm.startDelegate(am, appUid)
        try {
            val started = HiddenAm.startInstrumentation(
                am,
                ComponentName(context.packageName, PrivilegedProcess::class.java.name),
                HiddenAm.INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS or HiddenAm.INSTR_FLAG_NO_RESTART,
                args
            )
            if (!started) error("startInstrumentation returned false")
            if (!callback.latch.await(20, TimeUnit.SECONDS)) {
                error("Instrumentation 超时")
            }
            if (!callback.ok) error(callback.message.ifEmpty { "Instrumentation 失败" })
            return success("instrumentation", callback.persistent)
        } finally {
            store.clearToken()
            runCatching { HiddenAm.stopDelegate(am) }
        }
    }

    private fun writeViaCmd(
        subId: Int,
        bundle: android.os.PersistableBundle?,
        reset: Boolean
    ): Bundle {
        val command = if (reset || bundle == null) {
            arrayOf("cmd", "phone", "cc", "clear-values", "-s", subId.toString())
        } else {
            val args = mutableListOf("cmd", "phone", "cc", "set-values", "-s", subId.toString())
            bundle.getString(CarrierKeys.SIM_COUNTRY_ISO)?.let {
                args += listOf("sim_country_iso_override_string", it)
            }
            if (bundle.getBoolean(CarrierKeys.CARRIER_NAME_OVERRIDE, false)) {
                args += listOf(
                    "carrier_name_override_bool",
                    "true",
                    "carrier_name_string",
                    bundle.getString(CarrierKeys.CARRIER_NAME).orEmpty()
                )
            }
            args.toTypedArray()
        }
        val process = Runtime.getRuntime().exec(command)
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code != 0) {
            error("cmd phone 失败 ($code): ${(stdout + stderr).trim()}")
        }
        return success("cmd-phone", true)
    }

    private fun verifyInto(bundle: Bundle, subId: Int, expectedIso: String?) {
        repeat(6) { attempt ->
            val iso = CarrierConfigWriter.simCountryIso(context, subId)
            val (overrideIso, _) = runCatching {
                CarrierConfigWriter.currentOverride(context, subId)
            }.getOrDefault("" to "")
            bundle.putString(CarrierKeys.VERIFIED_ISO, iso)
            bundle.putString(CarrierKeys.VERIFIED_OPERATOR, CarrierConfigWriter.operator(context, subId))
            bundle.putString(CarrierKeys.OVERRIDE_ISO, overrideIso)
            if (expectedIso.isNullOrEmpty() || iso.equals(expectedIso, true) || overrideIso.equals(expectedIso, true)) {
                return
            }
            if (attempt < 5) Thread.sleep(400)
        }
    }

    private fun success(strategy: String, persistent: Boolean) = Bundle().apply {
        putBoolean(CarrierKeys.OK, true)
        putString(CarrierKeys.STRATEGY, strategy)
        putBoolean(CarrierKeys.PERSISTENT, persistent)
        putString(CarrierKeys.MESSAGE, "已写入 ($strategy)")
    }

    private fun failure(message: String) = Bundle().apply {
        putBoolean(CarrierKeys.OK, false)
        putString(CarrierKeys.MESSAGE, message)
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
