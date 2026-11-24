package cz.santa36low.doprava

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var db: Database

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Database(this)
        setContent { MaterialTheme { DopravaScreen() } }
    }

    @Composable
    private fun DopravaScreen() {
        val activities = listOf(
            "Jízda" to Icons.Filled.LocalShipping,
            "Nákladka" to Icons.Filled.MoveToInbox,
            "Výkladka" to Icons.Filled.Outbox,
            "Cesta domů" to Icons.Filled.Home
        )
        var active by remember { mutableStateOf(db.active()) }
        var history by remember { mutableStateOf(db.history()) }
        var pause by remember { mutableStateOf(db.activePause()) }
        var selectedDay by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var selectedType by remember { mutableStateOf("Jízda") }
        var page by remember { mutableStateOf(0) }
        var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var fuelText by remember { mutableStateOf("") }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }

        LaunchedEffect(Unit) {
            while (true) {
                tick = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000)
            }
        }

        fun hasLocation(): Boolean =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun withLocation(done: (Double?, Double?) -> Unit) {
            if (!hasLocation()) {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                done(null, null)
                return
            }
            try {
                LocationServices.getFusedLocationProviderClient(this).lastLocation
                    .addOnSuccessListener { done(it?.latitude, it?.longitude) }
                    .addOnFailureListener { done(null, null) }
            } catch (_: SecurityException) {
                done(null, null)
            }
        }

        fun refresh() {
            active = db.active()
            history = db.history()
            pause = db.activePause()
        }

        fun startSelected() {
            if (active != null) return
            withLocation { lat, lon ->
                val id = db.start(selectedType, lat, lon)
                if (selectedType == "Jízda" && hasLocation()) {
                    try {
                        ContextCompat.startForegroundService(
                            this,
                            Intent(this, RouteTrackingService::class.java).putExtra("activity_id", id)
                        )
                    } catch (_: Exception) { }
                } else {
                    stopService(Intent(this, RouteTrackingService::class.java))
                }
                refresh()
            }
        }

        fun finishActive() {
            val current = active ?: return
            withLocation { lat, lon ->
                db.finish(current.id, lat, lon)
                stopService(Intent(this, RouteTrackingService::class.java))
                refresh()
            }
        }

        val navy = Color(0xFF0B2B4D)
        val blue = Color(0xFF14558A)
        val gold = Color(0xFFFFB21A)
        val pageBg = Color(0xFFF0F3F8)
        val activityIcon = Color(0xFF0B4A78)

        Scaffold(
            containerColor = pageBg,
            bottomBar = {
                NavigationBar(
                    containerColor = navy,
                    contentColor = Color.White
                ) {
                    NavigationBarItem(
                        selected = page == 0,
                        onClick = { page = 0 },
                        icon = { Text("⌂", style = MaterialTheme.typography.headlineSmall) },
                        label = { Text("Činnosti") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = gold,
                            selectedTextColor = gold,
                            indicatorColor = Color(0xFF173F66),
                            unselectedIconColor = Color.LightGray,
                            unselectedTextColor = Color.LightGray
                        )
                    )
                    NavigationBarItem(
                        selected = page == 1,
                        onClick = { page = 1; refresh() },
                        icon = { Icon(Icons.Filled.History, contentDescription = "Historie", modifier = Modifier.size(30.dp)) },
                        label = { Text("Historie") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = gold,
                            selectedTextColor = gold,
                            indicatorColor = Color(0xFF173F66),
                            unselectedIconColor = Color.LightGray,
                            unselectedTextColor = Color.LightGray
                        )
                    )
                    NavigationBarItem(
                        selected = page == 2,
                        onClick = { page = 2; refresh() },
                        icon = { Icon(Icons.Filled.LocalShipping, contentDescription = "Denní přehled", modifier = Modifier.size(30.dp)) },
                        label = { Text("Den") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = gold, selectedTextColor = gold,
                            indicatorColor = Color(0xFF173F66), unselectedIconColor = Color.LightGray, unselectedTextColor = Color.LightGray)
                    )
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).background(pageBg).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                    colors = CardDefaults.cardColors(containerColor = navy)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier.size(66.dp).background(Color(0xFF153F67), RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.LocalShipping, contentDescription = "Logo kamionu",
                                tint = Color.White, modifier = Modifier.size(43.dp))
                            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp)
                                .fillMaxWidth(0.68f).height(4.dp).background(gold, RoundedCornerShape(50)))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("DOPRAVA", style = MaterialTheme.typography.headlineMedium,
                                color = Color.White)
                            Text("EVIDENCE JÍZD A ČINNOSTÍ", style = MaterialTheme.typography.labelSmall,
                                color = gold, letterSpacing = 1.4.sp)
                            Box(Modifier.padding(top = 5.dp).width(104.dp).height(3.dp)
                                .background(gold, RoundedCornerShape(50)))
                        }
                        Icon(Icons.Filled.Settings, contentDescription = "Nastavení",
                            tint = Color(0xFFB9C9DA), modifier = Modifier.size(27.dp))
                    }
                }

                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (page == 0) {
                        activities.chunked(2).forEach { pair ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pair.forEach { (name, icon) ->
                                    val chosen = active?.type == name || (active == null && selectedType == name)
                                    Card(
                                        modifier = Modifier.weight(1f).height(94.dp).clickable {
                                            if (active == null) selectedType = name
                                        },
                                        shape = RoundedCornerShape(19.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            if (chosen) 2.dp else 1.dp,
                                            if (chosen) blue else Color(0xFFD1D9E5)
                                        ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (chosen) Color(0xFFE1EDFA) else Color.White
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Column(
                                            Modifier.fillMaxSize().padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(icon, contentDescription = name, tint = activityIcon,
                                                modifier = Modifier.size(33.dp))
                                            Spacer(Modifier.height(6.dp))
                                            Text(name, style = MaterialTheme.typography.titleMedium, color = Color(0xFF101827))
                                            if (active?.type == name) Text("PROBÍHÁ", color = blue,
                                                style = MaterialTheme.typography.labelSmall)
                                            else if (active == null && selectedType == name) Text("VYBRÁNO",
                                                color = blue, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }

                        if (active == null) {
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Připraveno ke startu", color = navy,
                                        style = MaterialTheme.typography.titleMedium)
                                    Text(selectedType, color = blue, style = MaterialTheme.typography.headlineSmall)
                                    Button(
                                        onClick = { startSelected() },
                                        modifier = Modifier.fillMaxWidth().height(58.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = blue)
                                    ) { Text("▶  START", style = MaterialTheme.typography.titleLarge) }
                                }
                            }
                        } else {
                            val current = active!!
                            val duration = (tick - current.start).coerceAtLeast(0)
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Box(Modifier.fillMaxWidth().background(navy).padding(14.dp)) {
                                        Text("AKTUÁLNÍ ČINNOST", color = Color.White,
                                            style = MaterialTheme.typography.titleLarge)
                                    }
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                        Icon(activities.firstOrNull { it.first == current.type }?.second ?: Icons.Filled.LocalShipping,
                                            contentDescription = current.type, tint = blue, modifier = Modifier.size(48.dp))
                                        Text(current.type, style = MaterialTheme.typography.headlineSmall, color = navy)
                                        Text("TRVÁNÍ", color = blue, style = MaterialTheme.typography.labelLarge)
                                        Text(formatDuration((duration - db.pauseDurationMillis(current.id)).coerceAtLeast(0)), style = MaterialTheme.typography.headlineLarge, color = navy)
                                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text("Datum: ${dateOnly(current.start)}")
                                            Text("Začátek: ${timeOnly(current.start)}")
                                            if (current.type != "Pauza") Text("GPS začátku: ${coordinates(current.startLat, current.startLon)}")
                                        }
                                        if (pause == null) {
                                            OutlinedButton(onClick = { db.startPause(current.id); if (current.type == "Jízda") stopService(Intent(this@MainActivity, RouteTrackingService::class.java)); refresh() },
                                                modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("☕  ZAHÁJIT PAUZU") }
                                        } else {
                                            Text("PAUZA PROBÍHÁ", color = Color(0xFFB47B00), style = MaterialTheme.typography.titleMedium)
                                            Text(formatDuration(tick - pause!!.second), style = MaterialTheme.typography.headlineSmall, color = navy)
                                            Button(onClick = { db.finishPause(); if (current.type == "Jízda" && hasLocation()) ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, RouteTrackingService::class.java).putExtra("activity_id", current.id)); refresh() },
                                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB47B00))) { Text("UKONČIT PAUZU") }
                                        }
                                        Button(
                                            onClick = { if (pause != null) db.finishPause(); finishActive() },
                                            modifier = Modifier.fillMaxWidth().height(58.dp),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC92D35))
                                        ) { Text("■  KONEC", style = MaterialTheme.typography.titleLarge) }
                                    }
                                }
                            }
                        }

                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Tankování", style = MaterialTheme.typography.titleMedium, color = navy)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = fuelText,
                                        onValueChange = { fuelText = it },
                                        label = { Text("Litry") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Button(onClick = {
                                        val liters = fuelText.replace(',', '.').toDoubleOrNull()
                                        if (liters != null && liters > 0) {
                                            withLocation { lat, lon ->
                                                db.addFuel(liters, lat, lon)
                                                fuelText = ""
                                            }
                                        }
                                    }, colors = ButtonDefaults.buttonColors(containerColor = blue)) { Text("Uložit") }
                                }
                            }
                        }
                    } else if (page == 1) {
                        Text("Historie činností", style = MaterialTheme.typography.titleLarge, color = navy)
                        if (history.isEmpty()) {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Text("Zatím tu nejsou žádné záznamy.", Modifier.padding(18.dp))
                            }
                        } else {
                            history.forEach { item ->
                                val itemIcon = activities.firstOrNull { it.first == item.type }?.second ?: Icons.Filled.LocalShipping
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(itemIcon, contentDescription = item.type, tint = blue, modifier = Modifier.size(30.dp))
                                            Text(item.type, style = MaterialTheme.typography.titleMedium, color = navy)
                                        }
                                        Text("Datum: ${dateOnly(item.start)}")
                                        Text("Začátek: ${timeOnly(item.start)}")
                                        Text("Konec: ${item.end?.let { timeOnly(it) } ?: "Právě probíhá"}")
                                        Text("GPS začátku: ${coordinates(item.startLat, item.startLon)}")
                                        Text("GPS konce: ${coordinates(item.endLat, item.endLon)}")
                                        Text("Trvání: ${item.end?.let { formatDuration(it - item.start) } ?: formatDuration(tick - item.start)}")
                                    }
                                }
                            }
                        }
                    } else {
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = selectedDay
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }
                        val dayStart = cal.timeInMillis
                        val dayEnd = dayStart + 86400000L
                        val dayItems = history.filter { it.start < dayEnd && (it.end ?: tick) >= dayStart }
                        val dayPauses = db.pausesForDay(dayStart, dayEnd)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("‹", modifier = Modifier.clickable { selectedDay -= 86400000L }.padding(8.dp), style = MaterialTheme.typography.headlineMedium)
                            Text("Denní přehled · " + dateOnly(dayStart), style = MaterialTheme.typography.titleLarge, color = navy)
                            Text("›", modifier = Modifier.clickable { selectedDay += 86400000L }.padding(8.dp), style = MaterialTheme.typography.headlineMedium)
                        }
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Časová osa 00:00–24:00", color = navy, style = MaterialTheme.typography.titleMedium)
                                Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                                    drawRoundRect(Color(0xFFE8EDF4), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f,10f))
                                    dayItems.forEach { item ->
                                        val left = (((maxOf(item.start,dayStart)-dayStart).toFloat()/86400000f)*size.width).coerceIn(0f,size.width)
                                        val right = (((minOf(item.end ?: tick,dayEnd)-dayStart).toFloat()/86400000f)*size.width).coerceIn(left,size.width)
                                        drawRoundRect(blue, topLeft = androidx.compose.ui.geometry.Offset(left,8f),
                                            size = androidx.compose.ui.geometry.Size((right-left).coerceAtLeast(2f),54f),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f,5f))
                                    }
                                    dayPauses.forEach { item ->
                                        val left = (((maxOf(item.start,dayStart)-dayStart).toFloat()/86400000f)*size.width).coerceIn(0f,size.width)
                                        val right = (((minOf(item.end ?: tick,dayEnd)-dayStart).toFloat()/86400000f)*size.width).coerceIn(left,size.width)
                                        drawRoundRect(Color(0xFFFFB21A), topLeft = androidx.compose.ui.geometry.Offset(left,8f),
                                            size = androidx.compose.ui.geometry.Size((right-left).coerceAtLeast(2f),54f),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f,5f))
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text("■ Činnost", color = blue); Text("■ Pauza", color = Color(0xFFB47B00)) }
                            }
                        }
                        if (dayItems.isEmpty() && dayPauses.isEmpty()) Text("Pro tento den nejsou žádné záznamy.")
                        dayItems.forEach { item ->
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(item.type, style = MaterialTheme.typography.titleMedium, color = navy)
                                    Text(timeOnly(maxOf(item.start,dayStart)) + " – " + (item.end?.let { timeOnly(minOf(it,dayEnd)) } ?: "probíhá"))
                                    Text("Trvání: " + formatDuration(((item.end ?: tick).coerceAtMost(dayEnd)-item.start.coerceAtLeast(dayStart)).coerceAtLeast(0)))
                                }
                            }
                        }
                        dayPauses.forEach { item ->
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E5))) {
                                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("Pauza", style = MaterialTheme.typography.titleMedium, color = Color(0xFF946000))
                                    Text(timeOnly(maxOf(item.start,dayStart)) + " – " + (item.end?.let { timeOnly(minOf(it,dayEnd)) } ?: "probíhá"))
                                    Text("Trvání: " + formatDuration(((item.end ?: tick).coerceAtMost(dayEnd)-item.start.coerceAtLeast(dayStart)).coerceAtLeast(0)))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    private fun coordinates(lat: Double?, lon: Double?): String = if (lat != null && lon != null) "%.5f, %.5f".format(Locale.US, lat, lon) else "Není k dispozici"
    private fun dateOnly(ms: Long) = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ms))
    private fun timeOnly(ms: Long) = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))
    private fun date(ms: Long) = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(ms))
    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000).coerceAtLeast(0)
        return "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    }
}
