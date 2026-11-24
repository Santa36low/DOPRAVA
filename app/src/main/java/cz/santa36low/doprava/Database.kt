package cz.santa36low.doprava

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ActivityRecord(val id: Long, val type: String, val start: Long, val end: Long?, val note: String)
data class FuelRecord(val id: Long, val liters: Double, val time: Long, val latitude: Double?, val longitude: Double?)

class Database(context: Context) : SQLiteOpenHelper(context, "doprava.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE activities(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, start_time INTEGER NOT NULL, end_time INTEGER, start_lat REAL, start_lon REAL, end_lat REAL, end_lon REAL, note TEXT DEFAULT '')")
        db.execSQL("CREATE TABLE fuel(id INTEGER PRIMARY KEY AUTOINCREMENT, liters REAL NOT NULL, time INTEGER NOT NULL, lat REAL, lon REAL)")
        db.execSQL("CREATE TABLE route_points(id INTEGER PRIMARY KEY AUTOINCREMENT, activity_id INTEGER NOT NULL, time INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun active(): ActivityRecord? = readableDatabase.rawQuery("SELECT id,type,start_time,end_time,note FROM activities WHERE end_time IS NULL ORDER BY id DESC LIMIT 1", null).use {
        if (!it.moveToFirst()) null else ActivityRecord(it.getLong(0), it.getString(1), it.getLong(2), null, it.getString(4) ?: "")
    }
    fun start(type: String, lat: Double?, lon: Double?): Long {
        val v = ContentValues().apply { put("type", type); put("start_time", System.currentTimeMillis()); if(lat != null) put("start_lat", lat); if(lon != null) put("start_lon", lon) }
        return writableDatabase.insert("activities", null, v)
    }
    fun finish(id: Long, lat: Double?, lon: Double?) {
        val v = ContentValues().apply { put("end_time", System.currentTimeMillis()); if(lat != null) put("end_lat", lat); if(lon != null) put("end_lon", lon) }
        writableDatabase.update("activities", v, "id=?", arrayOf(id.toString()))
    }
    fun history(): List<ActivityRecord> = readableDatabase.rawQuery("SELECT id,type,start_time,end_time,note FROM activities ORDER BY id DESC LIMIT 100", null).use { c ->
        buildList { while(c.moveToNext()) add(ActivityRecord(c.getLong(0),c.getString(1),c.getLong(2),if(c.isNull(3)) null else c.getLong(3),c.getString(4) ?: "")) }
    }
    fun addFuel(liters: Double, lat: Double?, lon: Double?) {
        val v = ContentValues().apply { put("liters",liters); put("time",System.currentTimeMillis()); if(lat != null) put("lat",lat); if(lon != null) put("lon",lon) }
        writableDatabase.insert("fuel",null,v)
    }
    fun addRoutePoint(activityId: Long, lat: Double, lon: Double) {
        val v = ContentValues().apply { put("activity_id",activityId); put("time",System.currentTimeMillis()); put("lat",lat); put("lon",lon) }
        writableDatabase.insert("route_points",null,v)
    }
}
