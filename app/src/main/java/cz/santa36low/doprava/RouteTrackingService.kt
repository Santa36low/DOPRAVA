package cz.santa36low.doprava

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.google.android.gms.location.*

class RouteTrackingService : Service() {
    private lateinit var client: FusedLocationProviderClient
    private var callback: LocationCallback? = null
    private var lastSaved: android.location.Location? = null
    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("route_tracking","Záznam jízdy",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra("activity_id", -1L) ?: -1L
        val notification: Notification = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this,"route_tracking").setContentTitle("Doprava").setContentText("Zaznamenávám trasu jízdy").setSmallIcon(android.R.drawable.ic_menu_mylocation).build()
        else Notification.Builder(this).setContentTitle("Doprava").setContentText("Zaznamenávám trasu jízdy").setSmallIcon(android.R.drawable.ic_menu_mylocation).build()
        startForeground(21, notification)
        if (id > 0 && callback == null) {
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 20000).setMinUpdateDistanceMeters(40f).build()
            callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.locations.forEach { location ->
                        // Ignore poor fixes and stationary GPS drift to save battery and keep distance realistic.
                        if (location.accuracy > 60f) return@forEach
                        val previous = lastSaved
                        if (previous == null) {
                            Database(this@RouteTrackingService).addRoutePoint(id, location.latitude, location.longitude, location.time)
                            lastSaved = location
                        } else {
                            val distance = previous.distanceTo(location)
                            val elapsed = (location.time - previous.time).coerceAtLeast(1L) / 1000f
                            val speed = distance / elapsed
                            if (distance >= 35f && speed <= 50f) {
                                Database(this@RouteTrackingService).addRoutePoint(id, location.latitude, location.longitude, location.time)
                                lastSaved = location
                            }
                        }
                    }
                }
            }
            try { client.requestLocationUpdates(request, callback!!, mainLooper) } catch (_: SecurityException) { stopSelf() }
        }
        return START_STICKY
    }
    override fun onDestroy() { callback?.let { client.removeLocationUpdates(it) }; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
