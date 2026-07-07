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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import com.example.posmobile.data.Container
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

    var devices by remember { mutableStateOf<List<BtPrinter>>(emptyList()) }
    var selectedMac by remember { mutableStateOf(settings.printerMac) }
    var paperCols by remember { mutableStateOf(settings.paperCols) }
    var status by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(Unit) { refresh() }

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
                Text("Paper width", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = paperCols == 32,
                        onClick = { paperCols = 32; settings.paperCols = 32 },
                        label = { Text("58 mm") },
                    )
                    FilterChip(
                        selected = paperCols == 48,
                        onClick = { paperCols = 48; settings.paperCols = 48 },
                        label = { Text("80 mm") },
                    )
                }

                Spacer(Modifier.height(16.dp))
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
                                    .clickable {
                                        selectedMac = d.mac
                                        settings.printerMac = d.mac
                                        settings.printerName = d.name
                                    },
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
                                    if (selectedMac == d.mac) {
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

                Button(
                    onClick = {
                        scope.launch {
                            try {
                                printer.print(testTicket(paperCols))
                                snackbar.showSnackbar("Test sent")
                            } catch (e: PrinterException) {
                                snackbar.showSnackbar(e.message ?: "Print failed")
                            }
                        }
                    },
                    enabled = selectedMac != null,
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
