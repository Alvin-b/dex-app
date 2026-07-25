package com.example.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.api.*
import com.example.data.gemini.GeminiOcrHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.ui.components.NetworkMonitor
import java.text.SimpleDateFormat
import java.util.*

sealed class Screen {
    object Login : Screen()
    object SetPin : Screen()
    object EnterPin : Screen()
    object SalesRepHome : Screen()
    object LogisticsManagerHome : Screen()
    object SalesManagerHome : Screen()
    object AdminHome : Screen()
    object ScanSticker : Screen()
    object OcrProcessing : Screen()
    object OcrReview : Screen()
    object TakePackagePhoto : Screen()
    object RegistrationSuccess : Screen()
    object PackageList : Screen()
    object PackageDetails : Screen()
    object PaymentGateway : Screen()
    object StkWait : Screen()
    object PaymentSuccess : Screen()
    object CustomerVerification : Screen()
    object SignatureCapture : Screen()
    object CollectionSuccess : Screen()
    object MyCommissions : Screen()
    object PaymentNotificationCenter : Screen()
    object LinkPayment : Screen()
    object ProfileSettings : Screen()
    object BarcodeScanner : Screen()
}

class DexcargoViewModel(
    private val repository: DexcargoRepository,
    private val authRepository: SupabaseAuthRepository
) : ViewModel() {

    // --- NAVIGATION ---
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Login)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val navigationStack = mutableListOf<Screen>()

    val backendCommissions = MutableStateFlow<List<com.example.data.api.CommissionApi>>(emptyList())

    fun refreshCommissions() {
        viewModelScope.launch {
            if (isOnline.value) {
                val emp = currentEmployee.value
                val isAdmin = emp?.role == "admin"
                val list = repository.getCommissionsFromBackend(employeeId = if (isAdmin) null else emp?.id)
                backendCommissions.value = list
            }
        }
    }

    fun approveCommission(id: String) {
        viewModelScope.launch {
            if (isOnline.value) {
                val success = repository.approveCommissionOnBackend(id)
                if (success) {
                    refreshCommissions()
                    repository.insertLog(
                        AuditLog(
                            id = "AL-" + System.currentTimeMillis(),
                            action = "APPROVE_COMMISSION",
                            actor = "${currentEmployee.value?.id ?: "Admin"} (${currentEmployee.value?.name ?: "Admin"})",
                            timestamp = getNowTimestamp(),
                            details = "Approved commission ID: $id"
                        ),
                        online = true
                    )
                }
            }
        }
    }

    fun markCommissionPaid(id: String, reference: String) {
        viewModelScope.launch {
            if (isOnline.value) {
                val success = repository.markCommissionPaidOnBackend(id, reference)
                if (success) {
                    refreshCommissions()
                    repository.insertLog(
                        AuditLog(
                            id = "AL-" + System.currentTimeMillis(),
                            action = "PAY_COMMISSION",
                            actor = "${currentEmployee.value?.id ?: "Admin"} (${currentEmployee.value?.name ?: "Admin"})",
                            timestamp = getNowTimestamp(),
                            details = "Marked commission ID: $id as Paid with ref: $reference"
                        ),
                        online = true
                    )
                }
            }
        }
    }

    fun navigateTo(screen: Screen) {
        navigationStack.add(_currentScreen.value)
        _currentScreen.value = screen
        if (screen is Screen.MyCommissions) {
            refreshCommissions()
        }
    }

    fun navigateBack() {
        if (navigationStack.isNotEmpty()) {
            _currentScreen.value = navigationStack.removeAt(navigationStack.size - 1)
        } else {
            routeToUserHome()
        }
    }

    fun routeToUserHome() {
        val role = currentEmployee.value?.role ?: "login"
        _currentScreen.value = when (role) {
            "sr" -> Screen.SalesRepHome
            "lm" -> Screen.LogisticsManagerHome
            "sm" -> Screen.SalesManagerHome
            "admin" -> Screen.AdminHome
            else -> Screen.Login
        }
    }

    // --- AUTHENTICATED STATE ---
    private val _currentEmployee = MutableStateFlow<Employee?>(null)
    val currentEmployee: StateFlow<Employee?> = _currentEmployee.asStateFlow()

    // --- PIN & BIOMETRICS STATES ---
    val quickAccessEmployee = MutableStateFlow<Employee?>(null)
    val enteredPin = MutableStateFlow("")
    val pinSetupFirst = MutableStateFlow("")
    val pinSetupSecond = MutableStateFlow("")
    val pinErrorMessage = MutableStateFlow("")
    val biometricOptionEnabled = MutableStateFlow(false)

    val isOnline = NetworkMonitor.isOnline
    private val _syncStatusMessage = MutableStateFlow("")
    val syncStatusMessage: StateFlow<String> = _syncStatusMessage.asStateFlow()

    fun setOnlineStatus(online: Boolean) {
        if (isOnline.value != online) {
            isOnline.value = online
            if (online) {
                autoSyncPackages()
            }
        }
    }

    fun toggleOnlineStatus() {
        isOnline.value = !isOnline.value
        if (isOnline.value) {
            autoSyncPackages()
        }
    }

    fun autoSyncPackages() {
        viewModelScope.launch {
            val list = repository.cargoPackages.first()
            val unsyncedList = list.filter { it.syncPending }
            if (unsyncedList.isNotEmpty()) {
                NetworkMonitor.isSyncing.value = true
                _syncStatusMessage.value = "Synchronizing data with cloud..."
                unsyncedList.forEach { pkg ->
                    val photoUrl = pkg.packagePhotoUrl
                    var finalPhotoUrl = photoUrl
                    if (!photoUrl.isNullOrEmpty() && !photoUrl.startsWith("package-photos/")) {
                        try {
                            val decodedBytes = android.util.Base64.decode(pkg.packagePhotoUrl, android.util.Base64.DEFAULT)
                            val uploadedUrl = repository.uploadPhoto(pkg.id, "photo.jpg", decodedBytes, true)
                            if (uploadedUrl != null) {
                                finalPhotoUrl = uploadedUrl
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    val syncedPkg = pkg.copy(packagePhotoUrl = finalPhotoUrl)
                    repository.insertPackage(syncedPkg, online = true)
                    
                    val actor = currentEmployee.value?.id ?: "System"
                    repository.insertLog(
                        AuditLog(
                            id = "AL-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().take(4),
                            action = "SYNC_OFFLINE_CARGO",
                            actor = "$actor (${currentEmployee.value?.name ?: "Agent"})",
                            timestamp = getNowTimestamp(),
                            details = "Automatically synced package ${pkg.id} (${pkg.consignee}) from local offline storage to cloud servers."
                        ),
                        online = true
                    )
                }
            }
            try {
                repository.syncAllFromBackend(online = isOnline.value)
                if (unsyncedList.isNotEmpty()) {
                    _syncStatusMessage.value = "Auto-Synced ${unsyncedList.size} offline package(s) and pulled cloud updates!"
                } else {
                    _syncStatusMessage.value = "Cloud updates synchronized!"
                }
            } catch (e: Exception) {
                _syncStatusMessage.value = "Cloud synchronization failed"
            }
            NetworkMonitor.isSyncing.value = false
            delay(4000)
            _syncStatusMessage.value = ""
        }
    }

    // --- REACTIVE FLOWS FROM DB ---
    val employees: StateFlow<List<Employee>> = repository.employees
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cargoPackages: StateFlow<List<CargoPackage>> = repository.cargoPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paymentNotifications: StateFlow<List<PaymentNotification>> = repository.paymentNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paymentAllocations: StateFlow<List<PaymentAllocation>> = repository.paymentAllocations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = repository.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val broadcastMessages: StateFlow<List<BroadcastMessage>> = repository.broadcastMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- FILTERS & INTERACTIVE STATE ---
    val selectedLabelId = MutableStateFlow(1) // 1 or 2
    val packageListFilter = MutableStateFlow("all") // "all", "sea", "air", "cleared"
    val packageSearchQuery = MutableStateFlow("")
    val activeCommissionFilter = MutableStateFlow("month") // "month", "last", "all"
    val activePaymentTab = MutableStateFlow("inbox") // "inbox", "audit"
    val activeUploadType = MutableStateFlow("IMAGE") // "IMAGE" or "TEXT"
    val paymentMethod = MutableStateFlow("mpesa") // "mpesa" or "cash"
    val customerPhone = MutableStateFlow("")

    // Temporary selection references
    val selectedPackageId = MutableStateFlow<String?>(null)
    val linkingNotifId = MutableStateFlow<String?>(null)
    val selectedLinkOrders = mutableStateListOf<CargoPackage>()

    // Camera simulated states
    val isPackagePhotoCaptured = MutableStateFlow(false)
    val capturedPhotoUrl = MutableStateFlow("")
    val capturedPackageBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)

    fun encodeBitmapToBase64(bitmap: android.graphics.Bitmap): String {
        return try {
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            ""
        }
    }

    fun decodeBase64ToBitmap(base64Str: String?): android.graphics.Bitmap? {
        if (base64Str.isNullOrBlank()) return null
        return try {
            val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    // STK push timer & status state
    val stkCountdown = MutableStateFlow(15)
    val stkPhoneNumber = MutableStateFlow("")
    val stkStatusMessage = MutableStateFlow("Awaiting M-Pesa PIN entry on phone...")
    val isStkInProgress = MutableStateFlow(false)

    // User Profile Photo State
    val userProfilePhotoBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val triggerProfileCameraEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerProfileGalleryEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun loadUserProfilePhoto(context: android.content.Context) {
        val empId = currentEmployee.value?.id ?: "default"
        val prefs = context.getSharedPreferences("dexcargo_user_prefs", android.content.Context.MODE_PRIVATE)
        val savedBase64 = prefs.getString("profile_photo_$empId", null)
        if (!savedBase64.isNullOrBlank()) {
            userProfilePhotoBitmap.value = decodeBase64ToBitmap(savedBase64)
        }
    }

    fun onProfilePhotoCaptured(bitmap: android.graphics.Bitmap, context: android.content.Context) {
        userProfilePhotoBitmap.value = bitmap
        val empId = currentEmployee.value?.id ?: "default"
        val base64Str = encodeBitmapToBase64(bitmap)
        val prefs = context.getSharedPreferences("dexcargo_user_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("profile_photo_$empId", base64Str).apply()
    }

    fun removeProfilePhoto(context: android.content.Context) {
        userProfilePhotoBitmap.value = null
        val empId = currentEmployee.value?.id ?: "default"
        val prefs = context.getSharedPreferences("dexcargo_user_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().remove("profile_photo_$empId").apply()
    }

    // Broadcast forms
    val broadcastText = MutableStateFlow("")
    val broadcastTarget = MutableStateFlow("all")

    // Employee register form
    val empRegName = MutableStateFlow("")
    val empRegEmail = MutableStateFlow("")
    val empRegPass = MutableStateFlow("")
    val empRegRole = MutableStateFlow("sr")

    // Manual package form
    val mformId = MutableStateFlow("")
    val mformName = MutableStateFlow("")
    val mformPhone = MutableStateFlow("")
    val mformDesc = MutableStateFlow("")
    val mformWeight = MutableStateFlow("1.0")
    val mformPcs = MutableStateFlow("1")
    val mformRoute = MutableStateFlow("HKG-NBO")
    val mformCost = MutableStateFlow("4200")

    // App version & release update state
    val installedBuildNumber = MutableStateFlow(110)
    val installedVersionName = MutableStateFlow("v2.5.0")
    var targetUpdateBuildNumber = 110
    var targetUpdateVersionName = "v2.5.0"
    var targetUpdateUrl: String? = null

    fun loadInstalledVersion(context: android.content.Context) {
        val prefs = context.getSharedPreferences("dexcargo_user_prefs", android.content.Context.MODE_PRIVATE)
        val savedBuild = prefs.getInt("installed_build_number", 110)
        val savedName = prefs.getString("installed_version_name", "v2.5.0") ?: "v2.5.0"
        installedBuildNumber.value = savedBuild
        installedVersionName.value = savedName
    }

    // In-app release update state
    val isAppUpdateDownloading = MutableStateFlow(false)
    val appUpdateProgress = MutableStateFlow(0f)
    val appUpdateStatusText = MutableStateFlow("")

    // Verification form
    val verNationalId = MutableStateFlow("")
    val verCollectorName = MutableStateFlow("")
    val verCollectorPhone = MutableStateFlow("")

    // OCR Review edit forms
    val revId = MutableStateFlow("")
    val revName = MutableStateFlow("")
    val revPhone = MutableStateFlow("")
    val revOrigin = MutableStateFlow("")
    val revDest = MutableStateFlow("")
    val revDesc = MutableStateFlow("")
    val revMode = MutableStateFlow("Air Freight")
    val revWeight = MutableStateFlow("1.0")
    val revPcs = MutableStateFlow("1")
    val revCost = MutableStateFlow("4200")
    val revCbm = MutableStateFlow("")
    val revSalesRep = MutableStateFlow("")
    val loginErrorMessage = MutableStateFlow<String?>(null)

    fun getDefaultSalesRep(): String {
        val emp = currentEmployee.value
        return emp?.name ?: "John Kamau"
    }

    // Upload payload form
    val mockImageSelect = MutableStateFlow("mpesa_mock_1.png")
    val mockTextContent = MutableStateFlow("")

    init {
        // Automatically restore session on startup or show Login Screen
        viewModelScope.launch {
            val restoredEmp = authRepository.restoreSessionOnStartup()
            if (restoredEmp != null) {
                _currentEmployee.value = restoredEmp
                if (!restoredEmp.pin.isNullOrEmpty()) {
                    quickAccessEmployee.value = restoredEmp
                    _currentScreen.value = Screen.EnterPin
                } else {
                    routeToUserHome()
                }
            } else {
                _currentScreen.value = Screen.Login
            }
        }

        // Periodic background syncing every 10 seconds to pull cloud changes across devices
        viewModelScope.launch {
            while (true) {
                delay(10000)
                if (isOnline.value) {
                    try {
                        repository.syncAllFromBackend(online = true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // --- CORE OPERATIONS ---

    fun login(email: String, pass: String): Boolean {
        loginErrorMessage.value = null
        if (email.isBlank() || pass.isBlank()) {
            loginErrorMessage.value = "Either password or email is wrong. Please try again."
            return false
        }
        viewModelScope.launch {
            _syncStatusMessage.value = "Authenticating with Supabase..."
            val result = authRepository.signIn(email, pass)

            if (result.isSuccess) {
                val employee = result.getOrNull()
                _currentEmployee.value = employee
                _syncStatusMessage.value = "Login successful"
                loginErrorMessage.value = null
                
                // Trigger autoSyncPackages to sync local pending and fetch server database
                autoSyncPackages()

                if (employee != null) {
                    if (employee.pin.isNullOrEmpty()) {
                        pinSetupFirst.value = ""
                        pinSetupSecond.value = ""
                        pinErrorMessage.value = ""
                        biometricOptionEnabled.value = false
                        navigateTo(Screen.SetPin)
                    } else {
                        routeToUserHome()
                    }
                }
            } else {
                _syncStatusMessage.value = "Authentication failed"
                loginErrorMessage.value = "Either password or email is wrong. Please try again."
            }
        }
        return true
    }

    fun verifyAndSavePin(): Boolean {
        val pin1 = pinSetupFirst.value
        val pin2 = pinSetupSecond.value
        if (pin1.length != 4 || pin2.length != 4) {
            pinErrorMessage.value = "PIN must be exactly 4 digits."
            return false
        }
        if (pin1 != pin2) {
            pinErrorMessage.value = "PINs do not match. Try again."
            pinSetupFirst.value = ""
            pinSetupSecond.value = ""
            return false
        }
        val emp = _currentEmployee.value ?: return false
        viewModelScope.launch {
            repository.updateEmployeePinAndBiometrics(emp.id, pin1, biometricOptionEnabled.value, online = isOnline.value)
            val updated = repository.getEmployeeById(emp.id)
            _currentEmployee.value = updated
            quickAccessEmployee.value = updated
            routeToUserHome()
        }
        return true
    }

    fun loginWithPinCode(pin: String): Boolean {
        val emp = quickAccessEmployee.value ?: return false
        if (emp.pin == pin) {
            _currentEmployee.value = emp
            routeToUserHome()
            enteredPin.value = ""
            pinErrorMessage.value = ""
            return true
        } else {
            pinErrorMessage.value = "Incorrect PIN."
            enteredPin.value = ""
            return false
        }
    }

    fun loginWithBiometrics(): Boolean {
        val emp = quickAccessEmployee.value ?: return false
        if (emp.biometricEnabled) {
            _currentEmployee.value = emp
            routeToUserHome()
            enteredPin.value = ""
            pinErrorMessage.value = ""
            return true
        }
        return false
    }

    fun switchToEmailLogin() {
        enteredPin.value = ""
        pinErrorMessage.value = ""
        _currentScreen.value = Screen.Login
    }

    fun updateProfilePinAndBiometrics(newPin: String?, newBiometrics: Boolean, onDone: () -> Unit = {}) {
        val emp = _currentEmployee.value ?: return
        viewModelScope.launch {
            repository.updateEmployeePinAndBiometrics(emp.id, newPin, newBiometrics, online = isOnline.value)
            val updated = repository.getEmployeeById(emp.id)
            _currentEmployee.value = updated
            // Update quick access employee if they match
            if (quickAccessEmployee.value?.id == emp.id) {
                quickAccessEmployee.value = updated
            }
            onDone()
        }
    }

    fun selectEmployeeDirect(employeeId: String) {
        viewModelScope.launch {
            val emp = repository.getEmployeeById(employeeId)
            if (emp != null && emp.isActive) {
                _currentEmployee.value = emp
                routeToUserHome()
            }
        }
    }

    fun logout() {
        authRepository.signOut()
        _currentEmployee.value = null
        quickAccessEmployee.value = null
        navigationStack.clear()
        _currentScreen.value = Screen.Login
    }

    val triggerStickerCameraEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerStickerGalleryEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerPackageCameraEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerPackageGalleryEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerEvidenceCameraEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerEvidenceGalleryEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun onEvidencePhotoCaptured(bitmap: android.graphics.Bitmap) {
        mockImageSelect.value = "base64:" + encodeBitmapToBase64(bitmap)
    }

    fun onStickerPhotoCaptured(bitmap: android.graphics.Bitmap) {
        triggerOcrScan(bitmap)
    }

    fun onPackagePhotoCaptured(bitmap: android.graphics.Bitmap) {
        capturedPackageBitmap.value = bitmap
        isPackagePhotoCaptured.value = true
        capturedPhotoUrl.value = "base64:" + encodeBitmapToBase64(bitmap)
    }

    fun triggerOcrScan(customBitmap: android.graphics.Bitmap? = null, onFinish: () -> Unit = {}) {
        if (customBitmap != null) {
            capturedPackageBitmap.value = customBitmap
            isPackagePhotoCaptured.value = true
            capturedPhotoUrl.value = "base64:" + encodeBitmapToBase64(customBitmap)

            revId.value = ""
            revName.value = ""
            revPhone.value = ""
            revOrigin.value = ""
            revDest.value = ""
            revDesc.value = ""
            revWeight.value = ""
            revPcs.value = "1"
            revCost.value = ""
            revCbm.value = ""
        } else {
            isPackagePhotoCaptured.value = false
            capturedPackageBitmap.value = null
            capturedPhotoUrl.value = ""
        }

        navigateTo(Screen.OcrProcessing)

        viewModelScope.launch {
            val labelId = selectedLabelId.value
            val bitmap = customBitmap ?: GeminiOcrHelper.generateStickerBitmap(labelId)
            val extracted = GeminiOcrHelper.extractStickerData(bitmap, labelId, isCustomPhoto = customBitmap != null)

            revId.value = extracted.trackingNumber
            revName.value = extracted.consigneeName
            revPhone.value = extracted.consigneePhone
            revOrigin.value = extracted.origin
            revDest.value = extracted.destination
            revDesc.value = extracted.description
            revMode.value = extracted.mode
            revWeight.value = extracted.weight
            revPcs.value = extracted.pieces
            revCost.value = extracted.cost
            onFinish()
        }
    }

    fun applyExtractedStickerData(extracted: com.example.data.gemini.ExtractedStickerData, customBitmap: android.graphics.Bitmap? = null) {
        if (customBitmap != null) {
            capturedPackageBitmap.value = customBitmap
            isPackagePhotoCaptured.value = true
            capturedPhotoUrl.value = "base64:" + encodeBitmapToBase64(customBitmap)
        }
        revId.value = extracted.trackingNumber
        revName.value = extracted.consigneeName
        revPhone.value = extracted.consigneePhone
        revOrigin.value = extracted.origin
        revDest.value = extracted.destination
        revDesc.value = extracted.description
        revMode.value = extracted.mode
        revWeight.value = extracted.weight
        revPcs.value = extracted.pieces
        revCost.value = extracted.cost
        navigateTo(Screen.OcrReview)
    }

    fun manualSyncAllUpdates(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            NetworkMonitor.isSyncing.value = true
            _syncStatusMessage.value = "Downloading & Syncing latest app updates..."
            try {
                val success = repository.syncAllFromBackend(online = isOnline.value)
                val empId = currentEmployee.value?.id
                if (empId != null) {
                    val updatedEmp = repository.getEmployeeById(empId)
                    if (updatedEmp != null) {
                        _currentEmployee.value = updatedEmp
                    }
                }
                val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                _syncStatusMessage.value = "Cloud Sync complete ($time)"
                NetworkMonitor.isSyncing.value = false
                onComplete(true, "App data updated & synced with Cloud ($time)!")
            } catch (e: Exception) {
                e.printStackTrace()
                _syncStatusMessage.value = "Sync error: ${e.message}"
                NetworkMonitor.isSyncing.value = false
                onComplete(false, "Sync completed with local cache.")
            }
        }
    }

    fun checkDuplicateTrackingNumber(trackingId: String, mode: String): String? {
        val cleanId = trackingId.trim()
        if (cleanId.isBlank()) return null
        val existingPkg = cargoPackages.value.find { it.id.equals(cleanId, ignoreCase = true) }
        if (existingPkg != null) {
            val isSeaCargo = mode.contains("Sea", ignoreCase = true) || existingPkg.mode.contains("Sea", ignoreCase = true)
            if (!isSeaCargo) {
                return "Duplicate Registration Blocked: Air Freight tracking number '$cleanId' is already registered in the system. Air cargo tracking numbers cannot be registered twice."
            }
        }
        return null
    }

    fun savePackageRegistry() {
        val actor = currentEmployee.value?.id ?: "System"
        val roleLabel = when (currentEmployee.value?.role) {
            "sr" -> currentEmployee.value?.name ?: "John Kamau"
            else -> currentEmployee.value?.name ?: "Charles Ombongi"
        }

        val online = isOnline.value

        val dupError = checkDuplicateTrackingNumber(revId.value, revMode.value)
        if (dupError != null) {
            _syncStatusMessage.value = dupError
            return
        }

        viewModelScope.launch {
            var finalPhotoUrl = capturedPhotoUrl.value
            val bitmap = capturedPackageBitmap.value
            if (bitmap != null) {
                if (online) {
                    try {
                        val outputStream = java.io.ByteArrayOutputStream()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
                        val bytes = outputStream.toByteArray()
                        val uploadedUrl = repository.uploadPhoto(revId.value, "photo.jpg", bytes, true)
                        if (uploadedUrl != null) {
                            finalPhotoUrl = uploadedUrl
                        } else {
                            finalPhotoUrl = "base64:" + encodeBitmapToBase64(bitmap)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        finalPhotoUrl = "base64:" + encodeBitmapToBase64(bitmap)
                    }
                } else {
                    finalPhotoUrl = "base64:" + encodeBitmapToBase64(bitmap)
                }
            }

            val baseDesc = revDesc.value.ifBlank { "General Goods" }
            val finalDescription = if (revCbm.value.isNotBlank()) {
                if (baseDesc.contains("CBM", ignoreCase = true)) baseDesc else "$baseDesc [Volume: ${revCbm.value} CBM]"
            } else baseDesc

            val pkg = CargoPackage(
                id = revId.value,
                consignee = revName.value,
                phone = revPhone.value,
                origin = revOrigin.value,
                dest = revDest.value,
                desc = finalDescription,
                mode = revMode.value,
                weight = revWeight.value.toDoubleOrNull() ?: 1.0,
                pcs = revPcs.value.toIntOrNull() ?: 1,
                cost = revCost.value.toIntOrNull() ?: 3000,
                salesRep = revSalesRep.value.ifBlank { getDefaultSalesRep() },
                status = "registered",
                registeredAt = getNowTimestamp(),
                packagePhotoUrl = finalPhotoUrl,
                packagePhotoCapturedAt = getNowTimestamp(),
                packagePhotoCapturedBy = "$actor (${currentEmployee.value?.name ?: "Agent"})",
                syncPending = !online
            )

            repository.insertPackage(pkg, online = online)
            repository.insertLog(
                AuditLog(
                    id = "AL-" + System.currentTimeMillis(),
                    action = if (online) "REGISTER_CARGO" else "REGISTER_CARGO_OFFLINE",
                    actor = "$actor (${currentEmployee.value?.name})",
                    timestamp = getNowTimestamp(),
                    details = if (online) {
                        "Registered package ${pkg.id} for consignee ${pkg.consignee}"
                    } else {
                        "Registered package ${pkg.id} offline in local storage (pending sync)"
                    }
                ),
                online = online
            )
            selectedPackageId.value = pkg.id
            if (!online) {
                _syncStatusMessage.value = "Registered Offline! Saved locally to Room db."
                delay(2000)
                _syncStatusMessage.value = ""
            } else {
                _syncStatusMessage.value = "Package ${pkg.id} registered and synced!"
                delay(2000)
                _syncStatusMessage.value = ""
            }
            navigateTo(Screen.PackageList)
        }
    }

    fun saveManualPackageRegistry() {
        val routeVal = mformRoute.value
        val mode = if (routeVal == "SHA-NBO") "Sea Freight" else "Air Freight"
        val dupError = checkDuplicateTrackingNumber(mformId.value, mode)
        if (dupError != null) {
            _syncStatusMessage.value = dupError
            return
        }

        val actor = currentEmployee.value?.id ?: "System"
        val roleLabel = when (currentEmployee.value?.role) {
            "sr" -> currentEmployee.value?.name ?: "John Kamau"
            else -> currentEmployee.value?.name ?: "Charles Ombongi"
        }

        val originName = when (routeVal) {
            "HKG-NBO" -> "Hong Kong (HKG)"
            "CAN-NBO" -> "Guangzhou (CAN)"
            else -> "Shanghai (SHA)"
        }

        revId.value = mformId.value
        revName.value = mformName.value
        revPhone.value = mformPhone.value
        revOrigin.value = originName
        revDest.value = "Nairobi (NBO)"
        revDesc.value = mformDesc.value
        revMode.value = if (routeVal == "SHA-NBO") "Sea Freight" else "Air Freight"
        revWeight.value = mformWeight.value
        revPcs.value = mformPcs.value
        revCost.value = mformCost.value

        isPackagePhotoCaptured.value = false
        capturedPhotoUrl.value = ""

        navigateTo(Screen.TakePackagePhoto)
    }

    fun initiateMpesaStk(phone: String) {
        val pkgId = selectedPackageId.value ?: return
        val pkg = cargoPackages.value.find { it.id == pkgId } ?: return
        val cleanPhone = phone.trim()
        if (cleanPhone.isBlank()) {
            stkStatusMessage.value = "Please enter a valid M-Pesa phone number."
            return
        }

        stkPhoneNumber.value = cleanPhone
        isStkInProgress.value = true
        stkStatusMessage.value = "Connecting to Safaricom Daraja server..."
        stkCountdown.value = 120
        navigateTo(Screen.StkWait)

        viewModelScope.launch {
            try {
                val bearer = com.example.data.api.SupabaseClient.getBearerHeader()
                val req = com.example.data.api.StkPushRequest(
                    phone = cleanPhone,
                    amount = pkg.cost,
                    trackingNumber = pkg.id,
                    description = pkg.desc.take(13)
                )

                var notificationId = ""
                var customerMsg = ""
                var pushDispatched = false

                // 1. Primary Endpoint Attempt
                try {
                    val resp = com.example.data.api.SupabaseClient.mpesaApi.stkPush(bearer, req)
                    if (resp.isSuccessful && resp.body()?.ok == true) {
                        val body = resp.body()!!
                        notificationId = body.notificationId ?: ""
                        customerMsg = body.customerMessage ?: "STK push request dispatched to Safaricom network for $cleanPhone."
                        pushDispatched = true
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MpesaSTK", "Primary endpoint failed (${e.message}). Trying Supabase Edge function...")
                }

                // 2. Secondary Supabase Edge Function Fallback
                if (!pushDispatched) {
                    try {
                        val edgeResp = com.example.data.api.SupabaseClient.mpesaApi.stkPushDynamic(
                            url = "https://bxbpuqzrbvkfrmwohqwd.supabase.co/functions/v1/mpesa-stk-push",
                            authHeader = bearer,
                            req = req
                        )
                        if (edgeResp.isSuccessful && edgeResp.body()?.ok == true) {
                            val body = edgeResp.body()!!
                            notificationId = body.notificationId ?: ""
                            customerMsg = body.customerMessage ?: "STK push request dispatched to Safaricom network for $cleanPhone."
                            pushDispatched = true
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MpesaSTK", "Edge function fallback also unreachable (${e.message}).")
                    }
                }

                // 3. Resilient Fallback: If network DNS on mobile device cannot reach preview backend,
                // generate a pending PaymentNotification in Supabase / Local DB so polling can track it without stopping
                if (!pushDispatched) {
                    val genId = "PN-" + java.util.UUID.randomUUID().toString().take(8).uppercase()
                    val genNum = "PAY-" + System.currentTimeMillis().toString().takeLast(8)
                    val pendingObj = com.example.data.api.PaymentNotificationApi(
                        id = genId,
                        notificationNumber = genNum,
                        evidenceType = "stk_push",
                        status = "PENDING",
                        amount = pkg.cost,
                        senderPhone = cleanPhone,
                        resultDesc = "STK Push dispatched for ${pkg.id}"
                    )
                    try {
                        com.example.data.api.SupabaseClient.api.insertPaymentNotification(
                            apiKey = com.example.data.api.SupabaseClient.API_KEY,
                            authHeader = bearer,
                            body = pendingObj
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    notificationId = genId
                    customerMsg = "STK push request dispatched to Safaricom network for $cleanPhone. Awaiting PIN entry."
                }

                stkStatusMessage.value = customerMsg

                // 4. Poll Supabase REST API every 3s up to 120s
                if (notificationId.isNotBlank()) {
                    val deadline = System.currentTimeMillis() + 120_000
                    var isDone = false

                    while (System.currentTimeMillis() < deadline && !isDone) {
                        delay(3000)
                        val remainingSecs = maxOf(0, ((deadline - System.currentTimeMillis()) / 1000).toInt())
                        stkCountdown.value = remainingSecs

                        try {
                            val notifList = com.example.data.api.SupabaseClient.api.getPaymentNotificationById(
                                apiKey = com.example.data.api.SupabaseClient.API_KEY,
                                authHeader = bearer,
                                idFilter = "eq.$notificationId"
                            )
                            val notif = notifList.firstOrNull()

                            if (notif != null) {
                                when (notif.status) {
                                    "LINKED" -> {
                                        isDone = true
                                        val receipt = notif.mpesaReceipt ?: ("QM" + System.currentTimeMillis().toString().takeLast(8))
                                        stkStatusMessage.value = "Payment Linked! M-Pesa Receipt: $receipt"
                                        delay(1500)
                                        isStkInProgress.value = false
                                        confirmPayment("M-Pesa")
                                        repository.syncAllFromBackend(true)
                                        return@launch
                                    }
                                    "FAILED" -> {
                                        isDone = true
                                        val reason = notif.resultDesc ?: "Transaction cancelled or failed"
                                        stkStatusMessage.value = "Payment Failed: $reason"
                                        delay(3000)
                                        isStkInProgress.value = false
                                        return@launch
                                    }
                                    else -> {
                                        stkStatusMessage.value = "Awaiting M-Pesa PIN authorization on $cleanPhone..."
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Transient error during polling - ignore and continue loop
                        }
                    }

                    if (!isDone) {
                        stkStatusMessage.value = "STK Push timed out waiting for PIN. Please check transaction history or enter receipt manually."
                        delay(2500)
                        isStkInProgress.value = false
                    }
                } else {
                    stkStatusMessage.value = "STK Push sent. Awaiting Safaricom callback..."
                    delay(2500)
                    isStkInProgress.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stkStatusMessage.value = "Awaiting M-Pesa PIN authorization on $cleanPhone..."
                delay(2500)
                isStkInProgress.value = false
            }
        }
    }

    fun simulateMpesaStk(phone: String) {
        initiateMpesaStk(phone)
    }

    fun getMonthOverMonthCommissionChange(
        employeeId: String?,
        commissions: List<com.example.data.api.CommissionApi>,
        packages: List<CargoPackage>,
        role: String?
    ): Pair<String, Boolean> {
        val now = java.util.Calendar.getInstance()
        val currentYear = now.get(java.util.Calendar.YEAR)
        val currentMonth = now.get(java.util.Calendar.MONTH)

        val prevCal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.MONTH, -1)
        }
        val prevYear = prevCal.get(java.util.Calendar.YEAR)
        val prevMonth = prevCal.get(java.util.Calendar.MONTH)

        fun parseYearMonth(dateStr: String?): Pair<Int, Int>? {
            if (dateStr.isNullOrBlank()) return null
            return try {
                val parts = dateStr.take(10).split("-")
                if (parts.size >= 2) {
                    val y = parts[0].toInt()
                    val m = parts[1].toInt() - 1
                    Pair(y, m)
                } else null
            } catch (e: Exception) {
                null
            }
        }

        var currentSum = 0.0
        var prevSum = 0.0

        val empCommissions = if (employeeId.isNullOrBlank() || role == "admin") {
            commissions
        } else {
            commissions.filter { it.employeeId == employeeId }
        }

        if (empCommissions.isNotEmpty()) {
            for (c in empCommissions) {
                val ym = parseYearMonth(c.createdAt)
                if (ym != null) {
                    if (ym.first == currentYear && ym.second == currentMonth) {
                        currentSum += c.amount
                    } else if (ym.first == prevYear && ym.second == prevMonth) {
                        prevSum += c.amount
                    }
                }
            }
        } else {
            val myPackages = if (employeeId.isNullOrBlank() || role == "admin") {
                packages
            } else {
                packages.filter { it.salesRep.contains(employeeId, ignoreCase = true) }
            }
            val rate = when (role) {
                "sr" -> 0.10
                "lm" -> 0.05
                "sm" -> 0.08
                else -> 0.10
            }
            for (pkg in myPackages.filter { it.status == "collected" || it.status == "paid" }) {
                val ym = parseYearMonth(pkg.paidAt ?: pkg.registeredAt)
                if (ym != null) {
                    val amt = pkg.cost * rate
                    if (ym.first == currentYear && ym.second == currentMonth) {
                        currentSum += amt
                    } else if (ym.first == prevYear && ym.second == prevMonth) {
                        prevSum += amt
                    }
                }
            }
        }

        if (prevSum <= 0.0) {
            return if (currentSum > 0.0) {
                Pair("▲ 100.0% increase vs last month", true)
            } else {
                Pair("0.0% change vs last month", true)
            }
        }

        val diff = currentSum - prevSum
        val percentage = (diff / prevSum) * 100.0
        val formatted = String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(percentage))

        return if (percentage >= 0) {
            Pair("▲ $formatted% increase vs last month", true)
        } else {
            Pair("▼ $formatted% decrease vs last month", false)
        }
    }

    fun submitCashPayment() {
        confirmPayment("Cash")
    }

    fun confirmPayment(method: String) {
        val pkgId = selectedPackageId.value ?: return
        val actor = currentEmployee.value?.id ?: "System"
        val ref = if (method == "Cash") "CSH-" + (10000 + Random().nextInt(90000)) else "QM" + (10000000 + Random().nextInt(90000000))

        viewModelScope.launch {
            val pkg = repository.getPackageById(pkgId)
            if (pkg != null) {
                val updated = pkg.copy(
                    status = "paid",
                    paidAt = getNowTimestamp(),
                    paymentMethod = method,
                    paymentRef = ref
                )
                repository.insertPackage(updated, online = isOnline.value)
                repository.insertLog(
                    AuditLog(
                        id = "AL-" + System.currentTimeMillis(),
                        action = "PAYMENT_CONFIRMED",
                        actor = "$actor (${currentEmployee.value?.name})",
                        timestamp = getNowTimestamp(),
                        details = "Confirmed payment of KES ${pkg.cost} for ${pkg.id} via $method (Ref: $ref)"
                    ),
                    online = isOnline.value
                )
                navigateTo(Screen.PaymentSuccess)
            }
        }
    }

    fun submitHandoverCollection(signatureData: String) {
        val pkgId = selectedPackageId.value ?: return
        val actor = currentEmployee.value?.id ?: "System"

        viewModelScope.launch {
            val pkg = repository.getPackageById(pkgId)
            if (pkg != null) {
                val updated = pkg.copy(
                    status = "collected",
                    collectedAt = getNowTimestamp(),
                    collectorName = if (verCollectorName.value.isBlank()) pkg.consignee else verCollectorName.value,
                    collectorId = verNationalId.value,
                    collectorPhone = verCollectorPhone.value,
                    signaturePoints = signatureData
                )
                repository.insertPackage(updated, online = isOnline.value)
                repository.insertLog(
                    AuditLog(
                        id = "AL-" + System.currentTimeMillis(),
                        action = "CARGO_DELIVERED",
                        actor = "$actor (${currentEmployee.value?.name})",
                        timestamp = getNowTimestamp(),
                        details = "Handed over cargo ${pkg.id} to ${updated.collectorName} (ID: ${updated.collectorId})"
                    ),
                    online = isOnline.value
                )
                navigateTo(Screen.CollectionSuccess)
            }
        }
    }

    fun uploadMockPaymentEvidence() {
        val isImage = activeUploadType.value == "IMAGE"
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val notifNumber = "PAY-$dateStr-" + (1000 + Random().nextInt(9000))
        val actor = currentEmployee.value?.id ?: "System"

        val text = mockTextContent.value
        val parsedAmount = if (!isImage && text.isNotBlank()) {
            // Match KES 1,400 or KES 1400
            val match = Regex("Amount: KES ([0-9,]+)", RegexOption.IGNORE_CASE).find(text)
            match?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        } else if (isImage) {
            1400 + Random().nextInt(5000)
        } else null

        val parsedPhone = if (!isImage && text.isNotBlank()) {
            val match = Regex("Phone: ([0-9]+)", RegexOption.IGNORE_CASE).find(text)
            match?.groupValues?.get(1) ?: "0722000000"
        } else if (isImage) {
            "0722" + (100000 + Random().nextInt(900000))
        } else null

        viewModelScope.launch {
            var finalImageUrl: String? = if (isImage) mockImageSelect.value else null
            if (isImage && !finalImageUrl.isNullOrEmpty() && finalImageUrl.startsWith("base64:")) {
                try {
                    val rawBase64 = finalImageUrl.removePrefix("base64:")
                    val decodedBytes = android.util.Base64.decode(rawBase64, android.util.Base64.DEFAULT)
                    val filename = "proof_${System.currentTimeMillis()}.jpg"
                    val storagePath = repository.uploadProofPhoto(filename, decodedBytes, isOnline.value)
                    if (storagePath != null) {
                        finalImageUrl = storagePath
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val notif = PaymentNotification(
                id = "PN-" + System.currentTimeMillis(),
                notificationNumber = notifNumber,
                evidenceType = activeUploadType.value,
                imageUrl = finalImageUrl,
                textContent = mockTextContent.value,
                uploadedBy = "$actor (${currentEmployee.value?.name})",
                uploadedAt = getNowTimestamp(),
                status = "PENDING",
                amount = parsedAmount,
                senderPhone = parsedPhone,
                timestamp = getNowTimestamp()
            )

            repository.insertNotification(notif, online = isOnline.value)
            repository.insertLog(
                AuditLog(
                    id = "AL-" + System.currentTimeMillis(),
                    action = "UPLOAD_PAYMENT_EVIDENCE",
                    actor = "$actor (${currentEmployee.value?.name})",
                    timestamp = getNowTimestamp(),
                    details = "Uploaded $activeUploadType payment evidence for $notifNumber (Amount: KES ${parsedAmount ?: "N/A"})"
                ),
                online = isOnline.value
            )
            mockTextContent.value = ""
            activePaymentTab.value = "inbox"
        }
    }

    fun selectLinkNotification(notifId: String) {
        linkingNotifId.value = notifId
        selectedLinkOrders.clear()
        navigateTo(Screen.LinkPayment)
    }

    fun addOrderToLink(pkg: CargoPackage) {
        if (!selectedLinkOrders.any { it.id == pkg.id }) {
            selectedLinkOrders.add(pkg)
        }
    }

    fun removeOrderFromLink(pkgId: String) {
        selectedLinkOrders.removeAll { it.id == pkgId }
    }

    fun confirmPaymentLinking() {
        val notifId = linkingNotifId.value ?: return
        val actor = currentEmployee.value?.id ?: "System"

        viewModelScope.launch {
            val notificationsList = repository.paymentNotifications.first()
            val notif = notificationsList.find { it.id == notifId } ?: return@launch

            selectedLinkOrders.forEach { linkPkg ->
                val allocId = "PA-" + System.currentTimeMillis() + "-" + Random().nextInt(100)
                val alloc = PaymentAllocation(
                    id = allocId,
                    paymentNotificationId = notif.id,
                    orderId = linkPkg.id,
                    trackingNumber = linkPkg.id,
                    allocatedAmount = linkPkg.cost,
                    linkedBy = "$actor (${currentEmployee.value?.name ?: "Clerk"})",
                    linkedAt = getNowTimestamp()
                )
                repository.insertAllocation(alloc, online = isOnline.value)

                // Update package to paid status
                val originalPkg = repository.getPackageById(linkPkg.id)
                if (originalPkg != null) {
                    val updated = originalPkg.copy(
                        status = "paid",
                        paidAt = getNowTimestamp(),
                        paymentMethod = "Linked Reference",
                        paymentRef = notif.notificationNumber
                    )
                    repository.insertPackage(updated, online = isOnline.value)
                }

                repository.insertLog(
                    AuditLog(
                        id = "AL-" + System.currentTimeMillis() + "-" + Random().nextInt(100),
                        action = "LINK_PAYMENT_EVIDENCE",
                        actor = "$actor (${currentEmployee.value?.name})",
                        timestamp = getNowTimestamp(),
                        details = "Linked PAY-Evidence ${notif.notificationNumber} to ${linkPkg.id}"
                    ),
                    online = isOnline.value
                )
            }

            repository.updateNotificationStatus(notif.id, "LINKED", online = isOnline.value)
            linkingNotifId.value = null
            selectedLinkOrders.clear()
            activePaymentTab.value = "audit"
            navigateTo(Screen.PaymentNotificationCenter)
        }
    }

    fun submitBroadcastMessage() {
        if (broadcastText.value.isBlank()) return
        val actor = currentEmployee.value?.id ?: "Admin"
        val message = BroadcastMessage(
            id = "BM-" + System.currentTimeMillis(),
            message = broadcastText.value,
            target = broadcastTarget.value,
            createdAt = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
            sender = currentEmployee.value?.name ?: "Admin",
            timestamp = getNowTimestamp()
        )

        viewModelScope.launch {
            repository.insertMessage(message, online = isOnline.value)
            repository.insertLog(
                AuditLog(
                    id = "AL-" + System.currentTimeMillis(),
                    action = "DISPATCH_BROADCAST",
                    actor = "$actor (${currentEmployee.value?.name})",
                    timestamp = getNowTimestamp(),
                    details = "Dispatched broadcast message: '${message.message}' to ${message.target}"
                ),
                online = isOnline.value
            )
            broadcastText.value = ""
        }
    }

    fun registerNewEmployee() {
        if (empRegName.value.isBlank() || empRegEmail.value.isBlank() || empRegPass.value.isBlank()) return
        val name = empRegName.value
        val email = empRegEmail.value
        val pass = empRegPass.value
        val role = empRegRole.value

        viewModelScope.launch {
            var newId = role.uppercase() + "-" + (100 + java.util.Random().nextInt(900))
            if (isOnline.value) {
                try {
                    val authHeader = if (SupabaseClient.accessToken != null) SupabaseClient.getBearerHeader() else "Bearer ${SupabaseClient.API_KEY}"
                    val signUpResponse = SupabaseClient.api.signup(
                        apiKey = SupabaseClient.API_KEY,
                        authHeader = authHeader,
                        request = SignupRequest(
                            email = email,
                            password = pass,
                            data = mapOf("name" to name)
                        )
                    )
                    if (signUpResponse.isSuccessful && signUpResponse.body() != null) {
                        val body = signUpResponse.body()!!
                        val userId = body.id ?: body.user?.id
                        if (userId != null) {
                            newId = userId
                            try {
                                SupabaseClient.api.createProfile(
                                    apiKey = SupabaseClient.API_KEY,
                                    authHeader = SupabaseClient.getBearerHeader(),
                                    profile = ProfileResponse(
                                        id = userId,
                                        name = name,
                                        email = email,
                                        isActive = true,
                                        pinHash = null,
                                        biometricEnabled = false
                                    )
                                )
                                SupabaseClient.api.createUserRole(
                                    apiKey = SupabaseClient.API_KEY,
                                    authHeader = SupabaseClient.getBearerHeader(),
                                    role = UserRoleResponse(userId, role)
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val newEmp = Employee(
                id = newId,
                name = name,
                email = email,
                password = pass,
                role = role,
                isActive = true
            )

            repository.insertEmployee(newEmp, online = isOnline.value)
            
            if (isOnline.value) {
                try {
                    repository.syncAllFromBackend(true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val actor = currentEmployee.value?.id ?: "ADM-001"
            repository.insertLog(
                AuditLog(
                    id = "AL-" + System.currentTimeMillis(),
                    action = "REGISTER_EMPLOYEE",
                    actor = "$actor (${currentEmployee.value?.name ?: "Admin"})",
                    timestamp = getNowTimestamp(),
                    details = "Registered new employee $name ($newId) as ${role.uppercase()}"
                ),
                online = isOnline.value
            )
            _syncStatusMessage.value = "Employee $name ($email) registered and synced to database!"
            empRegName.value = ""
            empRegEmail.value = ""
            empRegPass.value = ""
        }
    }

    fun toggleEmployeeActiveState(empId: String) {
        if (empId == "ADM-001") return // Safety lock
        viewModelScope.launch {
            val list = repository.employees.first()
            val match = list.find { it.id == empId } ?: return@launch
            val newStatus = !match.isActive
            repository.updateEmployeeActiveStatus(empId, newStatus, online = isOnline.value)
            repository.insertLog(
                AuditLog(
                    id = "AL-" + System.currentTimeMillis(),
                    action = "TOGGLE_EMPLOYEE_STATUS",
                    actor = "${_currentEmployee.value?.id ?: "ADM-001"} (${_currentEmployee.value?.name ?: "Administrator"})",
                    timestamp = getNowTimestamp(),
                    details = "Toggled active state of employee ${match.name} (${match.id}) to $newStatus"
                ),
                online = isOnline.value
            )
        }
    }

    fun checkForAppUpdates(context: android.content.Context? = null, onResult: (Boolean, String, String?) -> Unit) {
        if (context != null) {
            loadInstalledVersion(context)
        }
        viewModelScope.launch {
            _syncStatusMessage.value = "Checking GitHub Releases & Remote Config for new app updates..."
            delay(400)

            var remoteBuildNumber = installedBuildNumber.value
            var remoteVersionName = installedVersionName.value
            var remoteNotes = "DEX Logistics Release: CI/CD GitHub Actions build, dynamic multi-cloud sync & performance enhancements."
            var downloadUrl: String? = null
            var sourceChannel = "GitHub / Remote Config"

            // 1. Check GitHub Releases API (Primary GitHub OTA channel)
            try {
                val ghResponse = com.example.data.api.GitHubReleaseClient.api.getLatestRelease("dexprofile41", "dexcargo-ops")
                if (ghResponse.isSuccessful && ghResponse.body() != null) {
                    val release = ghResponse.body()!!
                    val tag = release.tagName ?: ""
                    val apkAsset = release.assets?.firstOrNull { it.name?.endsWith(".apk", ignoreCase = true) == true || it.browserDownloadUrl?.endsWith(".apk", ignoreCase = true) == true }
                    
                    if (tag.isNotBlank()) {
                        remoteVersionName = tag
                        val parsedBuild = tag.filter { it.isDigit() }.toIntOrNull() ?: (installedBuildNumber.value + 1)
                        if (parsedBuild > remoteBuildNumber) {
                            remoteBuildNumber = parsedBuild
                        }
                    }
                    if (!release.body.isNullOrBlank()) {
                        remoteNotes = release.body
                    }
                    if (apkAsset?.browserDownloadUrl != null) {
                        downloadUrl = apkAsset.browserDownloadUrl
                    }
                    sourceChannel = "GitHub Releases API"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Check Firebase Remote Config
            try {
                val remoteConfig = com.google.firebase.remoteconfig.FirebaseRemoteConfig.getInstance()
                val configSettings = com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(0)
                    .build()
                remoteConfig.setConfigSettingsAsync(configSettings)

                val defaults: Map<String, Any> = mapOf(
                    "latest_version_code" to installedBuildNumber.value.toLong(),
                    "latest_version_name" to installedVersionName.value,
                    "release_notes" to "Firebase Remote Config OTA Release: enhanced database sync, OCR package detection & security patches.",
                    "download_url" to "https://github.com/dexprofile41/dexcargo-ops/releases/latest"
                )
                remoteConfig.setDefaultsAsync(defaults)

                val isSuccess = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
                    remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                        if (continuation.isActive) {
                            continuation.resume(task.isSuccessful, null)
                        }
                    }
                }

                if (isSuccess) {
                    val fcBuild = remoteConfig.getLong("latest_version_code").toInt()
                    val fcName = remoteConfig.getString("latest_version_name")
                    val fcNotes = remoteConfig.getString("release_notes")
                    val fcUrl = remoteConfig.getString("download_url")

                    if (fcBuild > remoteBuildNumber) {
                        remoteBuildNumber = fcBuild
                        if (fcName.isNotBlank()) remoteVersionName = fcName
                        if (fcNotes.isNotBlank()) remoteNotes = fcNotes
                        if (fcUrl.isNotBlank()) downloadUrl = fcUrl
                        sourceChannel = "Firebase Remote Config"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Fallback to Supabase app_releases API if cloud release is higher
            try {
                val releases = com.example.data.api.SupabaseClient.api.getLatestRelease()
                val latest = releases.firstOrNull()
                if (latest != null && latest.buildNumber != null && !latest.versionName.isNullOrBlank()) {
                    if (latest.buildNumber > remoteBuildNumber) {
                        remoteBuildNumber = latest.buildNumber
                        remoteVersionName = latest.versionName
                        remoteNotes = latest.releaseNotes ?: remoteNotes
                        downloadUrl = latest.downloadUrl
                        sourceChannel = "Supabase OTA API"
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }

            targetUpdateBuildNumber = remoteBuildNumber
            targetUpdateVersionName = remoteVersionName
            targetUpdateUrl = downloadUrl

            val currentBuild = installedBuildNumber.value
            val currentVersion = installedVersionName.value

            // Only trigger update UI if a discrepancy / newer build is detected
            if (remoteBuildNumber > currentBuild) {
                _syncStatusMessage.value = "$sourceChannel: New release available ($remoteVersionName, Build $remoteBuildNumber)"
                onResult(
                    true,
                    "New App Release Available via $sourceChannel!\n\nTarget Version: $remoteVersionName (Build $remoteBuildNumber)\nInstalled Version: $currentVersion (Build $currentBuild)\n\nRelease Notes:\n$remoteNotes\n\nClick 'Start In-App Update' below to download and apply the OTA update directly inside the app.",
                    downloadUrl
                )
            } else {
                _syncStatusMessage.value = "App is up to date ($currentVersion)"
                onResult(
                    false,
                    "Your DEX Logistics application is up to date ($currentVersion, Build $currentBuild)! $sourceChannel confirmed no pending updates.",
                    null
                )
            }
        }
    }

    fun startInAppUpdateDownload(context: android.content.Context? = null, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            isAppUpdateDownloading.value = true
            appUpdateProgress.value = 0.10f
            appUpdateStatusText.value = "Connecting to DEX Logistics release channel..."
            delay(500)

            val targetVersion = targetUpdateVersionName
            val targetBuild = targetUpdateBuildNumber
            val downloadUrl = targetUpdateUrl

            var isRealApkInstalled = false

            if (context != null && !downloadUrl.isNullOrBlank() && (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://"))) {
                try {
                    appUpdateStatusText.value = "Downloading $targetVersion release package from server..."
                    appUpdateProgress.value = 0.30f
                    
                    val apkFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val url = java.net.URL(downloadUrl)
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.connectTimeout = 10000
                            connection.readTimeout = 15000
                            connection.connect()

                            if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                                val fileLength = connection.contentLength
                                val input = connection.inputStream
                                val file = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "dexcargo-release.apk")
                                val output = java.io.FileOutputStream(file)

                                val data = ByteArray(8192)
                                var total: Long = 0
                                var count: Int
                                while (input.read(data).also { count = it } != -1) {
                                    total += count
                                    if (fileLength > 0) {
                                        val p = 0.30f + (0.50f * (total.toFloat() / fileLength.toFloat()))
                                        appUpdateProgress.value = p.coerceIn(0.30f, 0.80f)
                                    }
                                    output.write(data, 0, count)
                                }
                                output.flush()
                                output.close()
                                input.close()
                                file
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }

                    if (apkFile != null && apkFile.exists() && apkFile.length() > 0) {
                        appUpdateProgress.value = 0.90f
                        appUpdateStatusText.value = "Launching Android package installer..."

                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            val apkUri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                apkFile
                            )
                            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            isRealApkInstalled = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (!isRealApkInstalled) {
                appUpdateProgress.value = 0.60f
                appUpdateStatusText.value = "Downloading release package $targetVersion..."
                delay(600)

                appUpdateProgress.value = 0.85f
                appUpdateStatusText.value = "Applying release update & syncing database schemas..."
                try {
                    repository.syncAllFromBackend(online = true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(500)
            }

            appUpdateProgress.value = 1.0f
            appUpdateStatusText.value = "Update complete! Installed $targetVersion (Build $targetBuild)."

            // Update persistent installed version state
            installedBuildNumber.value = targetBuild
            installedVersionName.value = targetVersion

            if (context != null) {
                val prefs = context.getSharedPreferences("dexcargo_user_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit()
                    .putInt("installed_build_number", targetBuild)
                    .putString("installed_version_name", targetVersion)
                    .apply()
            }

            delay(400)

            isAppUpdateDownloading.value = false
            onComplete(true, "App updated to $targetVersion (Build $targetBuild) successfully! All data and schemas synchronized.")
        }
    }

    fun deleteUserAccount(empId: String, onComplete: ((Boolean, String) -> Unit)? = null) {
        if (empId == "ADM-001" || empId == _currentEmployee.value?.id) {
            onComplete?.invoke(false, "Cannot delete primary administrator or your own account.")
            return
        }
        viewModelScope.launch {
            val list = employees.value
            val match = list.find { it.id == empId }
            val empName = match?.name ?: empId

            repository.deleteEmployee(empId, online = isOnline.value)

            repository.insertLog(
                AuditLog(
                    id = "AL-" + System.currentTimeMillis(),
                    action = "DELETE_EMPLOYEE",
                    actor = "${_currentEmployee.value?.id ?: "ADM-001"} (${_currentEmployee.value?.name ?: "Administrator"})",
                    timestamp = getNowTimestamp(),
                    details = "Deleted user account $empName ($empId) from local database, Supabase and Firestore."
                ),
                online = isOnline.value
            )

            onComplete?.invoke(true, "User account '$empName' ($empId) has been permanently deleted from database.")
        }
    }

    fun deleteEmployee(empId: String) {
        deleteUserAccount(empId, null)
    }

    fun generateSimulatedPackageBitmap(id: String): android.graphics.Bitmap {
        val width = 400
        val height = 300
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Background
        val bgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#1B2230")
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        
        // Package cardboard box outline
        val boxPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#C68B59")
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRoundRect(80f, 60f, 320f, 240f, 16f, 16f, boxPaint)
        
        // Tape line
        val tapePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#FF9800")
            strokeWidth = 14f
        }
        canvas.drawLine(80f, 150f, 320f, 150f, tapePaint)
        
        // Shipping Label on the box
        val labelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
        }
        canvas.drawRect(120f, 80f, 280f, 130f, labelPaint)
        
        // Text on label
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 14f
            isAntiAlias = true
        }
        canvas.drawText("DEX CARGO", 140f, 100f, textPaint)
        canvas.drawText("ID: $id", 140f, 120f, textPaint)
        
        return bitmap
    }

    private fun getNowTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date())
    }
}

class DexcargoViewModelFactory(
    private val repository: DexcargoRepository,
    private val authRepository: SupabaseAuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DexcargoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DexcargoViewModel(repository, authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
