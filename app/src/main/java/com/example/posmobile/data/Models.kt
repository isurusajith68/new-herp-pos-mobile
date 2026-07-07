package com.example.posmobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Auth ──────────────────────────────────────────────────────────────────

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long = 900,
    val scope: String = "",
)

@Serializable
data class LoginBody(val email: String, val password: String)

@Serializable
data class LoginResponse(val redirect: String? = null, val error: String? = null)

@Serializable
data class TokenBody(
    @SerialName("grant_type") val grantType: String,
    @SerialName("client_id") val clientId: String,
    val code: String? = null,
    @SerialName("code_verifier") val codeVerifier: String? = null,
    @SerialName("redirect_uri") val redirectUri: String? = null,
)

@Serializable
data class CurrentUser(
    val id: String,
    val fullName: String = "",
    val email: String = "",
    val status: String = "",
)

@Serializable
data class Workspace(
    val slug: String = "",
    val name: String = "",
)

// ── Properties & outlets ────────────────────────────────────────────────────

@Serializable
data class Property(
    val id: String,
    val slug: String,
    val name: String,
    val modules: List<String> = emptyList(),
) {
    val hasPos: Boolean get() = modules.contains("pos")
}

@Serializable
data class PosLocation(
    val id: String,
    val name: String,
    val description: String? = null,
)

@Serializable
data class PosTable(
    val id: String,
    val name: String,
    val seats: Int = 0,
)

// ── Ordering catalog ────────────────────────────────────────────────────────

@Serializable
data class PrepLocation(val id: String, val name: String)

@Serializable
data class MenuItemPriceOption(
    val id: String,
    val menuTypeId: String,
    val menuTypeName: String,
    val priceTierId: String,
    val priceTierName: String,
    val priceTypeId: String,
    val priceTypeName: String,
    val priceCents: Int,
)

@Serializable
data class OrderableMenuItem(
    val id: String,
    val name: String,
    val description: String? = null,
    val categoryName: String? = null,
    val subcategoryName: String? = null,
    val prices: List<MenuItemPriceOption> = emptyList(),
    val prepLocations: List<PrepLocation> = emptyList(),
    val isInventory: Boolean = false,
)

@Serializable
data class OrderableInvItem(
    val id: String,
    val name: String,
    val code: String? = null,
)

// ── Ticket create / response ────────────────────────────────────────────────

@Serializable
data class TicketItemInput(
    val menuItemId: String? = null,
    val invItemId: String? = null,
    val itemName: String,
    val itemType: String = "food",
    val quantity: Int,
    val unitPriceCents: Int,
    val prepLocationId: String? = null,
    val menuTypeId: String? = null,
    val priceTierId: String? = null,
    val priceTypeId: String? = null,
)

@Serializable
data class CreateTicketBody(
    val locationId: String,
    val orderType: String = "dine_in",
    val tableId: String? = null,
    val reservationId: String? = null,
    val roomId: String? = null,
    val remarks: String? = null,
    val items: List<TicketItemInput>,
)

@Serializable
data class TicketItem(
    val id: String,
    val itemName: String,
    val itemType: String = "food",
    val quantity: Int,
    val unitPriceCents: Int,
    val amountCents: Int,
    val prepLocationId: String? = null,
    val prepLocationName: String? = null,
    val kitchenStatus: String = "pending",
)

@Serializable
data class PosOrderTicket(
    val id: String,
    val orderNo: Int,
    val orderDate: String,
    val status: String,
    val orderType: String,
    val tableId: String? = null,
    val tableName: String? = null,
    val roomNumber: String? = null,
    val guestName: String? = null,
    val remarks: String? = null,
    val subtotalCents: Int = 0,
    val kitchenNames: String? = null,
    val invoiceId: String? = null,
    val invoicePaid: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val items: List<TicketItem> = emptyList(),
)

@Serializable
data class TicketStatusCounts(
    val pending: Int = 0,
    val confirmed: Int = 0,
    val inProcess: Int = 0,
    val completed: Int = 0,
    val cancelled: Int = 0,
)

@Serializable
data class TicketListResult(
    val tickets: List<PosOrderTicket> = emptyList(),
    val total: Int = 0,
    val statusCounts: TicketStatusCounts = TicketStatusCounts(),
)

@Serializable
data class KitchenLocationSummary(
    val id: String,
    val name: String,
    val newCount: Int = 0,
    val confirmedCount: Int = 0,
    val inProcessCount: Int = 0,
    val completedCount: Int = 0,
)

@Serializable
data class KitchenOrderItem(
    val id: String,
    val itemName: String,
    val itemType: String = "food",
    val quantity: Int,
    val kitchenStatus: String = "pending",
)

@Serializable
data class KitchenOrder(
    val ticketId: String,
    val orderNo: Int,
    val orderDate: String,
    val status: String,
    val orderType: String,
    val kitchenStatus: String,
    val tableName: String? = null,
    val guestName: String? = null,
    val items: List<KitchenOrderItem> = emptyList(),
)

@Serializable
data class UpdateKitchenStatusBody(val kitchenStatus: String)

@Serializable
data class GenericOkResponse(val ok: Boolean = true)

@Serializable
class EmptyBody

@Serializable
data class CheckedInReservation(
    val reservationId: String,
    val roomId: String,
    val roomNumber: String,
    val guestName: String,
    val checkIn: String? = null,
    val checkOut: String? = null,
)

@Serializable
data class ServiceChargeSetting(
    val pct: Double,
)

@Serializable
data class UpdateServiceChargeBody(
    val pct: Double,
)

@Serializable
data class UpdateServiceChargeResponse(
    val ok: Boolean,
    val pct: Double,
)
