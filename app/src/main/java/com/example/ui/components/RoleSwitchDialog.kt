package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.config.LicenseStorage
import com.example.ui.theme.LocalLapakColors

/**
 * Quick role switcher dialog triggered by tapping the "Owner" / "Kasir" badge
 * in the top header bar.
 *
 * - From Owner -> Kasir: switches immediately to hide sensitive profit/commission figures.
 * - From Kasir -> Owner: requires the Owner PIN (default "1234", configurable inline).
 */
@Composable
fun QuickRoleSwitchDialog(
    isOwner: Boolean,
    isUsingDefaultPin: Boolean,
    onSwitchToCashier: () -> Unit,
    onVerifyOwnerPin: (String) -> Boolean,
    onUpdateOwnerPin: (oldPin: String, newPin: String) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val lapakColors = LocalLapakColors.current
    var showChangePinSection by remember { mutableStateOf(false) }

    // Kasir -> Owner PIN state
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    // Change PIN state (Owner only)
    var oldPinText by remember { mutableStateOf("") }
    var newPinText by remember { mutableStateOf("") }
    var confirmPinText by remember { mutableStateOf("") }
    var changePinFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isOwner) Icons.Default.VerifiedUser else Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (isOwner) "Mode Aktif: Owner" else "Masuk ke Mode Owner",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isOwner) {
                    // Active Owner state: offer fast switch to Kasir + optional PIN update
                    Surface(
                        color = lapakColors.omzetCardBg,
                        border = BorderStroke(1.dp, lapakColors.omzetCardBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Akses Penuh Aktif",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = lapakColors.omzetCardAccent
                            )
                            Text(
                                text = "Beralih ke Mode Kasir untuk menyembunyikan data Omset, Untung Lapak, Biaya Operasional, dan menu sensitif saat HP digunakan karyawan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = onSwitchToCashier,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_switch_to_cashier")
                    ) {
                        Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Beralih ke Mode Kasir", fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = lapakColors.cardBorder)

                    TextButton(
                        onClick = {
                            showChangePinSection = !showChangePinSection
                            changePinFeedback = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_toggle_change_owner_pin")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pin,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (showChangePinSection) "Tutup Ubah PIN Owner" else "Ubah PIN Owner",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (showChangePinSection) {
                        if (isUsingDefaultPin) {
                            Text(
                                text = "PIN bawaan saat ini: ${LicenseStorage.DEFAULT_OWNER_PIN}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = oldPinText,
                            onValueChange = { oldPinText = it.filter(Char::isDigit).take(6) },
                            label = { Text("PIN Lama") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_old_owner_pin")
                        )
                        OutlinedTextField(
                            value = newPinText,
                            onValueChange = { newPinText = it.filter(Char::isDigit).take(6) },
                            label = { Text("PIN Baru (4-6 angka)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_new_owner_pin")
                        )
                        OutlinedTextField(
                            value = confirmPinText,
                            onValueChange = { confirmPinText = it.filter(Char::isDigit).take(6) },
                            label = { Text("Konfirmasi PIN Baru") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_confirm_owner_pin")
                        )

                        changePinFeedback?.let { (ok, msg) ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (ok) lapakColors.stockSafeText else MaterialTheme.colorScheme.error
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (newPinText != confirmPinText) {
                                    changePinFeedback = false to "Konfirmasi PIN baru tidak cocok"
                                } else {
                                    val result = onUpdateOwnerPin(oldPinText, newPinText)
                                    result.fold(
                                        onSuccess = {
                                            oldPinText = ""
                                            newPinText = ""
                                            confirmPinText = ""
                                            changePinFeedback = true to "PIN Owner berhasil diperbarui!"
                                        },
                                        onFailure = { err ->
                                            changePinFeedback = false to (err.message ?: "Gagal memperbarui PIN")
                                        }
                                    )
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("btn_save_new_owner_pin")
                        ) {
                            Text("Simpan PIN Baru", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Kasir -> Owner: PIN entry
                    Text(
                        text = "Masukkan PIN Owner untuk membuka kembali laporan keuangan, pengaturan tarif, dan akses penuh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isUsingDefaultPin) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Info: PIN bawaan Owner adalah ${LicenseStorage.DEFAULT_OWNER_PIN} (dapat diubah setelah masuk Mode Owner).",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = {
                            enteredPin = it.filter(Char::isDigit).take(6)
                            pinError = null
                        },
                        label = { Text("PIN Owner") },
                        placeholder = { Text("Masukkan 4-6 digit PIN") },
                        singleLine = true,
                        isError = pinError != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_owner_pin")
                    )
                    if (pinError != null) {
                        Text(
                            text = pinError!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isOwner) {
                Button(
                    onClick = {
                        if (!onVerifyOwnerPin(enteredPin)) {
                            pinError = "PIN Owner salah. Silakan coba lagi."
                        }
                    },
                    enabled = enteredPin.length >= 4,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_confirm_owner_pin")
                ) {
                    Text("Buka Mode Owner", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("btn_close_role_dialog")
                ) {
                    Text("Tutup")
                }
            }
        },
        dismissButton = {
            if (!isOwner) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("btn_cancel_owner_pin")
                ) {
                    Text("Batal")
                }
            }
        }
    )
}
