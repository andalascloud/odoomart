package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StockAvailableBg
import com.example.ui.theme.StockAvailableGreen
import com.example.ui.theme.StockEmptyBg
import com.example.ui.theme.StockEmptyRed
import com.example.ui.theme.StockLowAmber
import com.example.ui.theme.StockLowBg
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatRupiah(amount: Double): String {
  val formatter = NumberFormat.getNumberInstance(Locale("in", "ID"))
  formatter.maximumFractionDigits = 0
  return "Rp ${formatter.format(amount)}"
}

fun formatTimestamp(millis: Long): String {
  val sdf = SimpleDateFormat("dd MMM HH:mm", Locale("in", "ID"))
  return sdf.format(Date(millis))
}

@Composable
fun OdooProductImage(
  imageBase64: String?,
  contentDescription: String,
  modifier: Modifier = Modifier,
) {
  val bitmap: ImageBitmap? =
    remember(imageBase64) {
      if (imageBase64.isNullOrBlank()) {
        null
      } else {
        runCatching {
          val bytes = Base64.decode(imageBase64, Base64.DEFAULT)
          BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
      }
    }

  if (bitmap != null) {
    Image(
      bitmap = bitmap,
      contentDescription = contentDescription,
      contentScale = ContentScale.Crop,
      modifier = modifier,
    )
  } else {
    Box(
      modifier =
        modifier.background(
          MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Outlined.Inventory2,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
        modifier = Modifier.size(34.dp),
      )
    }
  }
}

@Composable
fun LiveStockPill(freeQty: Double, uomName: String, modifier: Modifier = Modifier) {
  val (bg, fg, label) =
    when {
      freeQty <= 0.0 -> Triple(StockEmptyBg, StockEmptyRed, "Stok Habis")
      freeQty <= 5.0 ->
        Triple(StockLowBg, StockLowAmber, "Menipis: ${freeQty.toInt()} $uomName")
      else -> Triple(StockAvailableBg, StockAvailableGreen, "Stok: ${freeQty.toInt()} $uomName")
    }

  Surface(
    color = bg,
    shape = RoundedCornerShape(50),
    modifier = modifier,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
      Box(
        modifier =
          Modifier.size(6.dp)
            .clip(CircleShape)
            .background(fg)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun OdooConnectionStatusChip(
  isConnected: Boolean,
  isSyncing: Boolean,
  serverUrl: String,
  modifier: Modifier = Modifier,
) {
  val bg =
    if (isConnected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
  val fg =
    if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

  Surface(
    color = bg,
    shape = RoundedCornerShape(50),
    modifier = modifier,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
      Icon(
        imageVector =
          when {
            isSyncing -> Icons.Rounded.Sync
            isConnected -> Icons.Rounded.CloudDone
            else -> Icons.Rounded.CloudOff
          },
        contentDescription = if (isConnected) "Terhubung ke Odoo 20" else "Belum terhubung",
        tint = fg,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text =
          if (isConnected) {
            val host = serverUrl.removePrefix("https://").removePrefix("http://").substringBefore("/")
            if (isSyncing) "Sinkronisasi Odoo 20..." else "Odoo Live • $host"
          } else {
            "Odoo Belum Terhubung"
          },
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun OrderProgressStepper(deliveryStatus: String, paymentStatus: String, modifier: Modifier = Modifier) {
  val activeStep =
    when {
      deliveryStatus.contains("Selesai", ignoreCase = true) -> 3
      deliveryStatus.contains("Dikirim", ignoreCase = true) -> 2
      paymentStatus == "settlement" || paymentStatus == "capture" ||
        deliveryStatus.contains("Diproses", ignoreCase = true) -> 1
      else -> 0
    }
  val steps = listOf("Tagihan", "Dibayar", "Dikemas", "Selesai")

  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    steps.forEachIndexed { index, label ->
      val reached = index <= activeStep
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f),
      ) {
        Box(
          modifier =
            Modifier.size(22.dp)
              .clip(CircleShape)
              .background(
                if (reached) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
              ),
          contentAlignment = Alignment.Center,
        ) {
          if (reached) {
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = label,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(14.dp),
            )
          } else {
            Text(
              text = "${index + 1}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color =
            if (reached) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = if (reached) FontWeight.SemiBold else FontWeight.Normal,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
fun QrisMatrixPreview(qrString: String, modifier: Modifier = Modifier) {
  Surface(
    color = Color.White,
    shape = RoundedCornerShape(16.dp),
    modifier =
      modifier
        .size(180.dp)
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        .padding(14.dp),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val gridCount = 21
      val cell = size.width / gridCount
      val hashBytes = qrString.toByteArray()

      fun drawFinder(startX: Int, startY: Int) {
        for (y in 0 until 7) {
          for (x in 0 until 7) {
            val isBorder = x == 0 || x == 6 || y == 0 || y == 6
            val isInner = x in 2..4 && y in 2..4
            if (isBorder || isInner) {
              drawRect(
                color = Color(0xFF111827),
                topLeft = Offset((startX + x) * cell, (startY + y) * cell),
                size = Size(cell, cell),
              )
            }
          }
        }
      }

      drawFinder(0, 0)
      drawFinder(gridCount - 7, 0)
      drawFinder(0, gridCount - 7)

      for (y in 0 until gridCount) {
        for (x in 0 until gridCount) {
          val inFinder =
            (x < 8 && y < 8) ||
              (x >= gridCount - 8 && y < 8) ||
              (x < 8 && y >= gridCount - 8)
          if (!inFinder) {
            val byteVal = hashBytes[(x * 31 + y * 17) % hashBytes.size].toInt()
            if ((byteVal + x + y) % 2 == 0) {
              drawRect(
                color = Color(0xFF111827),
                topLeft = Offset(x * cell, y * cell),
                size = Size(cell, cell),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun MonoCodeText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
  Text(
    text = text,
    fontFamily = JetBrainsMonoFamily,
    style = MaterialTheme.typography.labelSmall,
    color = color,
    modifier = modifier,
  )
}
