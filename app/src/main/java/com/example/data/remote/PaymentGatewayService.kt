package com.example.data.remote

import android.util.Base64
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor

class PaymentGatewayService {
  private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
  private val mapAdapter =
    moshi.adapter<Map<String, Any?>>(
      Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )
  private val responseAdapter = moshi.adapter(PaymentChargeResponse::class.java)
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .addInterceptor(
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
      )
      .build()

  fun isGatewayConfigured(): Boolean {
    val key = BuildConfig.PAYMENT_GATEWAY_SERVER_KEY
    return key.isNotBlank() && !key.startsWith("YOUR_")
  }

  suspend fun createCharge(
    orderNumber: String,
    grossAmountIdr: Double,
    customerName: String,
    customerEmail: String,
    channel: LocalPaymentChannel,
  ): Result<PaymentChargeResponse> = withContext(Dispatchers.IO) {
    runCatching {
      val serverKey = BuildConfig.PAYMENT_GATEWAY_SERVER_KEY
      if (serverKey.isBlank() || serverKey.startsWith("YOUR_")) {
        throw IllegalStateException(
          "Kunci server gateway pembayaran belum dikonfigurasi pada Secrets AI Studio (PAYMENT_GATEWAY_SERVER_KEY)."
        )
      }
      val baseUrl =
        BuildConfig.PAYMENT_GATEWAY_BASE_URL.ifBlank { "https://api.sandbox.midtrans.com" }.trimEnd('/')
      val amountInt = grossAmountIdr.roundToLong().coerceAtLeast(1000L)

      val payload = mutableMapOf<String, Any?>(
        "payment_type" to channel.gatewayType,
        "transaction_details" to
          mapOf(
            "order_id" to orderNumber,
            "gross_amount" to amountInt,
          ),
        "customer_details" to
          mapOf(
            "first_name" to customerName.ifBlank { "Pelanggan OdooMart" },
            "email" to customerEmail.ifBlank { "customer@odoomart.id" },
          ),
      )

      when (channel) {
        LocalPaymentChannel.BCA_VA,
        LocalPaymentChannel.BNI_VA,
        LocalPaymentChannel.BRI_VA -> {
          payload["bank_transfer"] = mapOf("bank" to channel.bankCode)
        }
        LocalPaymentChannel.MANDIRI_BILL -> {
          payload["echannel"] =
            mapOf(
              "bill_info1" to "Pembayaran OdooMart",
              "bill_info2" to orderNumber,
            )
        }
        LocalPaymentChannel.QRIS -> {
          payload["qris"] = mapOf("acquirer" to "gopay")
        }
        LocalPaymentChannel.GOPAY -> {
          payload["gopay"] = mapOf("enable_callback" to true)
        }
      }

      val authHeader =
        "Basic " + Base64.encodeToString("$serverKey:".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

      val request =
        Request.Builder()
          .url("$baseUrl/v2/charge")
          .post(mapAdapter.toJson(payload).toRequestBody(jsonMediaType))
          .header("Accept", "application/json")
          .header("Content-Type", "application/json")
          .header("Authorization", authHeader)
          .build()

      client.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        val parsed = responseAdapter.fromJson(raw)
        if (!response.isSuccessful) {
          throw IllegalStateException(
            parsed?.statusMessage ?: "Gateway pembayaran menolak permintaan (HTTP ${response.code})"
          )
        }
        parsed ?: throw IllegalStateException("Respons gateway pembayaran kosong")
      }
    }
  }

  suspend fun checkTransactionStatus(orderNumber: String): Result<PaymentChargeResponse> =
    withContext(Dispatchers.IO) {
      runCatching {
        val serverKey = BuildConfig.PAYMENT_GATEWAY_SERVER_KEY
        if (serverKey.isBlank() || serverKey.startsWith("YOUR_")) {
          throw IllegalStateException("PAYMENT_GATEWAY_SERVER_KEY belum dikonfigurasi.")
        }
        val baseUrl =
          BuildConfig.PAYMENT_GATEWAY_BASE_URL.ifBlank { "https://api.sandbox.midtrans.com" }
            .trimEnd('/')
        val authHeader =
          "Basic " + Base64.encodeToString("$serverKey:".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        val request =
          Request.Builder()
            .url("$baseUrl/v2/$orderNumber/status")
            .get()
            .header("Accept", "application/json")
            .header("Authorization", authHeader)
            .build()

        client.newCall(request).execute().use { response ->
          val raw = response.body?.string().orEmpty()
          val parsed = responseAdapter.fromJson(raw)
          if (!response.isSuccessful) {
            throw IllegalStateException(
              parsed?.statusMessage ?: "Gagal memeriksa status pembayaran (HTTP ${response.code})"
            )
          }
          parsed ?: throw IllegalStateException("Respons status pembayaran kosong")
        }
      }
    }
}
