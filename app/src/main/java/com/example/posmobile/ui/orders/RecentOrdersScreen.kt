package com.example.posmobile.ui.orders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.posmobile.data.Container
import com.example.posmobile.data.Settings
import com.example.posmobile.data.PosOrderTicket
import com.example.posmobile.data.TicketListResult
import com.example.posmobile.print.PrinterException
import com.example.posmobile.print.Receipts
import com.example.posmobile.ui.Outlet
import com.example.posmobile.ui.formatCents
import com.example.posmobile.ui.theme.BrandBlue
import com.example.posmobile.ui.theme.BrandBlueDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val STATUS_FILTERS = listOf(
    null to "All",
    "pending" to "Pending",
    "confirmed" to "Confirmed",
    "in_process" to "In process",
    "completed" to "Completed",
    "cancelled" to "Cancelled",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentOrdersScreen(
    outlet: Outlet,
    onBack: () -> Unit,
    onOpenPrinter: () -> Unit,
) {
    val pos = Container.pos
    val printer = Container.printer
    val settings = Container.settings
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var serviceChargePercent by remember { mutableStateOf(10.0) }

    var status by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<TicketListResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var selectedOrderId by remember { mutableStateOf<String?>(null) }
    var detailOpen by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<PosOrderTicket?>(null) }
    var reprinting by remember { mutableStateOf(false) }

    var tabletDetail by remember { mutableStateOf<PosOrderTicket?>(null) }
    var tabletDetailLoading by remember { mutableStateOf(false) }
    var tabletReprinting by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun ensureBtPermission(): Boolean {
        if (printer.hasConnectPermission()) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return false
    }

    fun load(silent: Boolean = false) {
        if (!silent) {
            error = null
            result = null
        }
        scope.launch {
            try {
                result = pos.recentTickets(outlet.propertySlug, outlet.locationId, status)
                if (selectedOrderId != null) {
                    tabletDetail = runCatching { pos.ticket(outlet.propertySlug, selectedOrderId!!) }.getOrNull()
                }
            } catch (e: Exception) {
                if (!silent) {
                    error = e.message ?: "Couldn't load orders"
                    result = TicketListResult()
                }
            }
        }
    }

    LaunchedEffect(status, reloadKey) {
        if (reloadKey == 0) {
            selectedOrderId = null
        }
        load(silent = reloadKey > 0)
        scope.launch {
            runCatching {
                val sc = pos.getServiceCharge(outlet.propertySlug)
                serviceChargePercent = sc.pct
            }
        }
        while (true) {
            delay(10_000)
            load(silent = true)
        }
    }

    LaunchedEffect(selectedOrderId) {
        if (selectedOrderId != null) {
            tabletDetailLoading = true
            tabletDetail = runCatching { pos.ticket(outlet.propertySlug, selectedOrderId!!) }.getOrNull()
            tabletDetailLoading = false
        } else {
            tabletDetail = null
        }
    }

    fun openDetail(id: String) {
        detailOpen = true
        detail = null
        scope.launch {
            detail = runCatching { pos.ticket(outlet.propertySlug, id) }.getOrNull()
            if (detail == null) {
                detailOpen = false
                snackbar.showMessage("Couldn't open that order")
            }
        }
    }

    fun reprint(ticket: PosOrderTicket, isTablet: Boolean = false) {
        scope.launch {
            if (isTablet) tabletReprinting = true else reprinting = true
            // Bluetooth needs runtime permission; WiFi (TCP) does not.
            if (settings.printerType != Settings.TYPE_WIFI && !ensureBtPermission()) {
                snackbar.showMessage("Allow Bluetooth, then try again")
                if (isTablet) tabletReprinting = false else reprinting = false
                return@launch
            }
            val hasReceiptPrinter = settings.isPrinterConfigured
            val hasKitchenPrinter = settings.kotEnabled && settings.isKitchenPrinterConfigured
            
            if (!hasReceiptPrinter && !hasKitchenPrinter) {
                snackbar.showMessage("No printers configured — check Printer settings")
                if (isTablet) tabletReprinting = false else reprinting = false
                return@launch
            }
            try {
                var printedKot = false
                var printedReceipt = false
                
                if (settings.kotEnabled && settings.isKitchenPrinterConfigured) {
                    printer.print(
                        bytes = Receipts.kitchenTicket(ticket, settings.kitchenPaperCols),
                        type = settings.kitchenPrinterType,
                        mac = settings.kitchenPrinterMac,
                        host = settings.kitchenPrinterHost,
                        port = settings.kitchenPrinterPort
                    )
                    printedKot = true
                }
                if (settings.isPrinterConfigured) {
                    val receiptBytes = Receipts.customerReceipt(ticket, settings.paperCols, outlet.propertyName, outlet.locationName, serviceChargePercent)
                    repeat(settings.receiptCopies) {
                        printer.print(bytes = receiptBytes)
                    }
                    printedReceipt = true
                }
                
                if (printedKot && printedReceipt) {
                    snackbar.showMessage("Reprinted KOT + Receipt")
                } else if (printedKot) {
                    snackbar.showMessage("Reprinted KOT")
                } else if (printedReceipt) {
                    snackbar.showMessage("Reprinted Receipt")
                }
            } catch (e: PrinterException) {
                snackbar.showMessage(e.message ?: "Print failed")
            } finally {
                if (isTablet) tabletReprinting = false else reprinting = false
            }
        }
    }

    val isTablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 720

    androidx.compose.material3.Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        if (isTablet) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = inner.calculateBottomPadding())
            ) {
                // Left pane: Orders list
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    // Hero
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                            .background(Brush.linearGradient(listOf(BrandBlue, BrandBlueDark)))
                            .statusBarsPadding()
                            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 18.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Recent orders", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text(
                                    outlet.locationName,
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = onOpenPrinter) {
                                Icon(Icons.Filled.Print, "Printer", tint = Color.White)
                            }
                            IconButton(onClick = { reloadKey++ }) {
                                Icon(Icons.Filled.Refresh, "Refresh", tint = Color.White)
                            }
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(STATUS_FILTERS) { (key, label) ->
                            FilterChip(
                                selected = status == key,
                                onClick = { status = key },
                                label = { Text(label) },
                            )
                        }
                    }

                    val currentResult = result
                    when {
                        currentResult == null ->
                            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                        currentResult.tickets.isEmpty() ->
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text(
                                    error ?: "No orders yet",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                        else -> LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(currentResult.tickets, key = { it.id }) { t ->
                                val isSelected = selectedOrderId == t.id
                                Surface(
                                    onClick = { selectedOrderId = t.id },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 1.dp,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier),
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Max),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .width(6.dp)
                                                    .fillMaxHeight()
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                        Row(
                                            Modifier
                                                .weight(1f)
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("#${t.orderNo}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                                    Spacer(Modifier.size(8.dp))
                                                    StatusChip(t.status)
                                                }
                                                Spacer(Modifier.size(3.dp))
                                                Text(
                                                    orderTarget(t),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                t.kitchenNames?.takeIf { it.isNotBlank() }?.let {
                                                    Text(
                                                        it,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                val scCents = if (t.orderType == "dine_in") (t.subtotalCents * (serviceChargePercent / 100.0)).toInt() else 0
                                                val totalCents = t.subtotalCents + scCents
                                                Text(formatCents(totalCents), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                Text(
                                                    listOfNotNull(t.orderDate, shortTime(t.createdAt)).joinToString(" · "),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Right pane: Order detail inline
                Surface(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    shadowElevation = 2.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(24.dp)
                    ) {
                        when {
                            selectedOrderId == null -> {
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Print,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "Select an order to view details",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                            tabletDetailLoading -> {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                            tabletDetail != null -> {
                                OrderDetailContent(
                                    ticket = tabletDetail,
                                    onReprint = { tabletDetail?.let { reprint(it, isTablet = true) } },
                                    reprinting = tabletReprinting,
                                    isInline = true,
                                    serviceChargePercent = serviceChargePercent,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            else -> {
                                Text(
                                    "Failed to load order details",
                                    modifier = Modifier.align(Alignment.Center),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = inner.calculateBottomPadding()),
            ) {
                // Hero
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                        .background(Brush.linearGradient(listOf(BrandBlue, BrandBlueDark)))
                        .statusBarsPadding()
                        .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Recent orders", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(
                                outlet.locationName,
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = onOpenPrinter) {
                            Icon(Icons.Filled.Print, "Printer", tint = Color.White)
                        }
                        IconButton(onClick = { reloadKey++ }) {
                            Icon(Icons.Filled.Refresh, "Refresh", tint = Color.White)
                        }
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(STATUS_FILTERS) { (key, label) ->
                        FilterChip(
                            selected = status == key,
                            onClick = { status = key },
                            label = { Text(label) },
                        )
                    }
                }

                val currentResult = result
                when {
                    currentResult == null ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                    currentResult.tickets.isEmpty() ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(
                                error ?: "No orders yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(currentResult.tickets, key = { it.id }) { t ->
                            OrderRowCard(t, serviceChargePercent) { openDetail(t.id) }
                        }
                    }
                }
            }
        }
    }

    if (detailOpen) {
        OrderDetailSheet(
            ticket = detail,
            onDismiss = { detailOpen = false },
            onReprint = { detail?.let { reprint(it, isTablet = false) } },
            reprinting = reprinting,
            serviceChargePercent = serviceChargePercent,
        )
    }
}

@Composable
fun OrderDetailContent(
    ticket: PosOrderTicket?,
    onReprint: () -> Unit,
    reprinting: Boolean = false,
    isInline: Boolean,
    serviceChargePercent: Double = 0.0,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (ticket == null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (isInline) Modifier.weight(1f) else Modifier.height(160.dp)),
                Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }

        // Header info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Order #${ticket.orderNo}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = orderTarget(ticket),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = listOfNotNull(ticket.orderDate, shortTime(ticket.createdAt)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusChip(ticket.status)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()

        // List of items
        LazyColumn(
            modifier = if (isInline) {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            } else {
                Modifier.heightIn(max = 300.dp)
            }
        ) {
            items(ticket.items) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.itemName,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            listOfNotNull(
                                item.prepLocationName,
                                "${item.quantity} × ${formatCents(item.amountCents / maxOf(item.quantity, 1))}"
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatCents(item.amountCents),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        HorizontalDivider()
        Spacer(Modifier.height(10.dp))

        // Calculations
        val scCents = if (ticket.orderType == "dine_in") {
            (ticket.subtotalCents * (serviceChargePercent / 100.0)).toInt()
        } else {
            0
        }
        val totalCents = ticket.subtotalCents + scCents

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Subtotal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(formatCents(ticket.subtotalCents), style = MaterialTheme.typography.bodyMedium)
        }
        if (scCents > 0) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Service Charge (${serviceChargePercent}%)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(formatCents(scCents), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatCents(totalCents),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Remarks note
        ticket.remarks?.takeIf { it.isNotBlank() }?.let { remarks ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Note: $remarks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        // Reprint button
        Button(
            onClick = onReprint,
            enabled = !reprinting,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (reprinting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(8.dp))
                Text(text = "Printing…", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Filled.Print, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(text = "Reprint KOT + receipt", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderDetailSheet(
    ticket: PosOrderTicket?,
    onDismiss: () -> Unit,
    onReprint: () -> Unit,
    reprinting: Boolean = false,
    serviceChargePercent: Double = 0.0,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        OrderDetailContent(
            ticket = ticket,
            onReprint = onReprint,
            reprinting = reprinting,
            isInline = false,
            serviceChargePercent = serviceChargePercent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun OrderRowCard(t: PosOrderTicket, serviceChargePercent: Double, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${t.orderNo}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(8.dp))
                    StatusChip(t.status)
                }
                Spacer(Modifier.size(3.dp))
                Text(
                    orderTarget(t),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                t.kitchenNames?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                val scCents = if (t.orderType == "dine_in") (t.subtotalCents * (serviceChargePercent / 100.0)).toInt() else 0
                val totalCents = t.subtotalCents + scCents
                Text(formatCents(totalCents), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    listOfNotNull(t.orderDate, shortTime(t.createdAt)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val color = when (status) {
        "pending" -> Color(0xFFF59E0B)
        "confirmed" -> BrandBlue
        "in_process" -> Color(0xFF7C3AED)
        "completed" -> Color(0xFF16A34A)
        "cancelled" -> Color(0xFFDC2626)
        else -> Color(0xFF64748B)
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(
            status.replace('_', ' ').replaceFirstChar { it.uppercase() },
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun orderTarget(t: PosOrderTicket): String {
    val type = when (t.orderType) {
        "dine_in" -> "Dine in"
        "takeaway" -> "Takeaway"
        "room" -> "Room"
        else -> t.orderType
    }
    val where = when {
        t.tableName != null -> "· Table ${t.tableName}"
        t.roomNumber != null -> "· Room ${t.roomNumber}"
        else -> ""
    }
    val guest = t.guestName?.let { "· $it" } ?: ""
    return listOf(type, where, guest).filter { it.isNotBlank() }.joinToString(" ")
}

// "2026-07-06T14:32:10.123Z" -> "14:32" (server-local, best-effort, no java.time).
private fun shortTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val t = iso.substringAfter('T', "")
    return if (t.length >= 5) t.substring(0, 5) else ""
}

private suspend fun SnackbarHostState.showMessage(msg: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(msg)
}
