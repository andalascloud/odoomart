package com.example.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class JsonRpcEnvelope(
  val jsonrpc: String = "2.0",
  val method: String = "call",
  val params: Map<String, @JvmSuppressWildcards Any?>,
  val id: Long = System.currentTimeMillis(),
)

data class OdooAuthResult(
  val uid: Int,
  val partnerId: Int,
  val userName: String,
  val serverVersion: String,
  val sessionCookie: String,
)

data class OdooRemoteProduct(
  val id: Int,
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
)

data class OdooRemoteOrder(
  val odooOrderId: Int,
  val orderNumber: String,
  val state: String,
  val amountUntaxed: Double,
  val amountTax: Double,
  val amountTotal: Double,
  val deliveryStatus: String,
  val invoiceStatus: String,
  val clientOrderRef: String,
  val dateOrder: String,
)

object OdooJsonParser {
  fun parseMany2OneName(value: Any?, fallback: String = "Umum"): String {
    if (value is List<*> && value.size >= 2) {
      return value[1]?.toString()?.takeIf { it.isNotBlank() } ?: fallback
    }
    return fallback
  }

  fun parseOdooString(value: Any?, fallback: String = ""): String {
    return when (value) {
      null, is Boolean -> fallback
      else -> value.toString()
    }
  }

  fun parseOdooDouble(value: Any?, fallback: Double = 0.0): Double {
    return when (value) {
      is Number -> value.toDouble()
      is String -> value.toDoubleOrNull() ?: fallback
      else -> fallback
    }
  }

  fun parseOdooInt(value: Any?, fallback: Int = 0): Int {
    return when (value) {
      is Number -> value.toInt()
      is String -> value.toIntOrNull() ?: fallback
      else -> fallback
    }
  }
}
