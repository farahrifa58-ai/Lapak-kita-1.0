package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.i18n.AppStrings
import com.example.ui.screens.*
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LapakKitaTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.LapakViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LapakViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDark by viewModel.isDarkTheme.collectAsState()
            val currentScreen by viewModel.currentScreen.collectAsState()
            val lang by viewModel.currentLanguage.collectAsState()
            val cart by viewModel.cart.collectAsState()
            val cartItemCount = remember(cart) { cart.sumOf { it.quantity } }

            var showSplash by remember { mutableStateOf(true) }
            val context = androidx.compose.ui.platform.LocalContext.current
            val trialManager = remember { com.example.config.TrialManager(context) }
            var isTrialExpired by remember { mutableStateOf(trialManager.isExpired()) }

            if (com.example.config.AppConfig.IS_TRIAL) {
                LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(5000)
                        isTrialExpired = trialManager.isExpired()
                    }
                }
            }

            LapakKitaTheme(darkTheme = isDark) {
                if (showSplash) {
                    SplashScreen(onTimeout = { showSplash = false })
                } else if (com.example.config.AppConfig.IS_TRIAL && isTrialExpired) {
                    // Trial Expired Lock Screen
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.HourglassDisabled,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        text = "Masa Uji Coba Berakhir",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Waktu pemakaian versi Trial (60 menit) telah habis.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Terima kasih telah mencoba Lapak Kita by Rafline Dev. Silakan hubungi admin atau gunakan Full Version untuk akses tanpa batas waktu.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(Modifier.height(20.dp))
                                    Button(
                                        onClick = {
                                            (context as? android.app.Activity)?.finish()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Tutup Aplikasi")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Handle system back navigation to Dashboard
                    if (currentScreen != AppScreen.DASHBOARD) {
                        BackHandler {
                            viewModel.navigateTo(AppScreen.DASHBOARD)
                        }
                    }

                    Scaffold(
                    bottomBar = {
                        // Exactly 5 main navigation tabs at the bottom
                        if (currentScreen != AppScreen.SETTINGS) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp,
                                windowInsets = WindowInsets.navigationBars
                            ) {
                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.DASHBOARD,
                                    onClick = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                                    label = { Text(AppStrings.get("nav_dashboard", lang), maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldPrimary,
                                        selectedTextColor = EmeraldPrimary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.SALES,
                                    onClick = { viewModel.navigateTo(AppScreen.SALES) },
                                    icon = { Icon(Icons.Default.Storefront, contentDescription = "Penjualan") },
                                    label = { Text(AppStrings.get("nav_sales", lang), maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldPrimary,
                                        selectedTextColor = EmeraldPrimary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.CASHIER,
                                    onClick = { viewModel.navigateTo(AppScreen.CASHIER) },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (cartItemCount > 0) {
                                                    Badge { Text("$cartItemCount") }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.PointOfSale, contentDescription = "Kasir")
                                        }
                                    },
                                    label = { Text(AppStrings.get("nav_cashier", lang), maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldPrimary,
                                        selectedTextColor = EmeraldPrimary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.HISTORY,
                                    onClick = { viewModel.navigateTo(AppScreen.HISTORY) },
                                    icon = { Icon(Icons.Default.History, contentDescription = "Riwayat") },
                                    label = { Text(AppStrings.get("nav_history", lang), maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldPrimary,
                                        selectedTextColor = EmeraldPrimary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.SELLERS,
                                    onClick = { viewModel.navigateTo(AppScreen.SELLERS) },
                                    icon = { Icon(Icons.Default.People, contentDescription = "Penyetor") },
                                    label = { Text(AppStrings.get("nav_sellers", lang), maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldPrimary,
                                        selectedTextColor = EmeraldPrimary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Crossfade(
                        targetState = currentScreen,
                        label = "ScreenTransition",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = if (currentScreen != AppScreen.SETTINGS) innerPadding.calculateBottomPadding() else 0.dp)
                    ) { screen ->
                        when (screen) {
                            AppScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                            AppScreen.SALES -> ProductsScreen(viewModel = viewModel)
                            AppScreen.CASHIER -> CashierScreen(viewModel = viewModel)
                            AppScreen.HISTORY -> TransactionsScreen(viewModel = viewModel)
                            AppScreen.SELLERS -> SellersScreen(viewModel = viewModel)
                            AppScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            AppScreen.ABOUT -> SettingsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
}
