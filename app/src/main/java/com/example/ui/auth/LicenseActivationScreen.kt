package com.example.ui.auth

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.config.LicenseStorage
import com.example.data.firebase.LicenseActivationOutcome
import com.example.data.firebase.LicenseRepository
import com.example.ui.theme.LocalLapakColors
import kotlinx.coroutines.launch

@Composable
fun LicenseActivationScreen(
    licenseRepository: LicenseRepository,
    licenseStorage: LicenseStorage,
    onActivated: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lapakColors = LocalLapakColors.current

    var licenseCodeInput by remember { mutableStateOf("") }
    var isActivating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Hidden 7-tap trigger on "Lapak Kita" logo/title -> Admin License Generator
    var logoTapCount by remember { mutableIntStateOf(0) }
    var lastTapMillis by remember { mutableLongStateOf(0L) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showGeneratorDialog by remember { mutableStateOf(false) }
    var showOrderDialog by remember { mutableStateOf(false) }

    fun handleSecretLogoTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapMillis > 2500L) {
            logoTapCount = 1
        } else {
            logoTapCount += 1
        }
        lastTapMillis = now

        if (logoTapCount >= 7) {
            logoTapCount = 0
            showPinDialog = true
        } else if (logoTapCount >= 5) {
            val remaining = 7 - logoTapCount
            Toast.makeText(
                context,
                "Ketuk ${remaining}x lagi untuk Panel Admin Lisensi",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun submitActivation() {
        if (isActivating) return
        val normalized = licenseRepository.normalizeLicenseCode(licenseCodeInput)
        if (normalized.isBlank()) {
            errorMessage = "Silakan masukkan kode lisensi terlebih dahulu"
            return
        }

        scope.launch {
            isActivating = true
            errorMessage = null
            val deviceId = licenseStorage.getDeviceId()
            when (val result = licenseRepository.activateLicenseCode(normalized, deviceId)) {
                is LicenseActivationOutcome.Activated -> {
                    licenseStorage.savePermanentActivation(result.code, deviceId)
                    val msg = if (result.isReinstallRestore) {
                        "Lisensi ${result.code} berhasil dipulihkan di perangkat ini!"
                    } else {
                        "Aktivasi berhasil! Lapak Kita aktif permanen."
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    onActivated(result.code)
                }
                is LicenseActivationOutcome.InvalidCode -> {
                    errorMessage = result.message
                }
                is LicenseActivationOutcome.AlreadyUsedElsewhere -> {
                    errorMessage = result.message
                }
                is LicenseActivationOutcome.Error -> {
                    errorMessage = result.message
                }
            }
            isActivating = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, lapakColors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Tappable Logo + Brand Title (7 taps opens Developer License Generator)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { handleSecretLogoTap() }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("license_brand_header")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = "Logo Lapak Kita",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Lapak Kita",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Aktivasi Aplikasi Lapak Kita",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Masukkan kode lisensi resmi Anda untuk membuka seluruh fitur Admin Lapak Kita secara permanen (berlaku 1 perangkat).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(22.dp))

                    OutlinedTextField(
                        value = licenseCodeInput,
                        onValueChange = { raw ->
                            licenseCodeInput = raw.uppercase().replace(" ", "").take(32)
                            errorMessage = null
                        },
                        label = { Text("Masukkan Kode Lisensi") },
                        placeholder = { Text("LPK-XXXX-XXXX") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (licenseCodeInput.isNotEmpty() && !isActivating) {
                                IconButton(onClick = {
                                    licenseCodeInput = ""
                                    errorMessage = null
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Hapus Kode")
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitActivation() }
                        ),
                        isError = !errorMessage.isNullOrBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_license_code")
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = lapakColors.dangerBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = lapakColors.dangerText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = lapakColors.dangerText
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = { submitActivation() },
                        enabled = !isActivating,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_activate_license")
                    ) {
                        if (isActivating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Memverifikasi...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Aktifkan Sekarang",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showOrderDialog = true },
                        enabled = !isActivating,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_order_license")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Pesan Kode Lisensi",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = lapakColors.omzetCardBg,
                        border = BorderStroke(1.dp, lapakColors.omzetCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.OfflinePin,
                                contentDescription = null,
                                tint = lapakColors.omzetCardAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Aktivasi hanya butuh internet sekali. Setelah aktif, aplikasi bisa dipakai offline & online selamanya.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showOrderDialog) {
        AlertDialog(
            onDismissRequest = { showOrderDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("Pesan Kode Lisensi Resmi", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Hubungi pengembang Lapak Kita untuk mendapatkan kode lisensi permanen (LPK-XXXX-XXXX) tanpa perlu mengirim Device ID.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Email: ${LicenseStorage.DEVELOPER_EMAIL}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOrderDialog = false
                        val subject = "Pesan Kode Lisensi Lapak Kita"
                        val body = "Halo Developer Lapak Kita,\n\nSaya ingin memesan Kode Lisensi resmi untuk aplikasi Admin Lapak Kita.\n\nNama Lapak: \nKota: \n\nTerima kasih."
                        val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse(
                                "mailto:${LicenseStorage.DEVELOPER_EMAIL}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
                            )
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            context.startActivity(mailIntent)
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "Hubungi email: ${LicenseStorage.DEVELOPER_EMAIL}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kirim Email", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showOrderDialog = false
                        val waText = "Halo Developer Lapak Kita, saya ingin memesan Kode Lisensi resmi aplikasi Admin Lapak Kita. Terima kasih!"
                        val waIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(waText)}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            context.startActivity(waIntent)
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "WhatsApp tidak tersedia. Silakan kirim email ke ${LicenseStorage.DEVELOPER_EMAIL}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp")
                }
            }
        )
    }

    if (showPinDialog) {
        AdminPinPromptDialog(
            onVerified = {
                showPinDialog = false
                showGeneratorDialog = true
            },
            onDismiss = { showPinDialog = false }
        )
    }

    if (showGeneratorDialog) {
        AdminLicenseGeneratorDialog(
            licenseRepository = licenseRepository,
            onDismiss = { showGeneratorDialog = false }
        )
    }
}
