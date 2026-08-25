package roro.stellar.manager.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import roro.stellar.manager.MainActivity
import roro.stellar.manager.R
import roro.stellar.manager.compat.BuildUtils

class LocationMockService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            startAsForeground(LocationController.snapshot.value.label)
            LocationController.markStopped()
            stopInternal()
            return START_NOT_STICKY
        }
        if (intent == null) LocationController.restoreIfRunning()
        val snap = LocationController.snapshot.value
        if (!snap.active) {
            startAsForeground(snap.label)
            stopInternal()
            return START_NOT_STICKY
        }
        startAsForeground(snap.label)
        startLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        runCatching { LocationInjector.detach(this) }
        scope.cancel()
        super.onDestroy()
    }

    private fun startLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            LocationInjector.attach(this@LocationMockService)
            while (isActive && LocationController.snapshot.value.active) {
                val current = LocationController.snapshot.value
                LocationInjector.push(this@LocationMockService, current.lat, current.lng)
                delay(1000)
            }
        }
    }

    private fun startAsForeground(label: String) {
        ensureChannel()
        val notification = buildNotification(label)
        if (BuildUtils.atLeast34) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopInternal() {
        loop?.cancel()
        loop = null
        runCatching { LocationInjector.detach(this) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureChannel() {
        if (!BuildUtils.atLeast26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.location_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
    }

    private fun buildNotification(label: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, LocationMockService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (label.isBlank()) {
            getString(R.string.location_notification_generic)
        } else {
            getString(R.string.location_notification, label)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stellar)
            .setContentTitle(getString(R.string.tool_location))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.location_stop), stop)
            .build()
    }

    companion object {
        const val ACTION_STOP = "roro.stellar.manager.action.STOP_MOCK_LOCATION"
        private const val CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 2401
    }
}
