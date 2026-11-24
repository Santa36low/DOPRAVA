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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            "Jízda" to "🚛",
            "Nákladka" to "📦",
            "Výkladka" to "🏗️",
            "Pauza" to "☕",
            "Cesta domů" to "🏠"
        )
        var active by remember { mutableStateOf(db.active()) }
        var history by remember { mutableStateOf(db.history()) }
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

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = page == 0,
                        onClick = { page = 0 },
                        icon = { Text("🚦") },
                        label = { Text("Činnosti") }
                    )
                    NavigationBarItem(
                        selected = page == 1,
                        onClick = { page = 1; refresh() },
                        icon = { Text("📋") },
                        label = { Text("Historie") }
                    )
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("DOPRAVA", style = MaterialTheme.typography.headlineLarge)

                if (page == 0) {
                    Text("Vyber činnost", style = MaterialTheme.typography.titleLarge)
                    activities.forEach { (name, emoji) ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (active == null) selectedType = name
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (active?.type == name || (active == null && selectedType == name))
                                    MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(emoji, style = MaterialTheme.typography.headlineMedium)
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.weight(1f))
                                if (active?.type == name) Text("Probíhá", color = MaterialTheme.colorScheme.primary)
                                else if (active == null && selectedType == name) Text("Vybráno")
                            }
                        }
                    }

                    Divider()
                    if (active == null) {
                        Text("Připraveno: $selectedType", style = MaterialTheme.typography.titleLarge)
                        Button(
                            onClick = { startSelected() },
                            modifier = Modifier.fillMaxWidth().height(60.dp)
                        ) {
                            Text("▶  START", style = MaterialTheme.typography.titleLarge)
                        }
                    } else {
                        val duration = (tick - active!!.start).coerceAtLeast(0)
                        Text("Aktuální činnost", style = MaterialTheme.typography.titleLarge)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(activities.firstOrNull { it.first == active!!.type }?.second ?: "⏱️", style = MaterialTheme.typography.headlineLarge)
                                    Text(active!!.type, style = MaterialTheme.typography.headlineMedium)
                                }
                                Text("Od: ${date(active!!.start)}")
                                Text("Trvání: ${formatDuration(duration)}", style = MaterialTheme.typography.headlineSmall)
                                Button(
                                    onClick = { finishActive() },
                                    modifier = Modifier.fillMaxWidth().height(60.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("■  KONEC", style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                        Text("Po ukončení můžeš vybrat další činnost.")
                    }

                    Divider()
                    Text("Tankování", style = MaterialTheme.typography.titleMedium)
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
                        }) { Text("Uložit") }
                    }
                } else {
                    Text("Historie činností", style = MaterialTheme.typography.titleLarge)
                    if (history.isEmpty()) {
                        Text("Zatím tu nejsou žádné záznamy.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(history) { item ->
                                val emoji = activities.firstOrNull { it.first == item.type }?.second ?: "⏱️"
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(emoji, style = MaterialTheme.typography.titleLarge)
                                            Text(item.type, style = MaterialTheme.typography.titleMedium)
                                        }
                                        Text("Datum: ${dateOnly(item.start)}")
                                        Text("Začátek: ${timeOnly(item.start)}")
                                        Text("Konec: ${item.end?.let { timeOnly(it) } ?: "Právě probíhá"}")
                                        Text("Trvání: ${item.end?.let { formatDuration(it - item.start) } ?: formatDuration(tick - item.start)}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun dateOnly(ms: Long) = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ms))
    private fun timeOnly(ms: Long) = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))
    private fun date(ms: Long) = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(ms))
    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000).coerceAtLeast(0)
        return "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    }
}
