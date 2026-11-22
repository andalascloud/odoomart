package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
  @Query("SELECT * FROM products ORDER BY name ASC")
  fun observeProducts(): Flow<List<ProductEntity>>

  @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
  suspend fun getProductById(productId: Int): ProductEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertProducts(products: List<ProductEntity>)

  @Query("UPDATE products SET qtyAvailable = :qtyAvailable, freeQty = :freeQty, virtualAvailable = :virtualAvailable, updatedAt = :updatedAt WHERE id = :productId")
  suspend fun updateProductStock(
    productId: Int,
    qtyAvailable: Double,
    freeQty: Double,
    virtualAvailable: Double,
    updatedAt: Long = System.currentTimeMillis(),
  )

  @Query("DELETE FROM products")
  suspend fun clearAll()
}

@Dao
interface CartDao {
  @Query("SELECT * FROM cart_items ORDER BY name ASC")
  fun observeCartItems(): Flow<List<CartItemEntity>>

  @Query("SELECT * FROM cart_items")
  suspend fun getCartItemsSnapshot(): List<CartItemEntity>

  @Query("SELECT * FROM cart_items WHERE productId = :productId LIMIT 1")
  suspend fun getCartItem(productId: Int): CartItemEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCartItem(item: CartItemEntity)

  @Query("UPDATE cart_items SET quantity = :quantity, availableStock = :availableStock, odooOrderLineId = :odooOrderLineId WHERE productId = :productId")
  suspend fun updateQuantityAndStock(
    productId: Int,
    quantity: Int,
    availableStock: Double,
    odooOrderLineId: Int?,
  )

  @Query("DELETE FROM cart_items WHERE productId = :productId")
  suspend fun deleteCartItem(productId: Int)

  @Query("DELETE FROM cart_items")
  suspend fun clearCart()
}

@Dao
interface OrderDao {
  @Query("SELECT * FROM orders ORDER BY createdAt DESC")
  fun observeOrders(): Flow<List<OrderEntity>>

  @Query("SELECT * FROM orders ORDER BY createdAt DESC")
  suspend fun getOrdersSnapshot(): List<OrderEntity>

  @Query("SELECT * FROM orders WHERE orderNumber = :orderNumber LIMIT 1")
  suspend fun getOrderByNumber(orderNumber: String): OrderEntity?

  @Query("SELECT * FROM orders WHERE odooOrderId = :odooOrderId LIMIT 1")
  suspend fun getOrderByOdooId(odooOrderId: Int): OrderEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertOrder(order: OrderEntity): Long

  @Query("UPDATE orders SET odooState = :odooState, paymentStatus = :paymentStatus, deliveryStatus = :deliveryStatus, updatedAt = :updatedAt WHERE orderNumber = :orderNumber")
  suspend fun updateOrderStatus(
    orderNumber: String,
    odooState: String,
    paymentStatus: String,
    deliveryStatus: String,
    updatedAt: Long = System.currentTimeMillis(),
  )
}

@Dao
interface StockMovementDao {
  @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC LIMIT 50")
  fun observeRecentMovements(): Flow<List<StockMovementEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMovement(movement: StockMovementEntity)
}

@Dao
interface NotificationDao {
  @Query("SELECT * FROM notification_logs ORDER BY createdAt DESC")
  fun observeNotifications(): Flow<List<NotificationLogEntity>>

  @Query("SELECT COUNT(*) FROM notification_logs WHERE isRead = 0")
  fun observeUnreadCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertNotification(notification: NotificationLogEntity)

  @Query("UPDATE notification_logs SET isRead = 1 WHERE isRead = 0")
  suspend fun markAllRead()
}
