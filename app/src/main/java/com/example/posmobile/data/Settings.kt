package com.example.posmobile.data

import android.content.Context

/**
 * Small persisted-preferences wrapper. Holds the tenant/server config the app
 * needs to reach the deployed HERP backend, plus the last-used outlet and the
 * selected Bluetooth printer.
 *
 * Derived hosts (all from tenantSlug + domainBase):
 *   auth   -> https://{slug}-auth.{domain}
 *   api    -> https://api.{domain}/v1
 *   pos redirect_uri (registered) -> https://{slug}-pos.{domain}/callback
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("pos_settings", Context.MODE_PRIVATE)

    var tenantSlug: String
        get() = prefs.getString(KEY_SLUG, "") ?: ""
        set(v) = prefs.edit().putString(KEY_SLUG, v.trim()).apply()

    var domainBase: String
        get() = prefs.getString(KEY_DOMAIN, DEFAULT_DOMAIN) ?: DEFAULT_DOMAIN
        set(v) = prefs.edit().putString(KEY_DOMAIN, v.trim().ifEmpty { DEFAULT_DOMAIN }).apply()

    /** Active printer transport: "bluetooth" (paired SPP) or "wifi" (raw TCP). */
    var printerType: String
        get() = prefs.getString(KEY_PRINTER_TYPE, TYPE_BLUETOOTH) ?: TYPE_BLUETOOTH
        set(v) = prefs.edit().putString(KEY_PRINTER_TYPE, v).apply()

    var printerMac: String?
        get() = prefs.getString(KEY_PRINTER_MAC, null)
        set(v) = prefs.edit().putString(KEY_PRINTER_MAC, v).apply()

    var printerName: String?
        get() = prefs.getString(KEY_PRINTER_NAME, null)
        set(v) = prefs.edit().putString(KEY_PRINTER_NAME, v).apply()

    /** WiFi printer IP address, e.g. "192.168.1.50". */
    var printerHost: String?
        get() = prefs.getString(KEY_PRINTER_HOST, null)
        set(v) = prefs.edit().putString(KEY_PRINTER_HOST, v?.trim()).apply()

    /** Raw ESC/POS port — 9100 (JetDirect/RAW) on virtually every network printer. */
    var printerPort: Int
        get() = prefs.getInt(KEY_PRINTER_PORT, DEFAULT_PRINTER_PORT)
        set(v) = prefs.edit().putInt(KEY_PRINTER_PORT, v).apply()

    /** Characters per line: 32 for 58 mm, 48 for 80 mm. */
    var paperCols: Int
        get() = prefs.getInt(KEY_PAPER_COLS, 32)
        set(v) = prefs.edit().putInt(KEY_PAPER_COLS, v).apply()

    val clientId: String get() = "herp-pos"

    val authBase: String get() = "https://$tenantSlug-auth.$domainBase"
    val apiBase: String get() = "https://api.$domainBase/v1"
    val redirectUri: String get() = "https://$tenantSlug-pos.$domainBase/callback"

    val isConfigured: Boolean get() = tenantSlug.isNotBlank()

    /** True when the active printer transport has enough config to attempt a print. */
    val isPrinterConfigured: Boolean
        get() = when (printerType) {
            TYPE_WIFI -> !printerHost.isNullOrBlank()
            else -> printerMac != null
        }

    companion object {
        const val TYPE_BLUETOOTH = "bluetooth"
        const val TYPE_WIFI = "wifi"
        private const val DEFAULT_DOMAIN = "v3.ceyinfo.com"
        private const val DEFAULT_PRINTER_PORT = 9100
        private const val KEY_SLUG = "tenant_slug"
        private const val KEY_DOMAIN = "domain_base"
        private const val KEY_PRINTER_TYPE = "printer_type"
        private const val KEY_PRINTER_MAC = "printer_mac"
        private const val KEY_PRINTER_NAME = "printer_name"
        private const val KEY_PRINTER_HOST = "printer_host"
        private const val KEY_PRINTER_PORT = "printer_port"
        private const val KEY_PAPER_COLS = "paper_cols"
    }
}
