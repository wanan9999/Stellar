package roro.stellar.manager.carrier

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import roro.stellar.Stellar
import roro.stellar.manager.util.Logger.Companion.LOGGER

object CarrierReapply {
    @Volatile private var running = false

    fun onServiceReady() {
        if (!Stellar.pingBinder() || running) return
        running = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                delay(1200)
                val result = CarrierClient.ensure().reapplyStored()
                if (!result.getBoolean(CarrierKeys.OK)) {
                    LOGGER.d("carrier reapply skipped: ${result.getString(CarrierKeys.MESSAGE)}")
                } else {
                    LOGGER.d("carrier reapply: ${result.getString(CarrierKeys.STRATEGY)}")
                }
            } catch (e: Exception) {
                LOGGER.w("carrier reapply failed: ${e.message}")
            } finally {
                running = false
            }
        }
    }
}
