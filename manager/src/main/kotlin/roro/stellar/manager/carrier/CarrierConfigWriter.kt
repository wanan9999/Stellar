package roro.stellar.manager.carrier

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import java.lang.reflect.InvocationTargetException

internal object CarrierConfigWriter {
    private const val SETTLE_ATTEMPTS = 20
    private const val SETTLE_MS = 150L

    fun writeOverride(context: Context, subId: Int, bundle: PersistableBundle?) {
        overrideConfig(context, subId, bundle)
        if (bundle == null) notifyChanged(context, subId)
    }

    fun confirmOverride(context: Context, subId: Int, bundle: PersistableBundle?): Boolean {
        val expectedIso = bundle?.getString(CarrierKeys.SIM_COUNTRY_ISO).orEmpty()
        repeat(SETTLE_ATTEMPTS) { attempt ->
            val (iso, name) = currentOverride(context, subId)
            val matched = if (bundle == null) {
                iso.isEmpty() && name.isEmpty()
            } else {
                iso.equals(expectedIso, true)
            }
            if (matched) return true
            if (attempt < SETTLE_ATTEMPTS - 1) Thread.sleep(SETTLE_MS)
        }
        return false
    }

    fun overrideConfig(context: Context, subId: Int, bundle: PersistableBundle?) {
        val cm = context.getSystemService(CarrierConfigManager::class.java)
            ?: error("CarrierConfigManager unavailable")
        try {
            invokeOverride(cm, subId, bundle)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun notifyChanged(context: Context, subId: Int) {
        val cm = context.getSystemService(CarrierConfigManager::class.java) ?: return
        runCatching {
            cm.javaClass.getMethod("notifyConfigChangedForSubId", Int::class.javaPrimitiveType)
                .invoke(cm, subId)
        }
    }

    private fun invokeOverride(
        cm: CarrierConfigManager,
        subId: Int,
        bundle: PersistableBundle?
    ) {
        try {
            val method = CarrierConfigManager::class.java.getMethod(
                "overrideConfig",
                Int::class.javaPrimitiveType,
                PersistableBundle::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(cm, subId, bundle, false)
        } catch (e: NoSuchMethodException) {
            val method = CarrierConfigManager::class.java.getMethod(
                "overrideConfig",
                Int::class.javaPrimitiveType,
                PersistableBundle::class.java
            )
            method.invoke(cm, subId, bundle)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    @SuppressLint("MissingPermission")
    private fun readConfig(cm: CarrierConfigManager, context: Context, subId: Int): PersistableBundle? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return try {
                cm.getConfigForSubId(
                    subId,
                    CarrierKeys.SIM_COUNTRY_ISO,
                    CarrierKeys.CARRIER_NAME_OVERRIDE,
                    CarrierKeys.CARRIER_NAME
                )
            } catch (_: Exception) {
                null
            }
        }
        return try {
            val method = cm.javaClass.methods.firstOrNull {
                it.name == "getConfigForSubId" && it.parameterCount == 2 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType
            }
            (method?.invoke(cm, subId, context.packageName) as? PersistableBundle)
                ?: @Suppress("DEPRECATION") cm.getConfigForSubId(subId)
        } catch (_: Exception) {
            runCatching {
                @Suppress("DEPRECATION")
                cm.getConfigForSubId(subId)
            }.getOrNull()
        }
    }

    @SuppressLint("MissingPermission")
    fun currentOverride(context: Context, subId: Int): Pair<String, String> {
        val cm = context.getSystemService(CarrierConfigManager::class.java) ?: return "" to ""
        val config = readConfig(cm, context, subId) ?: return "" to ""
        val iso = config.getString(CarrierKeys.SIM_COUNTRY_ISO).orEmpty()
        val name = if (config.getBoolean(CarrierKeys.CARRIER_NAME_OVERRIDE, false)) {
            config.getString(CarrierKeys.CARRIER_NAME).orEmpty()
        } else {
            ""
        }
        return iso to name
    }

    @SuppressLint("MissingPermission")
    fun nativeCountryIso(context: Context, subId: Int, info: SubscriptionInfo? = null): String {
        val sub = info ?: activeSubscriptions(context).firstOrNull { it.subscriptionId == subId }
        val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            sub?.mccString.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            sub?.mcc?.takeIf { it != 0 }?.toString().orEmpty()
        }
        return isoForMcc(mcc)
    }

    private fun isoForMcc(mcc: String): String {
        if (mcc.length < 3) return ""
        return runCatching {
            val clazz = Class.forName("com.android.internal.telephony.MccTable")
            val method = clazz.methods.first {
                it.name == "countryCodeForMcc" && it.parameterCount == 1
            }
            val arg: Any = if (method.parameterTypes[0] == Int::class.javaPrimitiveType) {
                mcc.toInt()
            } else {
                mcc
            }
            (method.invoke(null, arg) as? String).orEmpty().lowercase()
        }.getOrDefault("")
    }

    @SuppressLint("MissingPermission")
    fun activeSubscriptions(context: Context): List<SubscriptionInfo> {
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            (sm.activeSubscriptionInfoList as List<SubscriptionInfo>?) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
