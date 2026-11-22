package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CartItemEntity
import com.example.data.local.OrderEntity
import com.example.data.remote.LocalPaymentChannel
import com.example.ui.components.MonoCodeText
import com.example.ui.components.OdooProductImage
import com.example.ui.components.QrisMatrixPreview
import com.example.ui.components.formatRupiah
import com.example.ui.state.CheckoutUiState
import com.example.ui.state.CommerceUiState
import com.example.ui.theme.StockAvailableGreen
import com.example.ui.theme.StockEmptyRed

@Composable
fun CartAndCheckoutScreen(
  uiState: CommerceUiState,
  onUpdateQty: (Int, Int) -> Unit,
  onRemoveItem: (Int) -> Unit,
  onShippingAddressChange: (String) -> Unit,
  onSelectPaymentChannel: (LocalPaymentChannel) -> Unit,
  onCheckout: () -> Unit,
  onVerifyPayment: (OrderEntity) -> Unit,
  onResetCheckout: () -> Unit,
  onExploreCatalog: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val clipboardManager = LocalClipboardManager.current

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 880.dp).align(Alignment.TopCenter),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      when (val checkout = uiState.checkoutState) {
        is CheckoutUiState.AwaitingPayment -> {
          item {
            ActivePaymentGatewayCard(
              order = checkout.order,
              onCopyCode = { code -> clipboardManager.setText(AnnotatedString(code)) },
              onVerifyPayment = { onVerifyPayment(checkout.order) },
              onBackToCart = onResetCheckout,
            )
          }
        }
        is CheckoutUiState.Paid -> {
          item {
            PaymentCompletedCard(
              order = checkout.order,
              onDone = onResetCheckout,
            )
          }
        }
        else -> Unit
      }

      if (uiState.cartItems.isEmpty() && uiState.checkoutState is CheckoutUiState.Idle) {
        item {
          EmptyCartCard(onExploreCatalog = onExploreCatalog)
        }
      } else if (uiState.cartItems.isNotEmpty()) {
        item {
          Column {
            Text(
              text = "Keranjang Belanja (${uiState.totalCartItemsCount} item)",
              style = MaterialTheme.typography.headlineSmall,
            )
            Text(
              text =
                if (uiState.session.activeDraftOrderId > 0) {
                  "Tersinkron ke Draft Sales Order Odoo #${uiState.session.activeDraftOrderId}"
                } else {
                  "Validasi stok real-time aktif sebelum pembayaran"
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        items(uiState.cartItems, key = { it.productId }) { item ->
          CartItemCard(
            item = item,
            onDecrease = { onUpdateQty(item.productId, item.quantity - 1) },
            onIncrease = { onUpdateQty(item.productId, item.quantity + 1) },
            onRemove = { onRemoveItem(item.productId) },
          )
        }

        item {
          ShippingAndGatewayCard(
            shippingAddress = uiState.shippingAddress,
            selectedChannel = uiState.selectedPaymentChannel,
            isGatewayConfigured = uiState.isPaymentGatewayConfigured,
            onShippingAddressChange = onShippingAddressChange,
            onSelectChannel = onSelectPaymentChannel,
          )
        }

        item {
          OrderSummaryCheckoutCard(
            subtotal = uiState.cartSubtotal,
            tax = uiState.cartTax,
            total = uiState.cartGrandTotal,
            isProcessing = uiState.checkoutState is CheckoutUiState.Processing,
            errorMessage = (uiState.checkoutState as? CheckoutUiState.Error)?.message,
            canCheckout = uiState.shippingAddress.isNotBlank(),
            onCheckout = onCheckout,
          )
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun EmptyCartCard(onExploreCatalog: () -> Unit) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_empty_cart_1795268893224),
        contentDescription = "Keranjang belanja kosong",
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(160.dp).clip(RoundedCornerShape(18.dp)),
      )
      Spacer(modifier = Modifier.height(14.dp))
      Text("Keranjang Anda Masih Kosong", style = MaterialTheme.typography.titleLarge)
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Tambahkan produk dari katalog Odoo 20 untuk memulai transaksi instan via QRIS atau Virtual Account.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(16.dp))
      Button(
        onClick = onExploreCatalog,
        modifier = Modifier.testTag("empty_cart_browse_button"),
      ) {
        Text("Jelajahi Katalog")
      }
    }
  }
}

