package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.DexcargoRepository
import com.example.ui.*
import com.example.ui.screens.*
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize local Room layers
        val database = AppDatabase.getDatabase(this)
        val repository = DexcargoRepository(database)
        val viewModelFactory = DexcargoViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[DexcargoViewModel::class.java]

        setContent {
            MyApplicationTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()
                val currentEmp by viewModel.currentEmployee.collectAsState()

                // Register Native Back button interception
                if (currentEmp != null) {
                    val isHomeScreen = currentScreen is Screen.SalesRepHome ||
                            currentScreen is Screen.LogisticsManagerHome ||
                            currentScreen is Screen.SalesManagerHome ||
                            currentScreen is Screen.AdminHome
                    if (!isHomeScreen) {
                        BackHandler {
                            viewModel.navigateBack()
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = DarkBg,
                    bottomBar = {
                        if (currentEmp != null && currentScreen !is Screen.SetPin && currentScreen !is Screen.EnterPin && currentScreen !is Screen.ProfileSettings) {
                            DexBottomNavBar(
                                currentScreen = currentScreen,
                                onTabClick = { target ->
                                    if (target == "home") {
                                        viewModel.routeToUserHome()
                                    } else if (target == "scan") {
                                        viewModel.navigateTo(Screen.ScanSticker)
                                    } else if (target == "list") {
                                        viewModel.navigateTo(Screen.PackageList)
                                    } else if (target == "payments") {
                                        viewModel.navigateTo(Screen.PaymentNotificationCenter)
                                    } else if (target == "commissions") {
                                        viewModel.navigateTo(Screen.MyCommissions)
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            is Screen.Login -> LoginScreen(viewModel)
                            is Screen.SetPin -> SetPinScreen(viewModel)
                            is Screen.EnterPin -> EnterPinScreen(viewModel)
                            is Screen.SalesRepHome -> SalesRepHomeScreen(viewModel)
                            is Screen.LogisticsManagerHome -> LogisticsManagerHomeScreen(viewModel)
                            is Screen.SalesManagerHome -> SalesManagerHomeScreen(viewModel)
                            is Screen.AdminHome -> AdminHomeScreen(viewModel)
                            is Screen.ScanSticker -> ScanStickerScreen(viewModel)
                            is Screen.OcrProcessing -> OcrProcessingScreen(viewModel)
                            is Screen.OcrReview -> OcrReviewScreen(viewModel)
                            is Screen.TakePackagePhoto -> TakePackagePhotoScreen(viewModel)
                            is Screen.RegistrationSuccess -> RegistrationSuccessScreen(viewModel)
                            is Screen.PackageList -> PackageListScreen(viewModel)
                            is Screen.PackageDetails -> PackageDetailsScreen(viewModel)
                            is Screen.PaymentGateway -> PaymentGatewayScreen(viewModel)
                            is Screen.StkWait -> StkWaitScreen(viewModel)
                            is Screen.PaymentSuccess -> PaymentSuccessScreen(viewModel)
                            is Screen.CustomerVerification -> CustomerVerificationScreen(viewModel)
                            is Screen.SignatureCapture -> SignatureCaptureScreen(viewModel)
                            is Screen.CollectionSuccess -> CollectionSuccessScreen(viewModel)
                            is Screen.MyCommissions -> CommissionsScreen(viewModel)
                            is Screen.PaymentNotificationCenter -> PaymentNotificationCenterScreen(viewModel)
                            is Screen.LinkPayment -> LinkPaymentScreen(viewModel)
                            is Screen.ProfileSettings -> ProfileSettingsScreen(viewModel)
                        }

                        // SUBTLE ROLE PREVIEW BAR (For reviewers and demos)
                        if (currentEmp != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                            ) {
                                RoleQuickPreviewSwitcher(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DexBottomNavBar(currentScreen: Screen, onTabClick: (String) -> Unit) {
    NavigationBar(
        containerColor = DarkSurfaceVariant,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        tonalElevation = 8.dp
    ) {
        val isHome = currentScreen is Screen.SalesRepHome ||
                currentScreen is Screen.LogisticsManagerHome ||
                currentScreen is Screen.SalesManagerHome ||
                currentScreen is Screen.AdminHome

        val navItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color(0xFF111F0D),
            selectedTextColor = Color(0xFF111F0D),
            indicatorColor = OrangeAccentBg,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextSecondary
        )

        NavigationBarItem(
            selected = isHome,
            onClick = { onTabClick("home") },
            icon = { Icon(Icons.Default.Dashboard, "Dashboard") },
            label = { Text("Dashboard", fontSize = 10.sp) },
            colors = navItemColors
        )
        NavigationBarItem(
            selected = currentScreen is Screen.ScanSticker || currentScreen is Screen.OcrProcessing || currentScreen is Screen.OcrReview || currentScreen is Screen.TakePackagePhoto,
            onClick = { onTabClick("scan") },
            icon = { Icon(Icons.Default.QrCodeScanner, "Scan") },
            label = { Text("Scan Sticker", fontSize = 10.sp) },
            colors = navItemColors
        )
        NavigationBarItem(
            selected = currentScreen is Screen.PackageList || currentScreen is Screen.PackageDetails,
            onClick = { onTabClick("list") },
            icon = { Icon(Icons.Default.FormatListBulleted, "Packages") },
            label = { Text("Packages", fontSize = 10.sp) },
            colors = navItemColors
        )
        NavigationBarItem(
            selected = currentScreen is Screen.PaymentNotificationCenter || currentScreen is Screen.LinkPayment,
            onClick = { onTabClick("payments") },
            icon = { Icon(Icons.Default.Payment, "Payments") },
            label = { Text("Payments", fontSize = 10.sp) },
            colors = navItemColors
        )
        NavigationBarItem(
            selected = currentScreen is Screen.MyCommissions,
            onClick = { onTabClick("commissions") },
            icon = { Icon(Icons.Default.MonetizationOn, "Commissions") },
            label = { Text("Commissions", fontSize = 10.sp) },
            colors = navItemColors
        )
    }
}

@Composable
fun RoleQuickPreviewSwitcher(viewModel: DexcargoViewModel) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xE61E293B))
                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                .clickable { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            Text("⚙️", fontSize = 16.sp)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFF1E293B))
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
        ) {
            DropdownMenuItem(
                text = { Text("John Kamau (Sales Rep)", color = Color.White, fontSize = 12.sp) },
                onClick = {
                    viewModel.selectEmployeeDirect("SR-002")
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Mary Wanjiku (Logistics Mgr)", color = Color.White, fontSize = 12.sp) },
                onClick = {
                    viewModel.selectEmployeeDirect("LM-001")
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Peter Mwangi (Sales Lead)", color = Color.White, fontSize = 12.sp) },
                onClick = {
                    viewModel.selectEmployeeDirect("SM-001")
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Administrator Console", color = Color.White, fontSize = 12.sp) },
                onClick = {
                    viewModel.selectEmployeeDirect("ADM-001")
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Reset Sandbox Database", color = Color(0xFFF43F5E), fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                onClick = {
                    viewModel.resetDemoData()
                    expanded = false
                }
            )
        }
    }
}
