package com.siro.nav.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.siro.nav.models.NavPoint
import java.util.Date

class NavBackgroundService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private val CHANNEL_ID = "siro_nav_fg_channel"
    private val NOTIF_ID = 202611

    companion object {
        var onLocationUpdate: ((NavPoint) -> Unit)? = null

        fun start(context: Context, title: String = "ملاحة سيرو نشطة 🧭", text: String = "استمر في السير نحو وجهتك...") {
            val intent = Intent(context, NavBackgroundService::class.java).apply {
                putExtra("title", title)
                putExtra("text", text)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NavBackgroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("title") ?: "ملاحة سيرو نشطة 🧭"
        val text = intent?.getStringExtra("text") ?: "استمر في السير نحو وجهتك..."

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        startForeground(NOTIF_ID, notification)
        startLocationUpdates()

        return START_STICKY
    }

    private fun startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                2.0f,
                this
            )
        } catch (_: SecurityException) {}
    }

    override fun onLocationChanged(loc: Location) {
        val point = NavPoint(
            latitude = loc.latitude,
            longitude = loc.longitude,
            heading = if (loc.hasBearing()) loc.bearing.toDouble() else -1.0,
            speedKmh = if (loc.hasSpeed()) loc.speed * 3.6 else -1.0,
            altitude = loc.altitude,
            accuracy = if (loc.hasAccuracy()) loc.accuracy.toDouble() else 5.0,
            timestamp = Date(loc.time)
        )
        onLocationUpdate?.invoke(point)
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Siro Navigation Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }
}
