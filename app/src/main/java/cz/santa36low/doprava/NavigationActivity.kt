package cz.santa36low.doprava

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class NavigationActivity : Activity() {
    private lateinit var mapView: MapView
    private lateinit var destinationInput: EditText
    private lateinit var statusText: TextView
    private lateinit var routeButton: Button
    private lateinit var routePreference: RadioGroup
    private lateinit var progress: ProgressBar
    private var currentLocation: Location? = null
    private var vehicleHeight = 0.0
    private var vehicleWidth = 0.0
    private var vehicleWeight = 0.0
    private var routePoints: List<LatLng> = emptyList()
    private var mapReady = false
    private var routeSummary = ""
    // Default route style: prefer main roads.
    private var preferMainRoads = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        buildLayout()
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            map.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                mapReady = true
                map.uiSettings.isLogoEnabled = true
                drawRoute()
                currentLocation?.let { map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 13.0)) }
            }
        }
        loadSelectedVehicle()
        locateUser()
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(12, 12, 12, 8)
        }
        val title = TextView(this).apply {
            text = "Plánování trasy"
            textSize = 21f
            setTextColor(Color.rgb(20, 54, 86))
            setPadding(4, 4, 4, 8)
        }
        root.addView(title)
        destinationInput = EditText(this).apply {
            hint = "Zadej cílové místo nebo adresu"
            isSingleLine = true
            textSize = 16f
        }
        root.addView(destinationInput, LinearLayout.LayoutParams(-1, -2))
        val preferenceTitle = TextView(this).apply {
            text = "Možnosti trasy"
            textSize = 14f
            setTextColor(Color.rgb(20, 54, 86))
            setPadding(4, 8, 4, 2)
        }
        root.addView(preferenceTitle)
        routePreference = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            val mainRoads = RadioButton(this@NavigationActivity).apply {
                id = View.generateViewId()
                text = "Upřednostňovat hlavní komunikace"
                isChecked = true
            }
            val balanced = RadioButton(this@NavigationActivity).apply {
                id = View.generateViewId()
                text = "Vyvážená trasa"
            }
            addView(mainRoads)
            addView(balanced)
            setOnCheckedChangeListener { group, checkedId ->
                preferMainRoads = checkedId == mainRoads.id
            }
        }
        root.addView(routePreference)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        routeButton = Button(this).apply {
            text = "Vypočítat trasu"
            setOnClickListener { calculateRoute() }
        }
        actions.addView(routeButton, LinearLayout.LayoutParams(0, -2, 1f))
        progress = ProgressBar(this).apply { visibility = View.GONE }
        actions.addView(progress, LinearLayout.LayoutParams(48, 48))
        root.addView(actions)
        statusText = TextView(this).apply {
            text = "Načítám vybrané vozidlo a aktuální polohu…"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(4, 4, 4, 8)
        }
        root.addView(statusText)
        mapView = MapView(this)
        root.addView(mapView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun loadSelectedVehicle() {
        val userId = intent.getStringExtra("user_id")
        if (userId.isNullOrBlank()) {
            statusText.text = "Přihlas se a vyber vozidlo v nabídce Vozidla."
            routeButton.isEnabled = false
            return
        }
        FirebaseFirestore.getInstance().collection("users").document(userId).collection("vehicles")
            .whereEqualTo("selected", true).limit(1).get()
            .addOnSuccessListener { result ->
                val doc = result.documents.firstOrNull()
                if (doc == null) {
                    statusText.text = "Nejdříve přidej a vyber vozidlo v nabídce Vozidla."
                    routeButton.isEnabled = false
                    return@addOnSuccessListener
                }
                vehicleHeight = doc.getDouble("heightMeters") ?: 0.0
                vehicleWidth = doc.getDouble("widthMeters") ?: 0.0
                vehicleWeight = doc.getDouble("weightTons") ?: 0.0
                val plate = doc.getString("plate").orEmpty()
                if (vehicleHeight <= 0 || vehicleWidth <= 0 || vehicleWeight <= 0) {
                    statusText.text = "Vozidlo $plate: doplň hmotnost, výšku a šířku v nabídce Vozidla."
                    routeButton.isEnabled = false
                } else {
                    statusText.text = "Vozidlo $plate • ${vehicleWeight} t • výška ${vehicleHeight} m • šířka ${vehicleWidth} m"
                    routeButton.isEnabled = true
                }
            }
            .addOnFailureListener { error ->
                statusText.text = "Vozidlo se nepodařilo načíst: ${error.localizedMessage ?: "chyba"}"
                routeButton.isEnabled = false
            }
    }

    private fun locateUser() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Aplikace nemá povolený přístup k poloze. Povol ho v nastavení telefonu."
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(this)
        val token = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = location
                    if (mapReady) mapView.getMapAsync { it.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 13.0)) }
                    if (statusText.text.toString().startsWith("Načítám")) statusText.text = "Poloha připravena. Zadej cíl a vypočítej trasu."
                } else {
                    client.lastLocation.addOnSuccessListener { last ->
                        currentLocation = last
                        if (last == null) statusText.text = "Nepodařilo se získat polohu. Zkontroluj GPS."
                        else statusText.text = "Poloha připravena. Zadej cíl a vypočítej trasu."
                    }
                }
            }
            .addOnFailureListener { error -> statusText.text = "Poloha není dostupná: ${error.localizedMessage ?: "chyba"}" }
    }

    private fun calculateRoute() {
        val origin = currentLocation
        val destination = destinationInput.text.toString().trim()
        if (origin == null) {
            Toast.makeText(this, "Nejdříve počkej na zjištění aktuální polohy.", Toast.LENGTH_LONG).show()
            locateUser()
            return
        }
        if (destination.isBlank()) {
            destinationInput.error = "Zadej cíl cesty"
            return
        }
        if (vehicleHeight <= 0 || vehicleWidth <= 0 || vehicleWeight <= 0) {
            statusText.text = "Nejdříve doplň parametry vybraného vozidla."
            return
        }
        routeButton.isEnabled = false
        progress.visibility = View.VISIBLE
        statusText.text = "Vyhledávám cíl a počítám trasu pro nákladní vozidlo…"
        thread {
            try {
                val target = geocode(destination)
                val route = requestTruckRoute(origin.latitude, origin.longitude, target.first, target.second, preferMainRoads)
                runOnUiThread {
                    routePoints = route.first
                    routeSummary = route.second
                    statusText.text = routeSummary
                    routeButton.isEnabled = true
                    progress.visibility = View.GONE
                    if (mapReady) drawRoute()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Trasu se nepodařilo vypočítat: ${e.message ?: "neznámá chyba"}"
                    routeButton.isEnabled = true
                    progress.visibility = View.GONE
                }
            }
        }
    }

    private fun geocode(query: String): Pair<Double, Double> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val connection = (URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=$encoded").openConnection() as HttpURLConnection)
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "Doprava-Android/1.0 (cz.santa36low.doprava)")
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val result = org.json.JSONArray(body)
            if (result.length() == 0) throw IllegalStateException("Cíl se nepodařilo najít. Zkus přesnější adresu.")
            val item = result.getJSONObject(0)
            return item.getString("lat").toDouble() to item.getString("lon").toDouble()
        } finally { connection.disconnect() }
    }

    private fun requestTruckRoute(startLat: Double, startLon: Double, endLat: Double, endLon: Double, preferMainRoads: Boolean): Pair<List<LatLng>, String> {
        val connection = (URL("https://valhalla1.openstreetmap.de/route").openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("User-Agent", "Doprava-Android/1.0 (cz.santa36low.doprava)")
        connection.setRequestProperty("X-Client-Id", "cz.santa36low.doprava")
        val request = JSONObject().apply {
            put("locations", org.json.JSONArray().put(JSONObject().put("lat", startLat).put("lon", startLon))
                .put(JSONObject().put("lat", endLat).put("lon", endLon)))
            put("costing", "truck")
            put("units", "kilometers")
            put("language", "cs-CZ")
            put("costing_options", JSONObject().put("truck", JSONObject()
                .put("height", vehicleHeight)
                .put("width", vehicleWidth)
                .put("weight", vehicleWeight)
                .put("use_highways", if (preferMainRoads) 0.95 else 0.45)
                .put("low_class_factor", if (preferMainRoads) 3.0 else 1.0)))
        }
        try {
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) throw IllegalStateException("Směrovací služba vrátila chybu ${connection.responseCode}: ${body.take(220)}")
            val json = JSONObject(body)
            val trip = json.optJSONObject("trip") ?: throw IllegalStateException(json.optJSONObject("error")?.optString("message") ?: "Trasa nebyla nalezena.")
            val legs = trip.getJSONArray("legs")
            val shape = legs.getJSONObject(0).getString("shape")
            val points = decodePolyline6(shape)
            if (points.size < 2) throw IllegalStateException("Směrovací služba nevrátila použitelnou trasu.")
            val summary = trip.getJSONObject("summary")
            val km = summary.optDouble("length", 0.0)
            val minutes = (summary.optDouble("time", 0.0) / 60.0).roundToInt()
            return points to "Trasa: ${"%.1f".format(java.util.Locale("cs", "CZ"), km)} km • přibližně ${minutes} min"
        } finally { connection.disconnect() }
    }

    private fun decodePolyline6(encoded: String): List<LatLng> {
        val result = ArrayList<LatLng>()
        var index = 0
        var lat = 0
        var lon = 0
        while (index < encoded.length) {
            var shift = 0
            var value = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                value = value or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lat += if ((value and 1) != 0) (value shr 1).inv() else value shr 1
            shift = 0
            value = 0
            do {
                b = encoded[index++].code - 63
                value = value or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lon += if ((value and 1) != 0) (value shr 1).inv() else value shr 1
            result.add(LatLng(lat / 1e6, lon / 1e6))
        }
        return result
    }

    private fun drawRoute() {
        if (!mapReady || routePoints.isEmpty()) return
        mapView.getMapAsync { map ->
            map.clear()
            map.addPolyline(PolylineOptions().addAll(routePoints).color(Color.rgb(20, 105, 180)).width(6f))
            map.addMarker(MarkerOptions().position(routePoints.first()).title("Start"))
            map.addMarker(MarkerOptions().position(routePoints.last()).title("Cíl"))
            val bounds = LatLngBounds.Builder()
            routePoints.forEach { bounds.include(it) }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 70))
        }
    }

    override fun onStart() { super.onStart(); if (::mapView.isInitialized) mapView.onStart() }
    override fun onResume() { super.onResume(); if (::mapView.isInitialized) mapView.onResume() }
    override fun onPause() { if (::mapView.isInitialized) mapView.onPause(); super.onPause() }
    override fun onStop() { if (::mapView.isInitialized) mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); if (::mapView.isInitialized) mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { if (::mapView.isInitialized) mapView.onSaveInstanceState(outState); super.onSaveInstanceState(outState) }
    override fun onDestroy() { if (::mapView.isInitialized) mapView.onDestroy(); super.onDestroy() }
}
