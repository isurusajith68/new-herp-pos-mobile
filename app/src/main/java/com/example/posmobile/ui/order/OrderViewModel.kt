package com.example.posmobile.ui.order

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posmobile.data.CheckedInReservation
import com.example.posmobile.data.Container
import com.example.posmobile.data.CreateTicketBody
import com.example.posmobile.data.OrderableMenuItem
import com.example.posmobile.data.PosOrderTicket
import com.example.posmobile.data.PosTable
import com.example.posmobile.data.TicketItemInput
import com.example.posmobile.ui.Outlet
import kotlinx.coroutines.launch

/** A staged line in the current order. */
data class CartLine(
    val key: Long,
    val menuItemId: String?,
    val itemName: String,
    val itemType: String,
    val quantity: Int,
    val unitPriceCents: Int,
    val prepLocationId: String?,
    val prepLocationName: String?,
    val menuTypeId: String?,
    val priceTierId: String?,
    val priceTypeId: String?,
)

class OrderViewModel : ViewModel() {
    private val pos = Container.pos

    lateinit var outlet: Outlet
        private set

    var menuItems by mutableStateOf<List<OrderableMenuItem>?>(null)
        private set
    var tables by mutableStateOf<List<PosTable>>(emptyList())
        private set
    var reservations by mutableStateOf<List<CheckedInReservation>>(emptyList())
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    var search by mutableStateOf("")
        private set
    var activeCategory by mutableStateOf(ALL)
        private set

    var orderType by mutableStateOf("takeaway")
        private set
    var tableId by mutableStateOf("")
        private set
    var reservationId by mutableStateOf("")
        private set
    var roomId by mutableStateOf("")
        private set
    var remarks by mutableStateOf("")
        private set

    val cart = mutableStateListOf<CartLine>()
    private var keySeq = 0L

    var placing by mutableStateOf(false)

    private var bound = false

    fun bind(outlet: Outlet) {
        if (bound) return
        this.outlet = outlet
        bound = true
        loadMenu()
        loadTables()
        loadReservations()
    }

    fun updateSearch(value: String) { search = value }
    fun selectCategory(cat: String) { activeCategory = cat }
    fun selectOrderType(type: String) { orderType = type }
    fun selectTable(id: String) { tableId = id }
    fun selectReservation(resId: String, room: String) {
        reservationId = resId
        roomId = room
    }
    fun updateRemarks(value: String) { remarks = value }

    val categories: List<String>
        get() = menuItems.orEmpty()
            .mapNotNull { it.categoryName }
            .distinct()

    fun visibleMenu(): List<OrderableMenuItem> {
        val items = menuItems.orEmpty()
        return when (activeCategory) {
            ALL -> items
            else -> items.filter { it.categoryName == activeCategory }
        }
    }

    fun loadMenu() {
        loadError = null
        menuItems = null
        viewModelScope.launch {
            try {
                menuItems = pos.menuItems(outlet.propertySlug, outlet.locationId, search.ifBlank { null })
            } catch (e: Exception) {
                loadError = e.message ?: "Could not load menu"
                menuItems = emptyList()
            }
        }
    }

    private fun loadTables() {
        viewModelScope.launch {
            tables = try {
                pos.tables(outlet.propertySlug, outlet.locationId)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun loadReservations() {
        viewModelScope.launch {
            reservations = try {
                pos.checkedInReservations(outlet.propertySlug)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun addToCart(
        menuItemId: String?,
        itemName: String,
        itemType: String,
        quantity: Int,
        unitPriceCents: Int,
        prepLocationId: String?,
        prepLocationName: String?,
        menuTypeId: String?,
        priceTierId: String?,
        priceTypeId: String?,
    ) {
        // Merge identical lines (same item, price option, prep location, price).
        val idx = cart.indexOfFirst {
            it.menuItemId == menuItemId &&
                it.menuTypeId == menuTypeId &&
                it.priceTierId == priceTierId &&
                it.priceTypeId == priceTypeId &&
                it.prepLocationId == prepLocationId &&
                it.unitPriceCents == unitPriceCents
        }
        if (menuItemId != null && idx >= 0) {
            val existing = cart[idx]
            cart[idx] = existing.copy(quantity = existing.quantity + quantity)
            return
        }
        cart.add(
            CartLine(
                key = keySeq++,
                menuItemId = menuItemId,
                itemName = itemName,
                itemType = itemType,
                quantity = quantity,
                unitPriceCents = unitPriceCents,
                prepLocationId = prepLocationId,
                prepLocationName = prepLocationName,
                menuTypeId = menuTypeId,
                priceTierId = priceTierId,
                priceTypeId = priceTypeId,
            ),
        )
    }

    fun changeQty(key: Long, delta: Int) {
        val idx = cart.indexOfFirst { it.key == key }
        if (idx < 0) return
        val updated = cart[idx].quantity + delta
        if (updated <= 0) cart.removeAt(idx)
        else cart[idx] = cart[idx].copy(quantity = updated)
    }

    fun removeLine(key: Long) { cart.removeAll { it.key == key } }
    fun clearCart() { cart.clear() }

    val subtotalCents: Int get() = cart.sumOf { it.quantity * it.unitPriceCents }
    val totalQty: Int get() = cart.sumOf { it.quantity }

    /** Places the order. Returns the created ticket, or throws with a message. */
    suspend fun placeOrder(): PosOrderTicket {
        if (cart.isEmpty()) throw IllegalStateException("Cart is empty")
        val effectiveType = orderType
        if (effectiveType == "dine_in" && tableId.isBlank()) {
            throw IllegalStateException("Select a table for dine-in orders")
        }
        val body = CreateTicketBody(
            locationId = outlet.locationId,
            orderType = effectiveType,
            tableId = if (effectiveType == "dine_in") tableId else null,
            reservationId = reservationId.ifBlank { null },
            roomId = roomId.ifBlank { null },
            remarks = remarks.trim().ifBlank { null },
            items = cart.map {
                TicketItemInput(
                    menuItemId = it.menuItemId,
                    itemName = it.itemName,
                    itemType = it.itemType,
                    quantity = it.quantity,
                    unitPriceCents = it.unitPriceCents,
                    prepLocationId = it.prepLocationId,
                    menuTypeId = it.menuTypeId,
                    priceTierId = it.priceTierId,
                    priceTypeId = it.priceTypeId,
                )
            },
        )
        return pos.createTicket(outlet.propertySlug, body)
    }

    fun resetAfterOrder() {
        cart.clear()
        remarks = ""
        tableId = ""
        reservationId = ""
        roomId = ""
    }

    companion object {
        const val ALL = "__ALL__"
    }
}
