package roro.stellar.manager.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.SystemClock
import roro.stellar.manager.compat.BuildUtils
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos

internal object LocationInjector {
    private val providers = listOf("gps", "network", "fused")
    private var useCmd = true
    private val attached = linkedSetOf<String>()

    fun attach(context: Context) {
        MockLocationOps.ensureSelected()
        useCmd = hasCmdLocation()
        if (useCmd) attachCmd() else attachManager(context)
        GmsFused.attach(context)
    }

    fun push(context: Context, lat: Double, lng: Double) {
        val point = jitter(lat, lng)
        if (useCmd) pushCmd(point.first, point.second) else pushManager(context, point.first, point.second)
        GmsFused.push(point.first, point.second)
    }

    fun detach(context: Context) {
        if (useCmd) detachCmd() else detachManager(context)
        GmsFused.detach()
        attached.clear()
    }

    private fun hasCmdLocation(): Boolean {
        val help = runCatching { MockLocationOps.exec("cmd location -h", 5) }.getOrNull() ?: return false
        return !help.failed && (help.text.contains("providers") || help.code == 0)
    }

    private fun attachCmd() {
        attached.clear()
        runCatching { MockLocationOps.exec("cmd location set-location-enabled true") }
        providers.forEach { name ->
            MockLocationOps.exec("cmd location providers add-test-provider $name")
            val enabled = MockLocationOps.exec("cmd location providers set-test-provider-enabled $name true")
            if (!enabled.failed) attached += name
        }
        if (attached.isEmpty()) error("cmd_location")
    }

    private fun pushCmd(lat: Double, lng: Double) {
        val loc = String.format(Locale.US, "%.6f,%.6f", lat, lng)
        val script = attached.joinToString("\n") { name ->
            "cmd location providers set-test-provider-location $name --location $loc --accuracy 1"
        }
        val result = MockLocationOps.exec(script, 5)
        if (result.failed) error("inject")
    }

    private fun detachCmd() {
        attached.forEach { name ->
            MockLocationOps.exec("cmd location providers set-test-provider-enabled $name false")
            MockLocationOps.exec("cmd location providers remove-test-provider $name")
        }
    }

    private fun attachManager(context: Context) {
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
                    ProviderProperties.POWER_USAGE_LOW,
                    ProviderProperties.ACCURACY_FINE
                )
            }
            if (runCatching { lm.setTestProviderEnabled(name, true) }.isSuccess) {
                attached += name
            }
        }
        if (attached.isEmpty()) error("providers")
    }

    private fun pushManager(context: Context, lat: Double, lng: Double) {
        val lm = manager(context)
        var ok = 0
        attached.toList().forEach { name ->
            val success = runCatching {
                lm.setTestProviderLocation(name, location(name, lat, lng))
            }.isSuccess
            if (success) ok++ else attached.remove(name)
        }
        if (ok == 0) error("inject")
    }

    private fun detachManager(context: Context) {
        val lm = manager(context)
        attached.toList().forEach { name ->
            runCatching { lm.setTestProviderEnabled(name, false) }
            runCatching { lm.removeTestProvider(name) }
        }
    }

    private fun manager(context: Context) =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private fun jitter(lat: Double, lng: Double): Pair<Double, Double> {
        val rand = ThreadLocalRandom.current()
        val north = rand.nextGaussian() * 0.000008
        val east = rand.nextGaussian() * 0.000008 / cos(Math.toRadians(lat)).coerceAtLeast(0.2)
        return lat + north to lng + east
    }

    private fun location(provider: String, lat: Double, lng: Double): Location {
        return Location(provider).apply {
            latitude = lat
            longitude = lng
            accuracy = 1f
            altitude = 10.0
            bearing = 0f
            speed = 0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (BuildUtils.atLeast26) {
                verticalAccuracyMeters = 3f
                speedAccuracyMetersPerSecond = 0.5f
                bearingAccuracyDegrees = 15f
            }
            if (BuildUtils.atLeast31) isMock = true
            runCatching { Location::class.java.getMethod("makeComplete").invoke(this) }
        }
    }
}

private object GmsFused {
    private var client: Any? = null
    private var setMockLocation: java.lang.reflect.Method? = null

    fun attach(context: Context) {
        detach()
        runCatching {
            val services = Class.forName("com.google.android.gms.location.LocationServices")
            val fused = services.getMethod("getFusedLocationProviderClient", Context::class.java)
                .invoke(null, context) ?: return
            fused.javaClass.getMethod("setMockMode", java.lang.Boolean.TYPE).invoke(fused, true)
            setMockLocation = fused.javaClass.getMethod("setMockLocation", Location::class.java)
            client = fused
        }
    }

    fun push(lat: Double, lng: Double) {
        val fused = client ?: return
        val method = setMockLocation ?: return
        val location = Location("fused").apply {
            latitude = lat
            longitude = lng
            accuracy = 1f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (BuildUtils.atLeast31) isMock = true
            runCatching { Location::class.java.getMethod("makeComplete").invoke(this) }
        }
        runCatching { method.invoke(fused, location) }
    }

    fun detach() {
        runCatching {
            client?.javaClass?.getMethod("setMockMode", java.lang.Boolean.TYPE)?.invoke(client, false)
        }
        client = null
        setMockLocation = null
    }
}
