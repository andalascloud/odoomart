package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "odoomart_prefs")

data class OdooSessionConfig(
  val baseUrl: String,
  val database: String,
  val userLogin: String,
  val userName: String,
  val uid: Int,
  val partnerId: Int,
  val activeDraftOrderId: Int,
  val sessionCookie: String,
  val notificationsEnabled: Boolean,
  val autoSyncEnabled: Boolean,
) {
  val isAuthenticated: Boolean
    get() = uid > 0 && baseUrl.isNotBlank() && database.isNotBlank()
}

class SessionPreferences(private val context: Context) {
  private object Keys {
    val BASE_URL = stringPreferencesKey("odoo_base_url")
    val DATABASE = stringPreferencesKey("odoo_database")
    val USER_LOGIN = stringPreferencesKey("odoo_user_login")
    val USER_NAME = stringPreferencesKey("odoo_user_name")
    val UID = intPreferencesKey("odoo_uid")
    val PARTNER_ID = intPreferencesKey("odoo_partner_id")
    val ACTIVE_DRAFT_ORDER_ID = intPreferencesKey("odoo_active_draft_order_id")
    val SESSION_COOKIE = stringPreferencesKey("odoo_session_cookie")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
  }

  val sessionFlow: Flow<OdooSessionConfig> =
    context.dataStore.data.map { prefs ->
      val defaultUrl =
        BuildConfig.ODOO_BASE_URL.takeIf {
          it.isNotBlank() && !it.contains("example.com", ignoreCase = true)
        } ?: ""
      val defaultDb =
        BuildConfig.ODOO_DB.takeIf { it.isNotBlank() } ?: "odoo20_community"
      OdooSessionConfig(
        baseUrl = prefs[Keys.BASE_URL] ?: defaultUrl,
        database = prefs[Keys.DATABASE] ?: defaultDb,
        userLogin = prefs[Keys.USER_LOGIN] ?: "",
        userName = prefs[Keys.USER_NAME] ?: "",
        uid = prefs[Keys.UID] ?: 0,
        partnerId = prefs[Keys.PARTNER_ID] ?: 0,
        activeDraftOrderId = prefs[Keys.ACTIVE_DRAFT_ORDER_ID] ?: 0,
        sessionCookie = prefs[Keys.SESSION_COOKIE] ?: "",
        notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
        autoSyncEnabled = prefs[Keys.AUTO_SYNC_ENABLED] ?: true,
      )
    }

  suspend fun getCurrentSession(): OdooSessionConfig = sessionFlow.first()

  suspend fun saveAuthenticatedSession(
    baseUrl: String,
    database: String,
    userLogin: String,
    userName: String,
    uid: Int,
    partnerId: Int,
    sessionCookie: String,
  ) {
    context.dataStore.edit { prefs ->
      prefs[Keys.BASE_URL] = baseUrl.trimEnd('/')
      prefs[Keys.DATABASE] = database.trim()
      prefs[Keys.USER_LOGIN] = userLogin.trim()
      prefs[Keys.USER_NAME] = userName
      prefs[Keys.UID] = uid
      prefs[Keys.PARTNER_ID] = partnerId
      prefs[Keys.SESSION_COOKIE] = sessionCookie
    }
  }

  suspend fun saveActiveDraftOrderId(orderId: Int) {
    context.dataStore.edit { prefs -> prefs[Keys.ACTIVE_DRAFT_ORDER_ID] = orderId }
  }

  suspend fun setNotificationsEnabled(enabled: Boolean) {
    context.dataStore.edit { prefs -> prefs[Keys.NOTIFICATIONS_ENABLED] = enabled }
  }

  suspend fun setAutoSyncEnabled(enabled: Boolean) {
    context.dataStore.edit { prefs -> prefs[Keys.AUTO_SYNC_ENABLED] = enabled }
  }

  suspend fun clearSession() {
    context.dataStore.edit { prefs ->
      prefs.remove(Keys.UID)
      prefs.remove(Keys.PARTNER_ID)
      prefs.remove(Keys.USER_NAME)
      prefs.remove(Keys.ACTIVE_DRAFT_ORDER_ID)
      prefs.remove(Keys.SESSION_COOKIE)
    }
  }
}
