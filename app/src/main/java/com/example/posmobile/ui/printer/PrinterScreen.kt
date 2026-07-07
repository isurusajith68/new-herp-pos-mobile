package com.example.posmobile.ui.printer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.posmobile.data.Container
import com.example.posmobile.data.Settings
import com.example.posmobile.print.BtPrinter
import com.example.posmobile.print.EscPos
import com.example.posmobile.print.PrinterException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterScreen(onBack: () -> Unit) {
    val printer = Container.printer
    val settings = Container.settings
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var activeTab by remember { mutableStateOf(0) } // 0 = POS/Receipt Printer, 1 = Kitchen Printer

    // Receipt Printer State
    var receiptType by remember { mutableStateOf(settings.printerType) }
    var receiptMac by remember { mutableStateOf(settings.printerMac) }
    var receiptHost by remember { mutableStateOf(settings.printerHost ?: "") }
    var receiptPort by remember { mutableStateOf(settings.printerPort.toString()) }
    var receiptCols by remember { mutableStateOf(settings.paperCols) }
    var receiptCopies by remember { mutableStateOf(settings.receiptCopies) }

    // Kitchen Printer State
    var kitchenType by remember { mutableStateOf(settings.kitchenPrinterType) }
    var kitchenMac by remember { mutableStateOf(settings.kitchenPrinterMac) }
    var kitchenHost by remember { mutableStateOf(settings.kitchenPrinterHost ?: "") }
    var kitchenPort by remember { mutableStateOf(settings.kitchenPrinterPort.toString()) }
    var kitchenCols by remember { mutableStateOf(settings.kitchenPaperCols) }
    var kotEnabled by remember { mutableStateOf(settings.kotEnabled) }

    var devices by remember { mutableStateOf<List<BtPrinter>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }

    val currentType = if (activeTab == 0) receiptType else kitchenType
    val currentMac = if (activeTab == 0) receiptMac else kitchenMac
    val currentHost = if (activeTab == 0) receiptHost else kitchenHost
    val currentPort = if (activeTab == 0) receiptPort else kitchenPort
    val currentCols = if (activeTab == 0) receiptCols else kitchenCols
    val currentIsWifi = currentType == Settings.TYPE_WIFI

    val canPrint = if (activeTab == 0) {
        if (currentIsWifi) currentHost.isNotBlank() else currentMac != null
    } else {
        kotEnabled && (if (currentIsWifi) currentHost.isNotBlank() else currentMac != null)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            runCatching { devices = printer.bondedPrinters() }
                .onFailure { status = it.message }
        } else {
            status = "Bluetooth permission denied"
        }
    }

    fun refresh() {
        if (!printer.hasConnectPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
            return
        }
        try {
            devices = printer.bondedPrinters()
            status = if (devices.isEmpty()) "No paired devices — pair your printer in Android settings first" else null
        } catch (e: Exception) {
            status = e.message
        }
    }

    fun updatePaperCols(cols: Int) {
        if (activeTab == 0) {
            receiptCols = cols
            settings.paperCols = cols
        } else {
            kitchenCols = cols
            settings.kitchenPaperCols = cols
        }
    }

    fun updatePrinterType(type: String) {
        if (activeTab == 0) {
            receiptType = type
            settings.printerType = type
        } else {
            kitchenType = type
            settings.kitchenPrinterType = type
        }
    }

    fun updateWifiHost(host: String) {
        if (activeTab == 0) {
            receiptHost = host
            settings.printerHost = host
        } else {
            kitchenHost = host
            settings.kitchenPrinterHost = host
        }
    }

    fun updateWifiPort(portStr: String) {
        val cleanPort = portStr.filter { it.isDigit() }
        val portInt = cleanPort.toIntOrNull() ?: 9100
        if (activeTab == 0) {
            receiptPort = cleanPort
            settings.printerPort = portInt
        } else {
            kitchenPort = cleanPort
            settings.kitchenPrinterPort = portInt
        }
    }

    fun selectMacAddress(mac: String, name: String) {
        if (activeTab == 0) {
            receiptMac = mac
            settings.printerMac = mac
            settings.printerName = name
        } else {
            kitchenMac = mac
            settings.kitchenPrinterMac = mac
            settings.kitchenPrinterName = name
        }
    }

    LaunchedEffect(activeTab, receiptType, kitchenType) {
        if (!currentIsWifi) refresh() else status = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Printer settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val isTablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier
                    .then(if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                TabRow(selectedTabIndex = activeTab, modifier = Modifier.fillMaxWidth()) {
                    Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) {
                        Text("POS Printer", modifier = Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold)
                    }
                    Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
                        Text("Kitchen Printer", modifier = Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (activeTab == 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Print Kitchen Order Tickets", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Text("Send orders to the kitchen printer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = kotEnabled,
                            onCheckedChange = { kotEnabled = it; settings.kotEnabled = it }
                        )
                    }
                }

                if (activeTab == 0 || kotEnabled) {
                    Text("Paper width", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = currentCols == 32,
                            onClick = { updatePaperCols(32) },
                            label = { Text("58 mm") },
                        )
                        FilterChip(
                            selected = currentCols == 48,
                            onClick = { updatePaperCols(48) },
                            label = { Text("80 mm") },
                        )
                    }

                    if (activeTab == 0) {
                        Spacer(Modifier.height(16.dp))
                        Text("Receipt copies", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = receiptCopies == 1,
                                onClick = { receiptCopies = 1; settings.receiptCopies = 1 },
                                label = { Text("1 copy") },
                            )
                            FilterChip(
                                selected = receiptCopies == 2,
                                onClick = { receiptCopies = 2; settings.receiptCopies = 2 },
                                label = { Text("2 copies") },
                            )
                            FilterChip(
                                selected = receiptCopies == 3,
                                onClick = { receiptCopies = 3; settings.receiptCopies = 3 },
                                label = { Text("3 copies") },
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Connection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !currentIsWifi,
                            onClick = { updatePrinterType(Settings.TYPE_BLUETOOTH) },
                            label = { Text("Bluetooth") },
                        )
                        FilterChip(
                            selected = currentIsWifi,
                            onClick = { updatePrinterType(Settings.TYPE_WIFI) },
                            label = { Text("Wi-Fi") },
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    if (currentIsWifi) {
                        Text("Wi-Fi printer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = currentHost,
                            onValueChange = { updateWifiHost(it) },
                            label = { Text("IP address") },
                            placeholder = { Text("192.168.1.50") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = currentPort,
                            onValueChange = { updateWifiPort(it) },
                            label = { Text("Port") },
                            placeholder = { Text("9100") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Most thermal printers use port 9100. Set a fixed IP (DHCP reservation) on your router so it doesn't change.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Paired printers",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = { refresh() }) { Text("Refresh") }
                        }
                        Spacer(Modifier.height(8.dp))

                        status?.let {
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                        }

                        Box(Modifier.weight(1f)) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(devices) { d ->
                                    Card(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { selectMacAddress(d.mac, d.name) },
                                    ) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(d.name, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    d.mac,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            if (currentMac == d.mac) {
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Kitchen printer disabled view
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            "Kitchen Order Tickets are disabled.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            try {
                                if (activeTab == 0) {
                                    printer.print(testTicket(currentCols))
                                } else {
                                    printer.print(
                                        bytes = testTicket(currentCols),
                                        type = currentType,
                                        mac = currentMac,
                                        host = currentHost,
                                        port = currentPort.toIntOrNull() ?: 9100
                                    )
                                }
                                snackbar.showSnackbar("Test sent")
                            } catch (e: PrinterException) {
                                snackbar.showSnackbar(e.message ?: "Print failed")
                            }
                        }
                    },
                    enabled = canPrint,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Print test receipt")
                }
            }
        }
    }
}

private fun testTicket(cols: Int): ByteArray {
    val e = EscPos(cols)
    e.align(EscPos.Align.CENTER).bold(true).bigSize(true).line("TEST PRINT").bigSize(false).bold(false)
    e.align(EscPos.Align.LEFT).rule()
    e.cols("Item", "Qty")
    e.cols("Sample burger", "1")
    e.cols("Cola", "2")
    e.rule()
    e.align(EscPos.Align.CENTER).line("HERP POS").feed(1).cut()
    return e.bytes()
}
