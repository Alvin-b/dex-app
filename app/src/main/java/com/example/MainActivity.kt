package com.example

import android.os.Bundle
import android.graphics.Bitmap
import android.content.Intent
import android.provider.MediaStore
import android.content.pm.PackageManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.fragment.app.FragmentActivity
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
import com.example.data.SupabaseAuthRepository
import com.example.ui.*
import com.example.ui.screens.*
import com.example.ui.theme.*

class MainActivity : FragmentActivity() {
    private lateinit var viewModel: DexcargoViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize local Room layers
        val database = AppDatabase.getDatabase(this)
        val repository = DexcargoRepository(database)
        val authRepository = SupabaseAuthRepository(this, database)
        val viewModelFactory = DexcargoViewModelFactory(repository, authRepository)
        viewModel = ViewModelProvider(this, viewModelFactory)[DexcargoViewModel::class.java]

        // Register automatic network observer
        val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        val isInitiallyConnected = activeNetwork != null && activeNetwork.isConnected
        viewModel.isOnline.value = isInitiallyConnected

        val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                super.onAvailable(network)
                lifecycleScope.launch {
                    viewModel.setOnlineStatus(true)
                }
            }

            override fun onLost(network: android.net.Network) {
                super.onLost(network)
                lifecycleScope.launch {
                    viewModel.setOnlineStatus(false)
                }
            }
        }
        val networkRequest = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // Subscribe to camera trigger events
        lifecycleScope.launch {
            viewModel.triggerStickerCameraEvent.collect {
                if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        startActivityForResult(intent, 102)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(this@MainActivity, "Could not open camera.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.triggerPackageCameraEvent.collect {
                if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        startActivityForResult(intent, 104)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(this@MainActivity, "Could not open camera: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 103)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.triggerEvidenceCameraEvent.collect {
                if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        startActivityForResult(intent, 106)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(this@MainActivity, "Could not open camera.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 105)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.triggerEvidenceGalleryEvent.collect {
                try {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    startActivityForResult(intent, 108)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this@MainActivity, "Could not open gallery.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

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
                            is Screen.BarcodeScanner -> BarcodeScannerScreen(viewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (requestCode == 101) {
            if (granted) {
                try {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    startActivityForResult(intent, 102)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "Could not open camera.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                android.widget.Toast.makeText(this, "Camera permission is required to capture sticker photos.", android.widget.Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == 103) {
            if (granted) {
                try {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    startActivityForResult(intent, 104)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "Could not open camera.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                android.widget.Toast.makeText(this, "Camera permission is required to take package photos.", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == 105) {
            if (granted) {
                try {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    startActivityForResult(intent, 106)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "Could not open camera.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                android.widget.Toast.makeText(this, "Camera permission is required to capture photos.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            if (requestCode == 108) {
                val uri = data?.data
                if (uri != null) {
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            if (viewModel.currentScreen.value is Screen.TakePackagePhoto) {
                                viewModel.onPackagePhotoCaptured(bitmap)
                            } else {
                                viewModel.onEvidencePhotoCaptured(bitmap)
                            }
                        } else {
                            android.widget.Toast.makeText(this, "Could not load image", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(this, "Error loading image", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                return
            }

            val bitmap = data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                if (requestCode == 102) {
                    viewModel.onStickerPhotoCaptured(bitmap)
                } else if (requestCode == 104) {
                    viewModel.onPackagePhotoCaptured(bitmap)
                } else if (requestCode == 106) {
                    viewModel.onEvidencePhotoCaptured(bitmap)
                }
            } else {
                handleCameraFallback(requestCode)
            }
        } else {
            handleCameraFallback(requestCode)
        }
    }

    private fun handleCameraFallback(requestCode: Int) {
        if (requestCode == 102) {
            android.widget.Toast.makeText(this, "No sticker photo captured", android.widget.Toast.LENGTH_SHORT).show()
        } else if (requestCode == 104) {
            android.widget.Toast.makeText(this, "No package photo captured. Please tap 'Snap Package Photo' to open camera.", android.widget.Toast.LENGTH_SHORT).show()
        } else if (requestCode == 106) {
            android.widget.Toast.makeText(this, "No photo captured", android.widget.Toast.LENGTH_SHORT).show()
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
            selectedIconColor = OrangeAccent,
            selectedTextColor = OrangeAccent,
            indicatorColor = OrangeAccentBg.copy(alpha = 0.5f),
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
            icon = { Icon(Icons.Default.QrCodeScanner, "Register") },
            label = { Text("Register", fontSize = 10.sp) },
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