@Composable
private fun CartItemCard(
  item: CartItemEntity,
  onDecrease: () -> Unit,
  onIncrease: () -> Unit,
  onRemove: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth().testTag("cart_item_${item.productId}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OdooProductImage(
        imageBase64 = item.imageBase64,
        contentDescription = item.name,
        modifier = Modifier.size(68.dp).clip(RoundedCornerShape(12.dp)),
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        MonoCodeText(text = item.sku)
        Text(item.name, style = MaterialTheme.typography.titleMedium)
        Text(
          text = "${formatRupiah(item.unitPrice)} • Tersedia: ${item.availableStock.toInt()}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onDecrease,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("cart_dec_${item.productId}"),
          ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Kurangi")
          }
          Text(
            text = "${item.quantity}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 6.dp),
          )
          IconButton(
            onClick = onIncrease,
            enabled = item.quantity < item.availableStock.toInt(),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("cart_inc_${item.productId}"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Tambah")
          }
        }
      }
      Column(horizontalAlignment = Alignment.End) {
        IconButton(
          onClick = onRemove,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("cart_del_${item.productId}"),
        ) {
          Icon(
            Icons.Outlined.DeleteOutline,
            contentDescription = "Hapus item",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          text = formatRupiah(item.unitPrice * item.quantity),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@Composable
private fun ShippingAndGatewayCard(
  shippingAddress: String,
  selectedChannel: LocalPaymentChannel,
  isGatewayConfigured: Boolean,
  onShippingAddressChange: (String) -> Unit,
  onSelectChannel: (LocalPaymentChannel) -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Alamat Pengiriman", style = MaterialTheme.typography.titleMedium)
      }

      OutlinedTextField(
        value = shippingAddress,
        onValueChange = onShippingAddressChange,
        label = { Text("Alamat Lengkap, Kota & Kode Pos") },
        placeholder = { Text("Contoh: Jl. Sudirman No. 45, Jakarta Selatan 12190") },
        minLines = 2,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("checkout_address_input"),
      )

      HorizontalDivider()

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Gateway Pembayaran Lokal", style = MaterialTheme.typography.titleMedium)
        }
        Surface(
          color =
            if (isGatewayConfigured) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.tertiaryContainer,
          shape = RoundedCornerShape(50),
        ) {
          Text(
            text = if (isGatewayConfigured) "TLS 1.2 Aktif" else "Butuh Kunci Secrets",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      for (channel in LocalPaymentChannel.entries) {
        val isSelected = selectedChannel == channel
        val borderColor =
          if (isSelected) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        val icon =
          when (channel) {
            LocalPaymentChannel.QRIS -> Icons.Outlined.QrCode2
            LocalPaymentChannel.GOPAY -> Icons.Outlined.Wallet
            else -> Icons.Outlined.AccountBalance
          }
        Surface(
          shape = RoundedCornerShape(14.dp),
          color =
            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surface,
          modifier =
            Modifier.fillMaxWidth()
              .border(1.dp, borderColor, RoundedCornerShape(14.dp))
              .clip(RoundedCornerShape(14.dp))
              .clickable { onSelectChannel(channel) }
              .testTag("payment_channel_${channel.code}"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = isSelected, onClick = { onSelectChannel(channel) })
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(channel.displayName, style = MaterialTheme.typography.titleSmall)
              Text(
                channel.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun OrderSummaryCheckoutCard(
  subtotal: Double,
  tax: Double,
  total: Double,
  isProcessing: Boolean,
  errorMessage: String?,
  canCheckout: Boolean,
  onCheckout: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SummaryRow("Subtotal Produk", formatRupiah(subtotal))
      SummaryRow("PPN (11%)", formatRupiah(tax))
      HorizontalDivider()
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Total Tagihan", style = MaterialTheme.typography.titleMedium)
        Text(
          text = formatRupiah(total),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
      }

      if (!errorMessage.isNullOrBlank()) {
        Text(
          text = errorMessage,
          style = MaterialTheme.typography.bodySmall,
          color = StockEmptyRed,
        )
      }

      Spacer(modifier = Modifier.height(4.dp))
      Button(
        onClick = onCheckout,
        enabled = canCheckout && !isProcessing,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("pay_now_button"),
      ) {
        if (isProcessing) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text("Membuat Transaksi Aman...")
        } else {
          Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Bayar Instan • ${formatRupiah(total)}")
        }
      }
    }
  }
}

@Composable
private fun ActivePaymentGatewayCard(
  order: OrderEntity,
  onCopyCode: (String) -> Unit,
  onVerifyPayment: () -> Unit,
  onBackToCart: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_payment_shield_1795268893315),
        contentDescription = "Keamanan pembayaran digital",
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(110.dp).clip(RoundedCornerShape(16.dp)),
      )
      Text("Menunggu Pembayaran ${order.paymentMethod}", style = MaterialTheme.typography.titleLarge)
      MonoCodeText(text = "Order ${order.orderNumber} • Total ${formatRupiah(order.totalAmount)}")

      if (!order.qrString.isNullOrBlank()) {
        QrisMatrixPreview(qrString = order.qrString)
        Text(
          text = "Pindai kode QRIS di atas melalui aplikasi e-wallet atau m-banking Anda.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (!order.vaNumber.isNullOrBlank()) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text("Nomor Virtual Account", style = MaterialTheme.typography.labelMedium)
              MonoCodeText(text = order.vaNumber, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { onCopyCode(order.vaNumber) }) {
              Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin VA")
            }
          }
        }
      }

      if (!order.paymentExpiry.isNullOrBlank()) {
        Text(
          text = "Berlaku hingga: ${order.paymentExpiry}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Button(
        onClick = onVerifyPayment,
        modifier = Modifier.fillMaxWidth().testTag("verify_payment_button"),
      ) {
        Text("Verifikasi Status Pembayaran")
      }
      OutlinedButton(onClick = onBackToCart, modifier = Modifier.fillMaxWidth()) {
        Text("Tutup")
      }
    }
  }
}

@Composable
private fun PaymentCompletedCard(order: OrderEntity, onDone: () -> Unit) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        Icons.Rounded.CheckCircle,
        contentDescription = null,
        tint = StockAvailableGreen,
        modifier = Modifier.size(44.dp),
      )
      Text("Pembayaran Berhasil!", style = MaterialTheme.typography.titleLarge)
      Text(
        text = "Pesanan ${order.orderNumber} telah dikonfirmasi di Odoo 20 Community.",
        style = MaterialTheme.typography.bodyMedium,
      )
      Button(onClick = onDone) { Text("Selesai") }
    }
  }
}

@Composable
private fun SummaryRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
  }
}
