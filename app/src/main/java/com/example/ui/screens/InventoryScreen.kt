package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.ProductEntity
import com.example.ui.components.LiveStockPill
import com.example.ui.components.MonoCodeText
import com.example.ui.components.formatRupiah
import com.example.ui.components.formatTimestamp
import com.example.ui.state.CommerceUiState
import com.example.ui.state.SyncUiState
import com.example.ui.theme.StockAvailableGreen
import com.example.ui.theme.StockEmptyRed
import com.example.ui.theme.StockLowAmber

@Composable
fun InventoryScreen(
  uiState: CommerceUiState,
  onSyncNow: () -> Unit,
  onAdjustStock: (ProductEntity, Double, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }

  val totalSkus = uiState.products.size
  val lowStockCount = uiState.products.count { it.freeQty > 0.0 && it.freeQty <= 5.0 }
  val outOfStockCount = uiState.products.count { it.freeQty <= 0.0 }
  val totalValuation = uiState.products.sumOf { it.price * it.qtyAvailable.coerceAtLeast(0.0) }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 920.dp).align(Alignment.TopCenter),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = "Manajemen Inventaris",
              style = MaterialTheme.typography.headlineSmall,
            )
            Text(
              text = "Pemantauan stok gudang Odoo 20 real-time",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          FilledTonalButton(
            onClick = onSyncNow,
            enabled = uiState.session.isAuthenticated && uiState.syncState !is SyncUiState.Syncing,
            modifier = Modifier.testTag("inventory_sync_button"),
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Sinkron")
          }
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            InventoryKpiCard(
              title = "Total SKU Aktif",
              value = "$totalSkus",
              subtitle = "Produk tersinkron",
              accentColor = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f),
            )
            InventoryKpiCard(
              title = "Nilai Stok Gudang",
              value = formatRupiah(totalValuation),
              subtitle = "Valuasi On-Hand",
              accentColor = StockAvailableGreen,
              modifier = Modifier.weight(1f),
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            InventoryKpiCard(
              title = "Stok Menipis (<=5)",
              value = "$lowStockCount",
              subtitle = "Perlu restock",
              accentColor = StockLowAmber,
              modifier = Modifier.weight(1f),
            )
            InventoryKpiCard(
              title = "Stok Habis",
              value = "$outOfStockCount",
              subtitle = "Tidak dapat dijual",
              accentColor = StockEmptyRed,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      if (uiState.products.isEmpty()) {
        item {
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                Icons.Outlined.Inventory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text("Belum Ada Data Inventaris", style = MaterialTheme.typography.titleMedium)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text =
                  "Hubungkan akun Odoo 20 Community Anda untuk memantau kuantitas gudang dan melakukan penyesuaian stok langsung.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        item {
          Text(
            text = "Daftar Stok Real-Time (${uiState.products.size})",
            style = MaterialTheme.typography.titleMedium,
          )
        }
        items(uiState.products, key = { it.id }) { product ->
          InventoryItemCard(
            product = product,
            canAdjust = uiState.session.isAuthenticated,
            onEditClick = { editingProduct = product },
          )
        }
      }

      if (uiState.stockMovements.isNotEmpty()) {
        item {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Riwayat Penyesuaian Stok",
            style = MaterialTheme.typography.titleMedium,
          )
        }
        items(uiState.stockMovements, key = { it.id }) { movement ->
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(movement.productName, style = MaterialTheme.typography.titleSmall)
                Text(
                  text = "${movement.reason} • ${formatTimestamp(movement.timestamp)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              val prefix = if (movement.deltaQty >= 0) "+" else ""
              Text(
                text = "$prefix${movement.deltaQty.toInt()} → ${movement.newFreeQty.toInt()}",
                style = MaterialTheme.typography.labelLarge,
                color =
                  if (movement.deltaQty >= 0) StockAvailableGreen else StockLowAmber,
              )
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }

  val target = editingProduct
  if (target != null) {
    AdjustStockDialog(
      product = target,
      onDismiss = { editingProduct = null },
      onConfirm = { newQty, reason ->
        onAdjustStock(target, newQty, reason)
        editingProduct = null
      },
    )
  }
}

@Composable
private fun InventoryKpiCard(
  title: String,
  value: String,
  subtitle: String,
  accentColor: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        color = accentColor,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun InventoryItemCard(
  product: ProductEntity,
  canAdjust: Boolean,
  onEditClick: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth().testTag("inventory_item_${product.id}"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          MonoCodeText(text = product.sku)
          Text(product.name, style = MaterialTheme.typography.titleMedium)
          Text(
            text = product.warehouseSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        LiveStockPill(freeQty = product.freeQty, uomName = product.uomName)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          Text(
            text = "On Hand: ${product.qtyAvailable.toInt()}",
            style = MaterialTheme.typography.bodySmall,
          )
          Text(
            text = "Forecast: ${product.virtualAvailable.toInt()}",
            style = MaterialTheme.typography.bodySmall,
          )
        }
        OutlinedButton(
          onClick = onEditClick,
          enabled = canAdjust,
          modifier = Modifier.testTag("adjust_stock_${product.id}"),
        ) {
          Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Sesuaikan")
        }
      }
    }
  }
}

@Composable
private fun AdjustStockDialog(
  product: ProductEntity,
  onDismiss: () -> Unit,
  onConfirm: (Double, String) -> Unit,
) {
  var qtyText by remember { mutableStateOf(product.freeQty.toInt().toString()) }
  var reasonText by remember { mutableStateOf("Stock opname mobile Odoo") }
  val parsedQty = qtyText.toDoubleOrNull()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Sesuaikan Stok Odoo 20") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "${product.name} (${product.sku})",
          style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(
          value = qtyText,
          onValueChange = { qtyText = it.filter { ch -> ch.isDigit() || ch == '.' } },
          label = { Text("Kuantitas Baru (${product.uomName})") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("adjust_stock_qty_input"),
        )
        OutlinedTextField(
          value = reasonText,
          onValueChange = { reasonText = it },
          label = { Text("Catatan Audit Stok") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (parsedQty == null) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Outlined.WarningAmber,
              contentDescription = null,
              tint = StockEmptyRed,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              "Masukkan angka kuantitas yang valid",
              style = MaterialTheme.typography.bodySmall,
              color = StockEmptyRed,
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { parsedQty?.let { onConfirm(it, reasonText) } },
        enabled = parsedQty != null && parsedQty >= 0.0,
        modifier = Modifier.testTag("confirm_adjust_stock_button"),
      ) {
        Text("Simpan ke Odoo")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Batal")
      }
    },
  )
}
