package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.repository.CartItem
import com.example.data.repository.ProductDetailItem
import com.example.i18n.AppStrings
import com.example.ui.components.ReceiptCustomerDialog
import com.example.ui.components.SelectSellerLotDialog
import com.example.ui.theme.LocalLapakColors
import com.example.ui.viewmodel.LapakViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierScreen(
    viewModel: LapakViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lapakColors = LocalLapakColors.current
    val lang by viewModel.currentLanguage.collectAsState()
    val shopProfile by viewModel.shopProfile.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val cashReceivedText by viewModel.cashReceivedText.collectAsState()
    val paymentMethod by viewModel.paymentMethod.collectAsState()
    val lastCompletedTrx by viewModel.lastCompletedTransaction.collectAsState()
    val productsWithDetails by viewModel.productsWithDetails.collectAsState()
    val cartWarning by viewModel.cartWarningMessage.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var matchingMultipleSellers by remember { mutableStateOf<List<ProductDetailItem>?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(cartWarning) {
        if (!cartWarning.isNullOrBlank()) {
            Toast.makeText(context, cartWarning, Toast.LENGTH_SHORT).show()
            viewModel.clearCartWarning()
        }
    }

    val totalAmount = remember(cart) { cart.sumOf { it.subtotal } }
    val cashReceived = remember(cashReceivedText, totalAmount) {
        cashReceivedText.toDoubleOrNull() ?: totalAmount
    }
    val changeAmount = remember(cashReceived, totalAmount) {
        (cashReceived - totalAmount).coerceAtLeast(0.0)
    }

    val quickAddProducts = remember(productsWithDetails, searchQuery) {
        productsWithDetails.filter { item ->
            item.currentStock > 0 &&
                (searchQuery.isBlank() ||
                    item.product.name.contains(searchQuery, ignoreCase = true) ||
                    item.seller.name.contains(searchQuery, ignoreCase = true))
        }
    }

    val stockByProductId = remember(productsWithDetails) {
        productsWithDetails.associate { it.product.id to it.currentStock }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = AppStrings.get("cashier_title", lang),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    if (cart.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearCart() },
                            modifier = Modifier.testTag("cashier_clear_cart_button")
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Kosongkan Keranjang",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp)
        ) {
            // Quick Product Selector Bar
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, lapakColors.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari & tambah menu ke kasir...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Hapus Pencarian")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cashier_search_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        if (quickAddProducts.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(quickAddProducts.take(12), key = { it.product.id }) { item ->
                                    Surface(
                                        onClick = {
                                            val sameNameProducts = productsWithDetails.filter {
                                                it.product.name.equals(item.product.name, ignoreCase = true) && it.currentStock > 0
                                            }

                                            if (sameNameProducts.size > 1) {
                                                matchingMultipleSellers = sameNameProducts
                                            } else {
                                                val targetLot = item.activeLots.firstOrNull()
                                                if (targetLot != null) {
                                                    viewModel.addToCart(item.product, item.seller, targetLot, 1)
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = item.product.name,
                                                maxLines = 1,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${LapakViewModel.formatRupiah(item.product.sellingPrice)} • ${item.seller.name}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Cart Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Keranjang Belanja (${cart.sumOf { it.quantity }} item)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (cart.isNotEmpty()) {
                        Text(
                            text = LapakViewModel.formatRupiah(totalAmount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (cart.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, lapakColors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = AppStrings.get("cashier_cart_empty", lang),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = AppStrings.get("cashier_cart_hint", lang),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(cart, key = { "${it.product.id}_${it.stockLot.id}" }) { item ->
                    val maxAvailable = stockByProductId[item.product.id] ?: item.stockLot.currentQty
                    CartItemRow(
                        item = item,
                        maxStock = maxAvailable,
                        onIncrease = { viewModel.updateCartQty(item, item.quantity + 1) },
                        onDecrease = { viewModel.updateCartQty(item, item.quantity - 1) },
                        onRemove = { viewModel.removeFromCart(item) }
                    )
                }
            }

            // Payment Section
            if (cart.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, lapakColors.cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Pembayaran",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(Modifier.height(12.dp))

                            Text(
                                text = AppStrings.get("cashier_payment_method", lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Tunai", "QRIS", "Transfer", "Lainnya").forEach { method ->
                                    FilterChip(
                                        selected = paymentMethod == method,
                                        onClick = { viewModel.setPaymentMethod(method) },
                                        label = { Text(method, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            if (paymentMethod == "Tunai") {
                                OutlinedTextField(
                                    value = cashReceivedText,
                                    onValueChange = { viewModel.setCashReceived(it.filter { c -> c.isDigit() }) },
                                    label = { Text(AppStrings.get("cashier_cash_received", lang)) },
                                    placeholder = { Text(LapakViewModel.formatRupiah(totalAmount)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )

                                Spacer(Modifier.height(8.dp))

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    item {
                                        SuggestionChip(
                                            onClick = { viewModel.setCashReceived(totalAmount.toInt().toString()) },
                                            label = { Text("Uang Pas") }
                                        )
                                    }
                                    val roundTargets = listOf(10000, 20000, 50000, 100000)
                                    items(roundTargets) { target ->
                                        if (target >= totalAmount) {
                                            SuggestionChip(
                                                onClick = { viewModel.setCashReceived(target.toString()) },
                                                label = { Text(LapakViewModel.formatRupiah(target.toDouble())) }
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = AppStrings.get("cashier_change", lang),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = LapakViewModel.formatRupiah(changeAmount),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (cashReceived >= totalAmount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (isProcessing) return@Button
                                    isProcessing = true
                                    viewModel.checkout(
                                        onSuccess = { trx ->
                                            isProcessing = false
                                            Toast.makeText(context, "Transaksi ${trx.transactionNumber} Berhasil!", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err ->
                                            isProcessing = false
                                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                },
                                enabled = !isProcessing && cart.isNotEmpty() && (paymentMethod != "Tunai" || cashReceived >= totalAmount),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("cashier_checkout_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "${AppStrings.get("cashier_btn_pay", lang)} • ${LapakViewModel.formatRupiah(totalAmount)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    matchingMultipleSellers?.let { list ->
        SelectSellerLotDialog(
            matchingProducts = list,
            lang = lang,
            onSelect = { selectedProd ->
                val targetLot = selectedProd.activeLots.firstOrNull()
                if (targetLot != null) {
                    viewModel.addToCart(selectedProd.product, selectedProd.seller, targetLot, 1)
                }
                matchingMultipleSellers = null
            },
            onDismiss = { matchingMultipleSellers = null }
        )
    }

    lastCompletedTrx?.let { trx ->
        val itemsFlow = remember(trx.id) { viewModel.getTransactionItems(trx.id) }
        val items by itemsFlow.collectAsState(initial = emptyList())

        ReceiptCustomerDialog(
            transaction = trx,
            items = items,
            shopProfile = shopProfile,
            lang = lang,
            onDismiss = { viewModel.clearLastCompletedTransaction() },
            onPrint = { viewModel.printCustomerReceipt(context, trx, items) },
            onShareWhatsApp = { text -> viewModel.shareTextToWhatsApp(context, text) },
            onShareGeneral = { text -> viewModel.shareTextGeneral(context, text) },
            onCopy = { text -> viewModel.copyToClipboard(context, text) }
        )
    }
}

@Composable
fun CartItemRow(
    item: CartItem,
    maxStock: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit
) {
    val lapakColors = LocalLapakColors.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, lapakColors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Dapur: ${item.seller.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${LapakViewModel.formatRupiah(item.product.sellingPrice)} / ${item.product.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Subtotal: ${LapakViewModel.formatRupiah(item.subtotal)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onDecrease),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Kurang",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = "${item.quantity}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.widthIn(min = 24.dp),
                    textAlign = TextAlign.Center
                )

                val canIncrease = item.quantity < maxStock
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (canIncrease) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .then(
                            if (canIncrease) Modifier.clickable(onClick = onIncrease) else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Tambah",
                        tint = if (canIncrease) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Hapus",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}
