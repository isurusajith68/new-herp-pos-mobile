package com.example.posmobile.data

import android.content.Context
import com.example.posmobile.print.PrinterService
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Simple service locator, initialised once from MainActivity. */
object Container {
    lateinit var settings: Settings
        private set
    lateinit var auth: AuthRepository
        private set
    lateinit var pos: PosRepository
        private set
    lateinit var printer: PrinterService
        private set
    lateinit var updater: GithubUpdater
        private set

    @Volatile private var initialised = false

    fun init(context: Context) {
        if (initialised) return
        val app = context.applicationContext

        settings = Settings(app)
        val cookieJar = PersistentCookieJar(app)
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val authClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        auth = AuthRepository(settings, authClient, json)

        val apiClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(auth))
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            // Placeholder base — every call supplies an absolute @Url.
            .baseUrl("https://api.${settings.domainBase}/")
            .client(apiClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        pos = PosRepository(settings, retrofit.create(PosApi::class.java))
        printer = PrinterService(app, settings)
        updater = GithubUpdater(app)

        initialised = true
    }
}
