package com.example.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.OrderEntity
import com.example.data.local.ProductEntity
import com.example.data.local.StockMovementEntity
import com.example.data.remote.LocalPaymentChannel
import com.example.data.repository.CommerceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private data class LocalControlState(
  val searchQuery: String = "",
  val selectedCategory: String = "Semua",
  val stockFilter: StockFilter = StockFilter.ALL,
  val selectedProduct: ProductEntity? = null,
  val selectedPaymentChannel: LocalPaymentChannel = LocalPaymentChannel.QRIS,
  val shippingAddress: String = "",
  val syncState: SyncUiState = SyncUiState.Idle,
  val authState: AuthUiState = AuthUiState.Idle,
  val checkoutState: CheckoutUiState = CheckoutUiState.Idle,
  val lastSyncTimestamp: Long? = null,
  val bannerMessage: String? = null,
)

class CommerceViewModel(private val repository: CommerceRepository) : ViewModel() {
  private val controlState = MutableStateFlow(LocalControlState())

  private val roomCombinedFlow =
    combine(
      repository.sessionFlow,
      repository.productsFlow,
      repository.cartItemsFlow,
      repository.ordersFlow,
      repository.stockMovementsFlow,
    ) { session, products, cart, orders, movements ->
      RoomBundle(session, products, cart, orders, movements)
    }

  private val notificationBundleFlow =
    combine(repository.notificationsFlow, repository.unreadNotificationsFlow) { logs, unread ->
      logs to unread
    }

