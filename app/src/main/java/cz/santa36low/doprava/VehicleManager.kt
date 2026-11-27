package cz.santa36low.doprava

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore

private data class DriverVehicle(val id: String, val kind: String, val type: String, val plate: String)

@Composable
fun VehicleManagerScreen(userId: String?, navy: Color, blue: Color) {
    var vehicles by remember(userId) { mutableStateOf<List<DriverVehicle>>(emptyList()) }
    var loading by remember(userId) { mutableStateOf(false) }
    var message by remember(userId) { mutableStateOf("") }
    var dialogOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<DriverVehicle?>(null) }

    fun loadVehicles() {
        if (userId == null) return
        loading = true
        FirebaseFirestore.getInstance().collection("users").document(userId).collection("vehicles").get()
            .addOnSuccessListener { snapshot ->
                vehicles = snapshot.documents.map { doc ->
                    DriverVehicle(doc.id, doc.getString("kind").orEmpty(), doc.getString("type").orEmpty(), doc.getString("plate").orEmpty())
                }.sortedBy { it.plate }
                loading = false
                message = ""
            }
            .addOnFailureListener { error ->
                loading = false
                message = "Vozidla se nepodařilo načíst: ${error.localizedMessage ?: "chyba"}"
            }
    }

    LaunchedEffect(userId) { loadVehicles() }

    if (userId == null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Moje vozidla", style = MaterialTheme.typography.titleLarge, color = navy)
            Text("Nejdříve se přihlas. Vozidla se ukládají k tvému Firebase účtu a budou dostupná i po přihlášení na jiném telefonu.")
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Moje vozidla", style = MaterialTheme.typography.titleLarge, color = navy)
        Text("Vozidla jsou uložená přímo u přihlášeného účtu.")
        Button(onClick = {
            editingId = null
            kind = ""
            type = ""
            plate = ""
            dialogOpen = true
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = blue)) {
            Text("Přidat vozidlo")
        }
        if (loading) CircularProgressIndicator()
        if (vehicles.isEmpty() && !loading) Text("Zatím nemáš přidané žádné vozidlo.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 440.dp)) {
            items(vehicles, key = { it.id }) { vehicle ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(vehicle.plate, style = MaterialTheme.typography.titleLarge, color = navy)
                        Text("Druh: ${vehicle.kind}")
                        Text("Typ: ${vehicle.type}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                editingId = vehicle.id
                                kind = vehicle.kind
                                type = vehicle.type
                                plate = vehicle.plate
                                dialogOpen = true
                            }) { Text("Upravit") }
                            TextButton(onClick = { deleting = vehicle }) { Text("Smazat", color = Color(0xFFB3261E)) }
                        }
                    }
                }
            }
        }
        if (message.isNotBlank()) Text(message, color = navy)
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(if (editingId == null) "Přidat vozidlo" else "Upravit vozidlo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = kind, onValueChange = { kind = it }, label = { Text("Druh vozidla") }, singleLine = true)
                    OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Typ vozidla") }, singleLine = true)
                    OutlinedTextField(value = plate, onValueChange = { plate = it.uppercase() }, label = { Text("SPZ") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = kind.isNotBlank() && type.isNotBlank() && plate.isNotBlank(), onClick = {
                    val id = editingId ?: java.util.UUID.randomUUID().toString()
                    val data = mapOf("kind" to kind.trim(), "type" to type.trim(), "plate" to plate.trim().uppercase())
                    loading = true
                    FirebaseFirestore.getInstance().collection("users").document(userId).collection("vehicles").document(id).set(data)
                        .addOnSuccessListener { dialogOpen = false; loadVehicles() }
                        .addOnFailureListener { error ->
                            loading = false
                            message = "Vozidlo se nepodařilo uložit: ${error.localizedMessage ?: "chyba"}"
                        }
                }) { Text("Uložit") }
            },
            dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text("Zrušit") } }
        )
    }

    deleting?.let { vehicle ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Smazat vozidlo?") },
            text = { Text("Opravdu chceš smazat vozidlo ${vehicle.plate}?") },
            confirmButton = {
                TextButton(onClick = {
                    FirebaseFirestore.getInstance().collection("users").document(userId).collection("vehicles").document(vehicle.id).delete()
                        .addOnSuccessListener { deleting = null; loadVehicles() }
                        .addOnFailureListener { error ->
                            deleting = null
                            message = "Vozidlo se nepodařilo smazat: ${error.localizedMessage ?: "chyba"}"
                        }
                }) { Text("Smazat", color = Color(0xFFB3261E)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Zrušit") } }
        )
    }
}
