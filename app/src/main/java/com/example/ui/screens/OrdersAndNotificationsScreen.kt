package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.NotificationLogEntity
import com.example.data.local.OrderEntity
import com.example.notifications.NotificationHelper
import com.example.ui.components.MonoCodeText
import com.example.ui.components.OrderProgressStepper
import com.example.ui.components.formatRupiah
import com.example.ui.components.formatTimestamp
import com.example.ui.state.CommerceUiState
import com.example.ui.theme.StockAvailableBg
import com.example.ui.theme.StockAvailableGreen
import com.example.ui.theme.StockLowAmber
import com.example.ui.theme.StockLowBg

@Composable
fun OrdersAndNotificationsScreen(
  uiState: CommerceUiState,
  onSyncOrders: () -> Unit,
  onVerifyOrderPayment: (OrderEntity) -> Unit,
  onMarkAllRead: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val notificationHelper = remember(context) { NotificationHelper(context) }
  var hasNotifPermission by remember { mutableStateOf(notificationHelper.hasNotificationPermission()) }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotifPermission = granted
    }

  var selectedSegment by remember { mutableIntStateOf(0) }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 880.dp).align(Alignment.TopCenter),
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
            Text("Pesanan & Notifikasi", style = MaterialTheme.typography.headlineSmall)
            Text(
              "Pelacakan status Odoo 20 & notifikasi push real-time",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          FilledTonalButton(
            onClick = onSyncOrders,
            enabled = uiState.session.isAuthenticated,
            modifier = Modifier.testTag("orders_sync_button"),
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cek Status")
          }
        }
      }

      if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        item {
          Card(
            shape = RoundedCornerShape(18.dp),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
              ),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  "Aktifkan Notifikasi Push",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  "Terima pembaruan instan saat pembayaran terverifikasi atau pesanan dikirim.",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Button(
                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.testTag("grant_notification_permission_button"),
              ) {
                Text("Izinkan")
              }
            }
          }
        }
      }

      item {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = selectedSegment == 0,
            onClick = { selectedSegment = 0 },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.testTag("segment_orders"),
          ) {
            Text("Pesanan (${uiState.orders.size})")
          }
          SegmentedButton(
            selected = selectedSegment == 1,
            onClick = { selectedSegment = 1 },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.testTag("segment_notifications"),
          ) {
            val unreadSuffix =
              if (uiState.unreadNotificationCount > 0) " (${uiState.unreadNotificationCount})"
              else ""
            Text("Notifikasi$unreadSuffix")
          }
        }
      }

      if (selectedSegment == 0) {
        if (uiState.orders.isEmpty()) {
          item {
            EmptySectionCard(
              icon = Icons.Outlined.ReceiptLong,
              title = "Belum Ada Pesanan",
              subtitle =
                "Pesanan yang dibuat melalui keranjang belanja atau tersinkron dari Odoo 20 akan tampil beserta status real-time di sini.",
            )
          }
        } else {
          items(uiState.orders, key = { it.localId }) { order ->
            OrderStatusCard(
              order = order,
              onVerifyPayment = { onVerifyOrderPayment(order) },
            )
          }
        }
      } else {
        if (uiState.notifications.isNotEmpty() && uiState.unreadNotificationCount > 0) {
          item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
              TextButton(
                onClick = onMarkAllRead,
                modifier = Modifier.testTag("mark_all_read_button"),
              ) {
                Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tandai Semua Dibaca")
              }
            }
          }
        }
        if (uiState.notifications.isEmpty()) {
          item {
            EmptySectionCard(
              icon = Icons.Outlined.NotificationsNone,
              title = "Kotak Notifikasi Kosong",
              subtitle =
                "Pembaruan push untuk konfirmasi pembayaran, pengemasan gudang, dan pengiriman Odoo 20 akan muncul di sini.",
            )
          }
        } else {
          items(uiState.notifications, key = { it.id }) { notif ->
            NotificationLogCard(notification = notif)
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun OrderStatusCard(order: OrderEntity, onVerifyPayment: () -> Unit) {
  val isPaid = order.paymentStatus == "settlement" || order.paymentStatus == "capture"
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth().testTag("order_card_${order.orderNumber}"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          MonoCodeText(text = order.orderNumber)
          Text(
            text = order.paymentMethod,
            style = MaterialTheme.typography.titleMedium,
          )
        }
        Surface(
          color = if (isPaid) StockAvailableBg else StockLowBg,
          shape = RoundedCornerShape(50),
        ) {
          Text(
            text = order.deliveryStatus,
            color = if (isPaid) StockAvailableGreen else StockLowAmber,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      Text(
        text = order.itemSummary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OrderProgressStepper(
        deliveryStatus = order.deliveryStatus,
        paymentStatus = order.paymentStatus,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = formatRupiah(order.totalAmount),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Diperbarui ${formatTimestamp(order.updatedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (!isPaid) {
          OutlinedButton(
            onClick = onVerifyPayment,
            modifier = Modifier.testTag("order_verify_${order.orderNumber}"),
          ) {
            Text("Cek Pembayaran")
          }
        }
      }
    }
  }
}

@Composable
private fun NotificationLogCard(notification: NotificationLogEntity) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (!notification.isRead) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
          else MaterialTheme.colorScheme.surface
      ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        MonoCodeText(text = "${notification.orderNumber} • ${notification.statusTag}")
        Text(
          text = formatTimestamp(notification.createdAt),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(notification.title, style = MaterialTheme.typography.titleSmall)
      Text(
        text = notification.message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun EmptySectionCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
      Spacer(modifier = Modifier.height(10.dp))
      Text(title, style = MaterialTheme.typography.titleMedium)
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
