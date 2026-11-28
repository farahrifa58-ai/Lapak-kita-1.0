package com.example

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.HourglassDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.config.AppConfig
import com.example.config.LicenseStorage
import com.example.config.TrialManager
import com.example.data.database.LapakDatabase
import com.example.data.firebase.ActivationAuthRepository
import com.example.data.firebase.DeviceAutoRestoreHint
import com.example.data.firebase.FinancialSyncRepository
import com.example.data.firebase.LoginDeviceCheckResult
import com.example.data.firebase.VendorSyncRepository
import com.example.data.model.ThemeMode
import com.example.data.repository.LapakRepository
import com.example.i18n.AppStrings
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.CompleteActivationScreen
import com.example.ui.auth.GoogleAuthHelper
import com.example.ui.auth.GoogleSignInOutcome
import com.example.ui.components.LucideIcons
import com.example.ui.components.NeuCard
import com.example.ui.components.QuickRoleSwitchDialog
import com.example.ui.components.neuExtruded
import com.example.ui.components.neuInset
import com.example.ui.screens.*
import com.example.ui.theme.LapakKitaTheme
import com.example.ui.theme.LocalLapakColors
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.LapakViewModel
import com.example.util.CloudAutoBackupWorker
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed class AccessGateState {
    data object Checking : AccessGateState()
    data class RequireGoogleSignIn(
        val autoRestoreHint: DeviceAutoRestoreHint? = null
    ) : AccessGateState()
    data class CompleteActivation(
        val uid: String,
        val email: String,
        val displayName: String
    ) : AccessGateState()
    data class Granted(
        val uid: String,
        val licenseCode: String,
        val businessName: String = ""
    ) : AccessGateState()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContext = applicationContext
        val licenseStorage = LicenseStorage(appContext)
        // One-time startup cleanup: purge legacy global (non-UID-prefixed) Room DB, prefs & files
        licenseStorage.cleanupLegacyGlobalCacheOnce()

        val primaryDatabaseId = appContext.getString(R.string.firestore_database_id).trim()
        val firestoreDb = FirebaseFirestore.getInstance(primaryDatabaseId).apply {
            runCatching {
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
            }
        }
        val fallbackLicenseDbs = buildList {
            val legacyDbId = ActivationAuthRepository.LEGACY_LICENSE_DATABASE_ID
            if (legacyDbId.isNotBlank() && legacyDbId != primaryDatabaseId) {
                runCatching { FirebaseFirestore.getInstance(legacyDbId) }
                    .getOrNull()
                    ?.let { add(it) }
            }
            if (primaryDatabaseId != "(default)") {
                runCatching { FirebaseFirestore.getInstance() }
                    .getOrNull()
                    ?.let { add(it) }
            }
        }
        val vendorSyncRepository = VendorSyncRepository(firestoreDb)
        val financialSyncRepository = FinancialSyncRepository(firestoreDb)
        val activationAuthRepository = ActivationAuthRepository(
            db = firestoreDb,
            fallbackDbs = fallbackLicenseDbs
        )

        setContent {
            val context = LocalContext.current
            val systemDark = isSystemInDarkTheme()

            var showSplash by remember { mutableStateOf(true) }
            val trialManager = remember { TrialManager(context) }
            var isTrialExpired by remember { mutableStateOf(trialManager.isExpired()) }

            if (AppConfig.IS_TRIAL) {
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(5000)
                        isTrialExpired = trialManager.isExpired()
                    }
                }
            }

            val auth = remember { Firebase.auth }
            val rootScope = rememberCoroutineScope()
            var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
            var gateState by remember { mutableStateOf<AccessGateState>(AccessGateState.Checking) }

            // Persistent hoisted auth error & flow lock (NEVER wiped by AuthStateListener or recompositions!)
            var authError by remember { mutableStateOf<String?>(null) }
            var isAuthActionRunning by remember { mutableStateOf(false) }

            DisposableEffect(auth) {
                val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                    val latestUser = firebaseAuth.currentUser
                    val previousUid = (gateState as? AccessGateState.Granted)?.uid
                    // If auth UID switched or signed out while an old session was mounted,
                    // immediately unmount the old session so all Firestore listeners & caches tear down first.
                    if (previousUid != null && latestUser?.uid != previousUid) {
                        gateState = if (latestUser == null) {
                            AccessGateState.RequireGoogleSignIn()
                        } else {
                            AccessGateState.Checking
                        }
                    }
                    currentUser = latestUser
                }
                auth.addAuthStateListener(listener)
                onDispose {
                    auth.removeAuthStateListener(listener)
                }
            }

            // Startup Auto-Restore & Single Device Lock observer (Offline-First, strictly UID-isolated!)
            LaunchedEffect(currentUser?.uid, isAuthActionRunning) {
                if (isAuthActionRunning) return@LaunchedEffect

                val deviceId = licenseStorage.getDeviceId()
                val fbUser = currentUser

                // Ensure any previous UID's Granted shell is unmounted BEFORE loading the new UID
                val currentGate = gateState
                if (currentGate is AccessGateState.Granted && currentGate.uid != fbUser?.uid) {
                    gateState = if (fbUser == null) {
                        AccessGateState.RequireGoogleSignIn()
                    } else {
                        AccessGateState.Checking
                    }
                }

                if (fbUser != null) {
                    val offlineSession = licenseStorage.getOfflineSession(fbUser.uid)

                    // Already on CompleteActivation screen for this exact UID -> don't bounce!
                    val existingState = gateState
                    if (existingState is AccessGateState.CompleteActivation && existingState.uid == fbUser.uid) {
                        return@LaunchedEffect
                    }

                    // 1. Offline-first instant entry if cached session matches this exact UID & device
                    if (offlineSession != null && offlineSession.uid == fbUser.uid) {
                        gateState = AccessGateState.Granted(
                            uid = fbUser.uid,
                            licenseCode = offlineSession.licenseCode,
                            businessName = offlineSession.businessName
                        )
                        // Background online Single Device Lock check (does NOT block offline use)
                        when (
                            val onlineCheck = activationAuthRepository.verifySingleDeviceOnLogin(
                                uid = fbUser.uid,
                                email = fbUser.email.orEmpty(),
                                currentDeviceId = deviceId
                            )
                        ) {
                            is LoginDeviceCheckResult.Allowed -> {
                                licenseStorage.saveOfflineSession(
                                    uid = onlineCheck.profile.userId,
                                    email = onlineCheck.profile.email,
                                    businessName = onlineCheck.profile.businessName,
                                    licenseCode = onlineCheck.profile.licenseCode,
                                    deviceId = deviceId,
                                    connectedDevicesCount = onlineCheck.profile.connectedDevicesCount,
                                    isOwnerDevice = onlineCheck.profile.isOwnerDevice(deviceId),
                                    active = true
                                )
                                gateState = AccessGateState.Granted(
                                    uid = onlineCheck.profile.userId,
                                    licenseCode = onlineCheck.profile.licenseCode,
                                    businessName = onlineCheck.profile.businessName
                                )
                            }
                            is LoginDeviceCheckResult.BlockedOtherDevice -> {
                                authError = onlineCheck.message
                                gateState = AccessGateState.RequireGoogleSignIn()
                                licenseStorage.clearSession(fbUser.uid)
                                GoogleAuthHelper.signOut(context)
                            }
                            is LoginDeviceCheckResult.InactiveAccount -> {
                                authError = onlineCheck.message
                                gateState = AccessGateState.RequireGoogleSignIn()
                                licenseStorage.clearSession(fbUser.uid)
                                GoogleAuthHelper.signOut(context)
                            }
                            is LoginDeviceCheckResult.NotRegistered -> {
                                licenseStorage.clearSession(fbUser.uid)
                                gateState = AccessGateState.CompleteActivation(
                                    uid = fbUser.uid,
                                    email = fbUser.email.orEmpty(),
                                    displayName = fbUser.displayName.orEmpty()
                                )
                            }
                            is LoginDeviceCheckResult.OfflineFallback -> Unit // Stay gracefully in offline mode!
                        }
                    } else {
                        // Existing Google Auth session without local cache -> verify users/{uid}
                        gateState = AccessGateState.Checking
                        when (
                            val onlineCheck = activationAuthRepository.verifySingleDeviceOnLogin(
                                uid = fbUser.uid,
                                email = fbUser.email.orEmpty(),
                                currentDeviceId = deviceId
                            )
                        ) {
                            is LoginDeviceCheckResult.Allowed -> {
                                licenseStorage.saveOfflineSession(
                                    uid = onlineCheck.profile.userId,
                                    email = onlineCheck.profile.email,
                                    businessName = onlineCheck.profile.businessName,
                                    licenseCode = onlineCheck.profile.licenseCode,
                                    deviceId = deviceId,
                                    connectedDevicesCount = onlineCheck.profile.connectedDevicesCount,
                                    isOwnerDevice = onlineCheck.profile.isOwnerDevice(deviceId),
                                    active = true
                                )
                                authError = null
                                gateState = AccessGateState.Granted(
                                    uid = onlineCheck.profile.userId,
                                    licenseCode = onlineCheck.profile.licenseCode,
                                    businessName = onlineCheck.profile.businessName
                                )
                            }
                            is LoginDeviceCheckResult.NotRegistered -> {
                                authError = null
                                gateState = AccessGateState.CompleteActivation(
                                    uid = fbUser.uid,
                                    email = fbUser.email.orEmpty(),
                                    displayName = fbUser.displayName.orEmpty()
                                )
                            }
                            is LoginDeviceCheckResult.BlockedOtherDevice -> {
                                authError = onlineCheck.message
                                gateState = AccessGateState.RequireGoogleSignIn()
                                licenseStorage.clearSession(fbUser.uid)
                                GoogleAuthHelper.signOut(context)
                            }
                            is LoginDeviceCheckResult.InactiveAccount -> {
                                authError = onlineCheck.message
                                gateState = AccessGateState.RequireGoogleSignIn()
                                licenseStorage.clearSession(fbUser.uid)
                                GoogleAuthHelper.signOut(context)
                            }
                            is LoginDeviceCheckResult.OfflineFallback -> {
                                authError = "Perlu koneksi internet untuk memverifikasi akun Google pertama kali."
                                gateState = AccessGateState.RequireGoogleSignIn()
                                GoogleAuthHelper.signOut(context)
                            }
                        }
                    }
                } else {
                    // Already showing Google Sign-In gate? Keep it mounted with its persistent authError!
                    if (gateState is AccessGateState.RequireGoogleSignIn) {
                        return@LaunchedEffect
                    }

                    // Initial startup with no Auth session -> check device_registry for same-HP Auto-Restore!
                    gateState = AccessGateState.Checking
                    val hint = activationAuthRepository.lookupDeviceRegistration(deviceId)
                    if (hint != null) {
                        when (GoogleAuthHelper.attemptAutoSignIn(context)) {
                            is GoogleSignInOutcome.Success -> return@LaunchedEffect
                            else -> Unit
                        }
                    }
                    gateState = AccessGateState.RequireGoogleSignIn(autoRestoreHint = hint)
                }
            }

            if (showSplash) {
                LapakKitaTheme(darkTheme = false, darkHeader = false) {
                    SplashScreen(onTimeout = { showSplash = false })
                }
            } else if (AppConfig.IS_TRIAL && isTrialExpired) {
                LapakKitaTheme(darkTheme = systemDark) {
                    TrialExpiredScreen(
                        onClose = { (context as? Activity)?.finish() }
                    )
                }
            } else {
                when (val state = gateState) {
                    is AccessGateState.Checking -> {
                        LapakKitaTheme(darkTheme = systemDark) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        text = "Memeriksa sesi & lisensi perangkat...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    is AccessGateState.RequireGoogleSignIn -> {
                        LapakKitaTheme(darkTheme = systemDark, darkHeader = false) {
                            AuthScreen(
                                activationAuthRepository = activationAuthRepository,
                                licenseStorage = licenseStorage,
                                authError = authError,
                                onSetAuthError = { authError = it },
                                onAuthFlowRunningChange = { isAuthActionRunning = it },
                                autoRestoreHint = state.autoRestoreHint,
                                onRequireActivation = { uid, email, displayName ->
                                    gateState = AccessGateState.CompleteActivation(
                                        uid = uid,
                                        email = email,
                                        displayName = displayName
                                    )
                                },
                                onAuthenticated = { profile ->
                                    authError = null
                                    gateState = AccessGateState.Granted(
                                        uid = profile.userId,
                                        licenseCode = profile.licenseCode,
                                        businessName = profile.businessName
                                    )
                                }
                            )
                        }
                    }
                    is AccessGateState.CompleteActivation -> {
                        LapakKitaTheme(darkTheme = systemDark, darkHeader = false) {
                            CompleteActivationScreen(
                                uid = state.uid,
                                email = state.email,
                                initialDisplayName = state.displayName,
                                activationAuthRepository = activationAuthRepository,
                                licenseStorage = licenseStorage,
                                authError = authError,
                                onSetAuthError = { authError = it },
                                onAuthFlowRunningChange = { isAuthActionRunning = it },
                                onSwitchAccount = {
                                    authError = null
                                    gateState = AccessGateState.RequireGoogleSignIn()
                                },
                                onActivated = { profile ->
                                    authError = null
                                    gateState = AccessGateState.Granted(
                                        uid = profile.userId,
                                        licenseCode = profile.licenseCode,
                                        businessName = profile.businessName
                                    )
                                }
                            )
                        }
                    }
                    is AccessGateState.Granted -> {
                        key(state.uid) {
                            // Dedicated per-UID ViewModelStore + UID-scoped Room DB (`lapakkita_{uid}_db`)
                            // so switching accounts NEVER reuses another account's ViewModel, listeners, or SQLite DB!
                            val sessionStoreOwner = remember(state.uid) {
                                object : ViewModelStoreOwner {
                                    override val viewModelStore: ViewModelStore = ViewModelStore()
                                }
                            }
                            val userDatabase = remember(state.uid) {
                                LapakDatabase.getDatabaseForUser(appContext, state.uid)
                            }
                            val userRepository = remember(state.uid, userDatabase) {
                                LapakRepository(userDatabase.lapakDao())
                            }
                            val viewModel: LapakViewModel = viewModel(
                                viewModelStoreOwner = sessionStoreOwner,
                                key = "lapak_vm_${state.uid}",
                                factory = viewModelFactory {
                                    initializer {
                                        LapakViewModel(
                                            repository = userRepository,
                                            vendorSyncRepository = vendorSyncRepository,
                                            financialSyncRepository = financialSyncRepository,
                                            activationAuthRepository = activationAuthRepository,
                                            licenseStorage = licenseStorage,
                                            currentUserId = state.uid,
                                            currentDeviceId = licenseStorage.getDeviceId(),
                                            initialBusinessName = state.businessName
                                        )
                                    }
                                }
                            )

                            DisposableEffect(state.uid) {
                                onDispose {
                                    // 1. Unsubscribe all Firestore onSnapshot listeners & reset in-memory state immediately
                                    viewModel.teardownSessionAndUnsubscribe()
                                    // 2. Clear dedicated per-UID ViewModelStore (cancels viewModelScope cleanly)
                                    sessionStoreOwner.viewModelStore.clear()
                                    // 3. Close & evict this UID's Room DB handle
                                    LapakDatabase.closeUserDatabase(state.uid)
                                }
                            }

                            AuthenticatedAppShell(
                                viewModel = viewModel,
                                systemDark = systemDark,
                                activeLicenseCode = state.licenseCode,
                                onSignedOut = {
                                    val uidToTearDown = state.uid
                                    rootScope.launch {
                                        isAuthActionRunning = true
                                        try {
                                            // 1. FIRST unsubscribe all Firestore listeners & reset in-memory state
                                            viewModel.teardownSessionAndUnsubscribe()
                                            // 1b. Cancel hourly WorkManager auto-backup for this UID
                                            CloudAutoBackupWorker.cancelHourlyAutoBackup(appContext, uidToTearDown)
                                            // 2. Clear cached offline session for this UID
                                            licenseStorage.clearSession(uidToTearDown)
                                            // 3. Immediately unmount AuthenticatedAppShell (triggers onDispose -> clear store & close DB)
                                            authError = null
                                            gateState = AccessGateState.RequireGoogleSignIn()
                                            // 4. Sign out from Firebase Auth & Android Credential Manager
                                            GoogleAuthHelper.signOut(context)
                                        } finally {
                                            isAuthActionRunning = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            LapakDatabase.closeAllDatabases()
        }
        super.onDestroy()
    }
}

@Composable
private fun AuthenticatedAppShell(
    viewModel: LapakViewModel,
    systemDark: Boolean,
    activeLicenseCode: String?,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val lang by viewModel.currentLanguage.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val isOwner by viewModel.isOwner.collectAsState()
    val isRestoringFromCloud by viewModel.isRestoringFromCloud.collectAsState()
    val showRoleSwitchDialog by viewModel.showRoleSwitchDialog.collectAsState()
    val webSyncNotification by viewModel.webSyncNotification.collectAsState()
    val webPortalSyncFeedback by viewModel.webPortalSyncFeedback.collectAsState()

    val cartItemCount = remember(cart) { cart.sumOf { it.quantity } }

    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> systemDark
    }

    // Schedule hourly periodic WorkManager auto-backup to Firestore when authenticated
    LaunchedEffect(viewModel.currentUserId) {
        CloudAutoBackupWorker.scheduleHourlyAutoBackup(context, viewModel.currentUserId)
    }

    // Observe internet connectivity via ConnectivityManager.NetworkCallback to auto-flush pending syncs
    DisposableEffect(context, viewModel) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val checkOnlineNow: () -> Boolean = {
            runCatching {
                val active = cm?.activeNetwork
                val caps = if (active != null) cm.getNetworkCapabilities(active) else null
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }.getOrDefault(true)
        }
        viewModel.onNetworkConnectivityChanged(checkOnlineNow())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                viewModel.onNetworkConnectivityChanged(true)
            }

            override fun onLost(network: Network) {
                viewModel.onNetworkConnectivityChanged(checkOnlineNow())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                viewModel.onNetworkConnectivityChanged(hasInternet)
            }
        }
        val registered = runCatching {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm?.registerNetworkCallback(req, callback)
            cm != null
        }.getOrDefault(false)

        onDispose {
            if (registered) {
                runCatching { cm?.unregisterNetworkCallback(callback) }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkOperationalCycleOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(webSyncNotification) {
        if (webSyncNotification != null) {
            delay(6500)
            viewModel.dismissWebSyncNotification()
        }
    }

    LaunchedEffect(webPortalSyncFeedback?.id) {
        val fb = webPortalSyncFeedback ?: return@LaunchedEffect
        Toast.makeText(
            context,
            fb.message,
            if (fb.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
        ).show()
        delay(if (fb.isSuccess) 4000L else 8500L)
        viewModel.dismissWebPortalSyncFeedback()
    }

    LapakKitaTheme(darkTheme = darkTheme) {
        if (currentScreen != AppScreen.DASHBOARD) {
            BackHandler {
                viewModel.navigateTo(AppScreen.DASHBOARD)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    val hideBottomBar = currentScreen == AppScreen.SETTINGS ||
                        currentScreen == AppScreen.ABOUT ||
                        currentScreen == AppScreen.FINANCIAL_REPORT
                    if (!hideBottomBar) {
                        LapakModernBottomBar(
                            currentScreen = currentScreen,
                            cartItemCount = cartItemCount,
                            lang = lang,
                            onSelectScreen = { viewModel.navigateTo(it) }
                        )
                    }
                }
            ) { innerPadding ->
                val hideBottomBar = currentScreen == AppScreen.SETTINGS ||
                    currentScreen == AppScreen.ABOUT ||
                    currentScreen == AppScreen.FINANCIAL_REPORT
                Crossfade(
                    targetState = currentScreen,
                    label = "ScreenTransition",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            bottom = if (!hideBottomBar) {
                                innerPadding.calculateBottomPadding()
                            } else {
                                0.dp
                            }
                        )
                ) { screen ->
                    when (screen) {
                        AppScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                        AppScreen.SALES -> ProductsScreen(viewModel = viewModel)
                        AppScreen.CASHIER -> CashierScreen(viewModel = viewModel)
                        AppScreen.HISTORY -> TransactionsScreen(viewModel = viewModel)
                        AppScreen.SELLERS -> SellersScreen(viewModel = viewModel)
                        AppScreen.FINANCIAL_REPORT -> {
                            if (isOwner) {
                                FinancialReportScreen(viewModel = viewModel)
                            } else {
                                DashboardScreen(viewModel = viewModel)
                            }
                        }
                        AppScreen.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            activeLicenseCode = activeLicenseCode,
                            onSignedOut = onSignedOut
                        )
                        AppScreen.ABOUT -> SettingsScreen(
                            viewModel = viewModel,
                            activeLicenseCode = activeLicenseCode,
                            onSignedOut = onSignedOut
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !webSyncNotification.isNullOrBlank(),
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                val lapakColors = LocalLapakColors.current
                NeuCard(
                    cornerRadius = 18.dp,
                    shadowOffset = 5.dp,
                    blurRadius = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .testTag("web_sync_notification_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(lapakColors.omzetCardBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Sinkronisasi Web Penyetor",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Update Web Penyetor",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = webSyncNotification.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { viewModel.dismissWebSyncNotification() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup Notifikasi",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Explicit Green / Red Web Portal Sync Status Alert Banner
            AnimatedVisibility(
                visible = webPortalSyncFeedback != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                val feedback = webPortalSyncFeedback
                if (feedback != null) {
                    val containerColor = if (feedback.isSuccess) {
                        Color(0xFF1F6E4E)
                    } else {
                        Color(0xFFC43B35)
                    }
                    Surface(
                        color = containerColor,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 520.dp)
                            .testTag(
                                if (feedback.isSuccess) {
                                    "web_portal_sync_success_banner"
                                } else {
                                    "web_portal_sync_error_banner"
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (feedback.isSuccess) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = if (feedback.isSuccess) {
                                    "Berhasil Sinkron Web Portal"
                                } else {
                                    "Gagal Sinkron Web Portal"
                                },
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (feedback.isSuccess) {
                                        "Tersinkron ke Web Portal"
                                    } else {
                                        "Gagal Sinkron ke Web Portal"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = feedback.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.92f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { viewModel.dismissWebPortalSyncFeedback() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup Peringatan Sinkronisasi",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Auto-Restore Cloud Data Indicator on fresh login / reinstall
            AnimatedVisibility(
                visible = isRestoringFromCloud,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .testTag("cloud_auto_restore_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Memulihkan data akun Anda...",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "Mengunduh penyetor, produk, transaksi & arsip dari Cloud Firestore",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Quick Owner / Kasir Switch Dialog (accessible from top header badge on every screen)
        if (showRoleSwitchDialog) {
            QuickRoleSwitchDialog(
                isOwner = isOwner,
                isUsingDefaultPin = viewModel.isUsingDefaultOwnerPin(),
                onSwitchToCashier = {
                    viewModel.switchToCashierMode()
                    Toast.makeText(context, "Beralih ke Mode Kasir", Toast.LENGTH_SHORT).show()
                },
                onVerifyOwnerPin = { pin ->
                    val ok = viewModel.verifyOwnerPinAndSwitch(pin)
                    if (ok) {
                        Toast.makeText(context, "Mode Owner diaktifkan", Toast.LENGTH_SHORT).show()
                    }
                    ok
                },
                onUpdateOwnerPin = { oldPin, newPin ->
                    viewModel.updateOwnerPin(oldPin, newPin)
                },
                onDismiss = { viewModel.dismissRoleSwitchDialog() }
            )
        }
    }
}

@Composable
private fun LapakModernBottomBar(
    currentScreen: AppScreen,
    cartItemCount: Int,
    lang: String,
    onSelectScreen: (AppScreen) -> Unit
) {
    val lapakColors = LocalLapakColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(lapakColors.neuSurface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(lapakColors.neuShadowLight.copy(alpha = 0.7f))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BottomNavItem(
                    label = AppStrings.get("nav_dashboard", lang),
                    selected = currentScreen == AppScreen.DASHBOARD,
                    icon = LucideIcons.LayoutGrid,
                    testTag = "nav_dashboard",
                    onClick = { onSelectScreen(AppScreen.DASHBOARD) },
                    modifier = Modifier.weight(1f)
                )

                BottomNavItem(
                    label = AppStrings.get("nav_sales", lang),
                    selected = currentScreen == AppScreen.SALES,
                    icon = LucideIcons.Store,
                    testTag = "nav_sales",
                    onClick = { onSelectScreen(AppScreen.SALES) },
                    modifier = Modifier.weight(1f)
                )

                CenterCashierNavButton(
                    label = AppStrings.get("nav_cashier", lang),
                    selected = currentScreen == AppScreen.CASHIER,
                    cartItemCount = cartItemCount,
                    onClick = { onSelectScreen(AppScreen.CASHIER) },
                    modifier = Modifier.weight(1.18f)
                )

                BottomNavItem(
                    label = AppStrings.get("nav_history", lang),
                    selected = currentScreen == AppScreen.HISTORY,
                    icon = LucideIcons.ReceiptText,
                    testTag = "nav_history",
                    onClick = { onSelectScreen(AppScreen.HISTORY) },
                    modifier = Modifier.weight(1f)
                )

                BottomNavItem(
                    label = AppStrings.get("nav_sellers", lang),
                    selected = currentScreen == AppScreen.SELLERS,
                    icon = LucideIcons.UsersRound,
                    testTag = "nav_sellers",
                    onClick = { onSelectScreen(AppScreen.SELLERS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    selected: Boolean,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                role = Role.Tab,
                onClick = onClick
            )
            .padding(vertical = 3.dp, horizontal = 2.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (selected) {
                        Modifier.neuInset(
                            cornerRadius = 12.dp,
                            shadowOffset = 2.dp,
                            blurRadius = 5.dp
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) activeColor else inactiveColor,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) activeColor else inactiveColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CenterCashierNavButton(
    label: String,
    selected: Boolean,
    cartItemCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 54.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .testTag("nav_cashier"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BadgedBox(
            badge = {
                if (cartItemCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    ) {
                        Text(
                            text = if (cartItemCount > 99) "99+" else "$cartItemCount",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .widthIn(min = 66.dp)
                    .neuExtruded(
                        cornerRadius = 22.dp,
                        shadowOffset = if (selected) 5.dp else 4.dp,
                        blurRadius = if (selected) 10.dp else 8.dp,
                        surfaceColor = primaryColor
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = LucideIcons.Calculator,
                    contentDescription = label,
                    tint = onPrimaryColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = primaryColor,
            maxLines = 1
        )
    }
}

@Composable
private fun TrialExpiredScreen(
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            NeuCard(
                cornerRadius = 22.dp,
                shadowOffset = 6.dp,
                blurRadius = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.HourglassDisabled,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Masa Uji Coba Berakhir",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Waktu pemakaian versi Trial (60 menit) telah habis.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Terima kasih telah mencoba Lapak Kita by Rafline Dev. Silakan hubungi admin atau gunakan Full Version untuk akses tanpa batas waktu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Tutup Aplikasi", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
