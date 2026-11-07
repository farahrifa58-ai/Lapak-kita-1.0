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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CartItem
import com.example.data.repository.ProductDetailItem
import com.example.i18n.AppStrings
import com.example.ui.components.ReceiptCustomerDialog
import com.example.ui.components.SelectSellerLotDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.LapakViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierScreen(
    viewModel: LapakViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lang by viewModel.currentLanguage.collectAsState()
    val shopProfile by viewModel.shopProfile.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val cashReceivedText by viewModel.cashReceivedText.collectAsState()
    val paymentMethod by viewModel.paymentMethod.collectAsState()
    val lastCompletedTrx by viewModel.lastCompletedTransaction.collectAsState()
    val productsWithDetails by viewModel.productsWithDetails.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var matchingMultipleSellers by remember { mutableStateOf<List<ProductDetailItem>?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val totalAmount = remember(cart) { cart.sumOf { it.subtotal } }
    val cashReceived = remember(cashReceivedText, totalAmount) {
        cashReceivedText.toDoubleOrNull() ?: totalAmount
    }
    val changeAmount = remember(cashReceived, totalAmount) {
        (cashReceived - totalAmount).coerceAtLeast(0.0)
    }

    // Filter available products for quick add
    val quickAddProducts = remember(productsWithDetails, searchQuery) {
        productsWithDetails.filter { item ->
            item.currentStock > 0 &&
                (searchQuery.isBlank() || item.product.name.contains(searchQuery, ignoreCase = true) || item.seller.name.contains(searchQuery, ignoreCase = true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = AppStrings.get("cashier_title", lang),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                },
                actions = {
                    if (cart.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearCart() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Cart", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Quick Product Selector Bar
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Spacer(Modifier.height(8.dp))

                        // Quick Horizontal Chips of Available Products
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(quickAddProducts.take(10)) { item ->
                                SuggestionChip(
                                    onClick = {
                                        // Check if another seller consigns a product with the same name!
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
                                    label = {
                                        Column {
                                            Text(item.product.name, maxLines = 1, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                            Text(
                                                "${LapakViewModel.formatRupiah(item.product.sellingPrice)} • ${item.seller.name}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldPrimary,
                                                maxLines = 1
                                            )
                                        }
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = EmeraldContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Cart Items Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Keranjang Belanja (${cart.sumOf { it.quantity }} item)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (cart.isNotEmpty()) {
                        Text(
                            text = LapakViewModel.formatRupiah(totalAmount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary
                        )
                    }
                }
            }

            if (cart.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = AppStrings.get("cashier_cart_empty", lang),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
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
                items(cart) { item ->
                    CartItemRow(
                        item = item,
                        onIncrease = { viewModel.updateCartQty(item, item.quantity + 1) },
                        onDecrease = { viewModel.updateCartQty(item, item.quantity - 1) },
                        onRemove = { viewModel.removeFromCart(item) }
                    )
                }
            }

            // Payment Controls Section (Only shown if cart is not empty)
            if (cart.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Pembayaran",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(12.dp))

                            // Payment Method Selector
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
                                        label = { Text(method, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EmeraldPrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // If Cash, provide cash input and quick denomination buttons
                            if (paymentMethod == "Tunai") {
                                OutlinedTextField(
                                    value = cashReceivedText,
                                    onValueChange = { viewModel.setCashReceived(it.filter { c -> c.isDigit() }) },
                                    label = { Text(AppStrings.get("cashier_cash_received", lang)) },
                                    placeholder = { Text(LapakViewModel.formatRupiah(totalAmount)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                Spacer(Modifier.height(8.dp))

                                // Quick Cash Buttons: Uang Pas, +10k, +20k, +50k, +100k
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

                                // Change Calculation
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = AppStrings.get("cashier_change", lang),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = LapakViewModel.formatRupiah(changeAmount),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (cashReceived >= totalAmount) EmeraldPrimary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Checkout Button
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
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
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

            item {
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // Modal when multiple sellers consign same product name
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

    // Customer Receipt Dialog popup after successful transaction
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
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                    fontWeight = FontWeight.Bold
                )
                // Internal seller identification for cashier
                Text(
                    text = "Penyetor: ${item.seller.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldPrimary,
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
                    color = EmeraldPrimary
                )
            }

            // Qty Stepper Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onDecrease,
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Kurang", modifier = Modifier.size(16.dp))
                }

                Text(
                    text = "${item.quantity}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 24.dp),
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = onIncrease,
                    enabled = item.quantity < item.stockLot.currentQty,
                    modifier = Modifier
                        .size(32.dp)
                        .background(EmeraldContainer, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah", tint = EmeraldDark, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
