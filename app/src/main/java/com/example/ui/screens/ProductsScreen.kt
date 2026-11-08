package com.example.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SellerStatus
import com.example.data.repository.ProductDetailItem
import com.example.i18n.AppStrings
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.LapakViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: LapakViewModel,
    modifier: Modifier = Modifier
) {
    val lang by viewModel.currentLanguage.collectAsState()
    val products by viewModel.productsWithDetails.collectAsState()
    val sellers by viewModel.rawSellers.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Semua") }
    var selectedSellerId by remember { mutableStateOf<Long?>(null) }
    var onlyInStock by remember { mutableStateOf(false) }

    // Dialog states
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = AppStrings.get("prod_title", lang),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EmeraldContainer,
                    titleContentColor = EmeraldPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddProductDialog = true },
                containerColor = EmeraldPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(50.dp)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // PROMINENT BUTTON: BAGIKAN STOK TERSISA
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showShareDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = AppStrings.get("share_stock_button", lang),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Bagikan ke penyetor via WhatsApp / Lainnya",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = Color.White
                        )
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
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
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
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // In Stock Filter & Seller Filter Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = onlyInStock,
                            onCheckedChange = { onlyInStock = it }
                        )
                        Text(
                            text = "Hanya stok tersedia",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable { onlyInStock = !onlyInStock }
                        )
                    }

                    // Seller dropdown filter
                    var expSeller by remember { mutableStateOf(false) }
                    val currentSellerLabel = sellers.firstOrNull { it.id == selectedSellerId }?.name ?: "Semua Penyetor"

                    Box {
                        TextButton(onClick = { expSeller = true }) {
                            Text(
                                text = currentSellerLabel,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = expSeller,
                            onDismissRequest = { expSeller = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Semua Penyetor") },
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

            // Product Cards List
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
                items(filteredProducts) { item ->
                    ProductCard(
                        item = item,
                        lang = lang,
                        onAddToCart = {
                            val targetLot = item.activeLots.firstOrNull()
                            if (targetLot != null) {
                                viewModel.addToCart(item.product, item.seller, targetLot, 1)
                                viewModel.navigateTo(AppScreen.CASHIER)
                            }
                        },
                        onAddDeposit = {
                            depositTargetProduct = item
                        },
                        onEdit = {
                            editTargetProduct = item
                        },
                        onDelete = {
                            deleteTargetProduct = item
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(80.dp)) // Space for FAB
            }
        }
    }

    // Share Dialog
    if (showShareDialog) {
        ShareStockReportDialog(
            sellers = sellers,
            viewModel = viewModel,
            lang = lang,
            onDismiss = { showShareDialog = false }
        )
    }

    // Add Product Dialog
    if (showAddProductDialog) {
        AddProductDialog(
            sellers = sellers,
            preselectedSellerId = selectedSellerId,
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
                    onSuccess = { depositTargetProduct = null },
                    onError = { /* handled */ }
                )
            },
            onDismiss = { depositTargetProduct = null }
        )
    }

    // Edit Product Dialog
    editTargetProduct?.let { prodDetail ->
        EditProductDialog(
            product = prodDetail.product,
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
fun ProductCard(
    item: ProductDetailItem,
    lang: String,
    onAddToCart: () -> Unit,
    onAddDeposit: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Header Row: Product Name & Seller on left, Stock Pill + Edit + Delete on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                    Text(
                        text = item.product.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
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
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = item.seller.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.seller.status != SellerStatus.AKTIF) {
                            Spacer(Modifier.width(6.dp))
                            StatusBadge(status = item.seller.status)
                        }
                    }
                }

                // Action area: Stock Pill + Edit Button + Delete Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Remaining Stock Pill
                    Box(
                        modifier = Modifier
                            .background(
                                if (item.currentStock > 5) EmeraldContainer else if (item.currentStock > 0) TealContainer else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${item.currentStock} ${item.product.unit}",
                            color = if (item.currentStock > 5) EmeraldDark else if (item.currentStock > 0) TealSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    // Edit button
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Produk",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Hapus Produk",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Pricing Grid: Compact 3-column banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = AppStrings.get("prod_selling_price", lang),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = LapakViewModel.formatRupiah(item.product.sellingPrice),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Column {
                    Text(
                        text = AppStrings.get("prod_seller_share", lang),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = LapakViewModel.formatRupiah(item.product.sellerShare),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TealSecondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = AppStrings.get("prod_admin_profit", lang),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = LapakViewModel.formatRupiah(item.product.adminProfitPerUnit),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action Buttons: Compact Height
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAddDeposit,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.AddBox,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = AppStrings.get("prod_add_deposit", lang),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = onAddToCart,
                    enabled = item.currentStock > 0,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = AppStrings.get("prod_add_to_cart", lang),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
