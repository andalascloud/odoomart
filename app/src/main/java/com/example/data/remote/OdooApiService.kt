package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor

class OdooApiService {
  private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
  private val mapAdapter =
    moshi.adapter<Map<String, Any?>>(
      Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .writeTimeout(25, TimeUnit.SECONDS)
      .addInterceptor(
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
      )
      .build()

  suspend fun authenticate(
    baseUrl: String,
    database: String,
    login: String,
    password: String,
  ): Result<OdooAuthResult> = withContext(Dispatchers.IO) {
    runCatching {
      val cleanUrl = normalizeUrl(baseUrl)
      val payload =
        mapOf(
          "jsonrpc" to "2.0",
          "method" to "call",
          "params" to mapOf("db" to database, "login" to login, "password" to password),
          "id" to System.currentTimeMillis(),
        )
      val body = mapAdapter.toJson(payload).toRequestBody(jsonMediaType)
      val request =
        Request.Builder()
          .url("$cleanUrl/web/session/authenticate")
          .post(body)
          .header("Content-Type", "application/json")
          .build()

      client.newCall(request).execute().use { response ->
        val rawBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          throw IllegalStateException("HTTP ${response.code}: Gagal terhubung ke Odoo 20")
        }
        val parsed = mapAdapter.fromJson(rawBody) ?: throw IllegalStateException("Respons Odoo tidak valid")
        val errorObj = parsed["error"] as? Map<*, *>
        if (errorObj != null) {
          val dataObj = errorObj["data"] as? Map<*, *>
          val msg = dataObj?.get("message")?.toString() ?: errorObj["message"]?.toString()
          throw IllegalStateException(msg ?: "Otentikasi Odoo gagal")
        }
        val result = parsed["result"] as? Map<*, *>
          ?: throw IllegalStateException("Email atau kata sandi Odoo salah")
        val uid = OdooJsonParser.parseOdooInt(result["uid"], 0)
        if (uid <= 0) {
          throw IllegalStateException("Akun Odoo tidak valid atau sesi ditolak")
        }
        val partnerId = OdooJsonParser.parseOdooInt(result["partner_id"], 0)
        val userName = OdooJsonParser.parseOdooString(result["name"], login)
        val serverVersion = OdooJsonParser.parseOdooString(result["server_version"], "20.0")
        val cookies =
          response.headers("Set-Cookie").joinToString("; ") { cookie ->
            cookie.substringBefore(";")
          }

        OdooAuthResult(
          uid = uid,
          partnerId = partnerId,
          userName = userName,
          serverVersion = serverVersion,
          sessionCookie = cookies,
        )
      }
    }
  }

