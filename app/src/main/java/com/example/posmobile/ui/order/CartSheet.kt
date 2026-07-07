package com.example.posmobile.ui.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.posmobile.ui.formatCents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartSheet(
    vm: OrderViewModel,
    onDismiss: () -> Unit,
    onPlace: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CartContent(
            vm = vm,
            onPlace = onPlace,
            isInline = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartContent(
    vm: OrderViewModel,
    onPlace: () -> Unit,
    isInline: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text("Current order", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        // Order type
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val types = listOf("dine_in" to "Dine In", "takeaway" to "Takeaway")
            types.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = vm.orderType == value,
                    onClick = { vm.selectOrderType(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, types.size),
                ) { Text(label) }
            }
        }

        if (vm.orderType == "dine_in") {
            Spacer(Modifier.height(8.dp))
            TableDropdown(vm)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()

        if (vm.cart.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (isInline) Modifier.weight(1f) else Modifier.height(120.dp)),
                Alignment.Center
            ) {
                Text("No items", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = if (isInline) {
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                } else {
                    Modifier.heightIn(max = 320.dp)
                }
            ) {
                items(vm.cart, key = { it.key }) { line ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                line.itemName,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(
                                    line.prepLocationName,
                                    "@ ${formatCents(line.unitPriceCents)}",
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StepButton(Icons.Filled.Remove) { vm.changeQty(line.key, -1) }
                        Text(
                            line.quantity.toString(),
                            modifier = Modifier.padding(horizontal = 10.dp),
                            fontWeight = FontWeight.Bold,
                        )
                        StepButton(Icons.Filled.Add) { vm.changeQty(line.key, 1) }
                        Spacer(Modifier.size(4.dp))
                        Text(
                            formatCents(line.quantity * line.unitPriceCents),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(onClick = { vm.removeLine(line.key) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = vm.remarks,
            onValueChange = vm::updateRemarks,
            label = { Text("Remarks (allergies, notes…)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                formatCents(vm.subtotalCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onPlace,
            enabled = vm.cart.isNotEmpty() && !vm.placing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (vm.placing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Place order & Print")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableDropdown(vm: OrderViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selected = vm.tables.firstOrNull { it.id == vm.tableId }

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                selected?.let { "${it.name} (${it.seats} seats)" } ?: "Select table…",
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (vm.tables.isEmpty()) {
                DropdownMenuItem(text = { Text("No tables configured") }, onClick = { expanded = false })
            }
            vm.tables.forEach { table ->
                DropdownMenuItem(
                    text = { Text("${table.name} (${table.seats} seats)") },
                    onClick = { vm.selectTable(table.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}
