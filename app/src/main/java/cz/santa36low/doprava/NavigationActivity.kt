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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import android.os.Looper
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
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
    private data class NavManeuver(val instruction: String, val endPointIndex: Int)

    private lateinit var mapView: MapView
    private lateinit var startInput: EditText
    private lateinit var destinationInput: EditText
    private lateinit var stopsContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var previewButton: Button
    private lateinit var navigateButton: Button
    private lateinit var routePreference: RadioGroup
    private lateinit var progress: ProgressBar
    private lateinit var guidanceText: TextView
    private val stopInputs = mutableListOf<EditText>()
    private var currentLocation: Location? = null
    private var vehicleHeight = 0.0
    private var vehicleWidth = 0.0
    private var vehicleWeight = 0.0
    private var routePoints: List<LatLng> = emptyList()
    private var mapReady = false
    private var routeSummary = ""
    private var preferMainRoads = true
    private var navigationStarted = false
    private var maneuvers: List<NavManeuver> = emptyList()
    private var isRecalculating = false
    private var lastRerouteAt = 0L
    private var currentManeuverIndex = 0
    private var locationCallback: LocationCallback? = null

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
            setBackgroundColor(Color.rgb(241, 245, 249))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 10)
            setBackgroundColor(Color.WHITE)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "Kamionová navigace"
            textSize = 21f
            setTextColor(Color.rgb(20, 54, 86))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply {
            text = "Zavřít"
            setOnClickListener { finish() }
        })
        panel.addView(header)
        startInput = field("Odkud – prázdné = moje poloha")
        panel.addView(startInput)
        destinationInput = field("Kam chceš jet?")
        panel.addView(destinationInput)
        val placeActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        placeActions.addView(Button(this).apply {
            text = "☆ Uložit cíl"
            setOnClickListener { saveFavoriteDestination() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        placeActions.addView(Button(this).apply {
            text = "Oblíbené / nedávné"
            setOnClickListener { showSavedPlaces() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(placeActions)
        stopsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(stopsContainer)
        panel.addView(Button(this).apply {
            text = "+ Přidat zastávku"
            setOnClickListener { addStopField() }
        })
        val optionsLabel = TextView(this).apply {
            text = "Typ trasy"
            textSize = 13f
            setTextColor(Color.rgb(70, 85, 100))
            setPadding(2, 6, 2, 0)
        }
        panel.addView(optionsLabel)
        routePreference = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            val main = RadioButton(this@NavigationActivity).apply {
                id = View.generateViewId()
                text = "Upřednostňovat hlavní komunikace"
                isChecked = true
            }
            val balanced = RadioButton(this@NavigationActivity).apply {
                id = View.generateViewId()
                text = "Vyvážená trasa"
            }
            addView(main)
            addView(balanced)
            setOnCheckedChangeListener { _, checked -> preferMainRoads = checked == main.id }
        }
        panel.addView(routePreference)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        previewButton = Button(this).apply {
            text = "Zobrazit trasu"
            setOnClickListener { calculateRoute() }
        }
        actions.addView(previewButton, LinearLayout.LayoutParams(0, -2, 1f))
        progress = ProgressBar(this).apply { visibility = View.GONE }
        actions.addView(progress, LinearLayout.LayoutParams(44, 44))
        panel.addView(actions)
        navigateButton = Button(this).apply {
            text = "Navigovat"
            isEnabled = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(22, 145, 85))
            setOnClickListener { startNavigationMode() }
        }
        panel.addView(navigateButton, LinearLayout.LayoutParams(-1, -2))
        statusText = TextView(this).apply {
            text = "Zjišťuji polohu a načítám parametry vybraného vozidla…"
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(3, 5, 3, 2)
        }
        panel.addView(statusText)
        guidanceText = TextView(this).apply {
            text = "Po výpočtu trasy se zde zobrazí pokyny k jízdě."
            textSize = 17f
            setTextColor(Color.rgb(20, 54, 86))
            setPadding(8, 10, 8, 10)
            setBackgroundColor(Color.rgb(232, 240, 248))
        }
        panel.addView(guidanceText)
        val scroll = ScrollView(this).apply { isFillViewport = false; addView(panel) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        mapView = MapView(this)
        root.addView(mapView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        isSingleLine = true
        textSize = 15f
        setPadding(10, 7, 10, 7)
    }

    private fun savedPlaces() = getSharedPreferences("navigation_places", MODE_PRIVATE)

    private fun readPlaceList(key: String): MutableList<String> {
        val array = try { JSONArray(savedPlaces().getString(key, "[]")) } catch (_: Exception) { JSONArray() }
        return MutableList(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }.toMutableList()
    }

    private fun writePlaceList(key: String, values: List<String>) {
        val array = JSONArray()
        values.distinct().take(12).forEach { array.put(it) }
        savedPlaces().edit().putString(key, array.toString()).apply()
    }

    private fun saveFavoriteDestination() {
        val destination = destinationInput.text.toString().trim()
        if (destination.isBlank()) {
            destinationInput.error = "Nejdříve zadej cíl"
            return
        }
        val favorites = readPlaceList("favorites")
        if (favorites.none { it.equals(destination, true) }) favorites.add(0, destination)
        writePlaceList("favorites", favorites)
        Toast.makeText(this, "Cíl uložený mezi oblíbené", Toast.LENGTH_SHORT).show()
    }

    private fun rememberRecentDestination(destination: String) {
        val recent = readPlaceList("recent")
        recent.removeAll { it.equals(destination, true) }
        recent.add(0, destination)
        writePlaceList("recent", recent)
    }

    private fun showSavedPlaces() {
        val favorites = readPlaceList("favorites")
        val recent = readPlaceList("recent")
        val all = (favorites.map { "★  $it" } + recent.filter { item -> favorites.none { it.equals(item, true) } }.map { "Nedávné: $it" })
        if (all.isEmpty()) {
            Toast.makeText(this, "Zatím nemáš uložená místa ani nedávné cíle.", Toast.LENGTH_SHORT).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Oblíbené a nedávné cíle")
            .setItems(all.toTypedArray()) { _, which ->
                val selected = all[which].removePrefix("★  ").removePrefix("Nedávné: ")
                destinationInput.setText(selected)
                destinationInput.setSelection(destinationInput.text.length)
            }
            .setNegativeButton("Zavřít", null)
            .show()
    }

    private fun addStopField() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val input = field("Zastávka")
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply {
            text = "×"
            setOnClickListener {
                stopInputs.remove(input)
                stopsContainer.removeView(row)
            }
        })
        stopInputs.add(input)
        stopsContainer.addView(row)
    }

    private fun loadSelectedVehicle() {
        val userId = intent.getStringExtra("user_id")
        if (userId.isNullOrBlank()) {
            statusText.text = "Přihlas se a vyber vozidlo v nabídce Vozidla."
            previewButton.isEnabled = false
            return
        }
        FirebaseFirestore.getInstance().collection("users").document(userId).collection("vehicles")
            .whereEqualTo("selected", true).limit(1).get()
            .addOnSuccessListener { result ->
                val doc = result.documents.firstOrNull()
                if (doc == null) {
                    statusText.text = "Nejdříve přidej a vyber vozidlo v nabídce Vozidla."
                    previewButton.isEnabled = false
                    return@addOnSuccessListener
                }
                vehicleHeight = doc.getDouble("heightMeters") ?: 0.0
                vehicleWidth = doc.getDouble("widthMeters") ?: 0.0
                vehicleWeight = doc.getDouble("weightTons") ?: 0.0
                val plate = doc.getString("plate").orEmpty()
                if (vehicleHeight <= 0 || vehicleWidth <= 0 || vehicleWeight <= 0) {
                    statusText.text = "Vozidlo $plate: doplň hmotnost, výšku a šířku v nabídce Vozidla."
                    previewButton.isEnabled = false
                } else {
                    statusText.text = "Vozidlo $plate • ${vehicleWeight} t • výška ${vehicleHeight} m • šířka ${vehicleWidth} m"
                    previewButton.isEnabled = true
                }
            }
            .addOnFailureListener { error ->
                statusText.text = "Vozidlo se nepodařilo načíst: ${error.localizedMessage ?: "chyba"}"
                previewButton.isEnabled = false
            }
    }

    private fun locateUser() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Povol aplikaci přístup k poloze v nastavení telefonu."
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(this)
        val token = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = location
                    if (mapReady) mapView.getMapAsync { it.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 13.0)) }
                    if (startInput.text.isNullOrBlank()) startInput.hint = "Odkud – moje poloha"
                    if (statusText.text.toString().startsWith("Zjišťuji")) statusText.text = "Poloha připravena. Zadej cíl a zobraz náhled trasy."
                } else {
                    client.lastLocation.addOnSuccessListener { last ->
                        currentLocation = last
                        if (last == null) statusText.text = "Nepodařilo se získat polohu. Zkontroluj GPS."
                        else if (statusText.text.toString().startsWith("Zjišťuji")) statusText.text = "Poloha připravena. Zadej cíl a zobraz náhled trasy."
                    }
                }
            }
            .addOnFailureListener { error -> statusText.text = "Poloha není dostupná: ${error.localizedMessage ?: "chyba"}" }
    }

    private fun calculateRoute(forceCurrentStart: Boolean = false) {
        val destination = destinationInput.text.toString().trim()
        if (destination.isBlank()) {
            destinationInput.error = "Zadej cíl cesty"
            return
        }
        if (vehicleHeight <= 0 || vehicleWidth <= 0 || vehicleWeight <= 0) {
            statusText.text = "Nejdříve doplň parametry vybraného vozidla."
            return
        }
        val startText = if (forceCurrentStart) "" else startInput.text.toString().trim()
        val stopTexts = stopInputs.map { it.text.toString().trim() }.filter { it.isNotBlank() }
        previewButton.isEnabled = false
        navigateButton.isEnabled = false
        progress.visibility = View.VISIBLE
        statusText.text = "Vyhledávám místa a plánuji trasu pro vybrané nákladní vozidlo…"
        thread {
            try {
                val locations = ArrayList<Pair<Double, Double>>()
                locations.add(if (startText.isBlank() || startText.equals("moje poloha", true)) {
                    val loc = currentLocation ?: throw IllegalStateException("Aktuální poloha není dostupná. Zadej start ručně.")
                    loc.latitude to loc.longitude
                } else geocode(startText))
                stopTexts.forEach { locations.add(geocode(it)) }
                locations.add(geocode(destination))
                val route = requestTruckRoute(locations, preferMainRoads)
                runOnUiThread {
                    routePoints = route.first
                    rememberRecentDestination(destination)
                    routeSummary = route.second
                    maneuvers = route.third
                    currentManeuverIndex = 0
                    if (!forceCurrentStart) navigationStarted = false
                    isRecalculating = false
                    statusText.text = if (forceCurrentStart && navigationStarted) "Trasa byla automaticky přepočítána." else routeSummary + " • Zkontroluj trasu a stiskni Navigovat."
                    previewButton.isEnabled = true
                    navigateButton.isEnabled = true
                    navigateButton.text = if (navigationStarted) "Ukončit navigaci" else "Navigovat"
                    progress.visibility = View.GONE
                    if (mapReady) drawRoute()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isRecalculating = false
                    statusText.text = "Trasu se nepodařilo vypočítat: ${e.message ?: "neznámá chyba"}"
                    previewButton.isEnabled = true
                    progress.visibility = View.GONE
                }
            }
        }
    }

    private fun startNavigationMode() {
        if (routePoints.isEmpty()) return
        navigationStarted = true
        navigateButton.text = "Ukončit navigaci"
        navigateButton.isEnabled = true
        navigateButton.setOnClickListener {
            stopNavigationUpdates()
            navigationStarted = false
            navigateButton.text = "Navigovat"
            navigateButton.isEnabled = true
            guidanceText.text = "Navigace byla ukončena."
            statusText.text = routeSummary
        }
        statusText.text = "Navigace je aktivní • sleduji tvoji polohu a trasu."
        updateGuidance(currentLocation)
        startNavigationUpdates()
    }

    private fun startNavigationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Pro navigování povol přístup k poloze."
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                currentLocation = location
                if (!navigationStarted || routePoints.isEmpty()) return
                updateGuidance(location)
                val distanceToRoute = distanceToRouteMeters(location)
                val now = System.currentTimeMillis()
                if (distanceToRoute > 100f && !isRecalculating && now - lastRerouteAt > 15000L) {
                    lastRerouteAt = now
                    isRecalculating = true
                    statusText.text = "Jsi mimo trasu (${distanceToRoute.roundToInt()} m). Přepočítávám…"
                    calculateRoute(forceCurrentStart = true)
                }
                if (mapReady) mapView.getMapAsync { map ->
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 16.5))
                }
            }
        }
        LocationServices.getFusedLocationProviderClient(this)
            .requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    private fun stopNavigationUpdates() {
        locationCallback?.let { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(it) }
        locationCallback = null
    }

    private fun distanceToRouteMeters(location: Location): Float {
        var best = Float.MAX_VALUE
        routePoints.forEach { point ->
            val result = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, point.latitude, point.longitude, result)
            if (result[0] < best) best = result[0]
        }
        return best
    }

    private fun updateGuidance(location: Location?) {
        if (location == null || routePoints.isEmpty()) return
        var nearestIndex = 0
        var nearestDistance = Float.MAX_VALUE
        routePoints.forEachIndexed { index, point ->
            val result = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, point.latitude, point.longitude, result)
            if (result[0] < nearestDistance) {
                nearestDistance = result[0]
                nearestIndex = index
            }
        }
        while (currentManeuverIndex < maneuvers.lastIndex &&
            nearestIndex > maneuvers[currentManeuverIndex].endPointIndex) currentManeuverIndex++
        val next = maneuvers.getOrNull(currentManeuverIndex)
        guidanceText.text = if (next != null) {
            "DALŠÍ POKYN\n${next.instruction}\n\nVzdálenost od trasy: ${nearestDistance.roundToInt()} m"
        } else {
            "Pokračuj po trase k cíli.\nVzdálenost od trasy: ${nearestDistance.roundToInt()} m"
        }
        val destination = routePoints.last()
        val destinationDistance = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, destination.latitude, destination.longitude, destinationDistance)
        if (destinationDistance[0] < 45f) {
            guidanceText.text = "DORAZIL JSI DO CÍLE"
            statusText.text = "Cíl dosažen."
            stopNavigationUpdates()
            navigationStarted = false
            navigateButton.text = "Navigovat znovu"
            navigateButton.isEnabled = true
            navigateButton.setOnClickListener { startNavigationMode() }
        }
    }

    private fun geocode(query: String): Pair<Double, Double> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val connection = URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=$encoded").openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "Doprava-Android/1.0 (cz.santa36low.doprava)")
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val result = JSONArray(body)
            if (result.length() == 0) throw IllegalStateException("Místo nebylo nalezeno: $query")
            val item = result.getJSONObject(0)
            return item.getString("lat").toDouble() to item.getString("lon").toDouble()
        } finally { connection.disconnect() }
    }

    private fun requestTruckRoute(locations: List<Pair<Double, Double>>, preferMainRoads: Boolean): Triple<List<LatLng>, String, List<NavManeuver>> {
        val connection = URL("https://valhalla1.openstreetmap.de/route").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("User-Agent", "Doprava-Android/1.0 (cz.santa36low.doprava)")
        val stops = JSONArray()
        locations.forEach { (lat, lon) -> stops.put(JSONObject().put("lat", lat).put("lon", lon)) }
        val request = JSONObject().apply {
            put("locations", stops)
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
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("Směrovací služba vrátila chybu $code: ${body.take(220)}")
            val json = JSONObject(body)
            val trip = json.optJSONObject("trip") ?: throw IllegalStateException(json.optJSONObject("error")?.optString("message") ?: "Trasa nebyla nalezena.")
            val legs = trip.getJSONArray("legs")
            val points = ArrayList<LatLng>()
            val parsedManeuvers = ArrayList<NavManeuver>()
            var pointOffset = 0
            for (i in 0 until legs.length()) {
                val leg = legs.getJSONObject(i)
                val legPoints = decodePolyline6(leg.getString("shape"))
                points.addAll(legPoints)
                val steps = leg.optJSONArray("maneuvers") ?: JSONArray()
                for (j in 0 until steps.length()) {
                    val step = steps.getJSONObject(j)
                    val instruction = step.optString("instruction").ifBlank { "Pokračuj podle trasy" }
                    val endIndex = pointOffset + step.optInt("end_shape_index", legPoints.lastIndex)
                    parsedManeuvers.add(NavManeuver(instruction, endIndex.coerceAtLeast(0)))
                }
                pointOffset += legPoints.size
            }
            if (points.size < 2) throw IllegalStateException("Směrovací služba nevrátila použitelnou trasu.")
            val summary = trip.getJSONObject("summary")
            val km = summary.optDouble("length", 0.0)
            val minutes = (summary.optDouble("time", 0.0) / 60.0).roundToInt()
            return Triple(points, "Trasa: ${"%.1f".format(java.util.Locale("cs", "CZ"), km)} km • přibližně $minutes min • ${locations.size - 2} zastávek", parsedManeuvers)
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
            map.addPolyline(PolylineOptions().addAll(routePoints).color(Color.rgb(20, 105, 180)).width(7f))
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
    override fun onDestroy() { stopNavigationUpdates(); if (::mapView.isInitialized) mapView.onDestroy(); super.onDestroy() }
}