  suspend fun fetchProductsWithStock(
    baseUrl: String,
    database: String,
    uid: Int,
    credential: String,
    sessionCookie: String,
  ): Result<List<OdooRemoteProduct>> = withContext(Dispatchers.IO) {
    runCatching {
      val fields =
        listOf(
          "id",
          "name",
          "default_code",
          "barcode",
          "list_price",
          "qty_available",
          "free_qty",
          "virtual_available",
          "categ_id",
          "uom_id",
          "description_sale",
          "image_256",
        )
      val domain = listOf(listOf("sale_ok", "=", true), listOf("active", "=", true))
      val rawProducts =
        executeKw(
          baseUrl = baseUrl,
          database = database,
          uid = uid,
          credential = credential,
          sessionCookie = sessionCookie,
          model = "product.product",
          method = "search_read",
          args = listOf(domain),
          kwargs = mapOf("fields" to fields, "limit" to 100, "order" to "name asc"),
        ) as? List<*> ?: emptyList<Any>()

      val productIds =
        rawProducts.mapNotNull { (it as? Map<*, *>)?.get("id")?.let { id -> OdooJsonParser.parseOdooInt(id) } }

      val quantMap =
        if (productIds.isNotEmpty()) {
          fetchWarehouseSummaryByProduct(
            baseUrl = baseUrl,
            database = database,
            uid = uid,
            credential = credential,
            sessionCookie = sessionCookie,
            productIds = productIds,
          )
        } else {
          emptyMap()
        }

      rawProducts.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val id = OdooJsonParser.parseOdooInt(map["id"], 0)
        if (id <= 0) return@mapNotNull null
        OdooRemoteProduct(
          id = id,
          name = OdooJsonParser.parseOdooString(map["name"], "Produk #$id"),
          sku = OdooJsonParser.parseOdooString(map["default_code"], "SKU-$id"),
          barcode = OdooJsonParser.parseOdooString(map["barcode"], ""),
          categoryName = OdooJsonParser.parseMany2OneName(map["categ_id"], "Umum"),
          price = OdooJsonParser.parseOdooDouble(map["list_price"], 0.0),
          qtyAvailable = OdooJsonParser.parseOdooDouble(map["qty_available"], 0.0),
          freeQty = OdooJsonParser.parseOdooDouble(map["free_qty"], 0.0),
          virtualAvailable = OdooJsonParser.parseOdooDouble(map["virtual_available"], 0.0),
          uomName = OdooJsonParser.parseMany2OneName(map["uom_id"], "Unit"),
          description = OdooJsonParser.parseOdooString(map["description_sale"], ""),
          imageBase64 = OdooJsonParser.parseOdooString(map["image_256"], "").ifBlank { null },
          warehouseSummary = quantMap[id] ?: "Gudang Utama",
        )
      }
    }
  }

  private fun fetchWarehouseSummaryByProduct(
    baseUrl: String,
    database: String,
    uid: Int,
    credential: String,
    sessionCookie: String,
    productIds: List<Int>,
  ): Map<Int, String> {
    return runCatching {
      val domain =
        listOf(
          listOf("product_id", "in", productIds),
          listOf("location_id.usage", "=", "internal"),
        )
      val quants =
        executeKw(
          baseUrl = baseUrl,
          database = database,
          uid = uid,
          credential = credential,
          sessionCookie = sessionCookie,
          model = "stock.quant",
          method = "search_read",
          args = listOf(domain),
          kwargs = mapOf("fields" to listOf("product_id", "location_id", "quantity", "reserved_quantity")),
        ) as? List<*> ?: emptyList<Any>()

      val grouped = mutableMapOf<Int, MutableList<String>>()
      for (q in quants) {
        val qMap = q as? Map<*, *> ?: continue
        val prodList = qMap["product_id"] as? List<*> ?: continue
        val prodId = OdooJsonParser.parseOdooInt(prodList.firstOrNull(), 0)
        if (prodId <= 0) continue
        val locName = OdooJsonParser.parseMany2OneName(qMap["location_id"], "Gudang")
        val qty = OdooJsonParser.parseOdooDouble(qMap["quantity"], 0.0)
        val reserved = OdooJsonParser.parseOdooDouble(qMap["reserved_quantity"], 0.0)
        val available = (qty - reserved).coerceAtLeast(0.0)
        grouped.getOrPut(prodId) { mutableListOf() }.add("$locName: ${available.toInt()}")
      }
      grouped.mapValues { (_, entries) -> entries.joinToString(" • ") }
    }.getOrDefault(emptyMap())
  }

  suspend fun syncDraftCartOrder(
    baseUrl: String,
    database: String,
    uid: Int,
    credential: String,
    sessionCookie: String,
    partnerId: Int,
    existingOrderId: Int,
    lines: List<Pair<Int, Int>>,
  ): Result<Int> = withContext(Dispatchers.IO) {
    runCatching {
      val orderLinesCommands =
        mutableListOf<Any>().apply {
          add(listOf(5, 0, 0)) // Clear existing draft lines
          for ((productId, qty) in lines) {
            add(listOf(0, 0, mapOf("product_id" to productId, "product_uom_qty" to qty)))
          }
        }

      if (existingOrderId > 0) {
        executeKw(
          baseUrl = baseUrl,
          database = database,
          uid = uid,
          credential = credential,
          sessionCookie = sessionCookie,
          model = "sale.order",
          method = "write",
          args = listOf(listOf(existingOrderId), mapOf("order_line" to orderLinesCommands)),
        )
        existingOrderId
      } else {
        val created =
          executeKw(
            baseUrl = baseUrl,
            database = database,
            uid = uid,
            credential = credential,
            sessionCookie = sessionCookie,
            model = "sale.order",
            method = "create",
            args =
              listOf(
                mapOf(
                  "partner_id" to partnerId,
                  "order_line" to orderLinesCommands,
                )
              ),
          )
        OdooJsonParser.parseOdooInt(created, 0)
      }
    }
  }

  suspend fun confirmSaleOrder(
    baseUrl: String,
    database: String,
    uid: Int,
    credential: String,
    sessionCookie: String,
    orderId: Int,
    paymentReference: String,
  ): Result<Boolean> = withContext(Dispatchers.IO) {
    runCatching {
      executeKw(
        baseUrl = baseUrl,
        database = database,
        uid = uid,
        credential = credential,
        sessionCookie = sessionCookie,
        model = "sale.order",
        method = "write",
        args = listOf(listOf(orderId), mapOf("client_order_ref" to paymentReference)),
      )
      executeKw(
        baseUrl = baseUrl,
        database = database,
        uid = uid,
        credential = credential,
        sessionCookie = sessionCookie,
        model = "sale.order",
        method = "action_confirm",
        args = listOf(listOf(orderId)),
      )
      true
    }
  }

  suspend fun fetchUserOrders(
    baseUrl: String,
    database: String,
    uid: Int,
    credential: String,
    sessionCookie: String,
    partnerId: Int,
  ): Result<List<OdooRemoteOrder>> = withContext(Dispatchers.IO) {
    runCatching {
      val domain =
        if (partnerId > 0) listOf(listOf("partner_id", "=", partnerId)) else emptyList<Any>()
      val fields =
        listOf(
          "id",
          "name",
          "state",
          "amount_untaxed",
          "amount_tax",
          "amount_total",
          "delivery_status",
          "invoice_status",
          "client_order_ref",
          "date_order",
        )
      val rows =
        executeKw(
          baseUrl = baseUrl,
          database = database,
          uid = uid,
          credential = credential,
          sessionCookie = sessionCookie,
          model = "sale.order",
          method = "search_read",
          args = listOf(domain),
          kwargs = mapOf("fields" to fields, "limit" to 40, "order" to "id desc"),
        ) as? List<*> ?: emptyList<Any>()

      rows.mapNotNull { row ->
        val map = row as? Map<*, *> ?: return@mapNotNull null
        val id = OdooJsonParser.parseOdooInt(map["id"], 0)
        if (id <= 0) return@mapNotNull null
        OdooRemoteOrder(
          odooOrderId = id,
          orderNumber = OdooJsonParser.parseOdooString(map["name"], "SO#$id"),
          state = OdooJsonParser.parseOdooString(map["state"], "draft"),
          amountUntaxed = OdooJsonParser.parseOdooDouble(map["amount_untaxed"], 0.0),
          amountTax = OdooJsonParser.parseOdooDouble(map["amount_tax"], 0.0),
          amountTotal = OdooJsonParser.parseOdooDouble(map["amount_total"], 0.0),
          deliveryStatus = OdooJsonParser.parseOdooString(map["delivery_status"], "pending"),
          invoiceStatus = OdooJsonParser.parseOdooString(map["invoice_status"], "no"),
          clientOrderRef = OdooJsonParser.parseOdooString(map["client_order_ref"], ""),
          dateOrder = OdooJsonParser.parseOdooString(map["date_order"], ""),
        )
      }
    }
  }

  suspend fun adjustInventoryQuantity(
    baseUrl: String,
    database: String,
    uid: Int,
    credential: String,
    sessionCookie: String,
    productId: Int,
    newQuantity: Double,
  ): Result<Boolean> = withContext(Dispatchers.IO) {
    runCatching {
      val wizardId =
        executeKw(
          baseUrl = baseUrl,
          database = database,
          uid = uid,
          credential = credential,
          sessionCookie = sessionCookie,
          model = "stock.change.product.qty",
          method = "create",
          args = listOf(mapOf("product_id" to productId, "new_quantity" to newQuantity)),
        )
      val idInt = OdooJsonParser.parseOdooInt(wizardId, 0)
      if (idInt > 0) {
        executeKw(
          baseUrl = baseUrl,
          database = database,
          uid = uid,
          credential = credential,
          sessionCookie = sessionCookie,
          model = "stock.change.product.qty",
          method = "change_product_qty",
          args = listOf(listOf(idInt)),
        )
        true
      } else {
        false
      }
    }
  }

  private fun executeKw(
    baseUrl: String,
    database: String,
    uid: Int,
    credential: String,
    sessionCookie: String,
    model: String,
    method: String,
    args: List<Any?>,
    kwargs: Map<String, Any?> = emptyMap(),
  ): Any? {
    val cleanUrl = normalizeUrl(baseUrl)
    val useSessionEndpoint = sessionCookie.isNotBlank() && credential.isBlank()
    val endpoint = if (useSessionEndpoint) "$cleanUrl/web/dataset/call_kw" else "$cleanUrl/jsonrpc"

    val payload =
      if (useSessionEndpoint) {
        mapOf(
          "jsonrpc" to "2.0",
          "method" to "call",
          "params" to
            mapOf(
              "model" to model,
              "method" to method,
              "args" to args,
              "kwargs" to kwargs,
            ),
          "id" to System.currentTimeMillis(),
        )
      } else {
        mapOf(
          "jsonrpc" to "2.0",
          "method" to "call",
          "params" to
            mapOf(
              "service" to "object",
              "method" to "execute_kw",
              "args" to listOf(database, uid, credential, model, method, args, kwargs),
            ),
          "id" to System.currentTimeMillis(),
        )
      }

    val requestBuilder =
      Request.Builder()
        .url(endpoint)
        .post(mapAdapter.toJson(payload).toRequestBody(jsonMediaType))
        .header("Content-Type", "application/json")

    if (sessionCookie.isNotBlank()) {
      requestBuilder.header("Cookie", sessionCookie)
    }

    client.newCall(requestBuilder.build()).execute().use { response ->
      val raw = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        throw IllegalStateException("Odoo HTTP ${response.code}")
      }
      val parsed = mapAdapter.fromJson(raw) ?: throw IllegalStateException("Respons JSON-RPC kosong")
      val error = parsed["error"] as? Map<*, *>
      if (error != null) {
        val data = error["data"] as? Map<*, *>
        val msg = data?.get("message")?.toString() ?: error["message"]?.toString()
        throw IllegalStateException(msg ?: "Operasi Odoo gagal")
      }
      return parsed["result"]
    }
  }

  private fun normalizeUrl(url: String): String {
    val trimmed = url.trim().trimEnd('/')
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      trimmed
    } else {
      "https://$trimmed"
    }
  }
}
