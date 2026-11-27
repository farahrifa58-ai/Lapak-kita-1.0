package com.example.ui.auth

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.config.LicenseStorage
import com.example.data.firebase.ActivationAuthRepository
import com.example.data.firebase.CodeAvailabilityResult
import com.example.data.firebase.DeviceAutoRestoreHint
import com.example.data.firebase.LoginDeviceCheckResult
import com.example.data.firebase.UserProfileDoc
import com.example.ui.components.NeuCard
import com.example.ui.components.NeuIconBadge
import com.example.ui.components.neuExtruded
import com.example.ui.components.neuInset
import com.example.ui.theme.LocalLapakColors
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

/**
 * Step 1 — Clean Google Sign-In Gate (Soft UI / Neumorphism).
 *
 * - Displays a single primary button "Masuk dengan Akun Google" with the Google "G" emblem.
 * - Uses native Android Jetpack Credential Manager (`GoogleAuthHelper.signInWithGoogle`).
 * - Existing active user + matching/empty `registeredDeviceId` -> enters Dashboard directly.
 * - Existing active user + mismatched `registeredDeviceId` -> signs out & pins red `authError`.
 * - New Google account (not in `users/{uid}`) -> routes smoothly to `CompleteActivationScreen`
 *   WITHOUT signing out so the user only needs to enter Business Name & Activation Code.
 */
