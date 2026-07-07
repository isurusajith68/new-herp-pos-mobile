package com.example.posmobile.ui.kitchen

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.posmobile.data.*
import com.example.posmobile.ui.Outlet
import com.example.posmobile.ui.orders.OrderDetailContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val COLUMNS = listOf(
    KStatusCol("pending", "New", Color(0xFFF59E0B), "confirmed", "Confirm order"),
    KStatusCol("confirmed", "Confirmed", Color(0xFF3B82F6), "in_process", "Start cooking"),
    KStatusCol("in_process", "Cooking", Color(0xFFF97316), "completed", "Mark ready"),
    KStatusCol("completed", "Completed", Color(0xFF10B981), null, null),
    KStatusCol("cancelled", "Cancelled", Color(0xFFEF4444), null, null)
)

private data class KStatusCol(
    val key: String,
    val label: String,
    val color: Color,
    val nextStatus: String?,
    val nextLabel: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenBoardScreen(
    outlet: Outlet,
    prepLocationId: String,
    prepLocationName: String,
    onBack: () -> Unit,
) {
    val repo = remember { Container.pos }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var orders by remember { mutableStateOf<List<KitchenOrder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    // Dialog state for full order detail
    var selectedFullTicketId by remember { mutableStateOf<String?>(null) }
    var fullTicketDetails by remember { mutableStateOf<PosOrderTicket?>(null) }
    var loadingFullTicket by remember { mutableStateOf(false) }

    fun load(silent: Boolean = false) {
        scope.launch {
            if (!silent) loading = true else refreshing = true
            try {
                orders = repo.kitchenOrders(outlet.propertySlug, prepLocationId)
            } catch (e: Exception) {
                if (!silent) snackbar.showSnackbar(e.message ?: "Failed to load orders")
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    // Auto-refresh every 10 seconds
    LaunchedEffect(reloadKey) {
        load(silent = reloadKey > 0)
        while (true) {
            delay(10_000)
            load(silent = true)
        }
    }

    fun advance(ticketId: String, next: String) {
        scope.launch {
            try {
                repo.setKitchenStatus(outlet.propertySlug, ticketId, prepLocationId, next)
                reloadKey++
                snackbar.showSnackbar("Order status updated")
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "Action failed")
            }
        }
    }

    fun cancelKitchenItems(order: KitchenOrder) {
        scope.launch {
            try {
                repo.cancelKitchenItems(outlet.propertySlug, order.ticketId, prepLocationId)
                reloadKey++
                snackbar.showSnackbar("Kitchen items cancelled")
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "Cancel failed")
            }
        }
    }

    fun cancelItem(itemId: String) {
        scope.launch {
            try {
                repo.cancelKitchenItem(outlet.propertySlug, itemId)
                reloadKey++
                snackbar.showSnackbar("Item cancelled")
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "Cancel failed")
            }
        }
    }

    fun viewFullOrder(ticketId: String) {
        selectedFullTicketId = ticketId
        loadingFullTicket = true
        fullTicketDetails = null
        scope.launch {
            try {
                fullTicketDetails = repo.ticket(outlet.propertySlug, ticketId)
            } catch (e: Exception) {
                snackbar.showSnackbar("Failed to fetch full order details")
                selectedFullTicketId = null
            } finally {
                loadingFullTicket = false
            }
        }
    }

    val isTablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 720
    var activeTab by remember { mutableIntStateOf(0) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(prepLocationName, fontWeight = FontWeight.Bold)
                        Text(outlet.locationName, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { reloadKey++ }, enabled = !loading && !refreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                if (isTablet) {
                    // Kanban horizontal flow
                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(COLUMNS) { col ->
                            val colOrders = orders.filter {
                                if (col.key == "cancelled") it.status == "cancelled"
                                else it.status != "cancelled" && it.kitchenStatus == col.key
                            }
                            Column(
                                modifier = Modifier
                                    .width(280.dp)
                                    .fillMaxHeight()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(10.dp).background(col.color, RoundedCornerShape(50))
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(col.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text(colOrders.size.toString(), fontWeight = FontWeight.Bold)
                                    }
                                }
                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(colOrders) { order ->
                                        OrderTicketCard(
                                            order = order,
                                            col = col,
                                            onAdvance = { advance(order.ticketId, col.nextStatus!!) },
                                            onCancelAll = { cancelKitchenItems(order) },
                                            onCancelItem = { itemId -> cancelItem(itemId) },
                                            onViewFull = { viewFullOrder(order.ticketId) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Mobile view: Tab bar + single column list
                    Column(Modifier.fillMaxSize()) {
                        ScrollableTabRow(
                            selectedTabIndex = activeTab,
                            edgePadding = 16.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            COLUMNS.forEachIndexed { idx, col ->
                                val colOrdersCount = orders.filter {
                                    if (col.key == "cancelled") it.status == "cancelled"
                                    else it.status != "cancelled" && it.kitchenStatus == col.key
                                }.size
                                Tab(
                                    selected = activeTab == idx,
                                    onClick = { activeTab = idx },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(8.dp).background(col.color, RoundedCornerShape(50)))
                                            Spacer(Modifier.width(6.dp))
                                            Text(col.label, fontWeight = FontWeight.Bold)
                                            if (colOrdersCount > 0) {
                                                Spacer(Modifier.width(4.dp))
                                                Text("($colOrdersCount)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        
                        val activeCol = COLUMNS[activeTab]
                        val colOrders = orders.filter {
                            if (activeCol.key == "cancelled") it.status == "cancelled"
                            else it.status != "cancelled" && it.kitchenStatus == activeCol.key
                        }
                        
                        if (colOrders.isEmpty()) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text("No orders in this column", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(colOrders) { order ->
                                    OrderTicketCard(
                                        order = order,
                                        col = activeCol,
                                        onAdvance = { advance(order.ticketId, activeCol.nextStatus!!) },
                                        onCancelAll = { cancelKitchenItems(order) },
                                        onCancelItem = { itemId -> cancelItem(itemId) },
                                        onViewFull = { viewFullOrder(order.ticketId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Full order details dialog
    if (selectedFullTicketId != null) {
        AlertDialog(
            onDismissRequest = { selectedFullTicketId = null },
            title = { Text("Full Order Details") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (loadingFullTicket) {
                        CircularProgressIndicator()
                    } else {
                        OrderDetailContent(
                            ticket = fullTicketDetails,
                            onReprint = {},
                            isInline = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedFullTicketId = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun OrderTicketCard(
    order: KitchenOrder,
    col: KStatusCol,
    onAdvance: () -> Unit,
    onCancelAll: () -> Unit,
    onCancelItem: (String) -> Unit,
    onViewFull: () -> Unit
) {
    val isCompleted = col.key == "completed"
    val isCancelled = col.key == "cancelled"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${order.orderNo}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = col.color.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = col.label,
                        color = col.color,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = order.orderDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Guest & Table info
            Row(Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = order.guestName ?: "Walk-in",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = order.tableName ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            // Items List
            Text(
                text = "ITEMS (${order.items.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            order.items.forEach { item ->
                val itemCancelled = item.kitchenStatus == "cancelled"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (itemCancelled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(width = 30.dp, height = 20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${item.quantity}x",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (itemCancelled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.itemName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (itemCancelled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (itemCancelled) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f)
                    )
                    if (itemCancelled) {
                        Text(
                            text = "Cancelled",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (col.key == "pending") {
                        IconButton(
                            onClick = { onCancelItem(item.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Cancel item",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (col.nextLabel != null && col.nextStatus != null) {
                    Button(
                        onClick = onAdvance,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (col.key) {
                                "pending" -> Color(0xFF3B82F6)
                                "confirmed" -> Color(0xFFF97316)
                                "in_process" -> Color(0xFF10B981)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        ),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Text(col.nextLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                if (isCompleted) {
                    Text(
                        text = "✓ Ready",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        fontSize = 13.sp
                    )
                }
                if (isCancelled) {
                    Text(
                        text = "✗ Order cancelled",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        fontSize = 13.sp
                    )
                }
                if (col.key == "pending") {
                    OutlinedButton(
                        onClick = onCancelAll,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Text("Cancel My Items", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = onViewFull,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Text("View Full Order", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
