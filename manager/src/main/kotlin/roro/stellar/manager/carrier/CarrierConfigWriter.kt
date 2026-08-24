package roro.stellar.manager.carrier

import android.annotation.SuppressLint
import android.content.Context
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import java.lang.reflect.InvocationTargetException

internal object CarrierConfigWriter {
    fun overrideConfig(context: Context, subId: Int, bundle: PersistableBundle?, persistent: Boolean) {
        val cm = context.getSystemService(CarrierConfigManager::class.java)
            ?: error("CarrierConfigManager unavailable")
        try {
            val method = CarrierConfigManager::class.java.getMethod(
                "overrideConfig",
                Int::class.javaPrimitiveType,
                PersistableBundle::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(cm, subId, bundle, persistent)
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

    fun applyWithPersistentFallback(
        context: Context,
        subId: Int,
        bundle: PersistableBundle?
    ): Boolean {
        return try {
            overrideConfig(context, subId, bundle, true)
            true
        } catch (e: SecurityException) {
            if (e.message?.contains("persistent", ignoreCase = true) == true) {
                overrideConfig(context, subId, bundle, false)
                false
            } else {
                throw e
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun currentOverride(context: Context, subId: Int): Pair<String, String> {
        val cm = context.getSystemService(CarrierConfigManager::class.java) ?: return "" to ""
        val config = try {
            val method = cm.javaClass.methods.firstOrNull {
                it.name == "getConfigForSubId" && it.parameterCount == 2 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType
            }
            (method?.invoke(cm, subId, context.packageName) as? PersistableBundle)
                ?: cm.getConfigForSubId(subId)
        } catch (_: Exception) {
            cm.getConfigForSubId(subId)
        } ?: return "" to ""
        val iso = config.getString(CarrierKeys.SIM_COUNTRY_ISO).orEmpty()
        val name = if (config.getBoolean(CarrierKeys.CARRIER_NAME_OVERRIDE, false)) {
            config.getString(CarrierKeys.CARRIER_NAME).orEmpty()
        } else {
            ""
        }
        return iso to name
    }

    @SuppressLint("MissingPermission")
    fun simCountryIso(context: Context, subId: Int): String {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return ""
        return try {
            tm.createForSubscriptionId(subId).simCountryIso.orEmpty()
        } catch (_: Exception) {
            tm.simCountryIso.orEmpty()
        }
    }

    @SuppressLint("MissingPermission")
    fun operator(context: Context, subId: Int): String {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return ""
        return try {
            tm.createForSubscriptionId(subId).simOperator.orEmpty()
        } catch (_: Exception) {
            tm.simOperator.orEmpty()
        }
    }

    @SuppressLint("MissingPermission")
    fun activeSubscriptions(context: Context) =
        context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
}
