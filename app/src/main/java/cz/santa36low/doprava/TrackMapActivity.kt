package cz.santa36low.doprava

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class TrackMapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activityId = intent.getLongExtra("activity_id", -1L)
        val points = Database(this).routePoints(activityId)
        if (points.size < 2) {
            Toast.makeText(this, "Pro tuto jízdu není zaznamenána úplná GPS trasa.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Google Maps URL accepts a limited number of intermediate waypoints.
        // Keep the first and last GPS point and sample the recorded track between them.
        val middle = points.drop(1).dropLast(1)
        // Google Maps URLs support up to three intermediate stops reliably in the mobile app.
        val maxWaypoints = 3
        val waypoints = if (middle.size <= maxWaypoints) {
            middle
        } else {
            (0 until maxWaypoints).map { index ->
                middle[(index.toLong() * (middle.size - 1) / (maxWaypoints - 1)).toInt()]
            }
        }
        fun coordinate(point: Database.RoutePoint): String =
            "${point.latitude},${point.longitude}"
        fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        val origin = coordinate(points.first())
        val destination = coordinate(points.last())
        val waypointParam = waypoints.takeIf { it.isNotEmpty() }
            ?.joinToString("|") { coordinate(it) }
        val url = buildString {
            append("https://www.google.com/maps/dir/?api=1")
            append("&origin=").append(encode(origin))
            append("&destination=").append(encode(destination))
            append("&travelmode=driving")
            if (waypointParam != null) append("&waypoints=").append(encode(waypointParam))
        }

        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "Google Mapy se nepodařilo otevřít.", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
