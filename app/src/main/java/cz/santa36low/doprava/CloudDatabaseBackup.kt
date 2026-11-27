package cz.santa36low.doprava

import android.database.sqlite.SQLiteDatabase
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Ruční záloha kompletní SQLite databáze do Firestore.
 * Každý účet má vlastní cestu users/{uid}; soubor se ukládá po blocích,
 * aby se vešel do limitu velikosti jednoho Firestore dokumentu.
 */
object CloudDatabaseBackup {
    private const val CHUNK_SIZE = 700 * 1024

    fun upload(databaseFile: File, uid: String) {
        require(uid.isNotBlank()) { "Uživatel není přihlášen." }
        require(databaseFile.exists() && databaseFile.length() > 0L) { "Místní databáze je prázdná." }
        val bytes = databaseFile.readBytes()
        validateDatabaseBytes(bytes)
        val firestore = FirebaseFirestore.getInstance()
        val backups = firestore.collection("users").document(uid).collection("backups")
        val backupId = UUID.randomUUID().toString()
        val backupRef = backups.document(backupId)
        val count = (bytes.size + CHUNK_SIZE - 1) / CHUNK_SIZE

        for (index in 0 until count) {
            val from = index * CHUNK_SIZE
            val to = minOf(from + CHUNK_SIZE, bytes.size)
            val chunk = bytes.copyOfRange(from, to)
            Tasks.await(
                backupRef.collection("chunks").document(index.toString().padStart(6, '0'))
                    .set(mapOf("data" to Blob.fromBytes(chunk)))
            )
        }

        // Ukazatel latest zapíšeme až po úspěšném nahrání všech bloků.
        Tasks.await(
            backups.document("latest").set(
                mapOf(
                    "backupId" to backupId,
                    "chunkCount" to count,
                    "byteCount" to bytes.size,
                    "createdAt" to System.currentTimeMillis(),
                    "formatVersion" to 1
                )
            )
        )
    }

    fun downloadToFile(target: File, uid: String) {
        require(uid.isNotBlank()) { "Uživatel není přihlášen." }
        val backups = FirebaseFirestore.getInstance()
            .collection("users").document(uid).collection("backups")
        val latest = Tasks.await(backups.document("latest").get())
        if (!latest.exists()) throw IllegalStateException("V cloudu zatím není žádná záloha.")
        val backupId = latest.getString("backupId")
            ?: throw IllegalStateException("Záloha v cloudu nemá platný identifikátor.")
        val chunkCount = (latest.getLong("chunkCount") ?: 0L).toInt()
        val byteCount = (latest.getLong("byteCount") ?: 0L).toInt()
        require(chunkCount in 1..100000 && byteCount > 0) { "Záloha v cloudu má neplatné údaje." }

        val output = ByteArrayOutputStream(byteCount)
        val backupRef = backups.document(backupId)
        for (index in 0 until chunkCount) {
            val snapshot = Tasks.await(
                backupRef.collection("chunks").document(index.toString().padStart(6, '0')).get()
            )
            val blob = snapshot.get("data") as? Blob
                ?: throw IllegalStateException("V záloze chybí část číslo ${index + 1}.")
            output.write(blob.toBytes())
        }
        val bytes = output.toByteArray()
        require(bytes.size == byteCount) { "Stažená záloha nemá očekávanou velikost." }
        validateDatabaseBytes(bytes)
        target.writeBytes(bytes)
    }

    private fun validateDatabaseBytes(bytes: ByteArray) {
        require(bytes.size >= 16 && String(bytes, 0, 15, Charsets.US_ASCII) == "SQLite format 3" && bytes[15].toInt() == 0) {
            "Soubor není platná databáze SQLite."
        }
        val temp = File.createTempFile("doprava-cloud-check", ".db")
        try {
            temp.writeBytes(bytes)
            SQLiteDatabase.openDatabase(temp.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                val integrity = database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else ""
                }
                require(integrity == "ok") { "Databáze je poškozená: $integrity" }
                val tables = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
                require(tables.containsAll(setOf("activities", "fuel", "route_points", "pauses"))) {
                    "Databáze neobsahuje všechna data aplikace."
                }
                val columns = database.rawQuery("PRAGMA table_info(activities)", null).use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
                }
                require(columns.containsAll(setOf("company", "note", "start_place", "end_place"))) {
                    "Databáze pochází z nekompatibilní verze aplikace."
                }
            }
        } finally {
            temp.delete()
        }
    }
}
