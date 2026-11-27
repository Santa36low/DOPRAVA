package cz.santa36low.doprava

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import java.io.File
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth

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
        var graphMode by remember { mutableStateOf("Den") }
        var selectedType by remember { mutableStateOf("Jízda") }
        var page by remember { mutableStateOf(0) }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val drawerScope = rememberCoroutineScope()
        var backupMessage by remember { mutableStateOf("") }
        var cloudBusy by remember { mutableStateOf(false) }
        val firebaseAuth = remember { FirebaseAuth.getInstance() }
        var currentUser by remember { mutableStateOf(firebaseAuth.currentUser) }
        var accountVehiclePlates by remember { mutableStateOf("") }
        var authDialogVisible by remember { mutableStateOf(false) }
        var authRegisterMode by remember { mutableStateOf(false) }
        var authEmail by remember { mutableStateOf("") }
        var authPassword by remember { mutableStateOf("") }
        var authDriverName by remember { mutableStateOf("") }
        var authMessage by remember { mutableStateOf("") }
        var authBusy by remember { mutableStateOf(false) }
        var accountDialogVisible by remember { mutableStateOf(false) }
        var accountName by remember { mutableStateOf("") }
        var accountEmail by remember { mutableStateOf("") }
        var accountCurrentPassword by remember { mutableStateOf("") }
        var accountNewPassword by remember { mutableStateOf("") }
        var accountMessage by remember { mutableStateOf("") }
        var accountBusy by remember { mutableStateOf(false) }
        var showRestoreConfirm by remember { mutableStateOf(false) }
        var pendingBackupUri by remember { mutableStateOf<Uri?>(null) }
        var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var fuelText by remember { mutableStateOf("") }
        var placeDialogVisible by remember { mutableStateOf(false) }
        var placeResults by remember { mutableStateOf<List<NearbyPlace>>(emptyList()) }
        var placeLoading by remember { mutableStateOf(false) }
        var manualPlace by remember { mutableStateOf("") }
        var pendingPlaceAction by remember { mutableStateOf<((String?) -> Unit)?>(null) }
        var placeDialogTitle by remember { mutableStateOf("Potvrzení firmy") }
        var noteDialogVisible by remember { mutableStateOf(false) }
        var activityNote by remember { mutableStateOf("") }
        var pendingNoteAction by remember { mutableStateOf<((String) -> Unit)?>(null) }
        var pendingLatitude by remember { mutableDoubleStateOf(0.0) }
        var pendingLongitude by remember { mutableDoubleStateOf(0.0) }
        var editingItem by remember { mutableStateOf<ActivityRecord?>(null) }
        var deletingItem by remember { mutableStateOf<ActivityRecord?>(null) }
        var detailItem by remember { mutableStateOf<ActivityRecord?>(null) }
        var editType by remember { mutableStateOf("") }
        var editStart by remember { mutableStateOf("") }
        var editEnd by remember { mutableStateOf("") }
        var editStartPlace by remember { mutableStateOf("") }
        var editEndPlace by remember { mutableStateOf("") }
        var editCompany by remember { mutableStateOf("") }
        var editNote by remember { mutableStateOf("") }

        val exportBackupLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null) {
                try {
                    db.checkpointForBackup()
                    db.close()
                    val source = getDatabasePath("doprava.db")
                    if (!source.exists() || source.length() == 0L) throw IllegalStateException("Databázový soubor je prázdný")
                    contentResolver.openOutputStream(uri)?.use { output ->
                        source.inputStream().use { it.copyTo(output) }
                        output.flush()
                    } ?: throw IllegalStateException("Soubor se nepodařilo vytvořit")
                    db = Database(this)
                    backupMessage = "Záloha byla úspěšně uložena. Uchovej ji mimo telefon."
                } catch (e: Exception) {
                    db = Database(this)
                    backupMessage = "Zálohu se nepodařilo uložit: ${e.localizedMessage ?: "chyba"}"
                }
            }
        }
        val importBackupLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                pendingBackupUri = uri
                showRestoreConfirm = true
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.any { it }) {
                val running = active
                if (running != null && running.type != "Pauza") {
                    try { ContextCompat.startForegroundService(this, Intent(this, RouteTrackingService::class.java).putExtra("activity_id", running.id)) } catch (_: Exception) { }
                }
            }
        }

        LaunchedEffect(currentUser?.uid) {
            val user = currentUser
            if (user == null) {
                accountVehiclePlates = ""
                accountName = ""
                accountEmail = ""
            } else {
                accountName = user.displayName.orEmpty()
                accountEmail = user.email.orEmpty()
            }
        }

        DisposableEffect(currentUser?.uid) {
            val userId = currentUser?.uid
            if (userId == null) {
                accountVehiclePlates = ""
                onDispose { }
            } else {
                val registration = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(userId).collection("vehicles")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null) {
                            accountVehiclePlates = ""
                        } else {
                            accountVehiclePlates = snapshot.documents
                                .firstOrNull { it.getBoolean("selected") == true }
                                ?.getString("plate")
                                .orEmpty()
                        }
                    }
                onDispose { registration.remove() }
            }
        }

        DisposableEffect(firebaseAuth) {
            val listener = FirebaseAuth.AuthStateListener { auth -> currentUser = auth.currentUser }
            firebaseAuth.addAuthStateListener(listener)
            onDispose { firebaseAuth.removeAuthStateListener(listener) }
        }

        LaunchedEffect(Unit) {
            while (true) {
                tick = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000)
            }
        }

        fun hasLocation(): Boolean =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        LaunchedEffect(Unit) {
            if (!hasLocation()) {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }

        fun withLocation(done: (Double?, Double?) -> Unit) {
            if (!hasLocation()) {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                return
            }
            try {
                val client = LocationServices.getFusedLocationProviderClient(this)
                val token = CancellationTokenSource()
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            done(location.latitude, location.longitude)
                        } else {
                            client.lastLocation
                                .addOnSuccessListener { last -> done(last?.latitude, last?.longitude) }
                                .addOnFailureListener { done(null, null) }
                        }
                    }
                    .addOnFailureListener {
                        client.lastLocation
                            .addOnSuccessListener { last -> done(last?.latitude, last?.longitude) }
                            .addOnFailureListener { done(null, null) }
                    }
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

        fun askForPlace(lat: Double?, lon: Double?, title: String, done: (String?) -> Unit) {
            placeDialogTitle = title
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
                runOnUiThread {
                    placeResults = results
                    if (results.isNotEmpty() && manualPlace.isBlank()) {
                        val first = results.first()
                        manualPlace = if (first.address.isBlank()) first.name else first.name + ", " + first.address
                    }
                    placeLoading = false
                }
            }.start()
        }

        fun startSelected() {
            if (active != null) return
            val startedAt = System.currentTimeMillis()
            // Záznam vytvoříme hned po stisku, GPS nesmí zdržovat zobrazení činnosti.
            val id = db.start(selectedType, null, null, null, null, startedAt)
            refresh()
            if (selectedType != "Pauza") {
                if (hasLocation()) {
                    try { ContextCompat.startForegroundService(this, Intent(this, RouteTrackingService::class.java).putExtra("activity_id", id)) } catch (_: Exception) { }
                    withLocation { lat, lon ->
                        if (lat != null && lon != null) {
                            db.updateStartLocation(id, lat, lon)
                            db.addRoutePoint(id, lat, lon)
                            Thread {
                                val locationName = runCatching { PlaceLookup.reverseGeocode(lat, lon) }.getOrNull()
                                if (!locationName.isNullOrBlank()) {
                                    db.updateStartPlace(id, locationName)
                                    runOnUiThread { refresh() }
                                }
                            }.start()
                            refresh()
                        }
                    }
                } else {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            } else {
                stopService(Intent(this, RouteTrackingService::class.java))
            }
        }

        fun finishActive() {
            val current = active ?: return
            if (pause != null) return
            val endedAt = System.currentTimeMillis()
            if (current.type == "Pauza") {
                activityNote = ""
                pendingNoteAction = { note ->
                    db.finish(current.id, null, null, null, null, endedAt, note)
                    refresh()
                }
                noteDialogVisible = true
                return
            }
            withLocation { lat, lon ->
                val save: (String?) -> Unit = { companyName ->
                    activityNote = ""
                    pendingNoteAction = { note ->
                        if (current.type != "Pauza" && lat != null && lon != null) db.addRoutePoint(current.id, lat, lon)
                        db.finish(current.id, lat, lon, null, companyName, endedAt, note)
                        if (lat != null && lon != null) {
                            Thread {
                                val locationName = runCatching { PlaceLookup.reverseGeocode(lat, lon) }.getOrNull()
                                if (!locationName.isNullOrBlank()) {
                                    db.updateEndPlace(current.id, locationName)
                                    runOnUiThread { refresh() }
                                }
                            }.start()
                        }
                        stopService(Intent(this, RouteTrackingService::class.java))
                        refresh()
                    }
                    noteDialogVisible = true
                }
                if (current.type == "Výkladka" || current.type == "Nákladka") {
                    val title = if (current.type == "Nákladka") "Potvrzení firmy při nákladce" else "Potvrzení firmy při výkladce"
                    askForPlace(lat, lon, title, save)
                } else save(null)
            }
        }

        fun uploadCloudBackup() {
            val userId = currentUser?.uid
            if (userId == null) {
                backupMessage = "Nejdříve se přihlas ke svému Firebase účtu."
                return
            }
            if (active != null) {
                backupMessage = "Před zálohováním ukonči probíhající činnost, aby se uložila kompletní databáze."
                return
            }
            if (cloudBusy) return
            cloudBusy = true
            backupMessage = "Připravuji databázi a odesílám ji do Firebase…"
            Thread {
                var closed = false
                var message = ""
                try {
                    db.checkpointForBackup()
                    db.close()
                    closed = true
                    val source = getDatabasePath("doprava.db")
                    CloudDatabaseBackup.upload(source, userId)
                    message = "Záloha byla úspěšně uložena do Firebase. Můžeš ji obnovit po instalaci nové verze."
                } catch (e: Exception) {
                    message = "Zálohu se nepodařilo odeslat: ${e.cause?.localizedMessage ?: e.localizedMessage ?: "chyba"}"
                } finally {
                    if (closed) {
                        runOnUiThread {
                            db = Database(this@MainActivity)
                            cloudBusy = false
                            refresh()
                            backupMessage = if (message.isBlank()) "Zálohování dokončeno." else message
                        }
                    } else {
                        runOnUiThread { cloudBusy = false; backupMessage = message }
                    }
                }
            }.start()
        }

        fun downloadCloudBackup() {
            val userId = currentUser?.uid
            if (userId == null) {
                backupMessage = "Nejdříve se přihlas ke svému Firebase účtu."
                return
            }
            if (active != null) {
                backupMessage = "Před synchronizací ukonči probíhající činnost. Stažená databáze nahradí místní data."
                return
            }
            if (cloudBusy) return
            cloudBusy = true
            backupMessage = "Stahuji zálohu z Firebase a ověřuji databázi…"
            Thread {
                var closed = false
                var message = ""
                try {
                    val temp = File(cacheDir, "doprava-cloud-restore.db")
                    CloudDatabaseBackup.downloadToFile(temp, userId)
                    db.close()
                    closed = true
                    stopService(Intent(this@MainActivity, RouteTrackingService::class.java))
                    val databaseFile = getDatabasePath("doprava.db")
                    File(databaseFile.path + "-wal").delete()
                    File(databaseFile.path + "-shm").delete()
                    File(databaseFile.path + "-journal").delete()
                    temp.copyTo(databaseFile, overwrite = true)
                    temp.delete()
                    message = "Synchronizace dokončena. Místní databáze byla obnovena z Firebase."
                } catch (e: Exception) {
                    message = "Synchronizaci se nepodařilo dokončit: ${e.cause?.localizedMessage ?: e.localizedMessage ?: "chyba"}"
                } finally {
                    if (closed) {
                        runOnUiThread {
                            db = Database(this@MainActivity)
                            cloudBusy = false
                            refresh()
                            backupMessage = message
                            page = 1
                        }
                    } else {
                        runOnUiThread { cloudBusy = false; backupMessage = message }
                    }
                }
            }.start()
        }

        val navy = Color(0xFF0B2B4D)
        val blue = Color(0xFF14558A)
        val gold = Color(0xFFFFB21A)
        val pageBg = Color(0xFFF0F3F8)
        val activityIcon = Color(0xFF0B4A78)

        if (accountDialogVisible) {
            AlertDialog(
                onDismissRequest = { if (!accountBusy) accountDialogVisible = false },
                title = { Text("Účet řidiče") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = accountName, onValueChange = { accountName = it }, label = { Text("Jméno řidiče") }, singleLine = true, enabled = !accountBusy)
                        OutlinedTextField(value = accountEmail, onValueChange = { accountEmail = it }, label = { Text("E-mail") }, singleLine = true, enabled = !accountBusy)
                        OutlinedTextField(value = accountCurrentPassword, onValueChange = { accountCurrentPassword = it }, label = { Text("Současné heslo – ověření změn") }, singleLine = true, enabled = !accountBusy, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        OutlinedTextField(value = accountNewPassword, onValueChange = { accountNewPassword = it }, label = { Text("Nové heslo (volitelné, min. 6 znaků)") }, singleLine = true, enabled = !accountBusy, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        if (accountMessage.isNotBlank()) Text(accountMessage, color = Color(0xFFB3261E))
                    }
                },
                confirmButton = {
                    TextButton(enabled = !accountBusy && accountName.isNotBlank() && accountEmail.isNotBlank() && accountCurrentPassword.isNotBlank() && (accountNewPassword.isBlank() || accountNewPassword.length >= 6), onClick = {
                        val user = firebaseAuth.currentUser
                        val oldEmail = user?.email
                        if (user == null || oldEmail.isNullOrBlank()) { accountMessage = "Účet není přihlášen."; return@TextButton }
                        accountBusy = true
                        accountMessage = ""
                        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(oldEmail, accountCurrentPassword)
                        user.reauthenticate(credential).addOnCompleteListener { reauth ->
                            if (!reauth.isSuccessful) {
                                accountBusy = false
                                accountMessage = reauth.exception?.localizedMessage ?: "Současné heslo není správné."
                            } else {
                                val profile = com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(accountName.trim()).build()
                                user.updateProfile(profile).addOnCompleteListener { nameResult ->
                                    if (!nameResult.isSuccessful) {
                                        accountBusy = false
                                        accountMessage = nameResult.exception?.localizedMessage ?: "Jméno se nepodařilo změnit."
                                    } else {
                                        val emailTask = if (accountEmail.trim() != oldEmail) user.updateEmail(accountEmail.trim()) else com.google.android.gms.tasks.Tasks.forResult<Void>(null)
                                        emailTask.addOnCompleteListener { emailResult ->
                                            if (!emailResult.isSuccessful) {
                                                accountBusy = false
                                                accountMessage = emailResult.exception?.localizedMessage ?: "E-mail se nepodařilo změnit."
                                            } else {
                                                val passwordTask = if (accountNewPassword.isNotBlank()) user.updatePassword(accountNewPassword) else com.google.android.gms.tasks.Tasks.forResult<Void>(null)
                                                passwordTask.addOnCompleteListener { passwordResult ->
                                                    accountBusy = false
                                                    if (passwordResult.isSuccessful) {
                                                        currentUser = firebaseAuth.currentUser
                                                        accountCurrentPassword = ""
                                                        accountNewPassword = ""
                                                        accountMessage = "Údaje účtu byly uloženy."
                                                        accountDialogVisible = false
                                                    } else accountMessage = passwordResult.exception?.localizedMessage ?: "Heslo se nepodařilo změnit."
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }) { Text(if (accountBusy) "Ukládám…" else "Uložit změny") }
                },
                dismissButton = { TextButton(onClick = { accountDialogVisible = false }, enabled = !accountBusy) { Text("Zavřít") } }
            )
        }

        if (authDialogVisible) {
            AlertDialog(
                onDismissRequest = { if (!authBusy) authDialogVisible = false },
                title = { Text(if (authRegisterMode) "Registrace řidiče" else "Přihlášení") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (authRegisterMode) OutlinedTextField(value = authDriverName, onValueChange = { authDriverName = it }, label = { Text("Jméno řidiče") }, singleLine = true, enabled = !authBusy)
                        OutlinedTextField(value = authEmail, onValueChange = { authEmail = it }, label = { Text("E-mail") }, singleLine = true, enabled = !authBusy)
                        OutlinedTextField(
                            value = authPassword, onValueChange = { authPassword = it },
                            label = { Text("Heslo (min. 6 znaků)") }, singleLine = true, enabled = !authBusy,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                        if (authMessage.isNotBlank()) Text(authMessage, color = Color(0xFFB3261E))
                        TextButton(onClick = { authRegisterMode = !authRegisterMode; authMessage = "" }, enabled = !authBusy) {
                            Text(if (authRegisterMode) "Už mám účet – přihlásit" else "Nemám účet – registrovat")
                        }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !authBusy && authEmail.isNotBlank() && authPassword.length >= 6 && (!authRegisterMode || authDriverName.isNotBlank()), onClick = {
                        authBusy = true
                        authMessage = ""
                        val task = if (authRegisterMode) firebaseAuth.createUserWithEmailAndPassword(authEmail.trim(), authPassword)
                            else firebaseAuth.signInWithEmailAndPassword(authEmail.trim(), authPassword)
                        task.addOnCompleteListener { result ->
                            if (!result.isSuccessful) {
                                authBusy = false
                                authMessage = result.exception?.localizedMessage ?: "Přihlášení se nezdařilo."
                            } else if (authRegisterMode) {
                                val profile = com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(authDriverName.trim()).build()
                                firebaseAuth.currentUser?.updateProfile(profile)?.addOnCompleteListener { profileResult ->
                                    authBusy = false
                                    if (profileResult.isSuccessful) {
                                        authDialogVisible = false
                                        authPassword = ""
                                        authDriverName = ""
                                        authMessage = ""
                                        currentUser = firebaseAuth.currentUser
                                    } else authMessage = profileResult.exception?.localizedMessage ?: "Jméno se nepodařilo uložit."
                                } ?: run { authBusy = false; authMessage = "Účet se nepodařilo načíst." }
                            } else {
                                authBusy = false
                                authDialogVisible = false
                                authPassword = ""
                                authMessage = ""
                            }
                        }
                    }) { Text(if (authBusy) "Čekej…" else if (authRegisterMode) "Vytvořit účet" else "Přihlásit") }
                },
                dismissButton = { TextButton(onClick = { authDialogVisible = false }, enabled = !authBusy) { Text("Zavřít") } }
            )
        }

        if (showRestoreConfirm) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirm = false; pendingBackupUri = null },
                title = { Text("Obnovit zálohu?") },
                text = { Text("Současná data v aplikaci budou nahrazena obsahem vybraného souboru. Pokračovat?") },
                confirmButton = {
                    TextButton(onClick = {
                        showRestoreConfirm = false
                        val uri = pendingBackupUri
                        pendingBackupUri = null
                        if (uri != null) {
                            try {
                                val temp = File(cacheDir, "restore-doprava.db")
                                contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                                    ?: throw IllegalStateException("Soubor nelze přečíst")
                                val header = temp.inputStream().use { ByteArray(16).also { bytes -> it.read(bytes) } }
                                if (String(header, 0, 15, Charsets.US_ASCII) != "SQLite format 3" || header[15].toInt() != 0) throw IllegalArgumentException("Vybraný soubor není platná záloha aplikace Doprava")

                                // Nejdřív ověříme integritu i kompletní strukturu zálohy.
                                android.database.sqlite.SQLiteDatabase.openDatabase(
                                    temp.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                                ).use { backupDb ->
                                    val integrity = backupDb.rawQuery("PRAGMA integrity_check", null).use { c ->
                                        if (c.moveToFirst()) c.getString(0) else ""
                                    }
                                    if (integrity != "ok") throw IllegalArgumentException("Záloha je poškozená ($integrity)")
                                    val requiredTables = setOf("activities", "fuel", "route_points", "pauses")
                                    val tables = backupDb.rawQuery(
                                        "SELECT name FROM sqlite_master WHERE type='table'", null
                                    ).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
                                    if (!tables.containsAll(requiredTables)) throw IllegalArgumentException("Soubor neobsahuje všechna data aplikace Doprava")
                                    val columns = backupDb.rawQuery("PRAGMA table_info(activities)", null).use { c ->
                                        buildSet { while (c.moveToNext()) add(c.getString(1)) }
                                    }
                                    if (!columns.containsAll(setOf("company", "note", "start_place", "end_place"))) {
                                        throw IllegalArgumentException("Záloha pochází ze starší nebo nekompatibilní verze aplikace")
                                    }
                                }

                                stopService(Intent(this@MainActivity, RouteTrackingService::class.java))
                                db.close()
                                val databaseFile = getDatabasePath("doprava.db")
                                File(databaseFile.path + "-wal").delete()
                                File(databaseFile.path + "-shm").delete()
                                File(databaseFile.path + "-journal").delete()
                                if (!temp.copyTo(databaseFile, overwrite = true).exists()) throw IllegalStateException("Obnova se nezdařila")
                                db = Database(this@MainActivity)
                                refresh()
                                page = 1
                                backupMessage = "Záloha byla úspěšně obnovena. Načteno záznamů: ${history.size}."
                            } catch (e: Exception) {
                                db = Database(this@MainActivity)
                                backupMessage = "Obnovu se nepodařilo dokončit: ${e.localizedMessage ?: "chyba"}"
                            }
                        }
                    }) { Text("Obnovit") }
                },
                dismissButton = { TextButton(onClick = { showRestoreConfirm = false; pendingBackupUri = null }) { Text("Zrušit") } }
            )
        }

        if (placeDialogVisible) {
            AlertDialog(
                onDismissRequest = { /* Firma je pro nakládku/vykládku povinná. */ },
                title = { Text(placeDialogTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (placeLoading) "Hledám firmy v okolí konečné GPS…" else if (placeResults.isEmpty()) "V okolí nebyla nalezena firma. Můžeš ji dopsat ručně." else "Vyber správnou firmu. Pokud je špatně, zvol jinou nebo ji napiš ručně:")
                        placeResults.forEach { place ->
                            TextButton(onClick = { manualPlace = if (place.address.isBlank()) place.name else place.name + ", " + place.address }) {
                                Text(if (place.address.isBlank()) place.name else place.name + " · " + place.address)
                            }
                        }
                        OutlinedTextField(value = manualPlace, onValueChange = { manualPlace = it }, label = { Text("Ruční zadání názvu / adresy") }, singleLine = true)
                        Text("Data firem: © OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                },
                confirmButton = { TextButton(onClick = { completePlace(manualPlace.trim().ifBlank { null }) }, enabled = manualPlace.isNotBlank()) { Text("Potvrdit firmu") } },
                dismissButton = { }
            )
        }

        if (noteDialogVisible) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Poznámka k činnosti") },
                text = {
                    OutlinedTextField(
                        value = activityNote,
                        onValueChange = { activityNote = it },
                        label = { Text("Poznámka (nepovinná)") },
                        placeholder = { Text("Můžeš doplnit poznámku…") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        noteDialogVisible = false
                        val action = pendingNoteAction
                        pendingNoteAction = null
                        action?.invoke(activityNote.trim())
                    }) { Text("Uložit a ukončit") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        noteDialogVisible = false
                        val action = pendingNoteAction
                        pendingNoteAction = null
                        action?.invoke("")
                    }) { Text("Přeskočit") }
                }
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

        if (detailItem != null) {
            val item = detailItem!!
            AlertDialog(
                onDismissRequest = { detailItem = null },
                title = { Text("Detail činnosti · ${item.type}") },
                text = {
                    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.company?.let { InfoRow("Firma", it) }
                        item.note.takeIf { it.isNotBlank() }?.let { InfoRow("Poznámka", it) }
                        InfoRow("Datum", dateOnly(item.start))
                        InfoRow("Začátek", timeOnly(item.start))
                        InfoRow("Konec", item.end?.let { timeOnly(it) } ?: "Právě probíhá")
                        InfoRow("Trvání", item.end?.let { formatDuration(it - item.start - db.pauseDurationMillis(item.id)) } ?: formatDuration(tick - item.start - db.pauseDurationMillis(item.id)))
                        if (item.type != "Pauza") {
                            GpsInfoRow("GPS začátku", item.startLat, item.startLon) { lat, lon -> openGpsOnMap(lat, lon) }
                            InfoRow("Lokace", item.startPlace ?: if (item.startLat != null && item.startLon != null) "Místo se nepodařilo určit" else "Není k dispozici")
                            GpsInfoRow("GPS konce", item.endLat, item.endLon) { lat, lon -> openGpsOnMap(lat, lon) }
                            InfoRow("Lokace", item.endPlace ?: if (item.endLat != null && item.endLon != null) "Místo se nepodařilo určit" else "Není k dispozici")
                        }
                        if (item.type != "Pauza") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ujeto: %.1f km".format(Locale.getDefault(), db.routeDistanceKm(item.id)), modifier = Modifier.weight(1f), color = Color(0xFF596575))
                                IconButton(onClick = {
                                    startActivity(Intent(this@MainActivity, TrackMapActivity::class.java).putExtra("activity_id", item.id))
                                }) { Icon(Icons.Filled.Map, contentDescription = "Zobrazit projetou trasu", tint = blue) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { detailItem = null }) { Text("Zavřít") } }
            )
        }

        Box(Modifier.fillMaxSize()) {
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
                        icon = { Icon(Icons.Filled.LocalShipping, contentDescription = "Činnosti", modifier = Modifier.size(30.dp)) },
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
                        icon = { Icon(Icons.Filled.ShowChart, contentDescription = "Graf přehledu", modifier = Modifier.size(30.dp)) },
                        label = { Text("Graf") },
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
                        IconButton(onClick = { drawerScope.launch { if (drawerState.isOpen) drawerState.close() else drawerState.open() } }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Otevřít nabídku",
                                tint = Color(0xFFB9C9DA), modifier = Modifier.size(27.dp))
                        }
                    }
                }

                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (page == 4) {
                        VehicleManagerScreen(userId = currentUser?.uid, navy = navy, blue = blue)
                    } else if (page == 3) {
                        Text("Záloha", style = MaterialTheme.typography.titleLarge, color = navy)
                        Text("Záloha databáze do souboru v telefonu. Soubor můžeš bezpečně uložit mimo telefon a později z něj obnovit všechna data.")
                        Button(
                            onClick = { exportBackupLauncher.launch("Doprava-zaloha.db") },
                            enabled = !cloudBusy && active == null,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = blue)
                        ) { Text("Uložit zálohu do souboru") }
                        OutlinedButton(
                            onClick = { importBackupLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "*/*")) },
                            enabled = !cloudBusy && active == null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Obnovit ze souboru") }
                        if (active != null) Text("Před vytvořením nebo obnovením zálohy ukonči probíhající činnost.", color = Color(0xFF9A5B00))
                        if (backupMessage.isNotBlank()) Text(backupMessage, color = navy)
                    } else if (page == 0) {
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
                                        Icon(if (current.type == "Pauza") Icons.Filled.Coffee else activities.firstOrNull { it.first == current.type }?.second ?: Icons.Filled.LocalShipping,
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
                                val itemIcon = if (item.type == "Pauza") Icons.Filled.Coffee
                                    else activities.firstOrNull { it.first == item.type }?.second ?: Icons.Filled.LocalShipping
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(itemIcon, contentDescription = item.type, tint = blue, modifier = Modifier.size(30.dp))
                                            Text(item.type, style = MaterialTheme.typography.titleMedium, color = navy)
                                        }
                                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            item.company?.let { InfoRow("Firma", it) }
                                            item.note.takeIf { it.isNotBlank() }?.let { InfoRow("Poznámka", it) }
                                            InfoRow("Datum", dateOnly(item.start))
                                            InfoRow("Začátek", timeOnly(item.start))
                                            InfoRow("Konec", item.end?.let { timeOnly(it) } ?: "Právě probíhá")
                                            InfoRow("Trvání", item.end?.let { formatDuration(it - item.start - db.pauseDurationMillis(item.id)) } ?: formatDuration(tick - item.start - db.pauseDurationMillis(item.id)))
                                            if (item.type != "Pauza") {
                                                GpsInfoRow("GPS začátku", item.startLat, item.startLon) { lat, lon -> openGpsOnMap(lat, lon) }
                                                InfoRow("Lokace", item.startPlace ?: if (item.startLat != null && item.startLon != null) "Místo se nepodařilo určit" else "Není k dispozici")
                                                GpsInfoRow("GPS konce", item.endLat, item.endLon) { lat, lon -> openGpsOnMap(lat, lon) }
                                                InfoRow("Lokace", item.endPlace ?: if (item.endLat != null && item.endLon != null) "Místo se nepodařilo určit" else "Není k dispozici")
                                            }
                                            if (item.type != "Pauza") {
                                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Ujeto: %.1f km".format(Locale.getDefault(), db.routeDistanceKm(item.id)), modifier = Modifier.weight(1f), color = Color(0xFF596575))
                                                    IconButton(onClick = {
                                                        startActivity(Intent(this@MainActivity, TrackMapActivity::class.java)
                                                            .putExtra("activity_id", item.id))
                                                    }) {
                                                        Icon(Icons.Filled.Map, contentDescription = "Zobrazit projetou trasu na mapě", tint = blue, modifier = Modifier.size(30.dp))
                                                    }
                                                }
                                            }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
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
                                            Spacer(Modifier.width(72.dp))
                                            IconButton(onClick = { deletingItem = item }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Smazat záznam", tint = Color(0xFFC92D35))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val graphCalendar = Calendar.getInstance().apply {
                            timeInMillis = selectedDay
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                            if (graphMode == "Týden") {
                                val offset = (get(Calendar.DAY_OF_WEEK) + 5) % 7
                                add(Calendar.DAY_OF_MONTH, -offset)
                            }
                        }
                        val periodStart = graphCalendar.timeInMillis
                        val periodEnd = Calendar.getInstance().apply {
                            timeInMillis = periodStart
                            if (graphMode == "Den") add(Calendar.DAY_OF_MONTH, 1) else add(Calendar.DAY_OF_MONTH, 7)
                        }.timeInMillis
                        val periodItems = history.filter { it.start < periodEnd && (it.end ?: tick) > periodStart }
                        val periodLabel = if (graphMode == "Den") dateOnly(periodStart) else dateOnly(periodStart) + " – " + dateOnly(periodEnd - 1)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            listOf("Den", "Týden").forEach { mode ->
                                FilterChip(selected = graphMode == mode, onClick = { graphMode = mode }, label = { Text(mode) })
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("‹", modifier = Modifier.clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = selectedDay }
                                cal.add(Calendar.DAY_OF_MONTH, if (graphMode == "Den") -1 else -7)
                                selectedDay = cal.timeInMillis
                            }.padding(8.dp), style = MaterialTheme.typography.headlineMedium)
                            Text(periodLabel, modifier = Modifier.clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = selectedDay }
                                DatePickerDialog(this@MainActivity, { _, year, month, day ->
                                    selectedDay = Calendar.getInstance().apply {
                                        set(year, month, day, 12, 0, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                            }.padding(8.dp), style = MaterialTheme.typography.titleMedium, color = navy)
                            Text("›", modifier = Modifier.clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = selectedDay }
                                cal.add(Calendar.DAY_OF_MONTH, if (graphMode == "Den") 1 else 7)
                                selectedDay = cal.timeInMillis
                            }.padding(8.dp), style = MaterialTheme.typography.headlineMedium)
                        }

                        val pauseRecords = db.pausesForDay(periodStart, periodEnd)
                        val pauseActivityItems = periodItems.filter { it.type == "Pauza" }
                        fun overlap(start: Long, end: Long?, from: Long, to: Long): Long =
                            (minOf(end ?: tick, to) - maxOf(start, from)).coerceAtLeast(0L)
                        val pauseMillis = pauseRecords.sumOf { overlap(it.start, it.end, periodStart, periodEnd) } +
                            pauseActivityItems.sumOf { overlap(it.start, it.end, periodStart, periodEnd) }
                        val activityMillis = periodItems.filter { it.type != "Pauza" }.sumOf {
                            overlap(it.start, it.end, periodStart, periodEnd)
                        }
                        val workMillis = (activityMillis - pauseMillis).coerceAtLeast(0L)
                        val distanceKm = periodItems.filter { it.type != "Pauza" }.sumOf { db.routeDistanceKm(it.id) }

                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(if (graphMode == "Den") "Časová osa dne" else "Přehled po dnech", style = MaterialTheme.typography.titleLarge, color = navy)
                                if (graphMode == "Den") {
                                    val dayStart = periodStart
                                    val dayEnd = periodEnd
                                    Canvas(Modifier.fillMaxWidth().height(66.dp)) {
                                        val width = size.width
                                        for (hour in 0..24 step 2) {
                                            val x = width * hour / 24f
                                            drawLine(Color(0xFFDCE3EC), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1f)
                                        }
                                        periodItems.filter { it.type != "Pauza" }.forEach { item ->
                                            val from = maxOf(item.start, dayStart)
                                            val to = minOf(item.end ?: tick, dayEnd)
                                            if (to > from) {
                                                val left = width * ((from - dayStart).toFloat() / 86_400_000f)
                                                val right = width * ((to - dayStart).toFloat() / 86_400_000f)
                                                drawRect(blue, androidx.compose.ui.geometry.Offset(left, 5f), androidx.compose.ui.geometry.Size((right-left).coerceAtLeast(2f), 22f))
                                            }
                                        }
                                        pauseRecords.forEach { pauseItem ->
                                            val from = maxOf(pauseItem.start, dayStart)
                                            val to = minOf(pauseItem.end ?: tick, dayEnd)
                                            if (to > from) {
                                                val left = width * ((from - dayStart).toFloat() / 86_400_000f)
                                                val right = width * ((to - dayStart).toFloat() / 86_400_000f)
                                                drawRect(Color(0xFFFF9800), androidx.compose.ui.geometry.Offset(left, 36f), androidx.compose.ui.geometry.Size((right-left).coerceAtLeast(2f), 22f))
                                            }
                                        }
                                        pauseActivityItems.forEach { pauseItem ->
                                            val from = maxOf(pauseItem.start, dayStart)
                                            val to = minOf(pauseItem.end ?: tick, dayEnd)
                                            if (to > from) {
                                                val left = width * ((from - dayStart).toFloat() / 86_400_000f)
                                                val right = width * ((to - dayStart).toFloat() / 86_400_000f)
                                                drawRect(Color(0xFFFF9800), androidx.compose.ui.geometry.Offset(left, 36f), androidx.compose.ui.geometry.Size((right-left).coerceAtLeast(2f), 22f))
                                            }
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFF596575)) }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(blue, RoundedCornerShape(2.dp))); Spacer(Modifier.width(5.dp)); Text("Činnost", style = MaterialTheme.typography.labelSmall) }
                                        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(Color(0xFFFF9800), RoundedCornerShape(2.dp))); Spacer(Modifier.width(5.dp)); Text("Pauza", style = MaterialTheme.typography.labelSmall) }
                                    }
                                } else {
                                    val days = (0..6).map { offset ->
                                        val startOfDay = periodStart + offset * 86_400_000L
                                        val endOfDay = minOf(startOfDay + 86_400_000L, periodEnd)
                                        val dayWork = periodItems.filter { it.type != "Pauza" }.sumOf { overlap(it.start, it.end, startOfDay, endOfDay) }
                                        val dayPause = pauseRecords.sumOf { overlap(it.start, it.end, startOfDay, endOfDay) } +
                                            pauseActivityItems.sumOf { overlap(it.start, it.end, startOfDay, endOfDay) }
                                        Triple(startOfDay, (dayWork - dayPause).coerceAtLeast(0L), dayPause)
                                    }
                                    val maxDay = (days.maxOfOrNull { it.second + it.third } ?: 0L).coerceAtLeast(1L)
                                    days.forEach { (day, work, pauseTime) ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(SimpleDateFormat("EEE dd.MM.", Locale.getDefault()).format(Date(day)), color = navy)
                                                Text(formatDuration(work + pauseTime), color = Color(0xFF596575))
                                            }
                                            Row(Modifier.fillMaxWidth().height(13.dp).background(Color(0xFFE8EDF4), RoundedCornerShape(20.dp))) {
                                                Box(Modifier.weight((work.toFloat() / maxDay).coerceAtLeast(0.001f)).fillMaxHeight().background(blue))
                                                if (pauseTime > 0) Box(Modifier.weight((pauseTime.toFloat() / maxDay).coerceAtLeast(0.001f)).fillMaxHeight().background(Color(0xFFFF9800)))
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(blue, RoundedCornerShape(2.dp))); Spacer(Modifier.width(5.dp)); Text("Odpracováno", style = MaterialTheme.typography.labelSmall) }
                                        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(Color(0xFFFF9800), RoundedCornerShape(2.dp))); Spacer(Modifier.width(5.dp)); Text("Pauza", style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                            }
                        }

                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Souhrn", style = MaterialTheme.typography.titleLarge, color = navy)
                                InfoRow("Odpracováno", formatDuration(workMillis))
                                InfoRow("Najeto", "%.1f km".format(Locale.getDefault(), distanceKm))
                                InfoRow("Natankováno", "%.1f l".format(Locale.getDefault(), db.fuelLitersBetween(periodStart, periodEnd)))
                                InfoRow("Pauza", formatDuration(pauseMillis))
                            }
                        }

                        Text("Jednotlivé činnosti", style = MaterialTheme.typography.titleLarge, color = navy)
                        if (periodItems.isEmpty()) {
                            Text("Pro toto období nejsou žádné záznamy.")
                        } else {
                            periodItems.sortedByDescending { it.start }.forEach { item ->
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            val itemIcon = if (item.type == "Pauza") Icons.Filled.Coffee
                                                else activities.firstOrNull { it.first == item.type }?.second ?: Icons.Filled.LocalShipping
                                            Icon(itemIcon, contentDescription = item.type, tint = blue, modifier = Modifier.size(28.dp))
                                            Text(item.type, style = MaterialTheme.typography.titleMedium, color = navy)
                                        }
                                        item.company?.let { Text(it, color = Color(0xFF596575)) }
                                        Text(dateOnly(item.start) + " · " + timeOnly(item.start) + " – " + (item.end?.let { timeOnly(it) } ?: "probíhá"))
                                        Text("Trvání: " + formatDuration(overlap(item.start, item.end, periodStart, periodEnd)))
                                        OutlinedButton(onClick = { detailItem = item }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Filled.History, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Zobrazit celý detail činnosti")
                                        }
                                    }
                                }
                            }
                        }
                    }               }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (drawerState.isOpen) {
            Box(
                Modifier.fillMaxSize()
                    .padding(top = 104.dp)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable { drawerScope.launch { drawerState.close() } }
            )
            val menuShape = RoundedCornerShape(14.dp)
            Column(
                Modifier.align(Alignment.TopEnd)
                    .padding(top = 104.dp)
                    .width(IntrinsicSize.Max)
                    .widthIn(min = 168.dp, max = 240.dp)
                    .border(2.dp, Color(0xFF2684D8), menuShape)
                    .background(Color(0xFF102B46), menuShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentUser == null) {
                    Row(Modifier.fillMaxWidth().clickable {
                        authRegisterMode = false; authMessage = ""; authDialogVisible = true
                        drawerScope.launch { drawerState.close() }
                    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Login, contentDescription = null, tint = Color.White)
                        Text("Přihlásit", color = Color.White)
                    }
                    Row(Modifier.fillMaxWidth().clickable {
                        authRegisterMode = true; authMessage = ""; authDialogVisible = true
                        drawerScope.launch { drawerState.close() }
                    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Color.White)
                        Text("Registrace", color = Color.White)
                    }
                } else {
                    Text(currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Řidič", color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (accountVehiclePlates.isNotBlank()) {
                        Text(accountVehiclePlates, color = Color(0xFFFFD166), style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    Row(Modifier.fillMaxWidth().clickable {
                        accountName = currentUser?.displayName.orEmpty()
                        accountEmail = currentUser?.email.orEmpty()
                        accountCurrentPassword = ""
                        accountNewPassword = ""
                        accountMessage = ""
                        accountDialogVisible = true
                        drawerScope.launch { drawerState.close() }
                    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
                        Text("Účet", color = Color.White)
                    }
                    Row(Modifier.fillMaxWidth().clickable {
                        firebaseAuth.signOut()
                        drawerScope.launch { drawerState.close() }
                    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Logout, contentDescription = null, tint = Color.White)
                        Text("Odhlásit", color = Color.White)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable {
                        page = 4
                        drawerScope.launch { drawerState.close() }
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Color.White)
                    Text("Vozidla", color = Color.White)
                }
                Row(
                    Modifier.fillMaxWidth().clickable {
                        page = 3
                        drawerScope.launch { drawerState.close() }
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Inventory2, contentDescription = null, tint = Color.White)
                    Text("Záloha", color = Color.White)
                }
            }
        }
        }
    }

    @Composable
    private fun GpsInfoRow(label: String, lat: Double?, lon: Double?, onMapClick: (Double, Double) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$label:", modifier = Modifier.weight(1f), color = Color(0xFF596575))
            Row(modifier = Modifier.weight(1.5f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Text(coordinates(lat, lon), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                if (lat != null && lon != null) {
                    IconButton(onClick = { onMapClick(lat, lon) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Map, contentDescription = "Zobrazit $label na mapě", tint = Color(0xFF14558A))
                    }
                }
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

    private fun openGpsOnMap(lat: Double, lon: Double) {
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            android.widget.Toast.makeText(this, "V telefonu není dostupná mapová aplikace.", android.widget.Toast.LENGTH_SHORT).show()
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
