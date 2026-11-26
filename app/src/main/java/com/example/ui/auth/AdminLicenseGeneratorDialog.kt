package com.example.ui.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.config.LicenseStorage
import com.example.data.firebase.LicenseCodeRecord
import com.example.data.firebase.LicenseRepository
import com.example.ui.theme.LocalLapakColors
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminPinPromptDialog(
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Akses Admin Developer",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Masukkan PIN Admin untuk membuka panel Generator Kode Lisensi Lapak Kita.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it.filter { ch -> ch.isDigit() }.take(8)
                        errorMsg = null
                    },
                    label = { Text("PIN Admin") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = errorMsg != null,
                    supportingText = errorMsg?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_pin_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (LicenseStorage.verifyAdminPin(pinInput)) {
                        onVerified()
                    } else {
                        errorMsg = "PIN Admin salah"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("btn_confirm_admin_pin")
            ) {
                Text("Buka Panel", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun AdminLicenseGeneratorDialog(
    licenseRepository: LicenseRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lapakColors = LocalLapakColors.current

    val auth = remember { Firebase.auth }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    var buyerNote by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var latestGeneratedCode by remember { mutableStateOf<String?>(null) }
    var licenses by remember { mutableStateOf<List<LicenseCodeRecord>>(emptyList()) }
    var isLoadingList by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val dateFormat = remember {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }
    }

    // Observe Firestore license_codes only while authenticated as developer
    LaunchedEffect(currentUser?.uid) {
        val user = currentUser
        if (user == null) {
            licenses = emptyList()
            isLoadingList = false
            return@LaunchedEffect
        }
        isLoadingList = true
        errorMessage = null
        try {
            licenseRepository.observeAllLicenses().collect { list ->
                licenses = list
                isLoadingList = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            isLoadingList = false
            errorMessage = if (e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
                "Akun Google (${user.email ?: "-"}) bukan akun Developer terdaftar. Masuk dengan akun ${LicenseStorage.DEVELOPER_EMAIL} untuk mengelola kode lisensi."
            } else {
                "Gagal memuat riwayat lisensi: ${e.message ?: "kesalahan jaringan"}"
            }
        }
    }

    fun copyLicenseToClipboard(code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Kode Lisensi Lapak Kita", code))
        Toast.makeText(context, "Kode $code disalin!", Toast.LENGTH_SHORT).show()
    }

    fun shareCodeToWhatsApp(code: String, buyer: String) {
        val greeting = if (buyer.isNotBlank()) "Halo $buyer,\n\n" else "Halo,\n\n"
        val msg = "${greeting}Terima kasih telah memesan *Lapak Kita*!\n\n" +
            "Berikut Kode Lisensi resmi Anda (berlaku permanen untuk 1 perangkat):\n" +
            "*$code*\n\n" +
            "Silakan masukkan kode tersebut pada layar Aktivasi Aplikasi Lapak Kita. Terima kasih!"
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(msg)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            copyLicenseToClipboard(code)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, lapakColors.cardBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .widthIn(max = 540.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Admin License Generator",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Koleksi Firestore: license_codes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }

                // Body
                if (currentUser == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Verifikasi Akun Google Developer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Masuk dengan akun Google Developer untuk membuat & melihat daftar kode lisensi di Firebase Firestore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                if (isSigningIn) return@Button
                                scope.launch {
                                    isSigningIn = true
                                    when (val outcome = GoogleAuthHelper.signInWithGoogle(context)) {
                                        is GoogleSignInOutcome.Success -> Unit
                                        is GoogleSignInOutcome.Cancelled -> Unit
                                        is GoogleSignInOutcome.Error -> {
                                            Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    isSigningIn = false
                                }
                            },
                            enabled = !isSigningIn,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_dev_google_signin")
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Menghubungkan...")
                            } else {
                                Text("Masuk dengan Google (Developer)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Generator Form Card
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, lapakColors.cardBorder),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Buat Kode Lisensi Baru",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OutlinedTextField(
                                        value = buyerNote,
                                        onValueChange = { buyerNote = it.take(100) },
                                        label = { Text("Catatan Nama Pembeli (Opsional)") },
                                        placeholder = { Text("Contoh: Bu Ani - Lapak Kue Bandung") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_license_buyer_note")
                                    )

                                    Button(
                                        onClick = {
                                            if (isGenerating) return@Button
                                            isGenerating = true
                                            errorMessage = null
                                            scope.launch {
                                                val res = licenseRepository.generateAndSaveNewLicense(buyerNote)
                                                isGenerating = false
                                                if (res.isSuccess) {
                                                    val created = res.getOrThrow()
                                                    latestGeneratedCode = created.code
                                                    buyerNote = ""
                                                    copyLicenseToClipboard(created.code)
                                                } else {
                                                    errorMessage = res.exceptionOrNull()?.message
                                                        ?: "Gagal membuat kode lisensi"
                                                }
                                            }
                                        },
                                        enabled = !isGenerating,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_generate_license")
                                    ) {
                                        if (isGenerating) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("Membuat Kode...", fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Generate Kode Lisensi Baru", fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    latestGeneratedCode?.let { newest ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = lapakColors.omzetCardBg,
                                            border = BorderStroke(1.dp, lapakColors.omzetCardBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Kode Baru Siap Dikirim:",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = lapakColors.omzetCardAccent
                                                    )
                                                    Text(
                                                        text = newest,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = lapakColors.omzetCardAccent
                                                    )
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    FilledTonalIconButton(
                                                        onClick = { copyLicenseToClipboard(newest) },
                                                        modifier = Modifier.size(38.dp)
                                                    ) {
                                                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin Kode", modifier = Modifier.size(18.dp))
                                                    }
                                                    FilledTonalIconButton(
                                                        onClick = { shareCodeToWhatsApp(newest, "") },
                                                        modifier = Modifier.size(38.dp)
                                                    ) {
                                                        Icon(Icons.Default.Share, contentDescription = "Kirim via WA", modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Error Banner if unauthorized or network issue
                        if (!errorMessage.isNullOrBlank()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = lapakColors.dangerBg,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = errorMessage!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = lapakColors.dangerText
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    GoogleAuthHelper.signOut(context)
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Ganti Akun Google")
                                        }
                                    }
                                }
                            }
                        }

                        // Summary Header
                        item {
                            val availableCount = licenses.count { !it.used }
                            val usedCount = licenses.count { it.used }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Riwayat Kode Lisensi (${licenses.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$availableCount Tersedia • $usedCount Dipakai",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isLoadingList) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (licenses.isEmpty() && errorMessage.isNullOrBlank()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, lapakColors.cardBorder),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Belum ada kode lisensi yang dibuat. Tekan tombol \"Generate Kode Lisensi Baru\" di atas.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(licenses, key = { it.code }) { item ->
                                LicenseHistoryRowCard(
                                    item = item,
                                    createdLabel = if (item.createdAtMillis > 0L) dateFormat.format(Date(item.createdAtMillis)) else "-",
                                    usedLabel = item.usedAtMillis?.let { dateFormat.format(Date(it)) },
                                    onCopy = { copyLicenseToClipboard(item.code) },
                                    onShareWa = { shareCodeToWhatsApp(item.code, item.buyerNote) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseHistoryRowCard(
    item: LicenseCodeRecord,
    createdLabel: String,
    usedLabel: String?,
    onCopy: () -> Unit,
    onShareWa: () -> Unit
) {
    val lapakColors = LocalLapakColors.current
    val badgeBg = if (item.used) lapakColors.stockEmptyBg else lapakColors.stockSafeBg
    val badgeText = if (item.used) lapakColors.stockEmptyText else lapakColors.stockSafeText
    val statusText = if (item.used) "Sudah Dipakai" else "Tersedia"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, lapakColors.cardBorder),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.code,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        color = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (item.buyerNote.isNotBlank()) {
                Text(
                    text = "Pembeli: ${item.buyerNote}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = if (item.used && usedLabel != null) {
                    "Dibuat: $createdLabel • Aktif: $usedLabel"
                } else {
                    "Dibuat: $createdLabel WIB"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCopy,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .testTag("btn_copy_license_${item.code}")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Salin Kode", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                if (!item.used) {
                    FilledTonalButton(
                        onClick = onShareWa,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = lapakColors.whatsappBg,
                            contentColor = lapakColors.whatsappText
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Kirim WA", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
