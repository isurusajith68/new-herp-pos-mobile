package com.example.posmobile.data

import android.net.Uri

/** High-level POS data access. Builds absolute URLs from the current config. */
class PosRepository(
    private val settings: Settings,
    private val api: PosApi,
) {
    private val base get() = settings.apiBase

    suspend fun me(): CurrentUser =
        api.me("$base/users/me")

    suspend fun currentWorkspace(): Workspace =
        api.currentWorkspace("$base/tenants/current")

    suspend fun properties(): List<Property> =
        api.properties("$base/properties")

    suspend fun locations(propertySlug: String): List<PosLocation> =
        api.locations("$base/pos/$propertySlug/locations")

    suspend fun tables(propertySlug: String, locationId: String): List<PosTable> =
        api.tables("$base/pos/$propertySlug/tables/$locationId")

    suspend fun checkedInReservations(propertySlug: String): List<CheckedInReservation> =
        api.checkedInReservations("$base/pos/$propertySlug/checked-in-reservations")

    suspend fun menuItems(
        propertySlug: String,
        locationId: String,
        search: String?,
    ): List<OrderableMenuItem> {
        val url = Uri.parse("$base/pos/$propertySlug/orderable-menu-items").buildUpon()
            .appendQueryParameter("locationId", locationId)
            .apply { if (!search.isNullOrBlank()) appendQueryParameter("search", search) }
            .build().toString()
        return api.orderableMenuItems(url)
    }

    suspend fun recentTickets(
        propertySlug: String,
        locationId: String,
        status: String?,
        limit: Int = 40,
    ): TicketListResult {
        val url = Uri.parse("$base/pos/$propertySlug/tickets").buildUpon()
            .appendQueryParameter("locationId", locationId)
            .appendQueryParameter("limit", limit.toString())
            .apply { if (!status.isNullOrBlank()) appendQueryParameter("status", status) }
            .build().toString()
        return api.tickets(url)
    }

    suspend fun ticket(propertySlug: String, id: String): PosOrderTicket =
        api.ticket("$base/pos/$propertySlug/tickets/$id")

    suspend fun createTicket(propertySlug: String, body: CreateTicketBody): PosOrderTicket =
        api.createTicket("$base/pos/$propertySlug/tickets", body)

    suspend fun kitchenLocations(propertySlug: String, locationId: String): List<KitchenLocationSummary> =
        api.kitchenLocations("$base/pos/$propertySlug/kitchen-locations/$locationId")

    suspend fun kitchenOrders(propertySlug: String, prepLocationId: String): List<KitchenOrder> =
        api.kitchenOrders("$base/pos/$propertySlug/kitchen-orders/$prepLocationId")

    suspend fun setKitchenStatus(propertySlug: String, ticketId: String, prepLocationId: String, status: String) {
        api.setKitchenStatus(
            "$base/pos/$propertySlug/kitchen-orders/$ticketId/$prepLocationId/status",
            UpdateKitchenStatusBody(status)
        )
    }

    suspend fun cancelKitchenItems(propertySlug: String, ticketId: String, prepLocationId: String) {
        api.cancelKitchenItems(
            "$base/pos/$propertySlug/kitchen-orders/$ticketId/$prepLocationId/cancel",
            EmptyBody()
        )
    }

    suspend fun cancelKitchenItem(propertySlug: String, itemId: String) {
        api.cancelKitchenItem(
            "$base/pos/$propertySlug/ticket-items/$itemId/cancel",
            EmptyBody()
        )
    }
}
