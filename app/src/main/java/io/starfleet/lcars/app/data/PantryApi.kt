package io.starfleet.lcars.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PantryApi(
    private val baseUrl: String = DEFAULT_BASE,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    fun snapshot(): List<PantryItem> {
        val request = Request.Builder().url("$baseUrl/api/pantry").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("pantry HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            return parseItems(json.optJSONArray("items") ?: JSONArray())
        }
    }

    fun scan(barcode: String, location: String, mode: String = "in"): ScanResult {
        val payload = JSONObject()
            .put("action", "scan")
            .put("barcode", barcode)
            .put("qty", 1)
            .put("location", location)
            .put("mode", mode)
        return post(payload)
    }

    fun manual(name: String, location: String, qty: Double = 1.0, unit: String = "stk"): ScanResult {
        val payload = JSONObject()
            .put("action", "manual")
            .put("name", name)
            .put("qty", qty)
            .put("unit", unit)
            .put("location", location)
        return post(payload)
    }

    fun adjust(id: String, delta: Double): ScanResult {
        val payload = JSONObject()
            .put("action", "adjust")
            .put("id", id)
            .put("delta", delta)
        return post(payload)
    }

    private fun post(payload: JSONObject): ScanResult {
        val request = Request.Builder()
            .url("$baseUrl/api/pantry/action")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                return ScanResult(false, false, null, "empty response", null)
            }
            val json = JSONObject(body)
            val item = json.optJSONObject("item")?.let { parseItem(it) }
            return ScanResult(
                success = json.optBoolean("success", false),
                created = json.optBoolean("created", false),
                item = item,
                error = json.optString("error").ifBlank { null },
                source = item?.source ?: json.optJSONObject("lookup")?.optString("source"),
            )
        }
    }

    private fun parseItems(array: JSONArray): List<PantryItem> =
        (0 until array.length()).map { parseItem(array.getJSONObject(it)) }

    private fun parseItem(json: JSONObject): PantryItem = PantryItem(
        id = json.optString("id"),
        barcode = json.optString("barcode").ifBlank { null },
        name = json.optString("name"),
        brand = json.optString("brand").ifBlank { null },
        qty = json.optDouble("qty", 0.0),
        unit = json.optString("unit", "stk"),
        location = json.optString("location"),
        locationLabel = json.optString("locationLabel").ifBlank { null },
        source = json.optString("source").ifBlank { null },
        needsName = json.optBoolean("needsName", false),
        lastScanAt = json.optString("lastScanAt").ifBlank { json.optString("updatedAt").ifBlank { null } },
    )

    companion object {
        const val DEFAULT_BASE = "http://192.168.68.123"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