@Composable
fun AuthScreen(
    activationAuthRepository: ActivationAuthRepository,
    licenseStorage: LicenseStorage,
    authError: String?,
    onSetAuthError: (String?) -> Unit,
    onAuthFlowRunningChange: (Boolean) -> Unit,
    autoRestoreHint: DeviceAutoRestoreHint? = null,
    onRequireActivation: (uid: String, email: String, displayName: String) -> Unit,
    onAuthenticated: (UserProfileDoc) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lapakColors = LocalLapakColors.current
    val currentDeviceId = remember { licenseStorage.getDeviceId() }

    var isLoading by remember { mutableStateOf(false) }
    var statusStep by remember { mutableStateOf<String?>(null) }

    fun handleGoogleSignInClick() {
        if (isLoading) return
        scope.launch {
            onSetAuthError(null)
            isLoading = true
            statusStep = "Membuka pemilih akun Google..."
            onAuthFlowRunningChange(true)

            try {
                when (val outcome = GoogleAuthHelper.signInWithGoogle(context)) {
                    is GoogleSignInOutcome.Cancelled -> Unit
                    is GoogleSignInOutcome.Error -> {
                        onSetAuthError(outcome.message)
                    }
                    is GoogleSignInOutcome.Success -> {
                        val fbUser = Firebase.auth.currentUser
                        if (fbUser == null) {
                            onSetAuthError("Gagal memuat sesi akun Google. Silakan coba lagi.")
                            return@launch
                        }

                        val email = fbUser.email?.trim().orEmpty()
                        val displayName = fbUser.displayName?.trim().orEmpty()
                        statusStep = "Memeriksa status akun & perangkat..."

                        when (
                            val check = activationAuthRepository.verifySingleDeviceOnLogin(
                                uid = fbUser.uid,
                                email = email,
                                currentDeviceId = currentDeviceId
                            )
                        ) {
                            is LoginDeviceCheckResult.Allowed -> {
                                licenseStorage.saveOfflineSession(
                                    uid = check.profile.userId,
                                    email = check.profile.email,
                                    businessName = check.profile.businessName,
                                    licenseCode = check.profile.licenseCode,
                                    deviceId = currentDeviceId,
                                    connectedDevicesCount = check.profile.connectedDevicesCount,
                                    isOwnerDevice = check.profile.isOwnerDevice(currentDeviceId),
                                    active = true
                                )
                                onSetAuthError(null)
                                onAuthenticated(check.profile)
                            }
                            is LoginDeviceCheckResult.NotRegistered -> {
                                // Keep Google session active & route to Complete Activation screen!
                                onSetAuthError(null)
                                onRequireActivation(fbUser.uid, email, displayName)
                            }
                            is LoginDeviceCheckResult.BlockedOtherDevice -> {
                                GoogleAuthHelper.signOut(context)
                                licenseStorage.clearSession()
                                onSetAuthError(check.message)
                            }
                            is LoginDeviceCheckResult.InactiveAccount -> {
                                GoogleAuthHelper.signOut(context)
                                licenseStorage.clearSession()
                                onSetAuthError(check.message)
                            }
                            is LoginDeviceCheckResult.OfflineFallback -> {
                                val cached = licenseStorage.getOfflineSession(fbUser.uid)
                                if (cached != null && cached.uid == fbUser.uid) {
                                    onSetAuthError(null)
                                    onAuthenticated(
                                        UserProfileDoc(
                                            userId = cached.uid,
                                            email = cached.email,
                                            businessName = cached.businessName,
                                            licenseCode = cached.licenseCode,
                                            registeredDeviceId = cached.registeredDeviceId,
                                            active = true
                                        )
                                    )
                                } else {
                                    GoogleAuthHelper.signOut(context)
                                    onSetAuthError(
                                        check.message.ifBlank {
                                            "Tidak dapat terhubung ke server. Pastikan koneksi internet aktif saat masuk."
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } finally {
                isLoading = false
                statusStep = null
                onAuthFlowRunningChange(false)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Raised Neumorphic Brand Logo
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .neuExtruded(
                            cornerRadius = 28.dp,
                            shadowOffset = 6.dp,
                            blurRadius = 14.dp,
                            surfaceColor = Color(0xFF194220)
                        )
                        .clip(RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_lapak_logo),
                        contentDescription = "Logo Lapak Kita",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "Lapak Kita",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Kasir Titipan & Sinkronisasi Web Penyetor",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                // Main Neumorphic Welcome Card
                NeuCard(
                    cornerRadius = 24.dp,
                    shadowOffset = 6.dp,
                    blurRadius = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Masuk Admin Lapak",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Pilih akun Google Anda untuk masuk langsung ke Dashboard atau mengaktifkan lisensi baru.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        // Auto-Restore Device Detected Banner (same HP after reinstall)
                        if (autoRestoreHint != null) {
                            Spacer(Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(lapakColors.omzetCardBg)
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = lapakColors.omzetCardAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Perangkat Ini Sudah Terdaftar",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = lapakColors.omzetCardAccent
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "Pilih akun Google ${autoRestoreHint.emailHint} (${autoRestoreHint.businessName.ifBlank { "Lapak Kita" }}) untuk langsung masuk tanpa kode aktivasi lagi.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Persistent Red Alert Box pinned above the Google Sign-In button
                        if (!authError.isNullOrBlank()) {
                            Spacer(Modifier.height(16.dp))
                            PersistentAuthErrorBanner(
                                message = authError,
                                onDismiss = { onSetAuthError(null) }
                            )
                        }

                        Spacer(Modifier.height(22.dp))

                        // Primary Google Sign-In Button (Neumorphic Raised Pill with Google "G" Logo)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .neuExtruded(
                                    cornerRadius = 18.dp,
                                    shadowOffset = 5.dp,
                                    blurRadius = 12.dp,
                                    surfaceColor = MaterialTheme.colorScheme.primary
                                )
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(enabled = !isLoading) { handleGoogleSignInClick() }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("google_sign_in_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = statusStep ?: "Menghubungkan...",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        GoogleGLogo(size = 18.dp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "Masuk dengan Akun Google",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Feature highlights inside soft recessed pill
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .neuInset(cornerRadius = 14.dp, shadowOffset = 2.dp, blurRadius = 5.dp)
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AuthFeatureRow(
                                    icon = Icons.Default.CloudSync,
                                    text = "Sinkronisasi real-time dengan Portal Web Penyetor",
                                    tint = lapakColors.omzetCardAccent
                                )
                                AuthFeatureRow(
                                    icon = Icons.Default.Devices,
                                    text = "Dukung hingga 2 HP Real-Time (Kasir & Owner) + Offline-First",
                                    tint = lapakColors.omzetCardAccent
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "By Rafline Dev",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/**
 * Step 2 — Lengkapi Aktivasi Akun (shown only for newly signed-in Google accounts not yet in `users/{uid}`).
 */
@Composable
fun CompleteActivationScreen(
    uid: String,
    email: String,
    initialDisplayName: String,
    activationAuthRepository: ActivationAuthRepository,
    licenseStorage: LicenseStorage,
    authError: String?,
    onSetAuthError: (String?) -> Unit,
    onAuthFlowRunningChange: (Boolean) -> Unit,
    onSwitchAccount: () -> Unit,
    onActivated: (UserProfileDoc) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lapakColors = LocalLapakColors.current
    val currentDeviceId = remember { licenseStorage.getDeviceId() }

    var businessName by remember(initialDisplayName) { mutableStateOf(initialDisplayName) }
    var activationCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusStep by remember { mutableStateOf<String?>(null) }

    // Keep authError pinned until user taps × or submits again (never auto-dismiss on IME/autofill edits)

    fun openContactAdminEmail() {
        val subject = Uri.encode("Pesan Kode Aktivasi Lapak Kita")
        val body = Uri.encode(
            "Halo Admin Rafline Dev,\n\n" +
                "Saya ingin memesan Kode Aktivasi resmi aplikasi Lapak Kita.\n" +
                "Email Google: ${email.ifBlank { "-" }}\n" +
                "Nama Usaha / Pemilik: ${businessName.ifBlank { "-" }}\n\n" +
                "Mohon informasi harga dan cara pembayarannya. Terima kasih!"
        )
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${LicenseStorage.DEVELOPER_EMAIL}?subject=$subject&body=$body")
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(
                context,
                "Hubungi Admin via Email: ${LicenseStorage.DEVELOPER_EMAIL}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun submitActivation() {
        if (isLoading) return
        val cleanBiz = businessName.trim()
        val cleanCode = activationAuthRepository.normalizeCode(activationCode)

        if (cleanBiz.isBlank()) {
            onSetAuthError("Silakan isi Nama Usaha / Pemilik terlebih dahulu.")
            return
        }
        if (cleanCode.length < 4) {
            onSetAuthError("Masukkan Kode Aktivasi Pembelian yang valid (contoh: RAFDEV-XXXX-XXXX).")
            return
        }

        scope.launch {
            onSetAuthError(null)
            isLoading = true
            statusStep = "Memeriksa kode aktivasi..."
            onAuthFlowRunningChange(true)

            try {
                // Step 1: Verify activation_codes/{code} is either "available" OR already bound to this same Google account
                when (
                    val codeCheck = activationAuthRepository.checkActivationCodeAvailable(
                        rawCode = cleanCode,
                        currentUid = uid,
                        currentEmail = email
                    )
                ) {
                    is CodeAvailabilityResult.InvalidOrUsed -> {
                        onSetAuthError(codeCheck.message)
                        return@launch
                    }
                    is CodeAvailabilityResult.NetworkError -> {
                        onSetAuthError(codeCheck.message)
                        return@launch
                    }
                    is CodeAvailabilityResult.Available,
                    is CodeAvailabilityResult.OwnedByCurrentUser -> {
                        val isReactivation = codeCheck is CodeAvailabilityResult.OwnedByCurrentUser
                        val resolvedCode = when (codeCheck) {
                            is CodeAvailabilityResult.Available -> codeCheck.normalizedCode
                            is CodeAvailabilityResult.OwnedByCurrentUser -> codeCheck.normalizedCode
                            else -> cleanCode
                        }
                        val resolvedBiz = if (codeCheck is CodeAvailabilityResult.OwnedByCurrentUser && codeCheck.businessName.isNotBlank()) {
                            codeCheck.businessName
                        } else {
                            cleanBiz
                        }

                        // Step 2: Create/restore users/{uid} & link activation_codes/{code}
                        statusStep = if (isReactivation) {
                            "Memulihkan aktivasi akun Anda..."
                        } else {
                            "Mengaktifkan lisensi & mengunci perangkat..."
                        }
                        val regResult = activationAuthRepository.completeRegistrationWithCode(
                            uid = uid,
                            email = email,
                            businessName = resolvedBiz,
                            rawCode = resolvedCode,
                            deviceId = currentDeviceId
                        )

                        regResult.fold(
                            onSuccess = { profile ->
                                licenseStorage.saveOfflineSession(
                                    uid = profile.userId,
                                    email = profile.email,
                                    businessName = profile.businessName,
                                    licenseCode = profile.licenseCode,
                                    deviceId = currentDeviceId,
                                    connectedDevicesCount = profile.connectedDevicesCount,
                                    isOwnerDevice = profile.isOwnerDevice(currentDeviceId),
                                    active = true
                                )
                                onSetAuthError(null)
                                Toast.makeText(
                                    context,
                                    if (isReactivation) {
                                        "Aktivasi akun berhasil dipulihkan! Selamat datang kembali."
                                    } else {
                                        "Aktivasi berhasil! Selamat datang di Lapak Kita."
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                                onActivated(profile)
                            },
                            onFailure = { err ->
                                onSetAuthError(
                                    err.message ?: "Kode aktivasi tidak valid atau sudah pernah digunakan."
                                )
                            }
                        )
                    }
                }
            } finally {
                isLoading = false
                statusStep = null
                onAuthFlowRunningChange(false)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .neuExtruded(
                            cornerRadius = 22.dp,
                            shadowOffset = 6.dp,
                            blurRadius = 12.dp,
                            surfaceColor = Color(0xFF194220)
                        )
                        .clip(RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_lapak_logo),
                        contentDescription = "Logo Lapak Kita",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Lengkapi Aktivasi Akun",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Masukkan nama usaha dan kode aktivasi pembelian resmi untuk mulai memakai Lapak Kita.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(18.dp))

                NeuCard(
                    cornerRadius = 22.dp,
                    shadowOffset = 6.dp,
                    blurRadius = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Selected Google Account pill + Ganti Akun button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .neuInset(cornerRadius = 14.dp, shadowOffset = 2.dp, blurRadius = 5.dp)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    GoogleGLogo(size = 18.dp)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Akun Google Dipilih",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = email.ifBlank { "Akun Google Terhubung" },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                TextButton(
                                    onClick = {
                                        if (!isLoading) {
                                            scope.launch {
                                                onAuthFlowRunningChange(true)
                                                GoogleAuthHelper.signOut(context)
                                                licenseStorage.clearSession()
                                                onSetAuthError(null)
                                                onAuthFlowRunningChange(false)
                                                onSwitchAccount()
                                            }
                                        }
                                    },
                                    enabled = !isLoading,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("switch_google_account_button")
                                ) {
                                    Text(
                                        text = "Ganti Akun",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        NeuInputField(
                            label = "Nama Usaha / Pemilik",
                            value = businessName,
                            onValueChange = { businessName = it },
                            placeholder = "Contoh: Lapak Kue Bu Sari",
                            leadingIcon = Icons.Default.Storefront,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words
                            ),
                            modifier = Modifier.testTag("activation_business_name_input")
                        )

                        Spacer(Modifier.height(12.dp))

                        NeuInputField(
                            label = "Kode Aktivasi Pembelian",
                            value = activationCode,
                            onValueChange = { raw ->
                                activationCode = raw.uppercase().replace(" ", "").take(32)
                            },
                            placeholder = "RAFDEV-XXXX-XXXX",
                            leadingIcon = Icons.Default.Key,
                            isMonospace = true,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters
                            ),
                            modifier = Modifier.testTag("activation_code_input")
                        )

                        // Persistent Red Alert Box directly above submit button
                        if (!authError.isNullOrBlank()) {
                            Spacer(Modifier.height(14.dp))
                            PersistentAuthErrorBanner(
                                message = authError,
                                onDismiss = { onSetAuthError(null) }
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        // Primary Button: Aktifkan Sekarang
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .neuExtruded(
                                    cornerRadius = 16.dp,
                                    shadowOffset = 5.dp,
                                    blurRadius = 10.dp,
                                    surfaceColor = MaterialTheme.colorScheme.primary
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = !isLoading) { submitActivation() }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("activate_now_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.4.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (isLoading) (statusStep ?: "Mengaktifkan...") else "Aktifkan Sekarang",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Secondary Button: Beli Kode Aktivasi / Hubungi Admin
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .neuExtruded(
                                    cornerRadius = 14.dp,
                                    shadowOffset = 4.dp,
                                    blurRadius = 8.dp
                                )
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = !isLoading) { openContactAdminEmail() }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("contact_admin_buy_code_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Beli Kode Aktivasi / Hubungi Admin",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    NeuIconBadge(
                        accentColor = lapakColors.omzetCardAccent,
                        size = 28.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = lapakColors.omzetCardAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Kode aktivasi hanya dimasukkan 1x untuk mengaktifkan lisensi permanen di perangkat ini.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "By Rafline Dev",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun AuthFeatureRow(
    icon: ImageVector,
    text: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Clean vector rendering of the 4-color Google "G" emblem inside the Sign-In button.
 */
@Composable
private fun GoogleGLogo(size: Dp = 18.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val strokeWidth = this.size.minDimension * 0.21f
        val inset = strokeWidth / 2f
        val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
        val topLeft = Offset(inset, inset)

        // Red top-left arc
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 215f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Yellow left arc
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 140f,
            sweepAngle = 78f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Green bottom arc
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 98f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Blue right-bottom arc
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -5f,
            sweepAngle = 55f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Blue horizontal bar
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(this.size.width * 0.50f, this.size.height * 0.50f),
            end = Offset(this.size.width - inset, this.size.height * 0.50f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt
        )
    }
}

/**
 * Persistent red alert banner pinned above submit actions until user edits input or taps `×`.
 */
@Composable
private fun PersistentAuthErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    val lapakColors = LocalLapakColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(lapakColors.dangerBg)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 6.dp)
            .testTag("auth_error_banner")
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Peringatan",
                tint = lapakColors.dangerText,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = lapakColors.dangerText,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 1.dp)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("auth_error_dismiss_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Tutup pesan error",
                    tint = lapakColors.dangerText,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun NeuInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    isMonospace: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .neuInset(cornerRadius = 14.dp, shadowOffset = 3.dp, blurRadius = 6.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        singleLine = true,
                        keyboardOptions = keyboardOptions,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = if (isMonospace) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                            letterSpacing = if (isMonospace) 1.sp else 0.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
