package roro.stellar.manager.carrier

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import java.lang.reflect.InvocationTargetException

internal object CarrierConfigWriter {
    private const val SETTLE_ATTEMPTS = 20
    private const val SETTLE_MS = 150L

    /**
     * Always `persistent=false`.
     *
     * On user builds since the 2026-01 telephony patch, `persistent=true` is either
     * rejected with SecurityException or thrown **inside** the phone-process handler
     * after the binder has already returned. The uncaught handler crash restarts
     * `com.android.phone` and shows up here as a NullPointerException on the next
     * telephony read. PixelIMS / vvb2060 Ims both write non-persistent only.
     *
     * `overrideConfig` itself is posted to a handler, so this waits until
     * `getConfigForSubId` reflects the new overlay (or the absence of one).
     */
    fun writeOverride(context: Context, subId: Int, bundle: PersistableBundle?) {
        if (bundle == null) {
            overrideConfig(context, subId, null, persistent = false)
            overrideConfig(context, subId, maskBundle(), persistent = false)
        } else {
            overrideConfig(context, subId, bundle, persistent = false)
        }
    }

    fun confirmOverride(context: Context, subId: Int, bundle: PersistableBundle?): Boolean {
        val expectedIso = bundle?.getString(CarrierKeys.SIM_COUNTRY_ISO).orEmpty()
        return settled(context, subId, expectedIso, clearing = bundle == null)
    }

    fun applyAndConfirm(
        context: Context,
        subId: Int,
        bundle: PersistableBundle?
    ): Boolean {
        writeOverride(context, subId, bundle)
        if (!confirmOverride(context, subId, bundle)) {
            val actual = currentOverride(context, subId).first
            error(
                if (bundle == null) "清除覆盖后系统仍返回 $actual"
                else "写入后系统未读到覆盖 ${bundle.getString(CarrierKeys.SIM_COUNTRY_ISO)}（当前 $actual）"
            )
        }
        return false
    }

    fun overrideConfig(context: Context, subId: Int, bundle: PersistableBundle?, persistent: Boolean) {
        val cm = context.getSystemService(CarrierConfigManager::class.java)
            ?: error("CarrierConfigManager unavailable")
        try {
            invokeOverride(cm, subId, bundle, persistent)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun invokeOverride(
        cm: CarrierConfigManager,
        subId: Int,
        bundle: PersistableBundle?,
        persistent: Boolean
    ) {
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

    private fun maskBundle() = PersistableBundle().apply {
        putInt(CarrierKeys.MARKER, 0)
        putString(CarrierKeys.SIM_COUNTRY_ISO, "")
        putBoolean(CarrierKeys.CARRIER_NAME_OVERRIDE, false)
        putString(CarrierKeys.CARRIER_NAME, "")
    }

    private fun settled(
        context: Context,
        subId: Int,
        expectedIso: String,
        clearing: Boolean
    ): Boolean {
        repeat(SETTLE_ATTEMPTS) { attempt ->
            val configIso = rawOverlayIso(context, subId)
            if (clearing && configIso == null) return true
            if (!clearing && configIso.equals(expectedIso, true)) return true
            if (attempt < SETTLE_ATTEMPTS - 1) Thread.sleep(SETTLE_MS)
        }
        return false
    }

    private fun rawOverlayIso(context: Context, subId: Int): String? {
        val cm = context.getSystemService(CarrierConfigManager::class.java) ?: return null
        val config = readConfig(cm, context, subId) ?: return null
        if (config.containsKey(CarrierKeys.MARKER) && config.getInt(CarrierKeys.MARKER, 0) != 1) {
            return null
        }
        val iso = config.getString(CarrierKeys.SIM_COUNTRY_ISO).orEmpty()
        if (config.containsKey(CarrierKeys.MARKER)) return iso
        return iso.takeIf { it.length == 2 }
    }

    @SuppressLint("MissingPermission")
    private fun readConfig(cm: CarrierConfigManager, context: Context, subId: Int): PersistableBundle? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return try {
                cm.getConfigForSubId(
                    subId,
                    CarrierKeys.MARKER,
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
            @Suppress("DEPRECATION")
            cm.getConfigForSubId(subId)
        }
    }

    @SuppressLint("MissingPermission")
    fun currentOverride(context: Context, subId: Int): Pair<String, String> {
        val cm = context.getSystemService(CarrierConfigManager::class.java) ?: return "" to ""
        val config = readConfig(cm, context, subId) ?: return "" to ""
        if (config.getInt(CarrierKeys.MARKER, 0) != 1) return "" to ""
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
            val forSub = tm.createForSubscriptionId(subId)
            (forSub?.simCountryIso ?: tm.simCountryIso).orEmpty()
        } catch (_: Exception) {
            tm.simCountryIso.orEmpty()
        }
    }

    @SuppressLint("MissingPermission")
    fun operator(context: Context, subId: Int): String {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return ""
        return try {
            val forSub = tm.createForSubscriptionId(subId)
            (forSub?.simOperator ?: tm.simOperator).orEmpty()
        } catch (_: Exception) {
            tm.simOperator.orEmpty()
        }
    }

    @SuppressLint("MissingPermission")
    fun activeSubscriptions(context: Context) =
        context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
}
