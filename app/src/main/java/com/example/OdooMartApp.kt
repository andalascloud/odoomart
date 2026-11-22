package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.SessionPreferences
import com.example.data.remote.OdooApiService
import com.example.data.remote.PaymentGatewayService
import com.example.data.repository.CommerceRepository
import com.example.notifications.NotificationHelper

class OdooMartApp : Application() {
  lateinit var repository: CommerceRepository
    private set

  lateinit var notificationHelper: NotificationHelper
    private set

  override fun onCreate() {
    super.onCreate()
    val database = AppDatabase.getInstance(this)
    val prefs = SessionPreferences(this)
    val odooApi = OdooApiService()
    val paymentGateway = PaymentGatewayService()
    notificationHelper = NotificationHelper(this).apply { ensureChannelCreated() }
    repository =
      CommerceRepository(
        database = database,
        sessionPreferences = prefs,
        odooApiService = odooApi,
        paymentGatewayService = paymentGateway,
        notificationHelper = notificationHelper,
      )
  }
}
