package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SellerStatus
import com.example.data.repository.ProductDetailItem
import com.example.i18n.AppStrings
import com.example.ui.components.*
import com.example.ui.theme.LocalLapakColors
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.LapakViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: LapakViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lapakColors = LocalLapakColors.current
    val lang by viewModel.currentLanguage.collectAsState()
    val shopProfile by viewModel.shopProfile.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val products by viewModel.productsWithDetails.collectAsState()
    val sellers by viewModel.rawSellers.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val cartWarning by viewModel.cartWarningMessage.collectAsState()

    val cartTotalItems = remember(cart) { cart.sumOf { it.quantity } }
    val cartTotalAmount = remember(cart) { cart.sumOf { it.subtotal } }
    val cartQtyByProductId = remember(cart) {
        cart.groupBy { it.product.id }.mapValues { (_, items) -> items.sumOf { it.quantity } }
    }

    LaunchedEffect(cartWarning) {
        if (!cartWarning.isNullOrBlank()) {
            Toast.makeText(context, cartWarning, Toast.LENGTH_SHORT).show()
            viewModel.clearCartWarning()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Semua") }
    var selectedSellerId by remember { mutableStateOf<Long?>(null) }
    var onlyInStock by remember { mutableStateOf(false) }

    // Dialog states
    var showPromoPosterDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showAddProductDialog by remember { mutableStateOf(false) }
    var depositTargetProduct by remember { mutableStateOf<ProductDetailItem?>(null) }
    var editTargetProduct by remember { mutableStateOf<ProductDetailItem?>(null) }
    var deleteTargetProduct by remember { mutableStateOf<ProductDetailItem?>(null) }

    val categories = remember(products) {
        listOf("Semua") + products.map { it.product.category }.distinct()
    }

    val filteredProducts = remember(products, searchQuery, selectedCategory, selectedSellerId, onlyInStock) {
        products.filter { item ->
            val matchQuery = searchQuery.isBlank() ||
                item.product.name.contains(searchQuery, ignoreCase = true) ||
                item.seller.name.contains(searchQuery, ignoreCase = true)

            val matchCat = selectedCategory == "Semua" || item.product.category == selectedCategory
            val matchSeller = selectedSellerId == null || item.seller.id == selectedSellerId
            val matchStock = !onlyInStock || item.currentStock > 0

            matchQuery && matchCat && matchSeller && matchStock
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LapakTopHeader(
                title = AppStrings.get("prod_title", lang),
                subtitle = "${products.count { it.currentStock > 0 }} menu siap jual • ${products.sumOf { it.currentStock }} stok",
                paletteKey = shopProfile.headerBgColorKey
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddProductDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .padding(bottom = if (cartTotalItems > 0) 68.dp else 0.dp)
                    .size(52.dp)
                    .testTag("add_product_fab")
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = AppStrings.get("prod_add_new", lang),
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 2 Separated Side-by-Side Share Action Buttons: Promo Pelanggan & Info ke Penyetor
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Button 1: Promo Pelanggan (Poster Gambar tanpa nama dapur)
                        Card(
                            onClick = { showPromoPosterDialog = true },
                            colors = CardDefaults.cardColors(containerColor = lapakColors.omzetCardBg),
                            border = BorderStroke(1.dp, lapakColors.omzetCardBorder),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_promo_pelanggan")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 11.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(Modifier.width(9.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Promo Pelanggan",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = lapakColors.omzetCardAccent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Poster Menu Ready",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Button 2: Info ke Penyetor (Teks Rincian Stok grup WA penjual)
                        Card(
                            onClick = { showShareDialog = true },
                            colors = CardDefaults.cardColors(containerColor = lapakColors.whatsappBg),
                            border = BorderStroke(1.dp, lapakColors.whatsappText.copy(alpha = 0.28f)),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_info_penyetor")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 11.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(lapakColors.whatsappText, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.FactCheck,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(Modifier.width(9.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Info ke Penyetor",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = lapakColors.whatsappText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Teks Stok Grup WA",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Search Bar
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(AppStrings.get("prod_search_hint", lang)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Bersihkan")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = lapakColors.cardBorder
                        ),
                        singleLine = true
                    )
                }

                // Category Chips
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }

                // Compact Filter Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onlyInStock = !onlyInStock }
                        ) {
                            Checkbox(
                                checked = onlyInStock,
                                onCheckedChange = { onlyInStock = it }
                            )
                            Text(
                                text = "Hanya ready stok",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        var expSeller by remember { mutableStateOf(false) }
                        val currentSellerLabel = sellers.firstOrNull { it.id == selectedSellerId }?.name ?: "Semua Dapur"

                        Box {
                            TextButton(onClick = { expSeller = true }) {
                                Text(
                                    text = currentSellerLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }

                            DropdownMenu(
                                expanded = expSeller,
                                onDismissRequest = { expSeller = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Semua Dapur") },
                                    onClick = {
                                        selectedSellerId = null
                                        expSeller = false
                                    }
                                )
                                sellers.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.name) },
                                        onClick = {
                                            selectedSellerId = s.id
                                            expSeller = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Compact, Dense Etalase Product List (No modal/profit clutter)
                if (filteredProducts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = AppStrings.get("prod_empty", lang),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredProducts, key = { it.product.id }) { item ->
                        CompactEtalaseProductCard(
                            item = item,
                            qtyInCart = cartQtyByProductId[item.product.id] ?: 0,
                            onAddToCart = {
                                val targetLot = item.activeLots.firstOrNull()
                                if (targetLot != null) {
                                    viewModel.addToCart(item.product, item.seller, targetLot, 1)
                                }
                            },
                            onAddDeposit = { depositTargetProduct = item },
                            onEdit = { editTargetProduct = item },
                            onDelete = { deleteTargetProduct = item }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(if (cartTotalItems > 0) 148.dp else 96.dp))
                }
            }

            // Floating Bottom Cart Summary Bar when items are in Cashier cart
            AnimatedVisibility(
                visible = cartTotalItems > 0,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .clickable { viewModel.navigateTo(AppScreen.CASHIER) }
                        .testTag("floating_cart_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.ShoppingBag,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "$cartTotalItems Item di Kasir • ${LapakViewModel.formatRupiah(cartTotalAmount)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Buka Kasir",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Customer Promo Poster Dialog ("Promo Pelanggan")
    if (showPromoPosterDialog) {
        PromoPosterDialog(
            shopProfile = shopProfile,
            appSettings = appSettings,
            productsWithDetails = products,
            onDismiss = { showPromoPosterDialog = false }
        )
    }

    // Vendor Stock Info Text Dialog ("Info ke Penyetor")
    if (showShareDialog) {
        ShareStockReportDialog(
            sellers = sellers,
            viewModel = viewModel,
            lang = lang,
            onDismiss = { showShareDialog = false }
        )
    }

    // Add Product Dialog (with automatic Admin Fee calculation)
    if (showAddProductDialog) {
        AddProductDialog(
            sellers = sellers,
            preselectedSellerId = selectedSellerId,
            appSettings = appSettings,
            lang = lang,
            onConfirm = { sId, name, cat, unit, sp, ss, initStock ->
                viewModel.addProduct(sId, name, cat, unit, sp, ss, initStock) {
                    showAddProductDialog = false
                }
            },
            onDismiss = { showAddProductDialog = false }
        )
    }

    // Deposit Stock Dialog
    depositTargetProduct?.let { prodDetail ->
        DepositStockDialog(
            productDetail = prodDetail,
            lang = lang,
            onConfirm = { qty, sp, ss, notes ->
                viewModel.depositStock(
                    sellerId = prodDetail.seller.id,
                    productId = prodDetail.product.id,
                    quantity = qty,
                    unit = prodDetail.product.unit,
                    sellingPrice = sp,
                    sellerShare = ss,
                    notes = notes,
                    onSuccess = {
                        depositTargetProduct = null
                        Toast.makeText(context, "Stok +$qty ${prodDetail.product.unit} ditambahkan!", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                )
            },
            onDismiss = { depositTargetProduct = null }
        )
    }

    // Edit Product Dialog (shows full share/profit breakdown & auto tariff button)
    editTargetProduct?.let { prodDetail ->
        EditProductDialog(
            product = prodDetail.product,
            seller = prodDetail.seller,
            appSettings = appSettings,
            lang = lang,
            onConfirm = { updatedProduct ->
                viewModel.updateProduct(updatedProduct) {
                    editTargetProduct = null
                }
            },
            onDismiss = { editTargetProduct = null }
        )
    }

    // Delete Product Dialog
    deleteTargetProduct?.let { prodDetail ->
        DeleteConfirmDialog(
            title = "Hapus Produk",
            message = "Apakah Anda yakin ingin menghapus produk '${prodDetail.product.name}' (${prodDetail.seller.name})? Semua riwayat stok produk ini akan terhapus.",
            onConfirm = {
                viewModel.deleteProduct(prodDetail.product) {
                    deleteTargetProduct = null
                }
            },
            onDismiss = { deleteTargetProduct = null }
        )
    }
}

@Composable
private fun CompactEtalaseProductCard(
    item: ProductDetailItem,
    qtyInCart: Int,
    onAddToCart: () -> Unit,
    onAddDeposit: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val lapakColors = LocalLapakColors.current
    var showMenu by remember { mutableStateOf(false) }

    val (stockBg, stockText, stockLabel) = when {
        item.currentStock <= 0 -> Triple(lapakColors.stockEmptyBg, lapakColors.stockEmptyText, "Habis")
        item.currentStock <= 3 -> Triple(lapakColors.stockLowBg, lapakColors.stockLowText, "Sisa ${item.currentStock} ${item.product.unit}")
        else -> Triple(lapakColors.stockSafeBg, lapakColors.stockSafeText, "${item.currentStock} ${item.product.unit}")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, lapakColors.cardBorder),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Row 1: Product Name + Seller Name on left, Stock Badge + overflow menu on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = item.product.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = item.seller.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.seller.status != SellerStatus.AKTIF) {
                            Spacer(Modifier.width(6.dp))
                            StatusBadge(status = item.seller.status)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(stockBg, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stockLabel,
                            color = stockText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Opsi Produk",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Tambah Stok") },
                                leadingIcon = { Icon(Icons.Default.AddBox, contentDescription = null) },
                                onClick = { showMenu = false; onAddDeposit() }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Produk & Harga") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { showMenu = false; onEdit() }
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus Produk", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = { showMenu = false; onDelete() }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Row 2: Selling Price prominently on left, compact "+ Ke Kasir" CTA on right (NO modal/laba admin clutter!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = LapakViewModel.formatRupiah(item.product.sellingPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " / ${item.product.unit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Button(
                    onClick = onAddToCart,
                    enabled = item.currentStock > 0,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .testTag("add_to_cart_btn_${item.product.id}")
                ) {
                    Icon(
                        Icons.Default.AddShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (qtyInCart > 0) "+ Ke Kasir ($qtyInCart)" else "+ Ke Kasir",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
