package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
  @PrimaryKey val id: Int,
  val name: String,
  val sku: String,
  val barcode: String,
  val categoryName: String,
  val price: Double,
  val qtyAvailable: Double,
  val freeQty: Double,
  val virtualAvailable: Double,
  val uomName: String,
  val description: String,
  val imageBase64: String?,
  val warehouseSummary: String,
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "cart_items")
data class CartItemEntity(
  @PrimaryKey val productId: Int,
  val name: String,
  val sku: String,
  val unitPrice: Double,
  val quantity: Int,
  val availableStock: Double,
  val imageBase64: String?,
  val odooOrderLineId: Int? = null,
)

@Entity(tableName = "orders")
data class OrderEntity(
  @PrimaryKey(autoGenerate = true) val localId: Long = 0L,
  val odooOrderId: Int? = null,
  val orderNumber: String,
  val partnerName: String,
  val shippingAddress: String,
  val subtotal: Double,
  val taxAmount: Double,
  val totalAmount: Double,
  val odooState: String,
  val paymentStatus: String,
  val paymentMethod: String,
  val paymentRef: String,
  val vaNumber: String? = null,
  val qrString: String? = null,
  val paymentActionUrl: String? = null,
  val paymentExpiry: String? = null,
  val deliveryStatus: String,
  val itemSummary: String,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "stock_movements")
data class StockMovementEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val productId: Int,
  val productName: String,
  val sku: String,
  val deltaQty: Double,
  val newFreeQty: Double,
  val reason: String,
  val syncedToOdoo: Boolean,
  val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "notification_logs")
data class NotificationLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val orderNumber: String,
  val title: String,
  val message: String,
  val statusTag: String,
  val isRead: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
)
