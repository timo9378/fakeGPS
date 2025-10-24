package com.koimsurai.fakegps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MockLocationService : Service() {

    private var mockLocationProvider: MockLocationProvider? = null
    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    companion object {
        const val CHANNEL_ID = "MockLocationServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_MOCK = "com.koimsurai.fakegps.ACTION_START_MOCK"
        const val ACTION_STOP_MOCK = "com.koimsurai.fakegps.ACTION_STOP_MOCK"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MOCK -> {
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val lon = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                startMockingLocation(lat, lon)
            }
            ACTION_STOP_MOCK -> {
                stopMockingLocation()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startMockingLocation(lat: Double, lon: Double) {
        try {
            mockLocationProvider = MockLocationProvider(LocationManager.GPS_PROVIDER, this)
            
            val notification = createNotification(lat, lon)
            startForeground(NOTIFICATION_ID, notification)

            serviceJob = serviceScope.launch {
                while (true) {
                    mockLocationProvider?.pushLocation(lat, lon)
                    delay(1000)
                }
            }
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun stopMockingLocation() {
        serviceJob?.cancel()
        mockLocationProvider?.shutdown()
        mockLocationProvider = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(lat: Double, lon: Double): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MockLocationService::class.java).apply {
            action = ACTION_STOP_MOCK
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, lat, lon))
            .setSmallIcon(R.drawable.ic_map)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, getString(R.string.notification_action_stop), stopPendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMockingLocation()
        super.onDestroy()
    }
}