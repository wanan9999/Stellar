package roro.stellar.manager.carrier

import android.os.PersistableBundle

internal object CarrierOverlay {
    fun build(countryIso: String?, carrierName: String?): PersistableBundle {
        val bundle = PersistableBundle()
        val iso = countryIso?.trim()?.lowercase().orEmpty()
        if (iso.length == 2) {
            bundle.putString(CarrierKeys.SIM_COUNTRY_ISO, iso)
        }
        val name = carrierName?.trim().orEmpty()
        if (name.isNotEmpty()) {
            bundle.putBoolean(CarrierKeys.CARRIER_NAME_OVERRIDE, true)
            bundle.putString(CarrierKeys.CARRIER_NAME, name)
        }
        if (!isEmpty(bundle)) {
            bundle.putInt(CarrierKeys.MARKER, 1)
        }
        return bundle
    }

    fun isEmpty(bundle: PersistableBundle): Boolean = bundle.size() == 0
}
