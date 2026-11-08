package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.DashboardSummary
import com.example.i18n.AppStrings
import com.example.ui.components.RevenueProfitBarChart
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.LapakViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: LapakViewModel,
    modifier: Modifier = Modifier
) {
    val lang by viewModel.currentLanguage.collectAsState()
    val shopProfile by viewModel.shopProfile.collectAsState()
    val summary by viewModel.dashboardSummary.collectAsState()
    val currentTimeframe by viewModel.timeframe.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = shopProfile.shopName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary
                        )
                        Text(
                            text = AppStrings.get("tagline", lang),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.SETTINGS) }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Pengaturan",
                            tint = EmeraldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EmeraldContainer,
                    titleContentColor = EmeraldPrimary
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Timeframe Filter Chips
            item {
                TimeframeSelector(
                    current = currentTimeframe,
                    lang = lang,
                    onSelect = { viewModel.setTimeframe(it) }
                )
            }

            if (com.example.config.AppConfig.IS_TRIAL) {
                item {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val trialManager = remember { com.example.config.TrialManager(context) }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AmberContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = OnAmberContainer)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Versi Uji Coba: Sisa waktu ${trialManager.getRemainingMinutes()} menit",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = OnAmberContainer
                            )
                        }
                    }
                }
            }

            // Key Financial Metrics Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = AppStrings.get("dash_total_omzet", lang),
                            value = LapakViewModel.formatRupiah(summary.totalOmzet),
                            subtitle = "${summary.totalItemsSold} ${AppStrings.get("dash_items_sold", lang)}",
                            icon = Icons.Default.Payments,
                            containerColor = EmeraldContainer,
                            contentColor = EmeraldDark,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = AppStrings.get("dash_net_profit", lang),
                            value = LapakViewModel.formatRupiah(summary.netAdminProfit),
                            subtitle = "Kotor: ${LapakViewModel.formatRupiah(summary.grossAdminProfit)}",
                            icon = Icons.Default.TrendingUp,
                            containerColor = TealContainer,
                            contentColor = TealSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = AppStrings.get("dash_seller_rights", lang),
                            value = LapakViewModel.formatRupiah(summary.totalSellerShare),
                            subtitle = "Hak barang terjual",
                            icon = Icons.Default.Storefront,
                            containerColor = AmberContainer,
                            contentColor = AmberTertiary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = AppStrings.get("dash_operating_expenses", lang),
                            value = LapakViewModel.formatRupiah(summary.operatingExpenses),
                            subtitle = "Biaya operasional",
                            icon = Icons.Default.ReceiptLong,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Formula Info Banner
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Laba Bersih = (Omzet - Hak Penyetor) - Biaya Operasional",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick Operational Health Bar
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        QuickHealthItem(
                            label = AppStrings.get("dash_active_sellers", lang),
                            value = "${summary.activeSellersCount}",
                            icon = Icons.Default.CheckCircle,
                            iconColor = EmeraldPrimary
                        )
                        QuickHealthItem(
                            label = AppStrings.get("dash_leave_sellers", lang),
                            value = "${summary.leaveSellersCount}",
                            icon = Icons.Default.EventBusy,
                            iconColor = AmberTertiary
                        )
                        QuickHealthItem(
                            label = AppStrings.get("dash_available_stock", lang),
                            value = "${summary.availableStockCount}",
                            icon = Icons.Default.Inventory2,
                            iconColor = TealSecondary
                        )
                    }
                }
            }

            // Unpaid Rights Alert Card
            if (summary.totalUnpaidSellerPayables > 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AmberContainer),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigateTo(AppScreen.SELLERS) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AmberTertiary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = AppStrings.get("dash_unpaid_rights", lang),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = OnAmberContainer
                                )
                                Text(
                                    text = "Total hak penyetor yang belum diselesaikan: ${LapakViewModel.formatRupiah(summary.totalUnpaidSellerPayables)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnAmberContainer
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AmberTertiary)
                        }
                    }
                }
            }

            // Chart: Revenue & Profit Trend
            item {
                RevenueProfitBarChart(metrics = summary.dailyTrend)
            }

            // Best Selling Products
            if (summary.topSellingProducts.isNotEmpty()) {
                item {
                    Text(
                        text = AppStrings.get("dash_top_products", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(summary.topSellingProducts) { top ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = top.productName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Penyetor: ${top.sellerName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldPrimary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${top.soldQty} terjual",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Laba: ${LapakViewModel.formatRupiah(top.adminProfit)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TealSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Low Stock Warning List
            if (summary.lowStockProducts.isNotEmpty()) {
                item {
                    Text(
                        text = AppStrings.get("dash_low_stock", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AmberTertiary
                    )
                }

                items(summary.lowStockProducts) { low ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AmberContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = low.product.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Penyetor: ${low.sellerName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(AmberTertiary, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Sisa ${low.currentStock} ${low.product.unit}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun TimeframeSelector(
    current: String,
    lang: String,
    onSelect: (String) -> Unit
) {
    val items = listOf(
        "TODAY" to AppStrings.get("filter_today", lang),
        "7_DAYS" to AppStrings.get("filter_7days", lang),
        "THIS_MONTH" to AppStrings.get("filter_month", lang),
        "ALL" to AppStrings.get("filter_all", lang)
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items) { (key, label) ->
            FilterChip(
                selected = current == key,
                onClick = { onSelect(key) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = EmeraldPrimary,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = contentColor
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(contentColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun QuickHealthItem(
    label: String,
    value: String,
    icon: ImageVector,
    iconColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