  val uiState: StateFlow<CommerceUiState> =
    combine(roomCombinedFlow, notificationBundleFlow, controlState) { room, notif, ctrl ->
        CommerceUiState(
          session = room.session,
          products = room.products,
          cartItems = room.cart,
          orders = room.orders,
          stockMovements = room.movements,
          notifications = notif.first,
          unreadNotificationCount = notif.second,
          searchQuery = ctrl.searchQuery,
          selectedCategory = ctrl.selectedCategory,
          stockFilter = ctrl.stockFilter,
          selectedProduct = ctrl.selectedProduct,
          selectedPaymentChannel = ctrl.selectedPaymentChannel,
          shippingAddress = ctrl.shippingAddress,
          syncState = ctrl.syncState,
          authState = ctrl.authState,
          checkoutState = ctrl.checkoutState,
          isPaymentGatewayConfigured = repository.isPaymentGatewayConfigured(),
          isOdooApiKeyConfigured = repository.isOdooApiKeyConfigured(),
          lastSyncTimestamp = ctrl.lastSyncTimestamp,
          bannerMessage = ctrl.bannerMessage,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue =
          CommerceUiState(
            isPaymentGatewayConfigured = repository.isPaymentGatewayConfigured(),
            isOdooApiKeyConfigured = repository.isOdooApiKeyConfigured(),
          ),
      )

  init {
    startRealtimeSyncLoop()
  }

  private fun startRealtimeSyncLoop() {
    viewModelScope.launch {
      while (isActive) {
        val current = uiState.value
        if (current.session.isAuthenticated && current.session.autoSyncEnabled) {
          repository.syncCatalogAndStock()
          repository.syncOrdersWithOdooAndNotify()
          controlState.update { it.copy(lastSyncTimestamp = System.currentTimeMillis()) }
        }
        delay(12_000L)
      }
    }
  }

  fun updateSearchQuery(query: String) {
    controlState.update { it.copy(searchQuery = query) }
  }

  fun selectCategory(category: String) {
    controlState.update { it.copy(selectedCategory = category) }
  }

  fun selectStockFilter(filter: StockFilter) {
    controlState.update { it.copy(stockFilter = filter) }
  }

  fun openProductDetail(product: ProductEntity?) {
    controlState.update { it.copy(selectedProduct = product) }
  }

  fun selectPaymentChannel(channel: LocalPaymentChannel) {
    controlState.update { it.copy(selectedPaymentChannel = channel) }
  }

  fun updateShippingAddress(address: String) {
    controlState.update { it.copy(shippingAddress = address) }
  }

  fun clearBannerMessage() {
    controlState.update { it.copy(bannerMessage = null) }
  }

  fun resetCheckoutState() {
    controlState.update { it.copy(checkoutState = CheckoutUiState.Idle) }
  }

  fun signInOdoo(baseUrl: String, database: String, login: String, password: String) {
    if (baseUrl.isBlank() || database.isBlank() || login.isBlank() || password.isBlank()) {
      controlState.update {
        it.copy(authState = AuthUiState.Error("Semua kolom koneksi Odoo 20 wajib diisi."))
      }
      return
    }
    viewModelScope.launch {
      controlState.update { it.copy(authState = AuthUiState.Loading) }
      repository
        .signInOdoo(baseUrl, database, login, password)
        .onSuccess {
          controlState.update {
            it.copy(
              authState = AuthUiState.Success(login),
              bannerMessage = "Terhubung ke server Odoo 20 Community.",
            )
          }
          refreshAllRealtime()
        }
        .onFailure { err ->
          controlState.update {
            it.copy(
              authState = AuthUiState.Error(err.message ?: "Gagal masuk ke Odoo 20 Community.")
            )
          }
        }
    }
  }

  fun signOutOdoo() {
    viewModelScope.launch {
      repository.signOutOdoo()
      controlState.update {
        it.copy(
          authState = AuthUiState.Idle,
          bannerMessage = "Sesi Odoo 20 telah keluar.",
        )
      }
    }
  }

  fun refreshAllRealtime() {
    viewModelScope.launch {
      controlState.update { it.copy(syncState = SyncUiState.Syncing) }
      val stockRes = repository.syncCatalogAndStock()
      val orderRes = repository.syncOrdersWithOdooAndNotify()
      if (stockRes.isSuccess) {
        val count = stockRes.getOrDefault(0)
        val orderCount = orderRes.getOrDefault(0)
        controlState.update {
          it.copy(
            syncState =
              SyncUiState.Success("Sinkronisasi real-time: $count produk & $orderCount pesanan"),
            lastSyncTimestamp = System.currentTimeMillis(),
            bannerMessage = "Stok inventaris & status pesanan diperbarui.",
          )
        }
      } else {
        val errorMsg = stockRes.exceptionOrNull()?.message ?: "Gagal sinkronisasi Odoo 20"
        controlState.update {
          it.copy(
            syncState = SyncUiState.Error(errorMsg),
            bannerMessage = errorMsg,
          )
        }
      }
    }
  }

  fun addToCart(product: ProductEntity, qty: Int = 1) {
    viewModelScope.launch {
      repository
        .addToCart(product, qty)
        .onSuccess {
          controlState.update {
            it.copy(
              selectedProduct = null,
              bannerMessage = "${product.name} ditambahkan ke keranjang Odoo.",
            )
          }
        }
        .onFailure { err ->
          controlState.update { it.copy(bannerMessage = err.message) }
        }
    }
  }

  fun updateCartQty(productId: Int, newQty: Int) {
    viewModelScope.launch {
      repository.updateCartQuantity(productId, newQty).onFailure { err ->
        controlState.update { it.copy(bannerMessage = err.message) }
      }
    }
  }

  fun removeFromCart(productId: Int) {
    viewModelScope.launch { repository.removeFromCart(productId) }
  }

  fun adjustInventoryStock(product: ProductEntity, newQty: Double, reason: String) {
    viewModelScope.launch {
      repository
        .adjustInventoryStock(product, newQty, reason)
        .onSuccess {
          controlState.update {
            it.copy(bannerMessage = "Stok ${product.name} diperbarui ke ${newQty.toInt()} unit di Odoo.")
          }
        }
        .onFailure { err ->
          controlState.update {
            it.copy(bannerMessage = err.message ?: "Gagal memperbarui stok Odoo.")
          }
        }
    }
  }

  fun startLocalGatewayCheckout() {
    val state = uiState.value
    viewModelScope.launch {
      controlState.update { it.copy(checkoutState = CheckoutUiState.Processing) }
      repository
        .checkoutWithLocalGateway(
          shippingAddress = state.shippingAddress,
          channel = state.selectedPaymentChannel,
        )
        .onSuccess { order ->
          val nextState =
            if (order.paymentStatus == "settlement" || order.paymentStatus == "capture") {
              CheckoutUiState.Paid(order)
            } else {
              CheckoutUiState.AwaitingPayment(order)
            }
          controlState.update { it.copy(checkoutState = nextState) }
        }
        .onFailure { err ->
          controlState.update {
            it.copy(
              checkoutState =
                CheckoutUiState.Error(err.message ?: "Transaksi gateway pembayaran gagal.")
            )
          }
        }
    }
  }

  fun verifyOrderPaymentStatus(order: OrderEntity) {
    viewModelScope.launch {
      controlState.update { it.copy(syncState = SyncUiState.Syncing) }
      repository
        .checkPaymentAndConfirmOrder(order)
        .onSuccess { response ->
          val status = response.transactionStatus ?: "pending"
          if (status == "settlement" || status == "capture") {
            controlState.update {
              it.copy(
                syncState = SyncUiState.Success("Pembayaran ${order.orderNumber} lunas!"),
                checkoutState =
                  CheckoutUiState.Paid(
                    order.copy(paymentStatus = status, deliveryStatus = "Diproses Gudang Odoo")
                  ),
                bannerMessage = "Pembayaran terverifikasi! Pesanan diteruskan ke Odoo.",
              )
            }
          } else {
            controlState.update {
              it.copy(
                syncState = SyncUiState.Idle,
                bannerMessage = "Status gateway saat ini: $status",
              )
            }
          }
        }
        .onFailure { err ->
          controlState.update {
            it.copy(
              syncState = SyncUiState.Error(err.message ?: "Gagal cek status pembayaran"),
              bannerMessage = err.message,
            )
          }
        }
    }
  }

  fun setNotificationsEnabled(enabled: Boolean) {
    viewModelScope.launch { repository.setNotificationsEnabled(enabled) }
  }

  fun setAutoSyncEnabled(enabled: Boolean) {
    viewModelScope.launch { repository.setAutoSyncEnabled(enabled) }
  }

  fun markAllNotificationsRead() {
    viewModelScope.launch { repository.markAllNotificationsRead() }
  }

  private data class RoomBundle(
    val session: com.example.data.local.OdooSessionConfig,
    val products: List<ProductEntity>,
    val cart: List<com.example.data.local.CartItemEntity>,
    val orders: List<OrderEntity>,
    val movements: List<StockMovementEntity>,
  )
}

class CommerceViewModelFactory(private val repository: CommerceRepository) :
  ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(CommerceViewModel::class.java)) {
      return CommerceViewModel(repository) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
