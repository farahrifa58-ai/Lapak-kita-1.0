package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.OperatingExpense
import com.example.i18n.AppStrings
import com.example.ui.components.AddExpenseDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.LapakViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: LapakViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lang by viewModel.currentLanguage.collectAsState()
    val isDark by viewModel.isDarkTheme.collectAsState()
    val shopProfile by viewModel.shopProfile.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()

    var shopName by remember(shopProfile) { mutableStateOf(shopProfile.shopName) }
    var ownerName by remember(shopProfile) { mutableStateOf(shopProfile.ownerName) }
    var waNumber by remember(shopProfile) { mutableStateOf(shopProfile.whatsappNumber) }
    var address by remember(shopProfile) { mutableStateOf(shopProfile.address) }
    var footerNote by remember(shopProfile) { mutableStateOf(shopProfile.footerMessage) }

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showRestorePrompt by remember { mutableStateOf(false) }
    var jsonInputForRestore by remember { mutableStateOf("") }

    BackHandler {
        viewModel.navigateTo(AppScreen.DASHBOARD)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = AppStrings.get("settings_title", lang),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.DASHBOARD) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
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
            // A. PROFIL LAPAK
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Store, contentDescription = null, tint = EmeraldPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = AppStrings.get("settings_shop_profile", lang),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        OutlinedTextField(
                            value = shopName,
                            onValueChange = { shopName = it },
                            label = { Text(AppStrings.get("settings_shop_name", lang)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it },
                            label = { Text(AppStrings.get("settings_owner_name", lang)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = waNumber,
                            onValueChange = { waNumber = it },
                            label = { Text(AppStrings.get("settings_wa_number", lang)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text(AppStrings.get("settings_address", lang)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = footerNote,
                            onValueChange = { footerNote = it },
                            label = { Text(AppStrings.get("settings_footer_note", lang)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = {
                                viewModel.updateProfile(shopName, ownerName, waNumber, address, footerNote)
                                Toast.makeText(context, "Profil lapak berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(AppStrings.get("btn_save", lang))
                        }
                    }
                }
            }

            // B. TEMA TAMPILAN & C. BAHASA
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Tema
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = null,
                                    tint = EmeraldPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = AppStrings.get("settings_theme_title", lang),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isDark) AppStrings.get("settings_theme_dark", lang) else AppStrings.get("settings_theme_light", lang),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = isDark,
                                onCheckedChange = { viewModel.toggleTheme(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrimary)
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Bahasa
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Language, contentDescription = null, tint = EmeraldPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = AppStrings.get("settings_lang_title", lang),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = lang == "id",
                                    onClick = { viewModel.setLanguage("id") },
                                    label = { Text("ID") },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldPrimary, selectedLabelColor = Color.White)
                                )
                                FilterChip(
                                    selected = lang == "en",
                                    onClick = { viewModel.setLanguage("en") },
                                    label = { Text("EN") },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldPrimary, selectedLabelColor = Color.White)
                                )
                            }
                        }
                    }
                }
            }

            // D. BIAYA OPERASIONAL MANAGER
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = EmeraldPrimary)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = AppStrings.get("settings_expenses_title", lang),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Total: ${LapakViewModel.formatRupiah(expenses.sumOf { it.amount })}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            IconButton(onClick = { showAddExpenseDialog = true }) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Tambah", tint = EmeraldPrimary)
                            }
                        }

                        if (expenses.isEmpty()) {
                            Text(
                                text = "Belum ada biaya operasional tercatat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Spacer(Modifier.height(8.dp))
                            expenses.take(4).forEach { exp ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(exp.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        if (exp.notes.isNotEmpty()) {
                                            Text(exp.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(LapakViewModel.formatRupiah(exp.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        IconButton(onClick = { viewModel.deleteExpense(exp) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // E. BACKUP & PEMULIHAN DATA & GOOGLE DRIVE
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = EmeraldPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = AppStrings.get("settings_backup_title", lang),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = AppStrings.get("settings_gdrive_info", lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.exportBackup { json ->
                                        viewModel.copyToClipboard(context, json)
                                        Toast.makeText(context, "Data cadangan JSON disalin ke clipboard!", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(AppStrings.get("settings_backup_btn", lang), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = { showRestorePrompt = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(AppStrings.get("settings_restore_btn", lang), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // F. TENTANG APLIKASI (CLICKABLE)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAboutDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(EmeraldContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = EmeraldDark)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = AppStrings.get("settings_about_title", lang),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Lapak Kita • ${AppStrings.get("settings_version", lang)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = EmeraldPrimary)
                    }
                }
            }

            item {
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    // Add Expense Dialog
    if (showAddExpenseDialog) {
        AddExpenseDialog(
            lang = lang,
            onConfirm = { title, amt, notes ->
                viewModel.addExpense(title, amt, notes) {
                    showAddExpenseDialog = false
                    Toast.makeText(context, "Biaya operasional berhasil dicatat!", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showAddExpenseDialog = false }
        )
    }

    // Restore JSON Prompt
    if (showRestorePrompt) {
        AlertDialog(
            onDismissRequest = { showRestorePrompt = false },
            title = { Text(AppStrings.get("settings_restore_btn", lang), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Tempelkan teks JSON cadangan untuk memulihkan seluruh data lapak:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jsonInputForRestore,
                        onValueChange = { jsonInputForRestore = it },
                        placeholder = { Text("{\"version\":1, \"shopProfile\":...}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importBackup(jsonInputForRestore) { ok, msg ->
                            showRestorePrompt = false
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Pulihkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestorePrompt = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // About App Modal
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text("Lapak Kita", fontWeight = FontWeight.Bold, color = EmeraldPrimary)
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.lapak_banner_1794055340379),
                        contentDescription = "Lapak Banner",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = AppStrings.get("settings_about_desc", lang),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = if (com.example.config.AppConfig.IS_TRIAL) "Versi: 1.0 (Trial 60 Menit)" else "Versi: 1.0 (Full Version)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Developer: Rafline Dev",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (!com.example.config.AppConfig.IS_TRIAL) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Status: Full Version Aktif (Lisensi Penuh)",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = AppStrings.get("settings_made_with_care", lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)) {
                    Text("Tutup")
                }
            }
        )
    }
}
