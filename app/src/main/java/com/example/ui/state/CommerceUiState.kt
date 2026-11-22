package com.example.ui.state

import com.example.data.local.CartItemEntity
import com.example.data.local.NotificationLogEntity
import com.example.data.local.OdooSessionConfig
import com.example.data.local.OrderEntity
import com.example.data.local.ProductEntity
import com.example.data.local.StockMovementEntity
import com.example.data.remote.LocalPaymentChannel

sealed interface SyncUiState {
  data object Idle : SyncUiState

  data object Syncing : SyncUiState

  data class Success(val message: String, val timestamp: Long = System.currentTimeMillis()) :
    SyncUiState

  data class Error(val message: String) : SyncUiState
}

sealed interface AuthUiState {
  data object Idle : AuthUiState

  data object Loading : AuthUiState

  data class Success(val userName: String) : AuthUiState

  data class Error(val message: String) : AuthUiState
}

sealed interface CheckoutUiState {
  data object Idle : CheckoutUiState

  data object Processing : CheckoutUiState

  data class AwaitingPayment(val order: OrderEntity) : CheckoutUiState

  data class Paid(val order: OrderEntity) : CheckoutUiState

  data class Error(val message: String) : CheckoutUiState
}

enum class StockFilter(val label: String) {
  ALL("Semua Stok"),
  IN_STOCK("Tersedia"),
  LOW_STOCK("Menipis"),
  OUT_OF_STOCK("Habis"),
}

data class CommerceUiState(
  val session: OdooSessionConfig =
    OdooSessionConfig(
      baseUrl = "",
      database = "odoo20_community",
      userLogin = "",
      userName = "",
      uid = 0,
      partnerId = 0,
      activeDraftOrderId = 0,
      sessionCookie = "",
      notificationsEnabled = true,
      autoSyncEnabled = true,
    ),
  val products: List<ProductEntity> = emptyList(),
  val cartItems: List<CartItemEntity> = emptyList(),
  val orders: List<OrderEntity> = emptyList(),
  val stockMovements: List<StockMovementEntity> = emptyList(),
  val notifications: List<NotificationLogEntity> = emptyList(),
  val unreadNotificationCount: Int = 0,
  val searchQuery: String = "",
  val selectedCategory: String = "Semua",
  val stockFilter: StockFilter = StockFilter.ALL,
  val selectedProduct: ProductEntity? = null,
  val selectedPaymentChannel: LocalPaymentChannel = LocalPaymentChannel.QRIS,
  val shippingAddress: String = "",
  val syncState: SyncUiState = SyncUiState.Idle,
  val authState: AuthUiState = AuthUiState.Idle,
  val checkoutState: CheckoutUiState = CheckoutUiState.Idle,
  val isPaymentGatewayConfigured: Boolean = false,
  val isOdooApiKeyConfigured: Boolean = false,
  val lastSyncTimestamp: Long? = null,
  val bannerMessage: String? = null,
) {
  val categories: List<String>
    get() = listOf("Semua") + products.map { it.categoryName }.distinct().sorted()

  val filteredProducts: List<ProductEntity>
    get() =
      products.filter { product ->
        val matchesQuery =
          searchQuery.isBlank() ||
            product.name.contains(searchQuery, ignoreCase = true) ||
            product.sku.contains(searchQuery, ignoreCase = true) ||
            product.barcode.contains(searchQuery, ignoreCase = true)
        val matchesCategory =
          selectedCategory == "Semua" || product.categoryName == selectedCategory
        val matchesStock =
          when (stockFilter) {
            StockFilter.ALL -> true
            StockFilter.IN_STOCK -> product.freeQty > 5.0
            StockFilter.LOW_STOCK -> product.freeQty > 0.0 && product.freeQty <= 5.0
            StockFilter.OUT_OF_STOCK -> product.freeQty <= 0.0
          }
        matchesQuery && matchesCategory && matchesStock
      }

  val totalCartItemsCount: Int
    get() = cartItems.sumOf { it.quantity }

  val cartSubtotal: Double
    get() = cartItems.sumOf { it.unitPrice * it.quantity }

  val cartTax: Double
    get() = cartSubtotal * 0.11

  val cartGrandTotal: Double
    get() = cartSubtotal + cartTax
}
