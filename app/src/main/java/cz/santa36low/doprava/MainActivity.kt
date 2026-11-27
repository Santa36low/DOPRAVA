package cz.santa36low.doprava

import android.Manifest
import android.app.DatePickerDialog
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
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
        var historyMode by remember { mutableStateOf("Den") }
        var selectedType by remember { mutableStateOf("Jízda") }
        var page by remember { mutableStateOf(0) }
        var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var fuelText by remember { mutableStateOf("") }
        var placeDialogVisible by remember { mutableStateOf(false) }
        var placeResults by remember { mutableStateOf<List<NearbyPlace>>(emptyList()) }
        var placeLoading by remember { mutableStateOf(false) }
        var manualPlace by remember { mutableStateOf("") }
        var pendingPlaceAction by remember { mutableStateOf<((String?) -> Unit)?>(null) }
        var pendingLatitude by remember { mutableDoubleStateOf(0.0) }
        var pendingLongitude by remember { mutableDoubleStateOf(0.0) }
        var editingItem by remember { mutableStateOf<ActivityRecord?>(null) }
        var deletingItem by remember { mutableStateOf<ActivityRecord?>(null) }
        var editType by remember { mutableStateOf("") }
        var editStart by remember { mutableStateOf("") }
        var editEnd by remember { mutableStateOf("") }
        var editStartPlace by remember { mutableStateOf("") }
        var editEndPlace by remember { mutableStateOf("") }
        var editCompany by remember { mutableStateOf("") }
        var editNote by remember { mutableStateOf("") }

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

        fun completePlace(name: String?) {
            val action = pendingPlaceAction
            pendingPlaceAction = null
            placeDialogVisible = false
            action?.invoke(name)
        }

        fun askForPlace(lat: Double?, lon: Double?, done: (String?) -> Unit) {
            pendingPlaceAction = done
            pendingLatitude = lat ?: 0.0
            pendingLongitude = lon ?: 0.0
            manualPlace = ""
            placeResults = emptyList()
            placeLoading = lat != null && lon != null
            placeDialogVisible = true
            if (lat == null || lon == null) return
            Thread {
                val results = runCatching { PlaceLookup.search(lat, lon) }.getOrDefault(emptyList())
                runOnUiThread { placeResults = results; placeLoading = false }
            }.start()
        }

        fun startSelected() {
            if (active != null) return
            withLocation { lat, lon ->
                val id = db.start(selectedType, lat, lon, null)
                if (selectedType != "Pauza" && lat != null && lon != null) db.addRoutePoint(id, lat, lon)
                if (selectedType != "Pauza" && hasLocation()) {
                    try { ContextCompat.startForegroundService(this, Intent(this, RouteTrackingService::class.java).putExtra("activity_id", id)) } catch (_: Exception) { }
                } else stopService(Intent(this, RouteTrackingService::class.java))
                refresh()
            }
        }

        fun finishActive() {
            val current = active ?: return
            if (pause != null) return
            withLocation { lat, lon ->
                val save: (String?) -> Unit = { companyName ->
                    if (current.type != "Pauza" && lat != null && lon != null) db.addRoutePoint(current.id, lat, lon)
                    db.finish(current.id, lat, lon, companyName, if (current.type == "Výkladka") companyName else null)
                    stopService(Intent(this, RouteTrackingService::class.java))
                    refresh()
                }
                if (current.type == "Výkladka") askForPlace(lat, lon, save) else save(null)
            }
        }

        val navy = Color(0xFF0B2B4D)
        val blue = Color(0xFF14558A)
        val gold = Color(0xFFFFB21A)
        val pageBg = Color(0xFFF0F3F8)
        val activityIcon = Color(0xFF0B4A78)

        if (placeDialogVisible) {
            AlertDialog(
                onDismissRequest = { completePlace(null) },
                title = { Text("Firma při výkladce") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (placeLoading) "Hledám firmy v okolí konečné GPS…" else if (placeResults.isEmpty()) "V okolí nebyla nalezena firma. Můžeš ji dopsat ručně." else "Vyber správnou firmu. Pokud je špatně, zvol jinou nebo ji napiš ručně:")
                        placeResults.forEach { place ->
                            TextButton(onClick = { completePlace(if (place.address.isBlank()) place.name else place.name + ", " + place.address) }) {
                                Text(if (place.address.isBlank()) place.name else place.name + " · " + place.address)
                            }
                        }
                        OutlinedTextField(value = manualPlace, onValueChange = { manualPlace = it }, label = { Text("Ruční zadání názvu / adresy") }, singleLine = true)
                        Text("Data firem: © OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                },
                confirmButton = { TextButton(onClick = { completePlace(manualPlace.trim().ifBlank { null }) }, enabled = manualPlace.isNotBlank()) { Text("Použít ruční zadání") } },
                dismissButton = { TextButton(onClick = { completePlace(null) }) { Text("Bez firmy") } }
            )
        }

        if (editingItem != null) {
            AlertDialog(
                onDismissRequest = { editingItem = null },
                title = { Text("Upravit záznam") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(editType, { editType = it }, label = { Text("Činnost") }, singleLine = true)
                        OutlinedTextField(editStart, { editStart = it }, label = { Text("Začátek (dd.MM.rrrr HH:mm:ss)") }, singleLine = true)
                        OutlinedTextField(editEnd, { editEnd = it }, label = { Text("Konec (prázdné = probíhá)") }, singleLine = true)
                        OutlinedTextField(editStartPlace, { editStartPlace = it }, label = { Text("Místo začátku") })
                        OutlinedTextField(editEndPlace, { editEndPlace = it }, label = { Text("Místo konce") })
                        OutlinedTextField(editCompany, { editCompany = it }, label = { Text("Firma") })
                        OutlinedTextField(editNote, { editNote = it }, label = { Text("Poznámka") })
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val parser = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                        val startMs = runCatching { parser.parse(editStart)?.time }.getOrNull()
                        val endMs = if (editEnd.isBlank()) null else runCatching { parser.parse(editEnd)?.time }.getOrNull()
                        val item = editingItem
                        if (item != null && startMs != null && (editEnd.isBlank() || endMs != null)) {
                            db.updateActivity(item.id, editType.trim().ifBlank { item.type }, startMs, endMs, editStartPlace, editEndPlace, editCompany, editNote)
                            editingItem = null
                            refresh()
                        }
                    }) { Text("Uložit") }
                },
                dismissButton = { TextButton(onClick = { editingItem = null }) { Text("Zrušit") } }
            )
        }
        if (deletingItem != null) {
            AlertDialog(
                onDismissRequest = { deletingItem = null },
                title = { Text("Smazat záznam?") },
                text = { Text("Opravdu chceš smazat celý záznam včetně jeho GPS trasy a pauz?") },
                confirmButton = {
                    TextButton(onClick = {
                        deletingItem?.let { item ->
                            if (active?.id == item.id) stopService(Intent(this@MainActivity, RouteTrackingService::class.java))
                            db.deleteActivity(item.id)
                        }
                        deletingItem = null
                        refresh()
                    }) { Text("Smazat") }
                },
                dismissButton = { TextButton(onClick = { deletingItem = null }) { Text("Zrušit") } }
            )
        }

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
                                tint = Color.White, modifier = Modifier.size(37.dp))
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
                        (activities + listOf("Pauza" to Icons.Filled.Coffee)).chunked(2).forEach { pair ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pair.forEach { (name, icon) ->
                                    val chosen = active?.type == name || (active == null && selectedType == name)
                                    Card(
                                        modifier = Modifier.weight(1f).height(94.dp).clickable {
                                            if (name == "Pauza" && active != null) {
                                                if (pause == null) {
                                                    db.startPause(active!!.id)
                                                    if (active!!.type != "Pauza") stopService(Intent(this@MainActivity, RouteTrackingService::class.java))
                                                } else {
                                                    db.finishPause()
                                                    if (active!!.type != "Pauza" && hasLocation()) ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, RouteTrackingService::class.java).putExtra("activity_id", active!!.id))
                                                }
                                                refresh()
                                            } else if (active == null) selectedType = name
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
                                    Text(selectedType, color = blue, style = MaterialTheme.typography.headlineSmall)
                                    Button(
                                        onClick = { startSelected() },
                                        modifier = Modifier.fillMaxWidth().height(58.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = blue)
                                    ) { Text("▶  START", style = MaterialTheme.typography.titleLarge) }
                                    Text("Připraveno ke startu", color = navy,
                                        style = MaterialTheme.typography.titleMedium)
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
                                    Box(Modifier.fillMaxWidth().background(navy).padding(14.dp), contentAlignment = Alignment.Center) {
                                        Text("AKTUÁLNÍ ČINNOST", color = Color.White,
                                            style = MaterialTheme.typography.titleLarge)
                                    }
                                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                        Icon(activities.firstOrNull { it.first == current.type }?.second ?: Icons.Filled.LocalShipping,
                                            contentDescription = current.type, tint = blue, modifier = Modifier.size(48.dp))
                                        Text(current.type, style = MaterialTheme.typography.headlineSmall, color = navy)
                                        Text("TRVÁNÍ", color = blue, style = MaterialTheme.typography.labelLarge)
                                        Text(formatDuration((duration - db.pauseDurationMillis(current.id)).coerceAtLeast(0)), style = MaterialTheme.typography.headlineLarge, color = navy)
                                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text("Datum: ${dateOnly(current.start)}", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                            Text("Začátek: ${timeOnly(current.start)}", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                            if (current.type != "Pauza") Text("GPS začátku: ${coordinates(current.startLat, current.startLon)}", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                            current.startPlace?.let { Text("Místo: $it", textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                                            db.latestPause(current.id)?.let { last ->
                                                Text("Začátek pauzy: ${timeOnly(last.first)}", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                                last.second?.let { Text("Konec pauzy: ${timeOnly(it)}", textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                                            }
                                            if (pause != null) Text("PAUZA PROBÍHÁ · ${formatDuration(tick - pause!!.second)}", color = Color(0xFFB47B00), style = MaterialTheme.typography.titleMedium)
                                        }
                                        Button(
                                            onClick = { finishActive() },
                                            enabled = pause == null,
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
                        val periodCalendar = Calendar.getInstance().apply {
                            timeInMillis = selectedDay
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                            when (historyMode) {
                                "Týden" -> {
                                    val offset = (get(Calendar.DAY_OF_WEEK) + 5) % 7
                                    add(Calendar.DAY_OF_MONTH, -offset)
                                }
                                "Měsíc" -> set(Calendar.DAY_OF_MONTH, 1)
                            }
                        }
                        val periodStart = periodCalendar.timeInMillis
                        val periodEnd = Calendar.getInstance().apply {
                            timeInMillis = periodStart
                            when (historyMode) {
                                "Den" -> add(Calendar.DAY_OF_MONTH, 1)
                                "Týden" -> add(Calendar.DAY_OF_MONTH, 7)
                                else -> add(Calendar.MONTH, 1)
                            }
                        }.timeInMillis
                        val filteredHistory = history.filter { it.start < periodEnd && (it.end ?: tick) >= periodStart }
                        val periodLabel = when (historyMode) {
                            "Den" -> dateOnly(periodStart)
                            "Týden" -> dateOnly(periodStart) + " – " + dateOnly(periodEnd - 1)
                            else -> SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Date(periodStart))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            listOf("Den", "Týden", "Měsíc").forEach { mode ->
                                FilterChip(
                                    selected = historyMode == mode,
                                    onClick = { historyMode = mode },
                                    label = { Text(mode) }
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("‹", modifier = Modifier.clickable {
                                val c = Calendar.getInstance().apply { timeInMillis = selectedDay }
                                when (historyMode) {
                                    "Den" -> c.add(Calendar.DAY_OF_MONTH, -1)
                                    "Týden" -> c.add(Calendar.DAY_OF_MONTH, -7)
                                    else -> c.add(Calendar.MONTH, -1)
                                }
                                selectedDay = c.timeInMillis
                            }.padding(8.dp), style = MaterialTheme.typography.headlineMedium)
                            Text(
                                periodLabel,
                                modifier = Modifier.clickable {
                                    val c = Calendar.getInstance().apply { timeInMillis = selectedDay }
                                    DatePickerDialog(this@MainActivity, { _, year, month, day ->
                                        selectedDay = Calendar.getInstance().apply {
                                            set(year, month, day, 12, 0, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }.timeInMillis
                                    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                                }.padding(8.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = navy
                            )
                            Text("›", modifier = Modifier.clickable {
                                val c = Calendar.getInstance().apply { timeInMillis = selectedDay }
                                when (historyMode) {
                                    "Den" -> c.add(Calendar.DAY_OF_MONTH, 1)
                                    "Týden" -> c.add(Calendar.DAY_OF_MONTH, 7)
                                    else -> c.add(Calendar.MONTH, 1)
                                }
                                selectedDay = c.timeInMillis
                            }.padding(8.dp), style = MaterialTheme.typography.headlineMedium)
                        }
                        if (filteredHistory.isEmpty()) {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Text("Zatím tu nejsou žádné záznamy.", Modifier.padding(18.dp))
                            }
                        } else {
                            filteredHistory.forEach { item ->
                                val itemIcon = activities.firstOrNull { it.first == item.type }?.second ?: Icons.Filled.LocalShipping
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(itemIcon, contentDescription = item.type, tint = blue, modifier = Modifier.size(30.dp))
                                            Text(item.type, style = MaterialTheme.typography.titleMedium, color = navy)
                                        }
                                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            InfoRow("Datum", dateOnly(item.start))
                                            InfoRow("Začátek", timeOnly(item.start))
                                            InfoRow("Konec", item.end?.let { timeOnly(it) } ?: "Právě probíhá")
                                            InfoRow("GPS začátku", coordinates(item.startLat, item.startLon))
                                            item.startPlace?.let { InfoRow("Místo začátku", it) }
                                            InfoRow("GPS konce", coordinates(item.endLat, item.endLon))
                                            if (item.type != "Výkladka") item.endPlace?.let { InfoRow("Místo konce", it) }
                                            item.company?.let { InfoRow("Firma", it) }
                                            InfoRow("Trvání", item.end?.let { formatDuration(it - item.start - db.pauseDurationMillis(item.id)) } ?: formatDuration(tick - item.start - db.pauseDurationMillis(item.id)))
                                        }
                                        if (item.type != "Pauza") {
                                            val kilometers = db.routeDistanceKm(item.id)
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Ujeto: %.1f km".format(Locale.getDefault(), kilometers), color = navy,
                                                    style = MaterialTheme.typography.titleMedium)
                                                IconButton(onClick = {
                                                    startActivity(Intent(this@MainActivity, TrackMapActivity::class.java)
                                                        .putExtra("activity_id", item.id))
                                                }) {
                                                    Icon(Icons.Filled.Map, contentDescription = "Zobrazit trasu na mapě", tint = blue, modifier = Modifier.size(30.dp))
                                                }
                                            }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            IconButton(onClick = {
                                                editingItem = item
                                                editType = item.type
                                                editStart = date(item.start)
                                                editEnd = item.end?.let { date(it) } ?: ""
                                                editStartPlace = item.startPlace ?: ""
                                                editEndPlace = item.endPlace ?: ""
                                                editCompany = item.company ?: ""
                                                editNote = item.note
                                            }) { Icon(Icons.Filled.Edit, contentDescription = "Upravit záznam", tint = blue) }
                                            IconButton(onClick = { deletingItem = item }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Smazat záznam", tint = Color(0xFFC92D35))
                                            }
                                        }
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

    @Composable
    private fun InfoRow(label: String, value: String) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$label:", modifier = Modifier.weight(1f), color = Color(0xFF596575))
            Text(value, modifier = Modifier.weight(1.5f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
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
