package cz.santa36low.doprava

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    @Composable private fun DopravaScreen() {
        val types = listOf("Jízda","Nákladka","Výkladka","Pauza","Cesta domů")
        var active by remember { mutableStateOf(db.active()) }
        var history by remember { mutableStateOf(db.history()) }
        var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var fuelText by remember { mutableStateOf("") }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
        LaunchedEffect(Unit) { while(true) { tick=System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
        fun hasLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        fun withLocation(done: (Double?,Double?) -> Unit) {
            if (!hasLocation()) { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)); done(null,null); return }
            try { LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { done(it?.latitude,it?.longitude) }.addOnFailureListener { done(null,null) } } catch (_: SecurityException) { done(null,null) }
        }
        fun toggle(type: String) {
            withLocation { lat,lon ->
                if (active?.type == type) {
                    val old = active!!
                    db.finish(old.id,lat,lon)
                    stopService(Intent(this,RouteTrackingService::class.java))
                } else {
                    active?.let { db.finish(it.id,lat,lon) }
                    val id = db.start(type,lat,lon)
                    if (type == "Jízda" && hasLocation()) {
                        try { ContextCompat.startForegroundService(this,Intent(this,RouteTrackingService::class.java).putExtra("activity_id",id)) } catch (_: Exception) { }
                    } else stopService(Intent(this,RouteTrackingService::class.java))
                }
                active=db.active(); history=db.history()
            }
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("DOPRAVA", style=MaterialTheme.typography.headlineLarge)
            Text(active?.let { "Právě probíhá: ${it.type}  •  ${formatDuration(tick-it.start)}" } ?: "Žádná aktivní činnost", style=MaterialTheme.typography.titleMedium)
            types.chunked(2).forEach { pair -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                pair.forEach { type -> Button(onClick={toggle(type)}, modifier=Modifier.weight(1f).height(68.dp), colors=ButtonDefaults.buttonColors(containerColor=if(active?.type==type) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)) { Text(type) } }
                if(pair.size==1) Spacer(Modifier.weight(1f))
            } }
            Divider()
            Text("Tankování", style=MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value=fuelText,onValueChange={fuelText=it},label={Text("Litry")},modifier=Modifier.weight(1f))
                Button(onClick={ val l=fuelText.replace(',','.').toDoubleOrNull(); if(l!=null && l>0) { withLocation { lat,lon -> db.addFuel(l,lat,lon); fuelText="" } } }) { Text("Uložit") }
            }
            Text("Historie", style=MaterialTheme.typography.titleLarge)
            LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(history) { item ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) {
                        Text(item.type,style=MaterialTheme.typography.titleMedium)
                        Text("${date(item.start)} – ${item.end?.let { date(it) } ?: "probíhá"}")
                    } }
                }
            }
        }
    }
    private fun date(ms: Long) = SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(Date(ms))
    private fun formatDuration(ms: Long): String { val s=(ms/1000).coerceAtLeast(0); return "%02d:%02d:%02d".format(s/3600,(s%3600)/60,s%60) }
}
