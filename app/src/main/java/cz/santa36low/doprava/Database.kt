package cz.santa36low.doprava

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ActivityRecord(
    val id: Long,
    val type: String,
    val start: Long,
    val end: Long?,
    val note: String,
    val startLat: Double? = null,
    val startLon: Double? = null,
    val endLat: Double? = null,
    val endLon: Double? = null,
    val startPlace: String? = null,
    val endPlace: String? = null,
    val company: String? = null
)
data class FuelRecord(val id: Long, val liters: Double, val time: Long, val latitude: Double?, val longitude: Double?)

class Database(context: Context) : SQLiteOpenHelper(context, "doprava.db", null, 5) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE activities(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, start_time INTEGER NOT NULL, end_time INTEGER, start_lat REAL, start_lon REAL, end_lat REAL, end_lon REAL, start_place TEXT, end_place TEXT, company TEXT, note TEXT DEFAULT '')")
        db.execSQL("CREATE TABLE fuel(id INTEGER PRIMARY KEY AUTOINCREMENT, liters REAL NOT NULL, time INTEGER NOT NULL, lat REAL, lon REAL)")
        db.execSQL("CREATE TABLE route_points(id INTEGER PRIMARY KEY AUTOINCREMENT, activity_id INTEGER NOT NULL, time INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL)")
        db.execSQL("CREATE TABLE pauses(id INTEGER PRIMARY KEY AUTOINCREMENT, activity_id INTEGER NOT NULL, start_time INTEGER NOT NULL, end_time INTEGER)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_route_activity_time ON route_points(activity_id, time)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("CREATE TABLE IF NOT EXISTS pauses(id INTEGER PRIMARY KEY AUTOINCREMENT, activity_id INTEGER NOT NULL, start_time INTEGER NOT NULL, end_time INTEGER)")
        if (oldVersion < 3) db.execSQL("CREATE INDEX IF NOT EXISTS idx_route_activity_time ON route_points(activity_id, time)")
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE activities ADD COLUMN start_place TEXT")
            db.execSQL("ALTER TABLE activities ADD COLUMN end_place TEXT")
        }
        if (oldVersion < 5) db.execSQL("ALTER TABLE activities ADD COLUMN company TEXT")
    }

    private fun readActivity(c: android.database.Cursor) = ActivityRecord(
        id = c.getLong(0),
        type = c.getString(1),
        start = c.getLong(2),
        end = if (c.isNull(3)) null else c.getLong(3),
        note = c.getString(4) ?: "",
        startLat = if (c.isNull(5)) null else c.getDouble(5),
        startLon = if (c.isNull(6)) null else c.getDouble(6),
        endLat = if (c.isNull(7)) null else c.getDouble(7),
        endLon = if (c.isNull(8)) null else c.getDouble(8),
        startPlace = if (c.isNull(9)) null else c.getString(9),
        endPlace = if (c.isNull(10)) null else c.getString(10),
        company = if (c.isNull(11)) null else c.getString(11)
    )

    fun active(): ActivityRecord? = readableDatabase.rawQuery(
        "SELECT id,type,start_time,end_time,note,start_lat,start_lon,end_lat,end_lon,start_place,end_place,company FROM activities WHERE end_time IS NULL ORDER BY id DESC LIMIT 1", null
    ).use { if (!it.moveToFirst()) null else readActivity(it) }

    fun start(type: String, lat: Double?, lon: Double?, place: String? = null, company: String? = null, startTime: Long = System.currentTimeMillis()): Long {
        val v = ContentValues().apply {
            put("type", type); put("start_time", startTime)
            if (lat != null) put("start_lat", lat)
            if (lon != null) put("start_lon", lon)
            if (!place.isNullOrBlank()) put("start_place", place)
            if (!company.isNullOrBlank()) put("company", company)
        }
        return writableDatabase.insert("activities", null, v)
    }

    fun updateStartLocation(id: Long, lat: Double, lon: Double) {
        val v = ContentValues().apply { put("start_lat", lat); put("start_lon", lon) }
        writableDatabase.update("activities", v, "id=?", arrayOf(id.toString()))
    }

    fun updateStartPlace(id: Long, place: String) {
        val v = ContentValues().apply { put("start_place", place) }
        writableDatabase.update("activities", v, "id=?", arrayOf(id.toString()))
    }

    fun updateEndPlace(id: Long, place: String) {
        val v = ContentValues().apply { put("end_place", place) }
        writableDatabase.update("activities", v, "id=?", arrayOf(id.toString()))
    }

    fun finish(id: Long, lat: Double?, lon: Double?, place: String? = null, company: String? = null, endTime: Long = System.currentTimeMillis()) {
        val v = ContentValues().apply {
            put("end_time", endTime)
            if (lat != null) put("end_lat", lat)
            if (lon != null) put("end_lon", lon)
            if (!place.isNullOrBlank()) put("end_place", place)
            if (!company.isNullOrBlank()) put("company", company)
        }
        writableDatabase.update("activities", v, "id=?", arrayOf(id.toString()))
    }

    fun history(): List<ActivityRecord> = readableDatabase.rawQuery(
        "SELECT id,type,start_time,end_time,note,start_lat,start_lon,end_lat,end_lon,start_place,end_place,company FROM activities ORDER BY id DESC LIMIT 100", null
    ).use { c -> buildList { while (c.moveToNext()) add(readActivity(c)) } }



    fun updateActivity(id: Long, type: String, start: Long, end: Long?, startPlace: String?, endPlace: String?, company: String?, note: String) {
        val v = ContentValues().apply {
            put("type", type); put("start_time", start)
            if (end == null) putNull("end_time") else put("end_time", end)
            if (startPlace.isNullOrBlank()) putNull("start_place") else put("start_place", startPlace)
            if (endPlace.isNullOrBlank()) putNull("end_place") else put("end_place", endPlace)
            if (company.isNullOrBlank()) putNull("company") else put("company", company)
            put("note", note)
        }
        writableDatabase.update("activities", v, "id=?", arrayOf(id.toString()))
    }

    fun deleteActivity(id: Long) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("route_points", "activity_id=?", arrayOf(id.toString()))
            writableDatabase.delete("pauses", "activity_id=?", arrayOf(id.toString()))
            writableDatabase.delete("activities", "id=?", arrayOf(id.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    fun activePause(): Pair<Long, Long>? = readableDatabase.rawQuery(
        "SELECT id,start_time FROM pauses WHERE end_time IS NULL ORDER BY id DESC LIMIT 1", null
    ).use { if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else null }

    fun startPause(activityId: Long) {
        val v = ContentValues().apply {
            put("activity_id", activityId)
            put("start_time", System.currentTimeMillis())
        }
        writableDatabase.insert("pauses", null, v)
    }

    fun pauseDurationMillis(activityId: Long): Long = readableDatabase.rawQuery(
        "SELECT COALESCE(SUM(COALESCE(end_time, ?) - start_time), 0) FROM pauses WHERE activity_id=?",
        arrayOf(System.currentTimeMillis().toString(), activityId.toString())
    ).use { if (it.moveToFirst()) it.getLong(0) else 0L }

    fun finishPause() {
        val v = ContentValues().apply { put("end_time", System.currentTimeMillis()) }
        writableDatabase.update("pauses", v, "end_time IS NULL", null)
    }

    fun latestPause(activityId: Long): Pair<Long, Long?>? =
        readableDatabase.rawQuery(
            "SELECT start_time,end_time FROM pauses WHERE activity_id=? ORDER BY id DESC LIMIT 1",
            arrayOf(activityId.toString())
        ).use { c ->
            if (c.moveToFirst()) c.getLong(0) to (if (c.isNull(1)) null else c.getLong(1)) else null
        }

    data class PauseRecord(val id: Long, val activityId: Long, val start: Long, val end: Long?)

    fun pausesForDay(dayStart: Long, dayEnd: Long): List<PauseRecord> =
        readableDatabase.rawQuery(
            "SELECT id,activity_id,start_time,end_time FROM pauses WHERE start_time < ? AND COALESCE(end_time, ?) >= ? ORDER BY start_time",
            arrayOf(dayEnd.toString(), System.currentTimeMillis().toString(), dayStart.toString())
        ).use { c -> buildList {
            while (c.moveToNext()) add(PauseRecord(c.getLong(0), c.getLong(1), c.getLong(2), if (c.isNull(3)) null else c.getLong(3)))
        } }

    fun addFuel(liters: Double, lat: Double?, lon: Double?) {
        val v = ContentValues().apply {
            put("liters", liters); put("time", System.currentTimeMillis())
            if (lat != null) put("lat", lat)
            if (lon != null) put("lon", lon)
        }
        writableDatabase.insert("fuel", null, v)
    }

    data class RoutePoint(val time: Long, val latitude: Double, val longitude: Double)

    fun addRoutePoint(activityId: Long, lat: Double, lon: Double, time: Long = System.currentTimeMillis()) {
        val v = ContentValues().apply {
            put("activity_id", activityId); put("time", time)
            put("lat", lat); put("lon", lon)
        }
        writableDatabase.insert("route_points", null, v)
    }

    fun routePoints(activityId: Long): List<RoutePoint> =
        readableDatabase.rawQuery(
            "SELECT time,lat,lon FROM route_points WHERE activity_id=? ORDER BY time",
            arrayOf(activityId.toString())
        ).use { c -> buildList {
            while (c.moveToNext()) add(RoutePoint(c.getLong(0), c.getDouble(1), c.getDouble(2)))
        } }

    fun routeDistanceKm(activityId: Long): Double {
        val points = routePoints(activityId)
        if (points.size < 2) return 0.0
        var meters = 0f
        val result = FloatArray(1)
        for (i in 1 until points.size) {
            android.location.Location.distanceBetween(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude, result
            )
            meters += result[0]
        }
        return meters / 1000.0
    }
}
