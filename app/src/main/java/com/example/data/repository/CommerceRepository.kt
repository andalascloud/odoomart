package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.CartItemEntity
import com.example.data.local.NotificationLogEntity
import com.example.data.local.OdooSessionConfig
import com.example.data.local.OrderEntity
import com.example.data.local.ProductEntity
import com.example.data.local.SessionPreferences
import com.example.data.local.StockMovementEntity
import com.example.data.remote.LocalPaymentChannel
import com.example.data.remote.OdooApiService
import com.example.data.remote.PaymentChargeResponse
import com.example.data.remote.PaymentGatewayService
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.flow.Flow

class CommerceRepository(
  private val database: AppDatabase,
  private val sessionPreferences: SessionPreferences,
  private val odooApiService: OdooApiService,
  private val paymentGatewayService: PaymentGatewayService,
  private val notificationHelper: NotificationHelper,
) {
  private val productDao = database.productDao()
  private val cartDao = database.cartDao()
  private val orderDao = database.orderDao()
  private val stockMovementDao = database.stockMovementDao()
  private val notificationDao = database.notificationDao()

  val sessionFlow: Flow<OdooSessionConfig> = sessionPreferences.sessionFlow
  val productsFlow: Flow<List<ProductEntity>> = productDao.observeProducts()
  val cartItemsFlow: Flow<List<CartItemEntity>> = cartDao.observeCartItems()
  val ordersFlow: Flow<List<OrderEntity>> = orderDao.observeOrders()
  val stockMovementsFlow: Flow<List<StockMovementEntity>> = stockMovementDao.observeRecentMovements()
  val notificationsFlow: Flow<List<NotificationLogEntity>> = notificationDao.observeNotifications()
  val unreadNotificationsFlow: Flow<Int> = notificationDao.observeUnreadCount()

  fun isPaymentGatewayConfigured(): Boolean = paymentGatewayService.isGatewayConfigured()

  fun isOdooApiKeyConfigured(): Boolean {
    val key = BuildConfig.ODOO_API_KEY
    return key.isNotBlank() && !key.startsWith("YOUR_")
  }

  suspend fun signInOdoo(
    baseUrl: String,
    databaseName: String,
    login: String,
    password: String,
  ): Result<Unit> {
    val authResult = odooApiService.authenticate(baseUrl, databaseName, login, password)
    return authResult.map { auth ->
      sessionPreferences.saveAuthenticatedSession(
        baseUrl = baseUrl,
        database = databaseName,
        userLogin = login,
        userName = auth.userName,
        uid = auth.uid,
        partnerId = auth.partnerId,
        sessionCookie = auth.sessionCookie,
      )
    }
  }

  suspend fun signOutOdoo() {
    sessionPreferences.clearSession()
  }

  suspend fun setNotificationsEnabled(enabled: Boolean) {
    sessionPreferences.setNotificationsEnabled(enabled)
  }

  suspend fun setAutoSyncEnabled(enabled: Boolean) {
    sessionPreferences.setAutoSyncEnabled(enabled)
  }

  suspend fun syncCatalogAndStock(): Result<Int> {
    val session = sessionPreferences.getCurrentSession()
    if (!session.isAuthenticated) {
      return Result.failure(IllegalStateException("Belum masuk ke server Odoo 20 Community."))
    }
    val credential = resolveOdooCredential()
    val remoteResult =
      odooApiService.fetchProductsWithStock(
        baseUrl = session.baseUrl,
        database = session.database,
        uid = session.uid,
        credential = credential,
        sessionCookie = session.sessionCookie,
      )
    return remoteResult.map { remoteList ->
      val now = System.currentTimeMillis()
      val entities =
        remoteList.map { remote ->
          ProductEntity(
            id = remote.id,
            name = remote.name,
            sku = remote.sku,
            barcode = remote.barcode,
            categoryName = remote.categoryName,
            price = remote.price,
            qtyAvailable = remote.qtyAvailable,
            freeQty = remote.freeQty,
            virtualAvailable = remote.virtualAvailable,
            uomName = remote.uomName,
            description = remote.description,
            imageBase64 = remote.imageBase64,
            warehouseSummary = remote.warehouseSummary,
            updatedAt = now,
          )
        }
      productDao.upsertProducts(entities)

      // Update real-time availableStock on existing cart items
      val currentCart = cartDao.getCartItemsSnapshot()
      val productMap = entities.associateBy { it.id }
      for (cartItem in currentCart) {
        val fresh = productMap[cartItem.productId] ?: continue
        if (fresh.freeQty != cartItem.availableStock) {
          val clampedQty =
            if (fresh.freeQty <= 0.0) 0 else cartItem.quantity.coerceAtMost(fresh.freeQty.toInt())
          if (clampedQty <= 0) {
            cartDao.deleteCartItem(cartItem.productId)
          } else {
            cartDao.updateQuantityAndStock(
              productId = cartItem.productId,
              quantity = clampedQty,
              availableStock = fresh.freeQty,
              odooOrderLineId = cartItem.odooOrderLineId,
            )
          }
        }
      }
      entities.size
    }
  }

  suspend fun addToCart(product: ProductEntity, qtyToAdd: Int = 1): Result<Unit> {
    if (product.freeQty <= 0.0) {
      return Result.failure(IllegalStateException("Stok ${product.name} sedang habis."))
    }
    val existing = cartDao.getCartItem(product.id)
    val nextQty = (existing?.quantity ?: 0) + qtyToAdd
    if (nextQty > product.freeQty.toInt()) {
      return Result.failure(
        IllegalStateException("Maksimal pembelian sesuai stok tersedia (${product.freeQty.toInt()} ${product.uomName}).")
      )
    }
    cartDao.upsertCartItem(
      CartItemEntity(
        productId = product.id,
        name = product.name,
        sku = product.sku,
        unitPrice = product.price,
        quantity = nextQty,
        availableStock = product.freeQty,
        imageBase64 = product.imageBase64,
        odooOrderLineId = existing?.odooOrderLineId,
      )
    )
    syncCartToOdooBestEffort()
    return Result.success(Unit)
  }

  suspend fun updateCartQuantity(productId: Int, newQty: Int): Result<Unit> {
    if (newQty <= 0) {
      cartDao.deleteCartItem(productId)
      syncCartToOdooBestEffort()
      return Result.success(Unit)
    }
    val freshProduct = productDao.getProductById(productId)
    val item = cartDao.getCartItem(productId) ?: return Result.success(Unit)
    val maxStock = (freshProduct?.freeQty ?: item.availableStock).toInt()
    if (newQty > maxStock) {
      return Result.failure(IllegalStateException("Stok tersedia hanya $maxStock unit."))
    }
    cartDao.updateQuantityAndStock(
      productId = productId,
      quantity = newQty,
      availableStock = freshProduct?.freeQty ?: item.availableStock,
      odooOrderLineId = item.odooOrderLineId,
    )
    syncCartToOdooBestEffort()
    return Result.success(Unit)
  }

  suspend fun removeFromCart(productId: Int) {
    cartDao.deleteCartItem(productId)
    syncCartToOdooBestEffort()
  }

  private suspend fun syncCartToOdooBestEffort() {
    val session = sessionPreferences.getCurrentSession()
    if (!session.isAuthenticated || session.partnerId <= 0) return
    val cartLines = cartDao.getCartItemsSnapshot().map { it.productId to it.quantity }
    if (cartLines.isEmpty() && session.activeDraftOrderId <= 0) return

    odooApiService
      .syncDraftCartOrder(
        baseUrl = session.baseUrl,
        database = session.database,
        uid = session.uid,
        credential = resolveOdooCredential(),
        sessionCookie = session.sessionCookie,
        partnerId = session.partnerId,
        existingOrderId = session.activeDraftOrderId,
        lines = cartLines,
      )
      .onSuccess { orderId ->
        if (orderId > 0 && orderId != session.activeDraftOrderId) {
          sessionPreferences.saveActiveDraftOrderId(orderId)
        }
      }
  }

  suspend fun adjustInventoryStock(
    product: ProductEntity,
    newQuantity: Double,
    reason: String,
  ): Result<Unit> {
    val session = sessionPreferences.getCurrentSession()
    if (!session.isAuthenticated) {
      return Result.failure(IllegalStateException("Masuk ke Odoo 20 untuk memperbarui inventaris."))
    }
    val result =
      odooApiService.adjustInventoryQuantity(
        baseUrl = session.baseUrl,
        database = session.database,
        uid = session.uid,
        credential = resolveOdooCredential(),
        sessionCookie = session.sessionCookie,
        productId = product.id,
        newQuantity = newQuantity,
      )
    return result.map {
      val delta = newQuantity - product.freeQty
      productDao.updateProductStock(
        productId = product.id,
        qtyAvailable = newQuantity,
        freeQty = newQuantity,
        virtualAvailable = newQuantity,
      )
      stockMovementDao.insertMovement(
        StockMovementEntity(
          productId = product.id,
          productName = product.name,
          sku = product.sku,
          deltaQty = delta,
          newFreeQty = newQuantity,
          reason = reason.ifBlank { "Penyesuaian stok real-time Odoo 20" },
          syncedToOdoo = true,
        )
      )
    }
  }

  suspend fun checkoutWithLocalGateway(
    shippingAddress: String,
    channel: LocalPaymentChannel,
  ): Result<OrderEntity> {
    val session = sessionPreferences.getCurrentSession()
    val cartItems = cartDao.getCartItemsSnapshot()
    if (cartItems.isEmpty()) {
      return Result.failure(IllegalStateException("Keranjang belanja masih kosong."))
    }
    if (shippingAddress.isBlank()) {
      return Result.failure(IllegalStateException("Alamat pengiriman wajib diisi."))
    }

    // Verify real-time stock with Odoo if connected
    if (session.isAuthenticated) {
      syncCatalogAndStock()
      val refreshedCart = cartDao.getCartItemsSnapshot()
      for (item in refreshedCart) {
        val latest = productDao.getProductById(item.productId)
        if (latest == null || latest.freeQty < item.quantity) {
          return Result.failure(
            IllegalStateException("Stok ${item.name} berubah dan tidak mencukupi.")
          )
        }
      }
    }

    val subtotal = cartItems.sumOf { it.unitPrice * it.quantity }
    val taxAmount = subtotal * 0.11
    val totalAmount = subtotal + taxAmount
    val orderNumber = "SO-${System.currentTimeMillis().toString().takeLast(7)}"

    // Ensure draft SO exists in Odoo if authenticated
    var odooOrderId: Int? = session.activeDraftOrderId.takeIf { it > 0 }
    if (session.isAuthenticated && session.partnerId > 0) {
      val syncRes =
        odooApiService.syncDraftCartOrder(
          baseUrl = session.baseUrl,
          database = session.database,
          uid = session.uid,
          credential = resolveOdooCredential(),
          sessionCookie = session.sessionCookie,
          partnerId = session.partnerId,
          existingOrderId = odooOrderId ?: 0,
          lines = cartItems.map { it.productId to it.quantity },
        )
      odooOrderId = syncRes.getOrNull()?.takeIf { it > 0 } ?: odooOrderId
    }

    // Charge via real Indonesian Payment Gateway API
    val chargeRes =
      paymentGatewayService.createCharge(
        orderNumber = orderNumber,
        grossAmountIdr = totalAmount,
        customerName = session.userName.ifBlank { "Pelanggan OdooMart" },
        customerEmail = session.userLogin.ifBlank { "customer@odoomart.id" },
        channel = channel,
      )

    return chargeRes.map { payment ->
      val vaNumber =
        payment.vaNumbers?.firstOrNull()?.vaNumber
          ?: payment.permataVaNumber
          ?: if (!payment.billKey.isNullOrBlank()) "${payment.billerCode.orEmpty()} - ${payment.billKey}" else null
      val actionUrl = payment.actions?.firstOrNull { !it.url.isNullOrBlank() }?.url
      val paymentState = payment.transactionStatus ?: "pending"

      val order =
        OrderEntity(
          odooOrderId = odooOrderId,
          orderNumber = orderNumber,
          partnerName = session.userName.ifBlank { "Pelanggan OdooMart" },
          shippingAddress = shippingAddress.trim(),
          subtotal = subtotal,
          taxAmount = taxAmount,
          totalAmount = totalAmount,
          odooState = if (paymentState == "settlement" || paymentState == "capture") "sale" else "sent",
          paymentStatus = paymentState,
          paymentMethod = channel.displayName,
          paymentRef = payment.transactionId ?: orderNumber,
          vaNumber = vaNumber,
          qrString = payment.qrString,
          paymentActionUrl = actionUrl,
          paymentExpiry = payment.expiryTime,
          deliveryStatus = if (paymentState == "settlement") "Diproses Gudang" else "Menunggu Pembayaran",
          itemSummary = cartItems.joinToString(", ") { "${it.name} (${it.quantity}x)" },
        )
      orderDao.upsertOrder(order)
      cartDao.clearCart()
      sessionPreferences.saveActiveDraftOrderId(0)

      emitOrderNotification(
        orderNumber = orderNumber,
        title = "Tagihan ${channel.displayName} Dibuat",
        message = "Pesanan $orderNumber menunggu pembayaran via ${channel.displayName}.",
        statusTag = "MENUNGGU BAYAR",
      )
      order
    }
  }

  suspend fun checkPaymentAndConfirmOrder(order: OrderEntity): Result<PaymentChargeResponse> {
    val statusResult = paymentGatewayService.checkTransactionStatus(order.orderNumber)
    statusResult.onSuccess { status ->
      val txStatus = status.transactionStatus ?: order.paymentStatus
      val isPaid = txStatus == "settlement" || txStatus == "capture"
      val newOdooState = if (isPaid) "sale" else order.odooState
      val newDeliveryStatus = if (isPaid) "Diproses Gudang Odoo" else order.deliveryStatus

      if (isPaid && order.paymentStatus != txStatus) {
        val session = sessionPreferences.getCurrentSession()
        if (session.isAuthenticated && order.odooOrderId != null && order.odooOrderId > 0) {
          odooApiService.confirmSaleOrder(
            baseUrl = session.baseUrl,
            database = session.database,
            uid = session.uid,
            credential = resolveOdooCredential(),
            sessionCookie = session.sessionCookie,
            orderId = order.odooOrderId,
            paymentReference = "${order.paymentMethod} (${order.paymentRef})",
          )
        }
        orderDao.updateOrderStatus(
          orderNumber = order.orderNumber,
          odooState = newOdooState,
          paymentStatus = txStatus,
          deliveryStatus = newDeliveryStatus,
        )
        emitOrderNotification(
          orderNumber = order.orderNumber,
          title = "Pembayaran Berhasil Diverifikasi",
          message = "Pesanan ${order.orderNumber} telah dibayar dan diteruskan ke gudang Odoo 20.",
          statusTag = "LUNAS",
        )
      } else if (txStatus != order.paymentStatus) {
        orderDao.updateOrderStatus(
          orderNumber = order.orderNumber,
          odooState = newOdooState,
          paymentStatus = txStatus,
          deliveryStatus = newDeliveryStatus,
        )
      }
    }
    return statusResult
  }

  suspend fun syncOrdersWithOdooAndNotify(): Result<Int> {
    val session = sessionPreferences.getCurrentSession()
    if (!session.isAuthenticated) {
      return Result.failure(IllegalStateException("Belum masuk ke server Odoo 20 Community."))
    }
    val remoteOrdersResult =
      odooApiService.fetchUserOrders(
        baseUrl = session.baseUrl,
        database = session.database,
        uid = session.uid,
        credential = resolveOdooCredential(),
        sessionCookie = session.sessionCookie,
        partnerId = session.partnerId,
      )

    return remoteOrdersResult.map { remoteOrders ->
      for (remote in remoteOrders) {
        val existing =
          orderDao.getOrderByOdooId(remote.odooOrderId)
            ?: orderDao.getOrderByNumber(remote.orderNumber)
        val mappedDelivery = mapOdooStateToDeliveryLabel(remote.state, remote.deliveryStatus)

        if (existing == null) {
          orderDao.upsertOrder(
            OrderEntity(
              odooOrderId = remote.odooOrderId,
              orderNumber = remote.orderNumber,
              partnerName = session.userName.ifBlank { "Pelanggan Odoo" },
              shippingAddress = "Alamat Odoo Partner",
              subtotal = remote.amountUntaxed,
              taxAmount = remote.amountTax,
              totalAmount = remote.amountTotal,
              odooState = remote.state,
              paymentStatus =
                if (remote.invoiceStatus == "invoiced" || remote.state == "sale" || remote.state == "done") {
                  "settlement"
                } else {
                  "pending"
                },
              paymentMethod = remote.clientOrderRef.ifBlank { "Odoo E-Commerce" },
              paymentRef = remote.clientOrderRef.ifBlank { remote.orderNumber },
              deliveryStatus = mappedDelivery,
              itemSummary = "Order Odoo #${remote.orderNumber}",
            )
          )
        } else if (existing.odooState != remote.state || existing.deliveryStatus != mappedDelivery) {
          orderDao.updateOrderStatus(
            orderNumber = existing.orderNumber,
            odooState = remote.state,
            paymentStatus = existing.paymentStatus,
            deliveryStatus = mappedDelivery,
          )
          emitOrderNotification(
            orderNumber = remote.orderNumber,
            title = "Pembaruan Pesanan ${remote.orderNumber}",
            message = "Status pesanan Anda kini: $mappedDelivery.",
            statusTag = mappedDelivery.uppercase(),
          )
        }
      }
      remoteOrders.size
    }
  }

  suspend fun markAllNotificationsRead() {
    notificationDao.markAllRead()
  }

  private suspend fun emitOrderNotification(
    orderNumber: String,
    title: String,
    message: String,
    statusTag: String,
  ) {
    notificationDao.insertNotification(
      NotificationLogEntity(
        orderNumber = orderNumber,
        title = title,
        message = message,
        statusTag = statusTag,
        isRead = false,
      )
    )
    val session = sessionPreferences.getCurrentSession()
    if (session.notificationsEnabled) {
      notificationHelper.showOrderStatusNotification(
        orderNumber = orderNumber,
        title = title,
        message = message,
      )
    }
  }

  private fun mapOdooStateToDeliveryLabel(state: String, deliveryStatus: String): String {
    return when {
      state == "cancel" -> "Dibatalkan"
      deliveryStatus == "full" || state == "done" -> "Selesai Dikirim"
      deliveryStatus == "partial" -> "Dikirim Sebagian"
      state == "sale" -> "Diproses Gudang Odoo"
      state == "sent" -> "Menunggu Pembayaran"
      else -> "Draft Keranjang"
    }
  }

  private fun resolveOdooCredential(): String {
    val apiKey = BuildConfig.ODOO_API_KEY
    return if (apiKey.isNotBlank() && !apiKey.startsWith("YOUR_")) apiKey else ""
  }
}
