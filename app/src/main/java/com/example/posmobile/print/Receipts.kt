package com.example.posmobile.print

import com.example.posmobile.data.PosOrderTicket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds ESC/POS byte streams for the kitchen ticket and the customer bill. */
object Receipts {

    private fun money(cents: Int): String =
        String.format(Locale.US, "%,.2f", cents / 100.0)

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

    private fun orderTypeLabel(t: String): String = when (t) {
        "dine_in" -> "DINE IN"
        "takeaway" -> "TAKEAWAY"
        "room" -> "ROOM"
        else -> t.uppercase(Locale.US)
    }

    private fun target(ticket: PosOrderTicket): String? = when {
        ticket.tableName != null -> "Table: ${ticket.tableName}"
        ticket.roomNumber != null -> "Room: ${ticket.roomNumber}"
        else -> null
    }

    /** Kitchen Order Ticket — no prices, grouped by prep/kitchen location. */
    fun kitchenTicket(ticket: PosOrderTicket, cols: Int): ByteArray {
        val e = EscPos(cols)
        e.align(EscPos.Align.CENTER).bold(true).bigSize(true)
            .line("KOT").bigSize(false)
        e.line("Order #${ticket.orderNo}").bold(false)
        e.line(orderTypeLabel(ticket.orderType))
        e.align(EscPos.Align.LEFT).rule()
        target(ticket)?.let { e.line(it) }
        ticket.guestName?.let { e.line("Guest: $it") }
        e.line("Time: ${now()}")
        e.rule()

        val groups = ticket.items
            .filter { it.kitchenStatus != "cancelled" }
            .groupBy { it.prepLocationName ?: "General" }

        for ((kitchen, items) in groups) {
            e.bold(true).line("[$kitchen]").bold(false)
            for (it in items) {
                e.bold(true).line("${it.quantity} x ${it.itemName}").bold(false)
            }
            e.newline()
        }

        if (!ticket.remarks.isNullOrBlank()) {
            e.rule().bold(true).line("NOTES").bold(false).wrap(ticket.remarks)
        }
        e.cut()
        return e.bytes()
    }

    /** Customer bill — priced line items with a total. */
    fun customerReceipt(
        ticket: PosOrderTicket,
        cols: Int,
        propertyName: String,
        locationName: String,
        serviceChargePercent: Double = 0.0,
    ): ByteArray {
        val e = EscPos(cols)
        e.align(EscPos.Align.CENTER).bold(true).bigSize(true)
            .line(propertyName.ifBlank { "RECEIPT" }).bigSize(false).bold(false)
        if (locationName.isNotBlank()) e.line(locationName)
        e.line(now())
        e.align(EscPos.Align.LEFT).rule()
        e.cols("Order #${ticket.orderNo}", orderTypeLabel(ticket.orderType))
        target(ticket)?.let { e.line(it) }
        ticket.guestName?.let { e.line("Guest: $it") }
        e.rule()

        for (it in ticket.items.filter { it.kitchenStatus != "cancelled" }) {
            e.line(it.itemName)
            e.cols("  ${it.quantity} x ${money(it.unitPriceCents)}", money(it.amountCents))
        }

        e.rule()
        val subtotal = ticket.items
            .filter { it.kitchenStatus != "cancelled" }
            .sumOf { it.amountCents }
        
        val serviceChargeCents = if (ticket.orderType == "dine_in") {
            (subtotal * (serviceChargePercent / 100.0)).toInt()
        } else {
            0
        }
        val total = subtotal + serviceChargeCents

        if (serviceChargeCents > 0) {
            e.cols("SUBTOTAL", money(subtotal))
            e.cols("SERVICE CHARGE (${serviceChargePercent}%)", money(serviceChargeCents))
        }
        e.bold(true).cols("TOTAL", money(total)).bold(false)
        e.rule()

        if (!ticket.remarks.isNullOrBlank()) e.wrap("Note: ${ticket.remarks}")
        e.align(EscPos.Align.CENTER).feed(1).line("Thank you!")
        e.cut()
        return e.bytes()
    }
}
