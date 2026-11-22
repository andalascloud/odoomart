package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.security.MessageDigest

enum class LocalPaymentChannel(
  val code: String,
  val displayName: String,
  val subtitle: String,
  val gatewayType: String,
  val bankCode: String? = null,
) {
  QRIS(
    code = "qris",
    displayName = "QRIS Instan",
    subtitle = "Scan via GoPay, OVO, DANA, ShopeePay & Mobile Banking",
    gatewayType = "qris",
  ),
  BCA_VA(
    code = "bca_va",
    displayName = "BCA Virtual Account",
    subtitle = "Verifikasi otomatis real-time tanpa unggah bukti",
    gatewayType = "bank_transfer",
    bankCode = "bca",
  ),
  MANDIRI_BILL(
    code = "mandiri_va",
    displayName = "Mandiri Bill / VA",
    subtitle = "Pembayaran instan via Livin' by Mandiri",
    gatewayType = "echannel",
    bankCode = "mandiri",
  ),
  BNI_VA(
    code = "bni_va",
    displayName = "BNI Virtual Account",
    subtitle = "Verifikasi cepat lewat BNI Mobile Banking",
    gatewayType = "bank_transfer",
    bankCode = "bni",
  ),
  BRI_VA(
    code = "bri_va",
    displayName = "BRI Virtual Account",
    subtitle = "Terhubung langsung dengan BRImo",
    gatewayType = "bank_transfer",
    bankCode = "bri",
  ),
  GOPAY(
    code = "gopay",
    displayName = "GoPay Langsung",
    subtitle = "Aktivasi cepat dengan otorisasi aplikasi Gojek",
    gatewayType = "gopay",
  ),
}

@JsonClass(generateAdapter = true)
data class PaymentChargeResponse(
  @Json(name = "status_code") val statusCode: String? = null,
  @Json(name = "status_message") val statusMessage: String? = null,
  @Json(name = "transaction_id") val transactionId: String? = null,
  @Json(name = "order_id") val orderId: String? = null,
  @Json(name = "gross_amount") val grossAmount: String? = null,
  @Json(name = "payment_type") val paymentType: String? = null,
  @Json(name = "transaction_status") val transactionStatus: String? = null,
  @Json(name = "expiry_time") val expiryTime: String? = null,
  @Json(name = "qr_string") val qrString: String? = null,
  @Json(name = "permata_va_number") val permataVaNumber: String? = null,
  @Json(name = "bill_key") val billKey: String? = null,
  @Json(name = "biller_code") val billerCode: String? = null,
  @Json(name = "va_numbers") val vaNumbers: List<VaNumberDto>? = null,
  @Json(name = "actions") val actions: List<PaymentActionDto>? = null,
)

@JsonClass(generateAdapter = true)
data class VaNumberDto(
  val bank: String? = null,
  @Json(name = "va_number") val vaNumber: String? = null,
)

@JsonClass(generateAdapter = true)
data class PaymentActionDto(
  val name: String? = null,
  val method: String? = null,
  val url: String? = null,
)

object PaymentSecurityUtils {
  fun verifySha512Signature(
    orderId: String,
    statusCode: String,
    grossAmount: String,
    serverKey: String,
    expectedSignature: String,
  ): Boolean {
    val raw = "$orderId$statusCode$grossAmount$serverKey"
    val digest = MessageDigest.getInstance("SHA-512").digest(raw.toByteArray(Charsets.UTF_8))
    val hex = digest.joinToString("") { "%02x".format(it) }
    return hex.equals(expectedSignature, ignoreCase = true)
  }

  fun maskKey(key: String): String {
    if (key.isBlank() || key.startsWith("YOUR_")) return "Belum dikonfigurasi"
    if (key.length <= 8) return "****"
    return "${key.take(6)}••••${key.takeLast(4)}"
  }
}
