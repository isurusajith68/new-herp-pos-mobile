package com.example.posmobile.data

import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Adds the Bearer token to API calls and transparently refreshes once on a 401.
 * Runs on OkHttp's worker thread, so the synchronous refresh is safe here.
 */
class AuthInterceptor(private val auth: AuthRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = auth.ensureAccessToken()
        val request = if (token != null) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }

        val response = chain.proceed(request)
        if (response.code != 401) return response

        // Token likely expired/revoked — force one refresh and retry.
        response.close()
        val fresh = auth.refreshBlocking() ?: return chain.proceed(request)
        val retry = chain.request().newBuilder().header("Authorization", "Bearer $fresh").build()
        return chain.proceed(retry)
    }
}

/** All calls use absolute @Url so the host can follow the configured domain. */
interface PosApi {
    @GET
    suspend fun me(@Url url: String): CurrentUser

    @GET
    suspend fun currentWorkspace(@Url url: String): Workspace

    @GET
    suspend fun properties(@Url url: String): List<Property>

    @GET
    suspend fun locations(@Url url: String): List<PosLocation>

    @GET
    suspend fun tables(@Url url: String): List<PosTable>

    @GET
    suspend fun orderableMenuItems(@Url url: String): List<OrderableMenuItem>

    @GET
    suspend fun tickets(@Url url: String): TicketListResult

    @GET
    suspend fun ticket(@Url url: String): PosOrderTicket

    @POST
    suspend fun createTicket(@Url url: String, @Body body: CreateTicketBody): PosOrderTicket
}
