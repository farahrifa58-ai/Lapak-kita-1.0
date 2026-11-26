package com.example.ui.auth

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
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

enum class AuthGateTab {
    LOGIN,
    REGISTER
}

/**
 * Unified Soft UI / Neumorphism Authentication & License Activation Screen.
 * - Tab 1 (Login): Google Sign-In via CredentialManager + Single Device Lock (users/{uid}).
 * - Tab 2 (Daftar Akun Baru): Validates `activation_codes/{code}` (`status == "available"`),
 *   signs in with Google, creates `users/{uid}`, and marks the code `"used"`.
 */
@Composable
fun AuthScreen(
    activationAuthRepository: ActivationAuthRepository,
    licenseStorage: LicenseStorage,
    initialTab: AuthGateTab = AuthGateTab.LOGIN,
    initialErrorMessage: String? = null,
    autoRestoreHint: DeviceAutoRestoreHint? = null,
    onAuthenticated: (UserProfileDoc) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lapakColors = LocalLapakColors.current
    val currentDeviceId = remember { licenseStorage.getDeviceId() }

    var activeTab by remember(initialTab, autoRestoreHint) {
        mutableStateOf(if (autoRestoreHint != null) AuthGateTab.LOGIN else initialTab)
    }
    var businessName by remember { mutableStateOf(autoRestoreHint?.businessName.orEmpty()) }
    var activationCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusStep by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember(initialErrorMessage) { mutableStateOf(initialErrorMessage) }

    fun openContactAdminEmail() {
        val subject = Uri.encode("Pesan Kode Aktivasi Lapak Kita")
        val body = Uri.encode(
            "Halo Admin Rafline Dev,\n\n" +
                "Saya ingin memesan Kode Aktivasi resmi aplikasi Lapak Kita.\n" +
                "Nama Usaha / Lapak: ${businessName.ifBlank { "-" }}\n\n" +
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
                .padding(horizontal = 20.dp, vertical = 20.dp),
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
                        .size(92.dp)
                        .neuExtruded(
                            cornerRadius = 24.dp,
                            shadowOffset = 6.dp,
                            blurRadius = 12.dp,
                            surfaceColor = Color(0xFF194220)
                        )
                        .clip(RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_lapak_logo),
                        contentDescription = "Logo Lapak Kita",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Lapak Kita",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Kasir Titipan & Sinkronisasi Web Penyetor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(22.dp))

                // Main Neumorphic Card Container
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
                        // Tactile Neumorphic 2-Tab Switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .neuInset(cornerRadius = 16.dp, shadowOffset = 3.dp, blurRadius = 6.dp)
                                .padding(5.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AuthTabButton(
                                label = "Login",
                                selected = activeTab == AuthGateTab.LOGIN,
                                onClick = {
                                    if (!isLoading) {
                                        activeTab = AuthGateTab.LOGIN
                                        errorMessage = null
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_tab_login")
                            )
                            AuthTabButton(
                                label = "Daftar Akun Baru",
                                selected = activeTab == AuthGateTab.REGISTER,
                                onClick = {
                                    if (!isLoading) {
                                        activeTab = AuthGateTab.REGISTER
                                        errorMessage = null
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_tab_register")
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        // Auto-Restore Device Detected Banner (same HP after reinstall)
                        if (autoRestoreHint != null && activeTab == AuthGateTab.LOGIN) {
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
                                            text = "Akun: ${autoRestoreHint.emailHint}\nUsaha: ${autoRestoreHint.businessName.ifBlank { "Lapak Kita" }}\nMasuk dengan akun Google tersebut untuk memulihkan sesi & data Anda.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        if (activeTab == AuthGateTab.LOGIN) {
                            // ===========================================================
                            // TAB 1: LOGIN (Single Device Lock)
                            // ===========================================================
                            Text(
                                text = "Masuk ke Akun Terdaftar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Gunakan akun Google yang sudah Anda aktifkan. Sistem otomatis menjaga keamanan 1 akun untuk 1 perangkat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (autoRestoreHint != null) {
                                Spacer(Modifier.height(12.dp))
                                NeuInputField(
                                    label = "Email Terdaftar di Perangkat Ini",
                                    value = autoRestoreHint.emailHint,
                                    onValueChange = {},
                                    placeholder = "",
                                    readOnly = true,
                                    leadingIcon = Icons.Default.Email
                                )
                            }

                            Spacer(Modifier.height(18.dp))

                            NeuPrimaryActionButton(
                                text = if (isLoading) (statusStep ?: "Memverifikasi...") else "Masuk dengan Google",
                                isLoading = isLoading,
                                onClick = {
                                    if (isLoading) return@NeuPrimaryActionButton
                                    scope.launch {
                                        errorMessage = null
                                        isLoading = true
                                        statusStep = "Menghubungkan akun Google..."
                                        when (val signIn = GoogleAuthHelper.signInWithGoogle(context)) {
                                            is GoogleSignInOutcome.Cancelled -> {
                                                isLoading = false
                                                statusStep = null
                                            }
                                            is GoogleSignInOutcome.Error -> {
                                                errorMessage = signIn.message
                                                isLoading = false
                                                statusStep = null
                                            }
                                            is GoogleSignInOutcome.Success -> {
                                                val fbUser = Firebase.auth.currentUser
                                                if (fbUser == null) {
                                                    errorMessage = "Sesi akun Google gagal dimuat. Coba lagi."
                                                    isLoading = false
                                                    statusStep = null
                                                    return@launch
                                                }
                                                statusStep = "Memeriksa lisensi & kunci perangkat..."
                                                when (
                                                    val check = activationAuthRepository.verifySingleDeviceOnLogin(
                                                        uid = fbUser.uid,
                                                        email = fbUser.email.orEmpty(),
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
                                                            active = true
                                                        )
                                                        isLoading = false
                                                        statusStep = null
                                                        onAuthenticated(check.profile)
                                                    }
                                                    is LoginDeviceCheckResult.NotRegistered -> {
                                                        GoogleAuthHelper.signOut(context)
                                                        licenseStorage.clearSession()
                                                        activeTab = AuthGateTab.REGISTER
                                                        errorMessage = "Akun Google (${fbUser.email ?: "ini"}) belum diaktifkan. Masukkan Kode Aktivasi Pembelian di tab Daftar Akun Baru."
                                                        isLoading = false
                                                        statusStep = null
                                                    }
                                                    is LoginDeviceCheckResult.BlockedOtherDevice -> {
                                                        GoogleAuthHelper.signOut(context)
                                                        licenseStorage.clearSession()
                                                        errorMessage = check.message
                                                        isLoading = false
                                                        statusStep = null
                                                    }
                                                    is LoginDeviceCheckResult.InactiveAccount -> {
                                                        GoogleAuthHelper.signOut(context)
                                                        licenseStorage.clearSession()
                                                        errorMessage = check.message
                                                        isLoading = false
                                                        statusStep = null
                                                    }
                                                    is LoginDeviceCheckResult.OfflineFallback -> {
                                                        val cached = licenseStorage.getOfflineSession()
                                                        if (cached != null && cached.uid == fbUser.uid) {
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
                                                            errorMessage = "Tidak dapat terhubung ke server. Pastikan internet aktif saat login pertama kali."
                                                        }
                                                        isLoading = false
                                                        statusStep = null
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("google_sign_in_button")
                            )
                        } else {
                            // ===========================================================
                            // TAB 2: DAFTAR AKUN BARU (Sinkron License Admin)
                            // ===========================================================
                            Text(
                                text = "Aktivasi & Daftar Akun Baru",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Masukkan nama usaha dan Kode Aktivasi Pembelian resmi dari admin, lalu hubungkan dengan akun Google Anda.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(14.dp))

                            NeuInputField(
                                label = "Nama Usaha / Pemilik",
                                value = businessName,
                                onValueChange = {
                                    businessName = it
                                    errorMessage = null
                                },
                                placeholder = "Contoh: Lapak Kue Bu Sari",
                                leadingIcon = Icons.Default.Storefront,
                                enabled = !isLoading,
                                modifier = Modifier.testTag("register_business_name_input")
                            )

                            Spacer(Modifier.height(12.dp))

                            NeuInputField(
                                label = "Kode Aktivasi Pembelian",
                                value = activationCode,
                                onValueChange = { raw ->
                                    activationCode = raw.uppercase().replace(" ", "").take(32)
                                    errorMessage = null
                                },
                                placeholder = "RAFDEV-XXXX-XXXX",
                                leadingIcon = Icons.Default.Key,
                                isMonospace = true,
                                enabled = !isLoading,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters
                                ),
                                modifier = Modifier.testTag("register_activation_code_input")
                            )

                            Spacer(Modifier.height(18.dp))

                            NeuPrimaryActionButton(
                                text = if (isLoading) (statusStep ?: "Memproses...") else "Aktifkan & Daftar dengan Google",
                                isLoading = isLoading,
                                onClick = {
                                    if (isLoading) return@NeuPrimaryActionButton
                                    val cleanBiz = businessName.trim()
                                    val cleanCode = activationAuthRepository.normalizeCode(activationCode)

                                    if (cleanBiz.isBlank()) {
                                        errorMessage = "Silakan isi Nama Usaha / Pemilik terlebih dahulu."
                                        return@NeuPrimaryActionButton
                                    }
                                    if (cleanCode.length < 4) {
                                        errorMessage = "Masukkan Kode Aktivasi Pembelian yang valid (contoh: RAFDEV-XXXX-XXXX)."
                                        return@NeuPrimaryActionButton
                                    }

                                    scope.launch {
                                        errorMessage = null
                                        isLoading = true
                                        statusStep = "Memeriksa kode aktivasi..."

                                        // Step 1: Verify status == "available" in Firestore `activation_codes`
                                        when (val codeCheck = activationAuthRepository.checkActivationCodeAvailable(cleanCode)) {
                                            is CodeAvailabilityResult.InvalidOrUsed -> {
                                                errorMessage = codeCheck.message
                                                isLoading = false
                                                statusStep = null
                                                return@launch
                                            }
                                            is CodeAvailabilityResult.NetworkError -> {
                                                errorMessage = codeCheck.message
                                                isLoading = false
                                                statusStep = null
                                                return@launch
                                            }
                                            is CodeAvailabilityResult.Available -> {
                                                // Step 2: Authenticate with Google via CredentialManager
                                                statusStep = "Memilih akun Google..."
                                                when (val signIn = GoogleAuthHelper.signInWithGoogle(context)) {
                                                    is GoogleSignInOutcome.Cancelled -> {
                                                        isLoading = false
                                                        statusStep = null
                                                    }
                                                    is GoogleSignInOutcome.Error -> {
                                                        errorMessage = signIn.message
                                                        isLoading = false
                                                        statusStep = null
                                                    }
                                                    is GoogleSignInOutcome.Success -> {
                                                        val fbUser = Firebase.auth.currentUser
                                                        if (fbUser == null) {
                                                            errorMessage = "Gagal membaca sesi akun Google."
                                                            isLoading = false
                                                            statusStep = null
                                                            return@launch
                                                        }

                                                        // Step 3: Atomically create users/{uid} & mark activation_codes/{code} -> "used"
                                                        statusStep = "Mengaktifkan lisensi & mengunci perangkat..."
                                                        val regResult = activationAuthRepository.completeRegistrationWithCode(
                                                            uid = fbUser.uid,
                                                            email = fbUser.email.orEmpty(),
                                                            businessName = cleanBiz,
                                                            rawCode = codeCheck.normalizedCode,
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
                                                                    active = true
                                                                )
                                                                isLoading = false
                                                                statusStep = null
                                                                Toast.makeText(
                                                                    context,
                                                                    "Aktivasi berhasil! Selamat datang di Lapak Kita.",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                                onAuthenticated(profile)
                                                            },
                                                            onFailure = { err ->
                                                                GoogleAuthHelper.signOut(context)
                                                                licenseStorage.clearSession()
                                                                errorMessage = err.message
                                                                    ?: "Kode aktivasi tidak valid atau sudah pernah digunakan."
                                                                isLoading = false
                                                                statusStep = null
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("register_activate_button")
                            )

                            Spacer(Modifier.height(12.dp))

                            // Secondary Neumorphic button: Contact Admin to buy activation code
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

                        // Error Display Box
                        if (!errorMessage.isNullOrBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(lapakColors.dangerBg)
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
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
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Subtle security & offline note
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    NeuIconBadge(
                        accentColor = MaterialTheme.colorScheme.primary,
                        size = 28.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "1 Akun = 1 Perangkat • Mendukung Mode Offline & Sinkron Cloud",
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
private fun AuthTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val tabModifier = if (selected) {
        modifier
            .neuExtruded(
                cornerRadius = 12.dp,
                shadowOffset = 3.dp,
                blurRadius = 6.dp,
                surfaceColor = MaterialTheme.colorScheme.primary
            )
            .clip(shape)
    } else {
        modifier.clip(shape)
    }

    Box(
        modifier = tabModifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun NeuInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    isMonospace: Boolean = false,
    readOnly: Boolean = false,
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
                        readOnly = readOnly,
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

@Composable
private fun NeuPrimaryActionButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .neuExtruded(
                cornerRadius = 16.dp,
                shadowOffset = 5.dp,
                blurRadius = 10.dp,
                surfaceColor = MaterialTheme.colorScheme.primary
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
