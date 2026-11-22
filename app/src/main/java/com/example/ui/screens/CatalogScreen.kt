package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ProductEntity
import com.example.ui.components.LiveStockPill
import com.example.ui.components.MonoCodeText
import com.example.ui.components.OdooConnectionStatusChip
import com.example.ui.components.OdooProductImage
import com.example.ui.components.formatRupiah
import com.example.ui.components.formatTimestamp
import com.example.ui.state.CommerceUiState
import com.example.ui.state.StockFilter
import com.example.ui.state.SyncUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
  uiState: CommerceUiState,
  onSearchChange: (String) -> Unit,
  onCategorySelect: (String) -> Unit,
  onStockFilterSelect: (StockFilter) -> Unit,
  onSyncNow: () -> Unit,
  onProductClick: (ProductEntity) -> Unit,
  onDismissProduct: () -> Unit,
  onAddToCart: (ProductEntity, Int) -> Unit,
  onNavigateToConnect: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isSyncing = uiState.syncState is SyncUiState.Syncing

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val columns = if (maxWidth >= 700.dp) 2 else 1
    val productRows = remember(uiState.filteredProducts, columns) {
      uiState.filteredProducts.chunked(columns)
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 920.dp).align(Alignment.TopCenter),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        HeroStorefrontCard(
          isConnected = uiState.session.isAuthenticated,
          isSyncing = isSyncing,
          serverUrl = uiState.session.baseUrl,
          lastSyncTimestamp = uiState.lastSyncTimestamp,
          onSyncNow = onSyncNow,
          onNavigateToConnect = onNavigateToConnect,
        )
      }

      item {
        OutlinedTextField(
          value = uiState.searchQuery,
          onValueChange = onSearchChange,
          modifier = Modifier.fillMaxWidth().testTag("catalog_search_input"),
          placeholder = { Text("Cari nama produk, SKU, atau barcode Odoo...") },
          leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = "Cari produk")
          },
          trailingIcon = {
            if (uiState.searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
        )
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.categories) { category ->
              FilterChip(
                selected = uiState.selectedCategory == category,
                onClick = { onCategorySelect(category) },
                label = { Text(category) },
                modifier = Modifier.testTag("category_chip_$category"),
              )
            }
          }

          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(StockFilter.entries) { filter ->
              FilterChip(
                selected = uiState.stockFilter == filter,
                onClick = { onStockFilterSelect(filter) },
                label = { Text(filter.label) },
                modifier = Modifier.testTag("stock_filter_${filter.name}"),
              )
            }
          }
        }
      }

      if (uiState.filteredProducts.isEmpty()) {
        item {
          EmptyCatalogCard(
            isConnected = uiState.session.isAuthenticated,
            hasSearch = uiState.searchQuery.isNotBlank() || uiState.selectedCategory != "Semua",
            onNavigateToConnect = onNavigateToConnect,
            onSyncNow = onSyncNow,
          )
        }
      } else {
        items(productRows) { rowItems ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            for (product in rowItems) {
              ProductCatalogCard(
                product = product,
                onClick = { onProductClick(product) },
                onQuickAdd = { onAddToCart(product, 1) },
                modifier = Modifier.weight(1f),
              )
            }
            if (rowItems.size < columns) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }

  val selected = uiState.selectedProduct
  if (selected != null) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = onDismissProduct,
      sheetState = sheetState,
    ) {
      ProductDetailSheetContent(
        product = selected,
        onAddToCart = { qty -> onAddToCart(selected, qty) },
        onClose = onDismissProduct,
      )
    }
  }
}

