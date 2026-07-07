package com.example.posmobile.ui.order

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.posmobile.data.Container
import com.example.posmobile.data.Settings
import com.example.posmobile.data.OrderableMenuItem
import com.example.posmobile.print.PrinterException
import com.example.posmobile.print.Receipts
import com.example.posmobile.ui.Outlet
import com.example.posmobile.ui.formatCents
import com.example.posmobile.ui.theme.BrandBlue
import com.example.posmobile.ui.theme.BrandBlueDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Soft category tiles — warmth + fast recognition on a busy service screen.
private val TILE_COLORS = listOf(
    Color(0xFFFFE9D6), Color(0xFFD8F3E4), Color(0xFFDCEBFF), Color(0xFFEBE4FE),
    Color(0xFFFCE1EE), Color(0xFFCFF4EF), Color(0xFFFDF3C7), Color(0xFFE2E7FF),
    Color(0xFFFFE1E4), Color(0xFFD5F3F8),
)

private fun catEmoji(name: String?): String {
    val n = (name ?: "").lowercase()
    return when {
        "appetiz" in n || "starter" in n -> "🥗"
        "main" in n || "course" in n -> "🍽️"
        "pizza" in n -> "🍕"
        "burger" in n -> "🍔"
        "pasta" in n || "noodle" in n -> "🍝"
        "dessert" in n || "sweet" in n || "cake" in n -> "🍰"
        "beverage" in n || "drink" in n || "juice" in n || "coffee" in n -> "☕"
        "soup" in n -> "🍲"
        "salad" in n -> "🥙"
        "seafood" in n || "fish" in n || "prawn" in n -> "🦐"
        "chicken" in n -> "🍗"
        "beef" in n || "steak" in n || "lamb" in n -> "🥩"
        "bread" in n || "bakery" in n || "sandwich" in n -> "🥖"
        "rice" in n -> "🍚"
        "ice" in n || "gelato" in n -> "🍦"
        "fruit" in n -> "🍓"
        else -> "🍴"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    outlet: Outlet,
    onChangeOutlet: () -> Unit,
    onOpenPrinter: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenKitchens: () -> Unit,
    vm: OrderViewModel = viewModel(),
) {
    LaunchedEffect(outlet.locationId) { vm.bind(outlet) }

    val scope = rememberCoroutineScope()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    var cartOpen by remember { mutableStateOf(false) }
    var addItem by remember { mutableStateOf<OrderableMenuItem?>(null) }

    LaunchedEffect(vm.search) {
        delay(300)
        if (vm.menuItems != null || vm.search.isNotBlank()) vm.loadMenu()
    }

    val printer = Container.printer
    val settings = Container.settings

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* rechecked at print time */ }

    fun ensureBtPermission(): Boolean {
        if (printer.hasConnectPermission()) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return false
    }

    fun placeAndPrint() {
        scope.launch {
            vm.placing = true
            val ticket = try {
                vm.placeOrder()
            } catch (e: Exception) {
                snackbar.showMessage(e.message ?: "Couldn't place the order")
                vm.placing = false
                return@launch
            }
            snackbar.showMessage("Order #${ticket.orderNo} placed")

            // Bluetooth needs runtime permission; WiFi (TCP) does not.
            if (settings.printerType != Settings.TYPE_WIFI && !ensureBtPermission()) {
                snackbar.showMessage("Allow Bluetooth, then reprint from settings")
                cartOpen = false
                vm.resetAfterOrder()
                vm.placing = false
                return@launch
            }
            val hasReceiptPrinter = settings.isPrinterConfigured
            val hasKitchenPrinter = settings.kotEnabled && settings.isKitchenPrinterConfigured
            
            if (!hasReceiptPrinter && !hasKitchenPrinter) {
                snackbar.showMessage("No printers configured — check Printer settings")
                cartOpen = false
                vm.resetAfterOrder()
                vm.placing = false
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
                    printer.print(
                        bytes = Receipts.customerReceipt(ticket, settings.paperCols, outlet.propertyName, outlet.locationName)
                    )
                    printedReceipt = true
                }
                
                if (printedKot && printedReceipt) {
                    snackbar.showMessage("Printed KOT + Receipt")
                } else if (printedKot) {
                    snackbar.showMessage("Printed KOT")
                } else if (printedReceipt) {
                    snackbar.showMessage("Printed Receipt")
                }
            } catch (e: PrinterException) {
                snackbar.showMessage(e.message ?: "Print failed")
            } finally {
                cartOpen = false
                vm.resetAfterOrder()
                vm.placing = false
            }
        }
    }

    val isTablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 720

    androidx.compose.material3.Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        bottomBar = {
            if (!isTablet && vm.cart.isNotEmpty()) {
                OrderBar(
                    qty = vm.totalQty,
                    total = formatCents(vm.subtotalCents),
                    onClick = { cartOpen = true },
                )
            }
        },
    ) { inner ->
        if (isTablet) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = inner.calculateBottomPadding())
            ) {
                // Left pane: Menu grid
                Column(
                    Modifier
                        .weight(2f)
                        .fillMaxSize()
                ) {
                    OrderHero(
                        outlet = outlet,
                        search = vm.search,
                        onSearch = vm::updateSearch,
                        onChangeOutlet = onChangeOutlet,
                        onOpenOrders = onOpenOrders,
                        onOpenPrinter = onOpenPrinter,
                        onOpenProfile = onOpenProfile,
                        onOpenKitchens = onOpenKitchens,
                    )

                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        CategorySidebar(
                            categories = vm.categories,
                            activeCategory = vm.activeCategory,
                            onCategorySelected = { vm.selectCategory(it) }
                        )
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        when {
                            vm.menuItems == null ->
                                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                            vm.visibleMenu().isEmpty() ->
                                Box(Modifier.fillMaxSize(), Alignment.Center) {
                                    Text(
                                        vm.loadError ?: "No items match your search",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                            else -> LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 158.dp),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(vm.visibleMenu(), key = { it.id }) { item ->
                                    MenuCard(item) { addItem = item }
                                }
                            }
                        }
                    }
                }

                // Right pane: Cart
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        CartContent(
                            vm = vm,
                            onPlace = { placeAndPrint() },
                            isInline = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = inner.calculateBottomPadding()),
            ) {
                OrderHero(
                    outlet = outlet,
                    search = vm.search,
                    onSearch = vm::updateSearch,
                    onChangeOutlet = onChangeOutlet,
                    onOpenOrders = onOpenOrders,
                    onOpenPrinter = onOpenPrinter,
                    onOpenProfile = onOpenProfile,
                    onOpenKitchens = onOpenKitchens,
                )

                val categories = vm.categories
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        CategoryPill("🍴", "All", vm.activeCategory == OrderViewModel.ALL) {
                            vm.selectCategory(OrderViewModel.ALL)
                        }
                    }
                    items(categories) { cat ->
                        CategoryPill(catEmoji(cat), cat, vm.activeCategory == cat) {
                            vm.selectCategory(cat)
                        }
                    }
                }

                when {
                    vm.menuItems == null ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                    vm.visibleMenu().isEmpty() ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(
                                vm.loadError ?: "No items match your search",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 158.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(vm.visibleMenu(), key = { it.id }) { item ->
                            MenuCard(item) { addItem = item }
                        }
                    }
                }
            }
        }
    }

    addItem?.let { item ->
        AddItemDialog(
            item = item,
            onDismiss = { addItem = null },
            onAdd = { qty, cents, itemType, prep, price ->
                vm.addToCart(
                    menuItemId = item.id,
                    itemName = item.name,
                    itemType = itemType,
                    quantity = qty,
                    unitPriceCents = cents,
                    prepLocationId = prep?.id,
                    prepLocationName = prep?.name,
                    menuTypeId = price?.menuTypeId,
                    priceTierId = price?.priceTierId,
                    priceTypeId = price?.priceTypeId,
                )
                addItem = null
            },
        )
    }

    if (cartOpen) {
        CartSheet(vm = vm, onDismiss = { cartOpen = false }, onPlace = { placeAndPrint() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderHero(
    outlet: Outlet,
    search: String,
    onSearch: (String) -> Unit,
    onChangeOutlet: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenPrinter: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenKitchens: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
            .background(Brush.linearGradient(listOf(BrandBlue, BrandBlueDark)))
            .statusBarsPadding()
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    outlet.locationName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    outlet.propertyName,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onChangeOutlet) {
                Icon(Icons.Filled.Storefront, "Change outlet", tint = Color.White)
            }
            IconButton(onClick = onOpenOrders) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, "Recent orders", tint = Color.White)
            }
            IconButton(onClick = onOpenKitchens) {
                Icon(Icons.Filled.SoupKitchen, "Kitchens", tint = Color.White)
            }
            IconButton(onClick = onOpenPrinter) {
                Icon(Icons.Filled.Print, "Printer", tint = Color.White)
            }
            IconButton(onClick = onOpenProfile) {
                Icon(Icons.Filled.AccountCircle, "Profile", tint = Color.White)
            }
        }

        Spacer(Modifier.height(10.dp))
        TextField(
            value = search,
            onValueChange = onSearch,
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search the menu") },
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CategoryPill(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(emoji, fontSize = 15.sp)
            Text(label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MenuCard(item: OrderableMenuItem, onClick: () -> Unit) {
    val tile = if (item.categoryName != null) {
        TILE_COLORS[(item.categoryName.hashCode() and 0x7fffffff) % TILE_COLORS.size]
    } else {
        TILE_COLORS[0]
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(tile),
                contentAlignment = Alignment.Center,
            ) {
                Text(catEmoji(item.categoryName), fontSize = 30.sp)
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.categoryName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                val label = if (item.prices.isNotEmpty()) {
                    formatCents(item.prices.minOf { it.priceCents }) + if (item.prices.size > 1) "+" else ""
                } else "Custom price"
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun OrderBar(qty: Int, total: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp)
            .heightIn(min = 56.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("$qty", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.size(12.dp))
            Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("View order", fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(total, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.size(4.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

private suspend fun androidx.compose.material3.SnackbarHostState.showMessage(msg: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(msg)
}

@Composable
private fun CategorySidebar(
    categories: List<String>,
    activeCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(96.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategorySidebarTile(
            emoji = "🍴",
            label = "All Items",
            isSelected = activeCategory == OrderViewModel.ALL,
            onClick = { onCategorySelected(OrderViewModel.ALL) }
        )
        categories.forEach { cat ->
            CategorySidebarTile(
                emoji = catEmoji(cat),
                label = cat,
                isSelected = activeCategory == cat,
                onClick = { onCategorySelected(cat) }
            )
        }
    }
}

@Composable
private fun CategorySidebarTile(
    emoji: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 12.sp
            )
        }
    }
}
