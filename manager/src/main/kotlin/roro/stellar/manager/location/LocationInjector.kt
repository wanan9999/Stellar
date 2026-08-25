package roro.stellar.manager.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import roro.stellar.manager.compat.BuildUtils
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos

internal object LocationInjector {
    private val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        "fused"
    )
    private val attached = linkedSetOf<String>()

    @Synchronized
    fun attach(context: Context) {
        val lm = manager(context)
        attached.clear()
        providers.forEach { name ->
            runCatching {
                lm.addTestProvider(
                    name,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    true,
                    1,
                    1
                )
            }
            if (runCatching { lm.setTestProviderEnabled(name, true) }.isSuccess) {
                attached += name
            }
        }
        if (attached.isEmpty()) error("providers")
    }

    @Synchronized
    fun push(context: Context, lat: Double, lng: Double) {
        val lm = manager(context)
        val jittered = jitter(lat, lng)
        attached.toList().forEach { name ->
            val ok = runCatching {
                lm.setTestProviderLocation(name, location(name, jittered.first, jittered.second))
            }.isSuccess
            if (!ok) attached.remove(name)
        }
    }

    @Synchronized
    fun detach(context: Context) {
        val lm = manager(context)
        attached.toList().forEach { name ->
            runCatching { lm.setTestProviderEnabled(name, false) }
            runCatching { lm.removeTestProvider(name) }
        }
        attached.clear()
    }

    private fun manager(context: Context) =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private fun jitter(lat: Double, lng: Double): Pair<Double, Double> {
        val rand = ThreadLocalRandom.current()
        val north = rand.nextGaussian() * 0.000015
        val east = rand.nextGaussian() * 0.000015 / cos(Math.toRadians(lat)).coerceAtLeast(0.2)
        return lat + north to lng + east
    }

    private fun location(provider: String, lat: Double, lng: Double): Location {
        return Location(provider).apply {
            latitude = lat
            longitude = lng
            accuracy = 5f
            altitude = 10.0
            bearing = 0f
            speed = 0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (BuildUtils.atLeast26) {
                verticalAccuracyMeters = 8f
                speedAccuracyMetersPerSecond = 0.5f
                bearingAccuracyDegrees = 15f
            }
            if (BuildUtils.atLeast31) {
                isMock = true
            }
            runCatching { Location::class.java.getMethod("makeComplete").invoke(this) }
        }
    }
}
