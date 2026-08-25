package roro.stellar.manager.carrier

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import roro.stellar.Stellar
import roro.stellar.manager.application
import roro.stellar.manager.util.UserHandleCompat

internal data class SimSnapshot(
    val slot: Int,
    val subId: Int,
    val displayName: String,
    val nativeIso: String,
    val overlayIso: String
)

internal object CarrierController {
    fun snapshots(context: Context = application): List<SimSnapshot> {
        ensurePhoneState(context)
        return CarrierConfigWriter.activeSubscriptions(context).map { info ->
            val subId = info.subscriptionId
            SimSnapshot(
                slot = info.simSlotIndex + 1,
                subId = subId,
                displayName = info.displayName?.toString().orEmpty(),
                nativeIso = CarrierConfigWriter.nativeCountryIso(context, subId, info),
                overlayIso = CarrierConfigWriter.currentOverride(context, subId).first
            )
        }
    }

    fun autoReapply(context: Context = application): Boolean =
        CarrierStore(context).load()?.autoReapply ?: true

    fun setAutoReapply(context: Context = application, enabled: Boolean) {
        CarrierStore(context).setAutoReapply(enabled)
    }

    fun apply(subId: Int, iso: String?, name: String?) {
        ensurePhoneState(application)
        val country = iso?.trim()?.lowercase().orEmpty()
        val carrier = name?.trim().orEmpty()
        if (country.isNotEmpty() && country.length != 2) {
            error("国家码必须是 2 位 ISO")
        }
        if (CarrierOverlay.isEmpty(CarrierOverlay.build(country, carrier))) {
            error("没有可写入的覆盖项")
        }
        CarrierInstrument.write(subId, country, carrier, reset = false)
    }

    fun reset(subId: Int) {
        ensurePhoneState(application)
        CarrierInstrument.write(subId, null, null, reset = true)
    }

    fun reapplyStored(context: Context = application) {
        ensurePhoneState(context)
        val stored = CarrierStore(context).load() ?: return
        if (!stored.autoReapply || stored.iso.isEmpty() || stored.subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return
        }
        CarrierInstrument.write(stored.subId, stored.iso, stored.name, reset = false)
    }

    private fun ensurePhoneState(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!Stellar.pingBinder()) return
        runCatching {
            Stellar.grantRuntimePermission(
                context.packageName,
                Manifest.permission.READ_PHONE_STATE,
                UserHandleCompat.myUserId()
            )
        }
    }
}
