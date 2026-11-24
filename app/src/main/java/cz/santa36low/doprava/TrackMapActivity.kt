package cz.santa36low.doprava

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject

class TrackMapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activityId = intent.getLongExtra("activity_id", -1L)
        val points = Database(this).routePoints(activityId)
        val json = JSONArray()
        points.forEach { point ->
            json.put(JSONObject().put("lat", point.latitude).put("lon", point.longitude))
        }
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        setContentView(web)
        val data = json.toString()
        val html = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>html,body,#map{height:100%;margin:0;font-family:Arial}#msg{position:absolute;z-index:1000;top:12px;left:12px;right:12px;background:white;padding:10px;border-radius:8px;box-shadow:0 1px 5px #888}</style>
            </head><body><div id="map"></div><div id="msg">Trasa jízdy · OpenStreetMap</div>
            <script>
            const pts=$data.map(p=>[p.lat,p.lon]);
            const map=L.map('map');
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
            if(pts.length){const line=L.polyline(pts,{color:'#14558a',weight:5}).addTo(map);L.marker(pts[0]).addTo(map).bindPopup('Začátek');L.marker(pts[pts.length-1]).addTo(map).bindPopup('Konec');map.fitBounds(line.getBounds(),{padding:[25,25]});}
            else {map.setView([49.8,15.5],7);document.getElementById('msg').innerText='Pro tuto jízdu zatím není zaznamenaná GPS trasa.';}
            </script></body></html>
        """.trimIndent()
        web.loadDataWithBaseURL("https://www.openstreetmap.org/", html, "text/html", "UTF-8", null)
    }
}
