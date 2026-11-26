package com.example

import android.app.Activity
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.config.AppConfig
import com.example.config.LicenseStorage
import com.example.config.TrialManager
import com.example.data.database.LapakDatabase
import com.example.data.firebase.FinancialSyncRepository
import com.example.data.firebase.LicenseRepository
import com.example.data.firebase.VendorSyncRepository
import com.example.data.model.ThemeMode
import com.example.data.repository.LapakRepository
import com.example.i18n.AppStrings
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.LicenseActivationScreen
import com.example.ui.screens.*
import com.example.ui.theme.LapakKitaTheme
import com.example.ui.theme.LocalLapakColors
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.LapakViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContext = applicationContext
        val database = LapakDatabase.getDatabase(appContext, appScope)
        val repository = LapakRepository(database.lapakDao())
        val firestoreDb = FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
        val vendorSyncRepository = VendorSyncRepository(firestoreDb)
        val financialSyncRepository = FinancialSyncRepository(firestoreDb)
        val licenseStorage = LicenseStorage(appContext)
        val licenseRepository = LicenseRepository(firestoreDb)

        setContent {
            val context = LocalContext.current
            val systemDark = isSystemInDarkTheme()

            var showSplash by remember { mutableStateOf(true) }
            val trialManager = remember { TrialManager(context) }
            var isTrialExpired by remember { mutableStateOf(trialManager.isExpired()) }
            var isLicensed by remember { mutableStateOf(licenseStorage.isLicenseActive()) }
            var activeLicenseCode by remember { mutableStateOf(licenseStorage.getActivatedLicenseCode()) }

            if (AppConfig.IS_TRIAL) {
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(5000)
                        isTrialExpired = trialManager.isExpired()
                    }
                }
            }

            // Observe Firebase Authentication state reactively
            val auth = remember { Firebase.auth }
            var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }

            DisposableEffect(auth) {
                val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                    currentUser = firebaseAuth.currentUser
                }
                auth.addAuthStateListener(listener)
                onDispose {
                    auth.removeAuthStateListener(listener)
                }
            }

            if (showSplash) {
                LapakKitaTheme(darkTheme = systemDark) {
                    SplashScreen(onTimeout = { showSplash = false })
                }
            } else if (AppConfig.IS_TRIAL && isTrialExpired) {
                LapakKitaTheme(darkTheme = systemDark) {
                    TrialExpiredScreen(
                        onClose = { (context as? Activity)?.finish() }
                    )
                }
            } else if (!isLicensed) {
                LapakKitaTheme(darkTheme = systemDark) {
                    LicenseActivationScreen(
                        licenseRepository = licenseRepository,
                        licenseStorage = licenseStorage,
                        onActivated = { code ->
                            activeLicenseCode = code
                            isLicensed = true
                        }
                    )
                }
            } else if (currentUser == null) {
                LapakKitaTheme(darkTheme = systemDark) {
                    AuthScreen()
                }
            } else {
                val uid = currentUser!!.uid
                key(uid) {
                    val viewModel: LapakViewModel = viewModel(
                        key = uid,
                        factory = viewModelFactory {
                            initializer {
                                LapakViewModel(
                                    repository = repository,
                                    vendorSyncRepository = vendorSyncRepository,
                                    financialSyncRepository = financialSyncRepository,
                                    currentUserId = uid
                                )
                            }
                        }
                    )

                    AuthenticatedAppShell(
                        viewModel = viewModel,
                        systemDark = systemDark,
                        licenseRepository = licenseRepository,
                        activeLicenseCode = activeLicenseCode
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedAppShell(
    viewModel: LapakViewModel,
    systemDark: Boolean,
    licenseRepository: LicenseRepository,
    activeLicenseCode: String?
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val lang by viewModel.currentLanguage.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val webSyncNotification by viewModel.webSyncNotification.collectAsState()

    val cartItemCount = remember(cart) { cart.sumOf { it.quantity } }

    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> systemDark
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

    // Auto-dismiss real-time Web Penyetor banner after 6.5 seconds
    LaunchedEffect(webSyncNotification) {
        if (webSyncNotification != null) {
            delay(6500)
            viewModel.dismissWebSyncNotification()
        }
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
                        AppScreen.FINANCIAL_REPORT -> FinancialReportScreen(viewModel = viewModel)
                        AppScreen.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            licenseRepository = licenseRepository,
                            activeLicenseCode = activeLicenseCode
                        )
                        AppScreen.ABOUT -> SettingsScreen(
                            viewModel = viewModel,
                            licenseRepository = licenseRepository,
                            activeLicenseCode = activeLicenseCode
                        )
                    }
                }
            }

            // Real-time Web Penyetor Update Notification Banner
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
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    shadowElevation = 6.dp,
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

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 1.dp, color = lapakColors.cardBorder)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BottomNavItem(
                    label = AppStrings.get("nav_dashboard", lang),
                    selected = currentScreen == AppScreen.DASHBOARD,
                    selectedIcon = Icons.Filled.Dashboard,
                    unselectedIcon = Icons.Outlined.Dashboard,
                    testTag = "nav_dashboard",
                    onClick = { onSelectScreen(AppScreen.DASHBOARD) },
                    modifier = Modifier.weight(1f)
                )

                BottomNavItem(
                    label = AppStrings.get("nav_sales", lang),
                    selected = currentScreen == AppScreen.SALES,
                    selectedIcon = Icons.Filled.Storefront,
                    unselectedIcon = Icons.Outlined.Storefront,
                    testTag = "nav_sales",
                    onClick = { onSelectScreen(AppScreen.SALES) },
                    modifier = Modifier.weight(1f)
                )

                // Prominent Center Kasir Button
                CenterCashierNavButton(
                    label = AppStrings.get("nav_cashier", lang),
                    selected = currentScreen == AppScreen.CASHIER,
                    cartItemCount = cartItemCount,
                    onClick = { onSelectScreen(AppScreen.CASHIER) },
                    modifier = Modifier.weight(1.15f)
                )

                BottomNavItem(
                    label = AppStrings.get("nav_history", lang),
                    selected = currentScreen == AppScreen.HISTORY,
                    selectedIcon = Icons.Filled.ReceiptLong,
                    unselectedIcon = Icons.Outlined.ReceiptLong,
                    testTag = "nav_history",
                    onClick = { onSelectScreen(AppScreen.HISTORY) },
                    modifier = Modifier.weight(1f)
                )

                BottomNavItem(
                    label = AppStrings.get("nav_sellers", lang),
                    selected = currentScreen == AppScreen.SELLERS,
                    selectedIcon = Icons.Filled.People,
                    unselectedIcon = Icons.Outlined.People,
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
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
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
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = if (selected) activeColor else inactiveColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
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
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (selected) primaryColor else primaryColor.copy(alpha = 0.90f),
                shadowElevation = if (selected) 8.dp else 4.dp,
                border = if (selected) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primaryContainer)
                } else {
                    null
                },
                modifier = Modifier
                    .height(44.dp)
                    .widthIn(min = 62.dp)
                    .shadow(if (selected) 6.dp else 2.dp, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PointOfSale,
                        contentDescription = label,
                        tint = onPrimaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
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
    val lapakColors = LocalLapakColors.current
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
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, lapakColors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
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
                        shape = RoundedCornerShape(12.dp),
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
