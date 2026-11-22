package com.example

import com.example.data.remote.OdooJsonParser
import com.example.data.remote.PaymentSecurityUtils
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OdooCommerceUnitTest {
  @Test
  fun odooJsonParser_handlesBooleanFalseAndMany2One() {
    assertEquals("", OdooJsonParser.parseOdooString(false))
    assertEquals("SKU-01", OdooJsonParser.parseOdooString("SKU-01"))
    assertEquals("Elektronik", OdooJsonParser.parseMany2OneName(listOf(4, "Elektronik")))
    assertEquals("Umum", OdooJsonParser.parseMany2OneName(false, "Umum"))
    assertEquals(15.0, OdooJsonParser.parseOdooDouble(15), 0.001)
    assertEquals(42, OdooJsonParser.parseOdooInt("42"))
  }

  @Test
  fun paymentSecurityUtils_verifiesSha512SignatureAndMasksKeys() {
    val orderId = "SO-12345"
    val statusCode = "200"
    val grossAmount = "111000.00"
    val serverKey = "SB-Mid-server-TestKey123"
    val raw = "$orderId$statusCode$grossAmount$serverKey"
    val expectedHex =
      MessageDigest.getInstance("SHA-512")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    assertTrue(
      PaymentSecurityUtils.verifySha512Signature(
        orderId = orderId,
        statusCode = statusCode,
        grossAmount = grossAmount,
        serverKey = serverKey,
        expectedSignature = expectedHex,
      )
    )
    assertFalse(
      PaymentSecurityUtils.verifySha512Signature(
        orderId = orderId,
        statusCode = statusCode,
        grossAmount = grossAmount,
        serverKey = serverKey,
        expectedSignature = "invalid_signature",
      )
    )
    assertEquals("Belum dikonfigurasi", PaymentSecurityUtils.maskKey("YOUR_PAYMENT_GATEWAY_SERVER_KEY"))
    assertEquals("SB-Mid••••y123", PaymentSecurityUtils.maskKey(serverKey))
  }
}
