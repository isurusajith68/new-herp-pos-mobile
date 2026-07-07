package com.example.posmobile.ui.order

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.posmobile.data.MenuItemPriceOption
import com.example.posmobile.data.OrderableMenuItem
import com.example.posmobile.data.PrepLocation
import com.example.posmobile.ui.formatCents

/**
 * Item add sheet: pick a price (or type a custom one), pick a kitchen if the
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

    fun resolveCents(): Int? =
        if (hasPrices) selectedPrice?.priceCents
        else customPrice.replace(",", "").toDoubleOrNull()?.let { (it * 100).toInt() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 460.dp),
            ) {
                if (hasPrices) {
                    Text("Price", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.size(4.dp))
                    item.prices.forEach { price ->
                        OptionRow(
                            selected = selectedPrice?.id == price.id,
                            onClick = { selectedPrice = price },
                            label = listOfNotNull(
                                price.priceTypeName,
                                price.menuTypeName,
                                price.priceTierName,
                            ).distinct().joinToString(" · "),
                            trailing = formatCents(price.priceCents),
                        )
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
                    Text("Quantity", modifier = Modifier.weight(1f))
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
