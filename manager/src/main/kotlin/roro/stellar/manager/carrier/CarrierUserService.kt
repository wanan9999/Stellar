package roro.stellar.manager.carrier

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.util.Log

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
            runCatching {
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
            }.onFailure { e ->
                Log.w(TAG, "listSims skipped a subscription", e)
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
        val outcome = write(subId, bundle)
        if (outcome.getBoolean(CarrierKeys.NEED_INSTRUMENT)) {
            outcome.putInt(CarrierKeys.SUB_ID, subId)
            outcome.putString(CarrierKeys.ISO, iso)
            outcome.putString(CarrierKeys.CARRIER, name)
            return outcome
        }
        if (outcome.getBoolean(CarrierKeys.OK)) {
            store.save(
                StoredOverlay(
                    subId = subId,
                    iso = iso,
                    name = name,
                    autoReapply = store.load()?.autoReapply ?: true
                )
            )
            outcome.putInt(CarrierKeys.SUB_ID, subId)
            outcome.putString(CarrierKeys.ISO, iso)
            outcome.putString(CarrierKeys.CARRIER, name)
            verifyInto(outcome, subId, iso)
        }
        return outcome
    }

    override fun resetOverride(subId: Int): Bundle {
        val outcome = write(subId, null)
        if (outcome.getBoolean(CarrierKeys.NEED_INSTRUMENT)) {
            outcome.putInt(CarrierKeys.SUB_ID, subId)
            return outcome
        }
        if (outcome.getBoolean(CarrierKeys.OK)) {
            store.clear()
            outcome.putInt(CarrierKeys.SUB_ID, subId)
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

    private fun write(subId: Int, bundle: android.os.PersistableBundle?): Bundle {
        val direct = runCatching {
            val persistent = CarrierConfigWriter.applyAndConfirm(context, subId, bundle)
            success("direct", persistent).apply {
                putString(
                    CarrierKeys.OVERRIDE_ISO,
                    CarrierConfigWriter.currentOverride(context, subId).first
                )
            }
        }
        if (direct.isSuccess) return direct.getOrThrow()
        val error = direct.exceptionOrNull()
        Log.w(TAG, "direct write failed", error)
        if (error is SecurityException) {
            return Bundle().apply {
                putBoolean(CarrierKeys.OK, false)
                putBoolean(CarrierKeys.NEED_INSTRUMENT, true)
                putString(CarrierKeys.MESSAGE, error.message)
            }
        }
        return failure(error?.message ?: "写入失败")
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
}