@Composable
private fun HeroStorefrontCard(
  isConnected: Boolean,
  isSyncing: Boolean,
  serverUrl: String,
  lastSyncTimestamp: Long?,
  onSyncNow: () -> Unit,
  onNavigateToConnect: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(modifier = Modifier.fillMaxWidth().height(176.dp)) {
      Image(
        painter = painterResource(id = R.drawable.img_hero_storefront_1795268894146),
        contentDescription = "Ilustrasi gudang dan etalase OdooMart",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(Color(0xCC0B3B31), Color(0xEB082821))
              )
            )
      )
      Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OdooConnectionStatusChip(
            isConnected = isConnected,
            isSyncing = isSyncing,
            serverUrl = serverUrl,
          )
          if (isConnected) {
            IconButton(
              onClick = onSyncNow,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("sync_catalog_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Sinkronkan stok Odoo sekarang",
                tint = Color.White,
              )
            }
          }
        }

        Column {
          Text(
            text = "Odoo 20 Real-Time Storefront",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text =
              if (lastSyncTimestamp != null) {
                "Sinkronisasi inventaris terakhir: ${formatTimestamp(lastSyncTimestamp)}"
              } else if (isConnected) {
                "Terhubung ke stok gudang Odoo 20 Community secara langsung"
              } else {
                "Hubungkan akun Odoo 20 Community Anda untuk katalog & stok live"
              },
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.88f),
          )
          AnimatedVisibility(visible = !isConnected) {
            Row(modifier = Modifier.padding(top = 10.dp)) {
              FilledTonalButton(
                onClick = onNavigateToConnect,
                modifier = Modifier.testTag("hero_connect_odoo_button"),
              ) {
                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Hubungkan Odoo 20")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ProductCatalogCard(
  product: ProductEntity,
  onClick: () -> Unit,
  onQuickAdd: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier =
      modifier
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onClick)
        .testTag("product_card_${product.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OdooProductImage(
        imageBase64 = product.imageBase64,
        contentDescription = product.name,
        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)),
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          MonoCodeText(text = product.sku)
          Text(
            text = product.categoryName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = product.name,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        LiveStockPill(freeQty = product.freeQty, uomName = product.uomName)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = formatRupiah(product.price),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
          )
          FilledTonalButton(
            onClick = onQuickAdd,
            enabled = product.freeQty > 0.0,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("quick_add_${product.id}"),
          ) {
            Icon(
              imageVector = Icons.Outlined.AddShoppingCart,
              contentDescription = "Tambah ke keranjang",
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Beli", style = MaterialTheme.typography.labelLarge)
          }
        }
      }
    }
  }
}

@Composable
private fun EmptyCatalogCard(
  isConnected: Boolean,
  hasSearch: Boolean,
  onNavigateToConnect: () -> Unit,
  onSyncNow: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        imageVector = Icons.Outlined.Storefront,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(44.dp),
      )
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text =
          when {
            !isConnected -> "Menunggu Koneksi Odoo 20 Community"
            hasSearch -> "Produk Tidak Ditemukan"
            else -> "Katalog Odoo Masih Kosong"
          },
        style = MaterialTheme.typography.titleMedium,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text =
          when {
            !isConnected ->
              "Masuk ke server Odoo 20 Community Anda pada tab Akun untuk memuat produk, harga, dan stok real-time."
            hasSearch -> "Ubah kata kunci pencarian atau filter stok untuk melihat produk lainnya."
            else -> "Pastikan produk di Odoo memiliki opsi 'Can be Sold' aktif, lalu sinkronkan ulang."
          },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(16.dp))
      if (!isConnected) {
        Button(
          onClick = onNavigateToConnect,
          modifier = Modifier.testTag("empty_connect_odoo_button"),
        ) {
          Text("Masuk ke Server Odoo")
        }
      } else {
        FilledTonalButton(onClick = onSyncNow) {
          Text("Muat Ulang dari Odoo")
        }
      }
    }
  }
}

@Composable
private fun ProductDetailSheetContent(
  product: ProductEntity,
  onAddToCart: (Int) -> Unit,
  onClose: () -> Unit,
) {
  var qty by remember(product.id) { mutableIntStateOf(1) }
  val maxQty = product.freeQty.toInt().coerceAtLeast(1)

  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      OdooProductImage(
        imageBase64 = product.imageBase64,
        contentDescription = product.name,
        modifier = Modifier.size(92.dp).clip(RoundedCornerShape(16.dp)),
      )
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        MonoCodeText(text = "SKU: ${product.sku}")
        Text(text = product.name, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = formatRupiah(product.price),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }

    Surface(
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Outlined.Warehouse,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Status Inventaris Real-Time Odoo 20",
            style = MaterialTheme.typography.labelLarge,
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          StockMetricItem("On Hand", "${product.qtyAvailable.toInt()} ${product.uomName}")
          StockMetricItem("Tersedia", "${product.freeQty.toInt()} ${product.uomName}")
          StockMetricItem("Forecast", "${product.virtualAvailable.toInt()} ${product.uomName}")
        }
        Text(
          text = "Lokasi Gudang: ${product.warehouseSummary}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (product.description.isNotBlank()) {
      Text(
        text = product.description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    HorizontalDivider()

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = { if (qty > 1) qty-- },
          enabled = qty > 1,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(Icons.Rounded.Remove, contentDescription = "Kurangi jumlah")
        }
        Text(
          text = "$qty",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(
          onClick = { if (qty < maxQty) qty++ },
          enabled = product.freeQty > 0 && qty < maxQty,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = "Tambah jumlah")
        }
      }

      Button(
        onClick = { onAddToCart(qty) },
        enabled = product.freeQty > 0.0,
        modifier = Modifier.testTag("sheet_add_to_cart_button"),
      ) {
        Text(
          if (product.freeQty > 0.0) {
            "Tambah • ${formatRupiah(product.price * qty)}"
          } else {
            "Stok Habis"
          }
        )
      }
    }

    Spacer(modifier = Modifier.height(16.dp))
  }
}

@Composable
private fun StockMetricItem(label: String, value: String) {
  Column {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
    )
  }
}
