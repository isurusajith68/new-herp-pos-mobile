package com.example.posmobile.ui.order

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.posmobile.data.MenuItemPriceOption
import com.example.posmobile.data.OrderableMenuItem
import com.example.posmobile.data.PrepLocation
import com.example.posmobile.ui.formatCents

/**
 * Item add sheet: pick a price using a matrix/grid layout, pick a kitchen if the
 * item routes to more than one, and choose quantity — mirrors the web terminal.
 */
@Composable
fun AddItemDialog(
    item: OrderableMenuItem,
    onDismiss: () -> Unit,
    onAdd: (
        quantity: Int,
        unitPriceCents: Int,
        itemType: String,
        prep: PrepLocation?,
        price: MenuItemPriceOption?,
    ) -> Unit,
) {
    val hasPrices = item.prices.isNotEmpty()
    var selectedPrice by remember { mutableStateOf(item.prices.firstOrNull()) }
    var customPrice by remember { mutableStateOf("") }
    var selectedPrep by remember { mutableStateOf(item.prepLocations.firstOrNull()) }
    var qty by remember { mutableIntStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }

    // Group price options by Type (e.g. Cost Price, Selling Price)
    val priceTypes = remember(item.prices) {
        item.prices.map { it.priceTypeName }.distinct()
    }
    var activePriceType by remember(priceTypes) {
        mutableStateOf(priceTypes.firstOrNull() ?: "")
    }

    // Filtered tiers and menu types for the active price type
    val priceTiers = remember(item.prices, activePriceType) {
        item.prices.filter { it.priceTypeName == activePriceType }.map { it.priceTierName }.distinct()
    }
    val menuTypes = remember(item.prices, activePriceType) {
        item.prices.filter { it.priceTypeName == activePriceType }.map { it.menuTypeName }.distinct()
    }

    fun resolveCents(): Int? =
        if (hasPrices) selectedPrice?.priceCents
        else customPrice.replace(",", "").toDoubleOrNull()?.let { (it * 100).toInt() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hasPrices) "Select price — ${item.name}" else item.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 460.dp),
            ) {
                if (hasPrices) {
                    if (priceTypes.size > 1) {
                        TabRow(
                            selectedTabIndex = priceTypes.indexOf(activePriceType),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            priceTypes.forEach { type ->
                                Tab(
                                    selected = activePriceType == type,
                                    onClick = {
                                        activePriceType = type
                                        // Pick the first option of this price type to auto-select
                                        item.prices.firstOrNull { it.priceTypeName == type }?.let {
                                            selectedPrice = it
                                        }
                                    }
                                ) {
                                    Text(
                                        text = type,
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    // Table Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MENU TYPE",
                            modifier = Modifier.weight(1.5f),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                        priceTiers.forEach { tier ->
                            Text(
                                text = tier.uppercase(),
                                modifier = Modifier.weight(1f),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // Table Rows
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                            )
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        menuTypes.forEach { menuType ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = menuType,
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .padding(start = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                priceTiers.forEach { tier ->
                                    val option = item.prices.firstOrNull {
                                        it.priceTypeName == activePriceType &&
                                        it.menuTypeName == menuType &&
                                        it.priceTierName == tier
                                    }
                                    if (option != null) {
                                        val isSelected = selectedPrice?.id == option.id
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedPrice = option },
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                                )
                                            ) {
                                                Text(
                                                    text = formatCents(option.priceCents),
                                                    modifier = Modifier.padding(vertical = 8.dp),
                                                    textAlign = TextAlign.Center,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = customPrice,
                        onValueChange = { customPrice = it },
                        label = { Text("Unit price") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (item.prepLocations.size > 1) {
                    Spacer(Modifier.size(12.dp))
                    Text("Kitchen", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.size(4.dp))
                    item.prepLocations.forEach { prep ->
                        OptionRow(
                            selected = selectedPrep?.id == prep.id,
                            onClick = { selectedPrep = prep },
                            label = prep.name,
                            trailing = null,
                        )
                    }
                }

                Spacer(Modifier.size(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Quantity", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    QtyButton(Icons.Filled.Remove) { if (qty > 1) qty-- }
                    Text(
                        qty.toString(),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    QtyButton(Icons.Filled.Add) { qty++ }
                }

                error?.let {
                    Spacer(Modifier.size(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = resolveCents()
                if (cents == null || cents < 0) {
                    error = "Enter a valid price"
                    return@TextButton
                }
                val itemType = if (hasPrices) (selectedPrice?.menuTypeName ?: "food") else "food"
                onAdd(qty, cents, itemType, selectedPrep, if (hasPrices) selectedPrice else null)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun OptionRow(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    trailing: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun QtyButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}
