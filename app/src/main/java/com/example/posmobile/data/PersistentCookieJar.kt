package com.example.posmobile.data

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cookie store backed by SharedPreferences. The IdP delivers the refresh token
 * as an HttpOnly cookie on the auth host; persisting it lets the app silently
 * re-authenticate after a restart (same as the web app's cookie-based refresh).
 */
class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("pos_cookies", Context.MODE_PRIVATE)

    // host -> (name -> Cookie)
    private val cache = HashMap<String, HashMap<String, Cookie>>()

    init { load() }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val map = cache.getOrPut(host) { HashMap() }
        for (c in cookies) {
            if (c.expiresAt <= System.currentTimeMillis()) map.remove(c.name) else map[c.name] = c
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val map = cache[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        val valid = ArrayList<Cookie>()
        val expired = ArrayList<String>()
        for ((name, c) in map) {
            if (c.expiresAt <= now) expired.add(name)
            else if (c.matches(url)) valid.add(c)
        }
        if (expired.isNotEmpty()) { expired.forEach { map.remove(it) }; persist() }
        return valid
    }

    @Synchronized
    fun clear() {
        cache.clear()
        prefs.edit().clear().apply()
    }

    private fun persist() {
        val root = JSONObject()
        for ((host, map) in cache) {
            val arr = JSONArray()
            for (c in map.values) arr.put(cookieToJson(c))
            root.put(host, arr)
        }
        prefs.edit().putString(KEY, root.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        try {
            val root = JSONObject(raw)
            for (host in root.keys()) {
                val arr = root.getJSONArray(host)
                val map = HashMap<String, Cookie>()
                for (i in 0 until arr.length()) {
                    val c = cookieFromJson(arr.getJSONObject(i)) ?: continue
                    if (c.expiresAt > System.currentTimeMillis()) map[c.name] = c
                }
                if (map.isNotEmpty()) cache[host] = map
            }
        } catch (_: Exception) {
            // Corrupt store — start clean rather than crash.
            prefs.edit().remove(KEY).apply()
        }
    }

    private fun cookieToJson(c: Cookie): JSONObject = JSONObject().apply {
        put("name", c.name)
        put("value", c.value)
        put("expiresAt", c.expiresAt)
        put("domain", c.domain)
        put("path", c.path)
        put("secure", c.secure)
        put("httpOnly", c.httpOnly)
        put("hostOnly", c.hostOnly)
    }

    private fun cookieFromJson(o: JSONObject): Cookie? = try {
        val b = Cookie.Builder()
            .name(o.getString("name"))
            .value(o.getString("value"))
            .expiresAt(o.getLong("expiresAt"))
            .path(o.optString("path", "/"))
        if (o.optBoolean("hostOnly", false)) b.hostOnlyDomain(o.getString("domain"))
        else b.domain(o.getString("domain"))
        if (o.optBoolean("secure", false)) b.secure()
        if (o.optBoolean("httpOnly", false)) b.httpOnly()
        b.build()
    } catch (_: Exception) { null }

    companion object {
        private const val KEY = "cookies_json"
    }
}
