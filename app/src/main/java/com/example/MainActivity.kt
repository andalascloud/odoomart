package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AccountAndConnectionScreen
import com.example.ui.screens.CartAndCheckoutScreen
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.OrdersAndNotificationsScreen
import com.example.ui.state.CommerceViewModel
import com.example.ui.state.CommerceViewModelFactory
import com.example.ui.theme.OdooMartTheme

private enum class AppDestination(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  CATALOG("catalog", "Katalog", Icons.Filled.Storefront, Icons.Outlined.Storefront),
  INVENTORY("inventory", "Inventaris", Icons.Filled.Inventory2, Icons.Outlined.Inventory2),
  CART("cart", "Keranjang", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
  ORDERS("orders", "Pesanan", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
  ACCOUNT("account", "Akun", Icons.Filled.Person, Icons.Outlined.Person),
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val app = application as OdooMartApp
    val initialTab = intent?.getStringExtra("target_tab")

    setContent {
      OdooMartTheme {
        val viewModel: CommerceViewModel =
          viewModel(factory = CommerceViewModelFactory(app.repository))
        OdooMartRootScaffold(viewModel = viewModel, initialTab = initialTab)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OdooMartRootScaffold(
  viewModel: CommerceViewModel,
  initialTab: String?,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route ?: AppDestination.CATALOG.route
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(initialTab) {
    if (initialTab == AppDestination.ORDERS.route) {
      navController.navigate(AppDestination.ORDERS.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
      }
    }
  }

  LaunchedEffect(uiState.bannerMessage) {
    val msg = uiState.bannerMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearBannerMessage()
    }
  }

  fun navigateTo(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 700.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        CenterAlignedTopAppBar(
          title = {
            Text(
              text = "OdooMart",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
          },
          colors =
            TopAppBarDefaults.centerAlignedTopAppBarColors(
              containerColor = MaterialTheme.colorScheme.background,
              titleContentColor = MaterialTheme.colorScheme.primary,
            ),
        )
      },
      bottomBar = {
        if (!useRail) {
          NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            AppDestination.entries.forEach { dest ->
              val selected = currentRoute == dest.route
              val badgeCount =
                when (dest) {
                  AppDestination.CART -> uiState.totalCartItemsCount
                  AppDestination.ORDERS -> uiState.unreadNotificationCount
                  else -> 0
                }
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTo(dest.route) },
                icon = {
                  NavIconWithBadge(
                    icon = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    label = dest.label,
                    badgeCount = badgeCount,
                  )
                },
                label = { Text(dest.label, maxLines = 1) },
                modifier = Modifier.testTag("nav_${dest.route}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight(),
          ) {
            Spacer(modifier = Modifier.weight(0.1f))
            AppDestination.entries.forEach { dest ->
              val selected = currentRoute == dest.route
              val badgeCount =
                when (dest) {
                  AppDestination.CART -> uiState.totalCartItemsCount
                  AppDestination.ORDERS -> uiState.unreadNotificationCount
                  else -> 0
                }
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTo(dest.route) },
                icon = {
                  NavIconWithBadge(
                    icon = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    label = dest.label,
                    badgeCount = badgeCount,
                  )
                },
                label = { Text(dest.label) },
                modifier = Modifier.testTag("rail_${dest.route}"),
              )
            }
            Spacer(modifier = Modifier.weight(1f))
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          NavHost(
            navController = navController,
            startDestination = AppDestination.CATALOG.route,
          ) {
            composable(AppDestination.CATALOG.route) {
              CatalogScreen(
                uiState = uiState,
                onSearchChange = viewModel::updateSearchQuery,
                onCategorySelect = viewModel::selectCategory,
                onStockFilterSelect = viewModel::selectStockFilter,
                onSyncNow = viewModel::refreshAllRealtime,
                onProductClick = viewModel::openProductDetail,
                onDismissProduct = { viewModel.openProductDetail(null) },
                onAddToCart = viewModel::addToCart,
                onNavigateToConnect = { navigateTo(AppDestination.ACCOUNT.route) },
              )
            }
            composable(AppDestination.INVENTORY.route) {
              InventoryScreen(
                uiState = uiState,
                onSyncNow = viewModel::refreshAllRealtime,
                onAdjustStock = viewModel::adjustInventoryStock,
              )
            }
            composable(AppDestination.CART.route) {
              CartAndCheckoutScreen(
                uiState = uiState,
                onUpdateQty = viewModel::updateCartQty,
                onRemoveItem = viewModel::removeFromCart,
                onShippingAddressChange = viewModel::updateShippingAddress,
                onSelectPaymentChannel = viewModel::selectPaymentChannel,
                onCheckout = viewModel::startLocalGatewayCheckout,
                onVerifyPayment = viewModel::verifyOrderPaymentStatus,
                onResetCheckout = viewModel::resetCheckoutState,
                onExploreCatalog = { navigateTo(AppDestination.CATALOG.route) },
              )
            }
            composable(AppDestination.ORDERS.route) {
              OrdersAndNotificationsScreen(
                uiState = uiState,
                onSyncOrders = viewModel::refreshAllRealtime,
                onVerifyOrderPayment = viewModel::verifyOrderPaymentStatus,
                onMarkAllRead = viewModel::markAllNotificationsRead,
              )
            }
            composable(AppDestination.ACCOUNT.route) {
              AccountAndConnectionScreen(
                uiState = uiState,
                onSignIn = viewModel::signInOdoo,
                onSignOut = viewModel::signOutOdoo,
                onToggleNotifications = viewModel::setNotificationsEnabled,
                onToggleAutoSync = viewModel::setAutoSyncEnabled,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun NavIconWithBadge(
  icon: ImageVector,
  label: String,
  badgeCount: Int,
) {
  BadgedBox(
    badge = {
      if (badgeCount > 0) {
        Badge {
          Text(if (badgeCount > 99) "99+" else "$badgeCount")
        }
      }
    }
  ) {
    Icon(imageVector = icon, contentDescription = label)
  }
}
