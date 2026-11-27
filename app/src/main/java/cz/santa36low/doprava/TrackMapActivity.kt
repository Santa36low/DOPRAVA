package cz.santa36low.doprava

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView

class TrackMapActivity : Activity() {
    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        MapLibre.getInstance(this)
        super.onCreate(savedInstanceState)

        val activityId = intent.getLongExtra("activity_id", -1L)
        val points = Database(this).routePoints(activityId)
        if (points.size < 2) {
            Toast.makeText(this, "Pro tuto jízdu není zaznamenána úplná GPS trasa.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        mapView = MapView(this)
        setContentView(mapView)
        mapView.onCreate(savedInstanceState)

        val route = points.map { LatLng(it.latitude, it.longitude) }
        mapView.getMapAsync { map ->
            map.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                map.addPolyline(
                    PolylineOptions()
                        .addAll(route)
                        .color(Color.BLUE)
                        .width(5f)
                )

                val redMarker = IconFactory.getInstance(this)
                    .defaultMarker(0f)
                map.addMarker(
                    MarkerOptions()
                        .position(route.first())
                        .title("Začátek")
                        .icon(redMarker)
                )
                map.addMarker(
                    MarkerOptions()
                        .position(route.last())
                        .title("Konec")
                        .icon(redMarker)
                )

                val boundsBuilder = LatLngBounds.Builder()
                route.forEach { boundsBuilder.include(it) }
                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 90)
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::mapView.isInitialized) mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
    }

    override fun onPause() {
        if (::mapView.isInitialized) mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        if (::mapView.isInitialized) mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::mapView.isInitialized) mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::mapView.isInitialized) mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::mapView.isInitialized) mapView.onDestroy()
        super.onDestroy()
    }
}
