package com.example.ui.auth

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class AuthGateTab {
    LOGIN,
    REGISTER
}

/**
 * Translates Firebase Auth exceptions into clear, actionable Indonesian messages.
 */
fun mapFirebaseAuthErrorToIndonesian(throwable: Throwable): String {
    if (throwable is FirebaseNetworkException) {
        return "Gagal terhubung ke server. Periksa koneksi internet Anda."
    }
    if (throwable is FirebaseAuthUserCollisionException) {
        return "Email ini sudah terdaftar, silakan pindah ke tab Login."
    }
    if (throwable is FirebaseAuthWeakPasswordException) {
        return "Password minimal 6 karakter."
    }
    if (throwable is FirebaseAuthInvalidUserException) {
        return "Email atau password salah."
    }
    if (throwable is FirebaseAuthInvalidCredentialsException) {
        val code = throwable.errorCode.uppercase()
        return when (code) {
            "ERROR_INVALID_EMAIL" -> "Format alamat email tidak valid."
            else -> "Email atau password salah."
        }
    }
    if (throwable is FirebaseAuthException) {
        val code = throwable.errorCode.uppercase()
        return when (code) {
            "ERROR_EMAIL_ALREADY_IN_USE", "EMAIL_EXISTS" ->
                "Email ini sudah terdaftar, silakan pindah ke tab Login."
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_WRONG_PASSWORD",
            "ERROR_USER_NOT_FOUND",
            "INVALID_LOGIN_CREDENTIALS" ->
                "Email atau password salah."
            "ERROR_WEAK_PASSWORD" ->
                "Password minimal 6 karakter."
            "ERROR_INVALID_EMAIL" ->
                "Format alamat email tidak valid."
            "ERROR_USER_DISABLED" ->
                "Akun ini telah dinonaktifkan oleh admin."
            "ERROR_TOO_MANY_REQUESTS" ->
                "Terlalu banyak percobaan gagal. Tunggu beberapa saat lalu coba lagi."
            "ERROR_OPERATION_NOT_ALLOWED" ->
                "Metode Email & Password belum diaktifkan di Firebase Console (Authentication → Sign-in method → Email/Password)."
            "ERROR_NETWORK_REQUEST_FAILED" ->
                "Gagal terhubung ke server. Periksa koneksi internet Anda."
            else ->
                "Autentikasi gagal ($code): ${throwable.localizedMessage ?: "Silakan coba lagi."}"
        }
    }

    val raw = throwable.message.orEmpty()
    val lower = raw.lowercase()
    return when {
        "email-already-in-use" in lower || "email_exists" in lower ->
            "Email ini sudah terdaftar, silakan pindah ke tab Login."
        "invalid-credential" in lower || "wrong-password" in lower || "user-not-found" in lower || "invalid_login_credentials" in lower ->
            "Email atau password salah."
        "weak-password" in lower ->
            "Password minimal 6 karakter."
        "invalid-email" in lower ->
            "Format alamat email tidak valid."
        "operation-not-allowed" in lower || "operation_not_allowed" in lower ->
            "Metode Email & Password belum diaktifkan di Firebase Console (Authentication → Sign-in method → Email/Password)."
        "network" in lower || "timeout" in lower || "unable to resolve host" in lower ->
            "Gagal terhubung ke server. Periksa koneksi internet Anda."
        raw.isNotBlank() -> raw
        else -> "Terjadi kesalahan saat memproses autentikasi. Silakan coba lagi."
    }
}

/**
 * Unified Soft UI / Neumorphism Email & Password Authentication + Activation Screen.
 *
 * Key stability guarantees:
 * - `authError` is hoisted in MainActivity so `AuthStateListener` or recompositions NEVER clear or flash it.
 * - `onAuthFlowRunningChange(true/false)` pauses MainActivity's startup auth observer while Login/Register executes,
 *   completely eliminating race conditions and accidental bounce-backs.
 * - Red alert box stays pinned directly above the submit button until the user edits input or taps the `×` close icon.
 */
