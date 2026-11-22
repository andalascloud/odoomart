package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.components.MonoCodeText
import com.example.ui.state.AuthUiState
import com.example.ui.state.CommerceUiState
import com.example.ui.theme.StockAvailableBg
import com.example.ui.theme.StockAvailableGreen
import com.example.ui.theme.StockEmptyRed
import com.example.ui.theme.StockLowAmber
import com.example.ui.theme.StockLowBg

@Composable
fun AccountAndConnectionScreen(
  uiState: CommerceUiState,
  onSignIn: (String, String, String, String) -> Unit,
  onSignOut: () -> Unit,
  onToggleNotifications: (Boolean) -> Unit,
  onToggleAutoSync: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  var serverUrl by remember(uiState.session.baseUrl) {
    mutableStateOf(uiState.session.baseUrl)
  }
  var databaseName by remember(uiState.session.database) {
    mutableStateOf(uiState.session.database)
  }
  var emailLogin by remember(uiState.session.userLogin) {
    mutableStateOf(uiState.session.userLogin)
  }
  var password by remember { mutableStateOf("") }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 860.dp).align(Alignment.TopCenter),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Column {
          Text("Koneksi Odoo 20 & Akun", style = MaterialTheme.typography.headlineSmall)
          Text(
            "Otentikasi sesi JSON-RPC Odoo 20 Community & preferensi sinkronisasi",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (uiState.session.isAuthenticated) {
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  tint = StockAvailableGreen,
                  modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(uiState.session.userName, style = MaterialTheme.typography.titleLarge)
                  Text(
                    uiState.session.userLogin,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              HorizontalDivider()
              MonoCodeText(text = "Server: ${uiState.session.baseUrl}")
              MonoCodeText(
                text = "Database: ${uiState.session.database} • UID: ${uiState.session.uid} • Partner: ${uiState.session.partnerId}"
              )
              OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().testTag("odoo_logout_button"),
              ) {
                Icon(
                  Icons.AutoMirrored.Rounded.Logout,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Keluar dari Sesi Odoo")
              }
            }
          }
        }
      } else {
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("Masuk ke Odoo 20 Community", style = MaterialTheme.typography.titleMedium)
              OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("URL Server Odoo 20") },
                placeholder = { Text("https://erp.tokoanda.co.id") },
                leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("odoo_url_input"),
              )
              OutlinedTextField(
                value = databaseName,
                onValueChange = { databaseName = it },
                label = { Text("Nama Database Odoo") },
                leadingIcon = { Icon(Icons.Outlined.Storage, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("odoo_db_input"),
              )
              OutlinedTextField(
                value = emailLogin,
                onValueChange = { emailLogin = it },
                label = { Text("Email / Username Pengguna Odoo") },
                leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("odoo_email_input"),
              )
              OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Kata Sandi Akun Odoo") },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("odoo_password_input"),
              )

              if (uiState.authState is AuthUiState.Error) {
                Text(
                  text = uiState.authState.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = StockEmptyRed,
                )
              }

              Button(
                onClick = { onSignIn(serverUrl, databaseName, emailLogin, password) },
                enabled = uiState.authState !is AuthUiState.Loading,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("odoo_login_button"),
              ) {
                if (uiState.authState is AuthUiState.Loading) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Menghubungkan...")
                } else {
                  Text("Hubungkan & Sinkronkan")
                }
              }
            }
          }
        }
      }

      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Otomatisasi & Notifikasi", style = MaterialTheme.typography.titleMedium)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Notifikasi Push Status Pesanan", style = MaterialTheme.typography.titleSmall)
                Text(
                  "Kirim pemberitahuan perangkat saat status pembayaran atau pengiriman berubah",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = uiState.session.notificationsEnabled,
                onCheckedChange = onToggleNotifications,
                modifier = Modifier.testTag("toggle_notifications"),
              )
            }
            HorizontalDivider()
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Sinkronisasi Inventaris Real-Time", style = MaterialTheme.typography.titleSmall)
                Text(
                  "Perbarui stok gudang Odoo 20 & status pesanan otomatis tiap 12 detik",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = uiState.session.autoSyncEnabled,
                onCheckedChange = onToggleAutoSync,
                modifier = Modifier.testTag("toggle_autosync"),
              )
            }
          }
        }
      }

      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Status Kredensial & Gateway", style = MaterialTheme.typography.titleMedium)
            }
            CredentialStatusRow(
              label = "Gateway Pembayaran Lokal",
              isReady = uiState.isPaymentGatewayConfigured,
            )
            CredentialStatusRow(
              label = "Odoo API Key (Opsional via Secrets)",
              isReady = uiState.isOdooApiKeyConfigured,
            )
            Text(
              text =
                "Kunci API dan kredensial gateway dikelola secara aman melalui panel Secrets AI Studio dan disuntikkan saat build.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun CredentialStatusRow(label: String, isReady: Boolean) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Surface(
      color = if (isReady) StockAvailableBg else StockLowBg,
      shape = RoundedCornerShape(50),
    ) {
      Text(
        text = if (isReady) "Terkonfigurasi" else "Atur di Secrets",
        color = if (isReady) StockAvailableGreen else StockLowAmber,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      )
    }
  }
}
