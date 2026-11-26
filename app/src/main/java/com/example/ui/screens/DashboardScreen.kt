package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SoldItemSummary
import com.example.data.model.ThemeMode
import com.example.data.repository.SellerDetailItem
import com.example.i18n.AppStrings
import com.example.ui.components.*
import com.example.ui.theme.LocalLapakColors
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.LapakViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: LapakViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lapakColors = LocalLapakColors.current
    val isDarkNow = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val lang by viewModel.currentLanguage.collectAsState()
    val shopProfile by viewModel.shopProfile.collectAsState()
    val summary by viewModel.dashboardSummary.collectAsState()
    val currentTimeframe by viewModel.timeframe.collectAsState()
    val sellers by viewModel.rawSellers.collectAsState()
    val sellersWithStats by viewModel.sellersWithStats.collectAsState()
    val productsWithDetails by viewModel.productsWithDetails.collectAsState()
    val payoutReceiptState by viewModel.lastSellerPaymentReceipt.collectAsState()
    val isSyncing by viewModel.isSyncingCloud.collectAsState()

    var showQuickDepositDialog by remember { mutableStateOf(false) }
    var showQuickPayoutPicker by remember { mutableStateOf(false) }
    var paymentTargetSeller by remember { mutableStateOf<SellerDetailItem?>(null) }
    var soldItemsForPayment by remember { mutableStateOf<List<SoldItemSummary>>(emptyList()) }

    LaunchedEffect(paymentTargetSeller) {
        val target = paymentTargetSeller
        soldItemsForPayment = if (target != null) {
            viewModel.getSoldItemsSummary(target.seller.id)
        } else {
            emptyList()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LapakTopHeader(
                title = shopProfile.shopName,
                subtitle = if (isSyncing) "Menyinkronkan ke cloud..." else AppStrings.get("tagline", lang),
                paletteKey = shopProfile.headerBgColorKey,
                leadingContent = {
                    ShopAvatarBadge(
                        iconKey = shopProfile.shopIconKey,
                        logoUri = shopProfile.logoUri,
                        size = 42.dp
                    )
                },
                actions = {
                    HeaderActionIconButton(
                        icon = if (isDarkNow) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (isDarkNow) "Ganti ke Mode Terang" else "Ganti ke Mode Gelap",
                        onClick = {
                            viewModel.setThemeMode(if (isDarkNow) ThemeMode.LIGHT else ThemeMode.DARK)
                        },
                        modifier = Modifier.testTag("dashboard_theme_toggle")
                    )
                    HeaderActionIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = "Pengaturan",
                        onClick = { viewModel.navigateTo(AppScreen.SETTINGS) },
                        modifier = Modifier.testTag("dashboard_settings_button")
                    )
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Timeframe Filter Chips (Tactile Neumorphic Extruded / Inset)
            item {
                TimeframeSelector(
                    current = currentTimeframe,
                    lang = lang,
                    onSelect = { viewModel.setTimeframe(it) }
                )
            }

            if (com.example.config.AppConfig.IS_TRIAL) {
                item {
                    val trialManager = remember { com.example.config.TrialManager(context) }
                    NeuCard(
                        cornerRadius = 14.dp,
                        shadowOffset = 4.dp,
                        blurRadius = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = lapakColors.warningText)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Versi Uji Coba: Sisa waktu ${trialManager.getRemainingMinutes()} menit",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = lapakColors.warningText
                            )
                        }
                    }
                }
            }

            // 4 Monochromatic Soft UI / Neumorphism Financial Summary Cards
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        MetricCard(
                            title = AppStrings.get("dash_total_omzet", lang),
                            value = LapakViewModel.formatRupiah(summary.totalOmzet),
                            subtitle = "${summary.totalItemsSold} ${AppStrings.get("dash_items_sold", lang)}",
                            icon = LucideIcons.Wallet,
                            accentColor = lapakColors.omzetCardAccent,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = AppStrings.get("dash_net_profit", lang),
                            value = LapakViewModel.formatRupiah(summary.netAdminProfit),
                            subtitle = "Kotor: ${LapakViewModel.formatRupiah(summary.grossAdminProfit)}",
                            icon = LucideIcons.TrendingUp,
                            accentColor = lapakColors.profitCardAccent,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        MetricCard(
                            title = AppStrings.get("dash_seller_rights", lang),
                            value = LapakViewModel.formatRupiah(summary.totalSellerShare),
                            subtitle = "Hak barang terjual",
                            icon = LucideIcons.HandCoins,
                            accentColor = lapakColors.payoutCardAccent,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = AppStrings.get("dash_operating_expenses", lang),
                            value = LapakViewModel.formatRupiah(summary.operatingExpenses),
                            subtitle = "Biaya lapak",
                            icon = LucideIcons.Receipt,
                            accentColor = lapakColors.expenseCardAccent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Operational Seller Count Summary Bar (Neumorphic Raised Card)
            item {
                NeuCard(
                    cornerRadius = 16.dp,
                    shadowOffset = 5.dp,
                    blurRadius = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        QuickHealthItem(
                            label = AppStrings.get("dash_active_sellers", lang),
                            value = "${summary.activeSellersCount}",
                            icon = Icons.Default.CheckCircle,
                            iconColor = lapakColors.omzetCardAccent
                        )
                        QuickHealthItem(
                            label = AppStrings.get("dash_leave_sellers", lang),
                            value = "${summary.leaveSellersCount}",
                            icon = Icons.Default.EventBusy,
                            iconColor = lapakColors.payoutCardAccent
                        )
                        QuickHealthItem(
                            label = AppStrings.get("dash_available_stock", lang),
                            value = "${summary.availableStockCount}",
                            icon = Icons.Default.Inventory2,
                            iconColor = lapakColors.profitCardAccent
                        )
                    }
                }
            }

            // 2 Tactile Neumorphic Quick Action Buttons (Fixed single PlusCircle icon + "Terima Titipan")
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Button 1: Terima Titipan (Single PlusCircle icon, soft emerald raised Neumorphic button)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .neuExtruded(
                                cornerRadius = 16.dp,
                                shadowOffset = 5.dp,
                                blurRadius = 10.dp,
                                surfaceColor = MaterialTheme.colorScheme.primary
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showQuickDepositDialog = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .testTag("dashboard_quick_deposit_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = LucideIcons.PlusCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "Terima Titipan",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1
                            )
                        }
                    }

                    // Button 2: Bayar Setoran (Neumorphic extruded card button with soft amber accent)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .neuExtruded(
                                cornerRadius = 16.dp,
                                shadowOffset = 5.dp,
                                blurRadius = 10.dp,
                                surfaceColor = lapakColors.neuSurface
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showQuickPayoutPicker = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .testTag("dashboard_quick_payout_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = LucideIcons.HandCoins,
                                contentDescription = null,
                                tint = lapakColors.payoutCardAccent,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "Bayar Setoran",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = lapakColors.payoutCardAccent,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Financial Report & Export (Excel / PDF) Entry Card (Neumorphic)
            item {
                NeuCard(
                    cornerRadius = 16.dp,
                    shadowOffset = 5.dp,
                    blurRadius = 10.dp,
                    onClick = { viewModel.navigateTo(AppScreen.FINANCIAL_REPORT) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_financial_report_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NeuIconBadge(
                            accentColor = lapakColors.omzetCardAccent,
                            size = 42.dp
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = null,
                                tint = lapakColors.omzetCardAccent,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Laporan Keuangan Lapak",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Buku kas kronologis, rekap penyetor & ekspor Excel / PDF",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Buka Laporan Keuangan",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Unpaid Rights Alert Card (Neumorphic extruded card with soft amber accent)
            if (summary.totalUnpaidSellerPayables > 0) {
                item {
                    NeuCard(
                        cornerRadius = 16.dp,
                        shadowOffset = 5.dp,
                        blurRadius = 10.dp,
                        onClick = { showQuickPayoutPicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_unpaid_alert_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(lapakColors.payoutCardAccent.copy(alpha = 0.07f))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NeuIconBadge(
                                accentColor = lapakColors.payoutCardAccent,
                                size = 40.dp
                            ) {
                                Icon(
                                    imageVector = LucideIcons.HandCoins,
                                    contentDescription = null,
                                    tint = lapakColors.payoutCardAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = AppStrings.get("dash_unpaid_rights", lang),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = lapakColors.payoutCardAccent
                                )
                                Text(
                                    text = "Total yang belum dibayarkan: ${LapakViewModel.formatRupiah(summary.totalUnpaidSellerPayables)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = lapakColors.payoutCardAccent
                            )
                        }
                    }
                }
            }

            // Chart: Revenue & Profit Trend
            item {
                RevenueProfitBarChart(metrics = summary.dailyTrend)
            }

            // Best Selling Products (Neumorphic cards)
            if (summary.topSellingProducts.isNotEmpty()) {
                item {
                    Text(
                        text = AppStrings.get("dash_top_products", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(summary.topSellingProducts) { top ->
                    NeuCard(
                        cornerRadius = 16.dp,
                        shadowOffset = 4.dp,
                        blurRadius = 9.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = top.productName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = top.sellerName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${top.soldQty} terjual",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Untung: ${LapakViewModel.formatRupiah(top.adminProfit)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = lapakColors.profitCardAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Low Stock Warning List (Neumorphic cards)
            if (summary.lowStockProducts.isNotEmpty()) {
                item {
                    Text(
                        text = AppStrings.get("dash_low_stock", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = lapakColors.stockLowText
                    )
                }

                items(summary.lowStockProducts) { low ->
                    val isZero = low.currentStock == 0
                    NeuCard(
                        cornerRadius = 16.dp,
                        shadowOffset = 4.dp,
                        blurRadius = 9.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = low.product.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = low.sellerName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isZero) lapakColors.stockEmptyBg else lapakColors.stockLowBg,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (isZero) "Habis" else "Sisa ${low.currentStock} ${low.product.unit}",
                                    color = if (isZero) lapakColors.stockEmptyText else lapakColors.stockLowText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // Quick Deposit Popup ("Terima Titipan")
    if (showQuickDepositDialog) {
        QuickDepositDialog(
            sellers = sellers,
            productsWithDetails = productsWithDetails,
            onConfirm = { sId, pId, qty, unit, sp, ss, note ->
                viewModel.depositStock(
                    sellerId = sId,
                    productId = pId,
                    quantity = qty,
                    unit = unit,
                    sellingPrice = sp,
                    sellerShare = ss,
                    notes = note,
                    onSuccess = {
                        showQuickDepositDialog = false
                        Toast.makeText(context, "Titipan +$qty $unit berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onDismiss = { showQuickDepositDialog = false }
        )
    }

    // Quick Payout Seller Picker ("Bayar Setoran")
    if (showQuickPayoutPicker) {
        QuickPayoutPickerDialog(
            sellersWithStats = sellersWithStats,
            onSelectSeller = { detail ->
                showQuickPayoutPicker = false
                paymentTargetSeller = detail
            },
            onDismiss = { showQuickPayoutPicker = false }
        )
    }

    // Payout Settlement Dialog
    paymentTargetSeller?.let { sellerDetail ->
        SellerPaymentDialog(
            sellerDetail = sellerDetail,
            lang = lang,
            soldItems = soldItemsForPayment,
            onConfirm = { amount, notes, method ->
                viewModel.recordSellerPayment(
                    sellerId = sellerDetail.seller.id,
                    amount = amount,
                    notes = notes,
                    method = method
                ) {
                    paymentTargetSeller = null
                    Toast.makeText(
                        context,
                        "Setoran ${LapakViewModel.formatRupiah(amount)} berhasil dicatat!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { paymentTargetSeller = null }
        )
    }

    // Payout Receipt Dialog
    payoutReceiptState?.let { state ->
        SellerPaymentReceiptDialog(
            payment = state.payment,
            seller = state.seller,
            remainingBalance = state.remainingBalance,
            shopProfile = shopProfile,
            lang = lang,
            soldItems = state.soldItems,
            onPrint = {
                viewModel.printSellerPayoutReceipt(
                    context = context,
                    payment = state.payment,
                    seller = state.seller,
                    remainingBalance = state.remainingBalance,
                    soldItems = state.soldItems
                )
            },
            onShareWhatsApp = { text -> viewModel.shareTextToWhatsApp(context, text, state.seller.whatsappNumber) },
            onShareGeneral = { text -> viewModel.shareTextGeneral(context, text) },
            onCopy = { text -> viewModel.copyToClipboard(context, text) },
            onDismiss = { viewModel.clearSellerPaymentReceipt() }
        )
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items) { (key, label) ->
            NeuFilterChip(
                label = label,
                selected = current == key,
                onClick = { onSelect(key) }
            )
        }
    }
}

/**
 * Monochromatic Soft UI / Neumorphism summary card:
 * Shares the base background surface (#EEF2F0 / #1A2220) with dual soft shadows,
 * while applying accent colors strictly to the circular raised icon badge and primary numeric value.
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    NeuCard(
        cornerRadius = 16.dp,
        shadowOffset = 5.dp,
        blurRadius = 10.dp,
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
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                NeuIconBadge(
                    accentColor = accentColor,
                    size = 34.dp
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