@Composable
fun AuthScreen(
    activationAuthRepository: ActivationAuthRepository,
    licenseStorage: LicenseStorage,
    activeTab: AuthGateTab,
    onTabChange: (AuthGateTab) -> Unit,
    authError: String?,
    onSetAuthError: (String?) -> Unit,
    onAuthFlowRunningChange: (Boolean) -> Unit,
    autoRestoreHint: DeviceAutoRestoreHint? = null,
    onAuthenticated: (UserProfileDoc) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lapakColors = LocalLapakColors.current
    val currentDeviceId = remember { licenseStorage.getDeviceId() }

    // Login form state (pre-filled with registered email if same-HP reinstall detected)
    var loginEmail by remember(autoRestoreHint?.emailHint) {
        mutableStateOf(autoRestoreHint?.emailHint.orEmpty())
    }
    var loginPassword by remember { mutableStateOf("") }
    var showLoginPassword by remember { mutableStateOf(false) }

    // Register form state
    var businessName by remember { mutableStateOf(autoRestoreHint?.businessName.orEmpty()) }
    var registerEmail by remember { mutableStateOf("") }
    var registerPassword by remember { mutableStateOf("") }
    var showRegisterPassword by remember { mutableStateOf(false) }
    var activationCode by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var statusStep by remember { mutableStateOf<String?>(null) }

    fun clearErrorOnEdit() {
        if (authError != null) {
            onSetAuthError(null)
        }
    }

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

    fun submitLogin() {
        if (isLoading) return
        val email = loginEmail.trim()
        val password = loginPassword

        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            onSetAuthError("Masukkan alamat email yang valid.")
            return
        }
        if (password.length < 6) {
            onSetAuthError("Password minimal 6 karakter.")
            return
        }

        scope.launch {
            onSetAuthError(null)
            isLoading = true
            statusStep = "Memeriksa akun..."
            onAuthFlowRunningChange(true)

            try {
                val authResult = Firebase.auth.signInWithEmailAndPassword(email, password).await()
                val fbUser = authResult.user
                if (fbUser == null) {
                    onSetAuthError("Gagal memuat sesi akun. Silakan coba lagi.")
                    return@launch
                }

                statusStep = "Memeriksa lisensi & kunci perangkat..."
                when (
                    val check = activationAuthRepository.verifySingleDeviceOnLogin(
                        uid = fbUser.uid,
                        email = fbUser.email ?: email,
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
                        onSetAuthError(null)
                        onAuthenticated(check.profile)
                    }
                    is LoginDeviceCheckResult.NotRegistered -> {
                        Firebase.auth.signOut()
                        licenseStorage.clearSession()
                        onSetAuthError(
                            "Akun ($email) belum memiliki lisensi aktif. Silakan daftar dengan Kode Aktivasi Pembelian di tab Daftar Akun Baru."
                        )
                    }
                    is LoginDeviceCheckResult.BlockedOtherDevice -> {
                        Firebase.auth.signOut()
                        licenseStorage.clearSession()
                        onSetAuthError(check.message)
                    }
                    is LoginDeviceCheckResult.InactiveAccount -> {
                        Firebase.auth.signOut()
                        licenseStorage.clearSession()
                        onSetAuthError(check.message)
                    }
                    is LoginDeviceCheckResult.OfflineFallback -> {
                        val cached = licenseStorage.getOfflineSession()
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
                            Firebase.auth.signOut()
                            onSetAuthError("Tidak dapat terhubung ke server. Pastikan koneksi internet aktif saat login.")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AuthScreen", "Email/Password login error", e)
                onSetAuthError(mapFirebaseAuthErrorToIndonesian(e))
            } finally {
                isLoading = false
                statusStep = null
                onAuthFlowRunningChange(false)
            }
        }
    }

    fun submitRegister() {
        if (isLoading) return
        val cleanBiz = businessName.trim()
        val email = registerEmail.trim()
        val password = registerPassword
        val cleanCode = activationAuthRepository.normalizeCode(activationCode)

        if (cleanBiz.isBlank()) {
            onSetAuthError("Silakan isi Nama Usaha / Pemilik terlebih dahulu.")
            return
        }
        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            onSetAuthError("Format alamat email tidak valid.")
            return
        }
        if (password.length < 6) {
            onSetAuthError("Password minimal 6 karakter.")
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

            var createdNewAuthUser = false
            try {
                // Step 1: Strictly verify activation_codes/{code} has status == "available" FIRST
                when (val codeCheck = activationAuthRepository.checkActivationCodeAvailable(cleanCode)) {
                    is CodeAvailabilityResult.InvalidOrUsed -> {
                        onSetAuthError(codeCheck.message)
                        return@launch
                    }
                    is CodeAvailabilityResult.NetworkError -> {
                        onSetAuthError(codeCheck.message)
                        return@launch
                    }
                    is CodeAvailabilityResult.Available -> {
                        // Step 2: Create Firebase Auth account with Email & Password
                        statusStep = "Membuat akun baru..."
                        val authResult = Firebase.auth.createUserWithEmailAndPassword(email, password).await()
                        val fbUser = authResult.user
                        if (fbUser == null) {
                            onSetAuthError("Gagal membuat akun Firebase Auth. Silakan coba lagi.")
                            return@launch
                        }
                        createdNewAuthUser = true

                        // Step 3: Save profile in users/{uid} (with registeredDeviceId) & mark activation_codes/{code} -> "used"
                        statusStep = "Menyimpan profil & mengaktifkan kode..."
                        val regResult = activationAuthRepository.completeRegistrationWithCode(
                            uid = fbUser.uid,
                            email = fbUser.email ?: email,
                            businessName = cleanBiz,
                            rawCode = codeCheck.normalizedCode,
                            deviceId = currentDeviceId
                        )

                        regResult.fold(
                            onSuccess = { profile ->
                                // Step 4: Persist active session locally so user enters Dashboard immediately without bouncing back
                                licenseStorage.saveOfflineSession(
                                    uid = profile.userId,
                                    email = profile.email,
                                    businessName = profile.businessName,
                                    licenseCode = profile.licenseCode,
                                    deviceId = currentDeviceId,
                                    active = true
                                )
                                onSetAuthError(null)
                                Toast.makeText(
                                    context,
                                    "Aktivasi berhasil! Selamat datang di Lapak Kita.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onAuthenticated(profile)
                            },
                            onFailure = { err ->
                                // Clean up half-created auth user so user can fix code/network and retry cleanly
                                if (createdNewAuthUser) {
                                    runCatching { Firebase.auth.currentUser?.delete()?.await() }
                                }
                                Firebase.auth.signOut()
                                licenseStorage.clearSession()
                                onSetAuthError(
                                    err.message ?: "Kode aktivasi tidak valid atau sudah pernah digunakan."
                                )
                            }
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AuthScreen", "Email/Password registration error", e)
                if (createdNewAuthUser) {
                    runCatching { Firebase.auth.currentUser?.delete()?.await() }
                    Firebase.auth.signOut()
                }
                onSetAuthError(mapFirebaseAuthErrorToIndonesian(e))
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
                        .size(88.dp)
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

                Spacer(Modifier.height(14.dp))

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

                Spacer(Modifier.height(20.dp))

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
                                        onTabChange(AuthGateTab.LOGIN)
                                        onSetAuthError(null)
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
                                        onTabChange(AuthGateTab.REGISTER)
                                        onSetAuthError(null)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_tab_register")
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        // Auto-Restore Info Banner (same HP after reinstall)
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
                                            text = "Email terdaftar otomatis diisi (${autoRestoreHint.emailHint}). Masukkan password Anda untuk masuk kembali.",
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
                            // TAB 1: LOGIN (Email & Password)
                            // ===========================================================
                            Text(
                                text = "Masuk ke Akun Terdaftar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Masukkan email dan password akun Lapak Kita Anda.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(14.dp))

                            NeuInputField(
                                label = "Email",
                                value = loginEmail,
                                onValueChange = {
                                    loginEmail = it
                                    clearErrorOnEdit()
                                },
                                placeholder = "nama@email.com",
                                leadingIcon = Icons.Default.Email,
                                enabled = !isLoading,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    capitalization = KeyboardCapitalization.None
                                ),
                                modifier = Modifier.testTag("login_email_input")
                            )

                            Spacer(Modifier.height(12.dp))

                            NeuPasswordInputField(
                                label = "Password",
                                value = loginPassword,
                                onValueChange = {
                                    loginPassword = it
                                    clearErrorOnEdit()
                                },
                                placeholder = "Masukkan password",
                                passwordVisible = showLoginPassword,
                                onTogglePasswordVisibility = { showLoginPassword = !showLoginPassword },
                                enabled = !isLoading,
                                modifier = Modifier.testTag("login_password_input")
                            )

                            // Persistent Red Alert Box pinned directly above Submit button
                            if (!authError.isNullOrBlank()) {
                                Spacer(Modifier.height(14.dp))
                                PersistentAuthErrorBanner(
                                    message = authError,
                                    onDismiss = { onSetAuthError(null) }
                                )
                            }

                            Spacer(Modifier.height(18.dp))

                            NeuPrimaryActionButton(
                                text = if (isLoading) (statusStep ?: "Memproses...") else "Masuk ke Lapak",
                                isLoading = isLoading,
                                onClick = { submitLogin() },
                                modifier = Modifier.testTag("login_submit_button")
                            )
                        } else {
                            // ===========================================================
                            // TAB 2: DAFTAR AKUN BARU (Email, Password & Kode Aktivasi)
                            // ===========================================================
                            Text(
                                text = "Aktivasi & Daftar Akun Baru",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Lengkapi data akun dan Kode Aktivasi Pembelian resmi dari admin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(14.dp))

                            NeuInputField(
                                label = "Nama Usaha / Pemilik",
                                value = businessName,
                                onValueChange = {
                                    businessName = it
                                    clearErrorOnEdit()
                                },
                                placeholder = "Contoh: Lapak Kue Bu Sari",
                                leadingIcon = Icons.Default.Storefront,
                                enabled = !isLoading,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words
                                ),
                                modifier = Modifier.testTag("register_business_name_input")
                            )

                            Spacer(Modifier.height(12.dp))

                            NeuInputField(
                                label = "Email",
                                value = registerEmail,
                                onValueChange = {
                                    registerEmail = it
                                    clearErrorOnEdit()
                                },
                                placeholder = "nama@email.com",
                                leadingIcon = Icons.Default.Email,
                                enabled = !isLoading,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    capitalization = KeyboardCapitalization.None
                                ),
                                modifier = Modifier.testTag("register_email_input")
                            )

                            Spacer(Modifier.height(12.dp))

                            NeuPasswordInputField(
                                label = "Password (minimal 6 karakter)",
                                value = registerPassword,
                                onValueChange = {
                                    registerPassword = it
                                    clearErrorOnEdit()
                                },
                                placeholder = "Buat password minimal 6 karakter",
                                passwordVisible = showRegisterPassword,
                                onTogglePasswordVisibility = { showRegisterPassword = !showRegisterPassword },
                                enabled = !isLoading,
                                modifier = Modifier.testTag("register_password_input")
                            )

                            Spacer(Modifier.height(12.dp))

                            NeuInputField(
                                label = "Kode Aktivasi Pembelian",
                                value = activationCode,
                                onValueChange = { raw ->
                                    activationCode = raw.uppercase().replace(" ", "").take(32)
                                    clearErrorOnEdit()
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

                            // Persistent Red Alert Box pinned directly above Submit button
                            if (!authError.isNullOrBlank()) {
                                Spacer(Modifier.height(14.dp))
                                PersistentAuthErrorBanner(
                                    message = authError,
                                    onDismiss = { onSetAuthError(null) }
                                )
                            }

                            Spacer(Modifier.height(18.dp))

                            NeuPrimaryActionButton(
                                text = if (isLoading) (statusStep ?: "Memproses...") else "Aktifkan & Daftar Akun",
                                isLoading = isLoading,
                                onClick = { submitRegister() },
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
                    }
                }

                Spacer(Modifier.height(16.dp))

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

/**
 * Persistent red alert banner that stays visible until user edits an input field or taps `×`.
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
    leadingIcon: ImageVector,
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
private fun NeuPasswordInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    passwordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
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
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            capitalization = KeyboardCapitalization.None
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                IconButton(
                    onClick = onTogglePasswordVisibility,
                    enabled = enabled,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Sembunyikan password" else "Tampilkan password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
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
