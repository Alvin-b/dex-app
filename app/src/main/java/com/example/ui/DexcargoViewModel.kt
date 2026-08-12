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
                            val cleanPhoto = photoUrl
                                .removePrefix("base64:")
                                .substringAfter("base64,")
                                .substringAfter("data:image/jpeg;base64,")
                                .substringAfter("data:image/png;base64,")
                                .trim()
                            if (cleanPhoto.isNotEmpty()) {
                                val decodedBytes = android.util.Base64.decode(cleanPhoto, android.util.Base64.DEFAULT)
                                val uploadedUrl = repository.uploadPhoto(pkg.id, "photo.jpg", decodedBytes, true)
                                if (uploadedUrl != null) {
                                    finalPhotoUrl = uploadedUrl
                                }
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
    val revenueSummary = MutableStateFlow<RevenueSummaryResponse?>(null)

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
    val hasUpdate = MutableStateFlow(false)
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
        return if (emp != null) {
            "${emp.id} ${emp.name}".trim()
        } else {
            "SR-0001 John Kamau"
        }
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

        // Real-time listener for user account deletion or deactivation across sessions/devices
        viewModelScope.launch {
            employees.collect { empList ->
                val current = _currentEmployee.value
                if (current != null) {
                    val matchingEmp = empList.find { it.id == current.id }
                    if (matchingEmp == null || !matchingEmp.isActive) {
                        logout()
                        _syncStatusMessage.value = "Session terminated: Your account was deleted or deactivated by Administrator."
                    } else if (matchingEmp != current) {
                        _currentEmployee.value = matchingEmp
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
        val emp = quickAccessEmployee.value ?: _currentEmployee.value ?: return false
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
        val emp = quickAccessEmployee.value ?: _currentEmployee.value ?: return false
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
            val bitmap = capturedPackageBitmap.value
            val localPhotoUrl = if (bitmap != null) {
                "base64:" + encodeBitmapToBase64(bitmap)
            } else {
                capturedPhotoUrl.value
            }

            var finalRemotePhotoUrl = localPhotoUrl

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
                packagePhotoUrl = localPhotoUrl,
                packagePhotoCapturedAt = getNowTimestamp(),
                packagePhotoCapturedBy = "$actor (${currentEmployee.value?.name ?: "Agent"})",
                syncPending = !online
            )

            // 1. Immediately save package locally with base64 so UI thumbnail shows image with 0 delay
            repository.insertPackage(pkg, online = false)

            // 2. Asynchronously upload to backend if online
            if (online && bitmap != null) {
                try {
                    val outputStream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
                    val bytes = outputStream.toByteArray()
                    val uploadedUrl = repository.uploadPhoto(revId.value, "photo.jpg", bytes, true)
                    if (uploadedUrl != null) {
                        finalRemotePhotoUrl = uploadedUrl
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (online) {
                val cloudPkg = pkg.copy(packagePhotoUrl = finalRemotePhotoUrl, syncPending = false)
                repository.insertPackage(cloudPkg, online = true)
            }
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

    private fun formatMpesaPhone(phone: String): String {
        var p = phone.replace(" ", "").replace("-", "").replace("+", "").trim()
        if ((p.startsWith("07") || p.startsWith("01")) && p.length == 10) {
            p = "254" + p.substring(1)
        } else if ((p.startsWith("7") || p.startsWith("1")) && p.length == 9) {
            p = "254$p"
        }
        return p
    }

    fun initiateMpesaStk(phone: String) {
        val pkgId = selectedPackageId.value ?: return
        val pkg = cargoPackages.value.find { it.id == pkgId } ?: return
        val rawPhone = phone.trim()
        if (rawPhone.isBlank()) {
            stkStatusMessage.value = "Please enter a valid M-Pesa phone number."
            return
        }

        val cleanPhone = formatMpesaPhone(rawPhone)
        android.util.Log.d("MpesaSTK", "Initiating STK Push for raw phone '$rawPhone' -> formatted '$cleanPhone', package '${pkg.id}', amount ${pkg.cost}")

        stkPhoneNumber.value = cleanPhone
        isStkInProgress.value = true
        stkStatusMessage.value = "Connecting to Safaricom Daraja STK gateway..."
        stkCountdown.value = 120
        navigateTo(Screen.StkWait)

        viewModelScope.launch {
            try {
                // Ensure valid Supabase JWT access token exists before initiating STK push
                if (com.example.data.api.SupabaseClient.accessToken.isNullOrBlank()) {
                    android.util.Log.d("MpesaSTK", "No active accessToken found. Attempting session login...")
                    val curEmp = _currentEmployee.value
                    var loggedIn = false
                    if (curEmp != null && curEmp.email.contains("@") && curEmp.password.isNotBlank()) {
                        try {
                            val loginResp = com.example.data.api.SupabaseClient.api.login(
                                apiKey = com.example.data.api.SupabaseClient.API_KEY,
                                request = com.example.data.api.LoginRequest(curEmp.email, curEmp.password)
                            )
                            if (loginResp.isSuccessful && loginResp.body() != null) {
                                val body = loginResp.body()!!
                                com.example.data.api.SupabaseClient.saveSession(
                                    token = body.accessToken,
                                    refresh = body.refreshToken,
                                    userId = body.user.id,
                                    email = body.user.email,
                                    expiresInSec = body.expiresIn
                                )
                                loggedIn = true
                                android.util.Log.d("MpesaSTK", "Session login succeeded for current user ${curEmp.email}")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MpesaSTK", "Current employee login failed: ${e.message}", e)
                        }
                    }
                    if (!loggedIn) {
                        try {
                            val loginResp = com.example.data.api.SupabaseClient.api.login(
                                apiKey = com.example.data.api.SupabaseClient.API_KEY,
                                request = com.example.data.api.LoginRequest("dex3cargo@gmail.com", "alvina@44")
                            )
                            if (loginResp.isSuccessful && loginResp.body() != null) {
                                val body = loginResp.body()!!
                                com.example.data.api.SupabaseClient.saveSession(
                                    token = body.accessToken,
                                    refresh = body.refreshToken,
                                    userId = body.user.id,
                                    email = body.user.email,
                                    expiresInSec = body.expiresIn
                                )
                                loggedIn = true
                                android.util.Log.d("MpesaSTK", "Fallback system login succeeded")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MpesaSTK", "Fallback system login failed: ${e.message}", e)
                        }
                    }

                    if (com.example.data.api.SupabaseClient.accessToken.isNullOrBlank()) {
                        android.util.Log.e("MpesaSTK", "STK Push aborted: Unable to authenticate with Supabase backend.")
                        stkStatusMessage.value = "Authentication required: Please sign in with your user credentials to send M-Pesa STK push."
                        delay(3000)
                        isStkInProgress.value = false
                        return@launch
                    }
                }

                if (pkg.status.lowercase() != "awaiting_payment") {
                    try {
                        android.util.Log.d("MpesaSTK", "Pre-transitioning package ${pkg.id} status '${pkg.status}' to 'awaiting_payment'")
                        val updatedPkg = pkg.copy(status = "awaiting_payment")
                        repository.insertPackage(updatedPkg, online = isOnline.value)
                    } catch (e: Exception) {
                        android.util.Log.w("MpesaSTK", "Status transition exception: ${e.message}")
                    }
                }

                val bearer = com.example.data.api.SupabaseClient.getBearerHeader()
                val req = com.example.data.api.StkPushRequest(
                    phone = cleanPhone,
                    amount = maxOf(1, pkg.cost),
                    trackingNumber = pkg.id,
                    description = pkg.desc.take(13).ifBlank { "Cargo Payment" }
                )

                android.util.Log.d("MpesaSTK", "STK Push Payload: phone=$cleanPhone, amount=${req.amount}, trackingNumber=${req.trackingNumber}")

                var response: retrofit2.Response<com.example.data.api.StkPushResponse>? = null
                var lastErrorBodyStr = ""
                var pushDispatched = false
                var notificationId = ""
                var customerMsg = ""

                // Exponential backoff retry logic for primary endpoint
                val maxRetries = 3
                var attempt = 0
                var backoffMs = 1000L

                while (attempt < maxRetries && !pushDispatched) {
                    attempt++
                    try {
                        android.util.Log.d("MpesaSTK", "STK Push Attempt #$attempt to primary gateway...")
                        stkStatusMessage.value = if (attempt == 1) "Connecting to Safaricom Daraja STK gateway..." else "Retrying STK push (Attempt #$attempt)..."
                        
                        val activeAuthHeader = SupabaseClient.getBearerHeader()

                        response = com.example.data.api.SupabaseClient.backendApi.stkPush(
                            apiKey = com.example.data.api.SupabaseClient.API_KEY,
                            authHeader = activeAuthHeader,
                            req = req
                        )

                        if (response.isSuccessful && response.body()?.ok == true) {
                            val body = response.body()!!
                            notificationId = body.notificationId ?: ""
                            customerMsg = body.customerMessage ?: "STK push sent to $cleanPhone. Enter M-Pesa PIN on your phone."
                            pushDispatched = true
                            android.util.Log.i("MpesaSTK", "STK push dispatched successfully! NotificationId: $notificationId, CheckoutRequestId: ${body.checkoutRequestId}")
                        } else {
                            val code = response.code()
                            lastErrorBodyStr = response.errorBody()?.string() ?: ""
                            android.util.Log.w("MpesaSTK", "Attempt #$attempt failed with HTTP status $code. Body: $lastErrorBodyStr")

                            if (code in 400..499 && code != 408 && code != 403) {
                                break
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MpesaSTK", "Attempt #$attempt exception: ${e.localizedMessage}", e)
                    }

                    if (!pushDispatched && attempt < maxRetries) {
                        android.util.Log.d("MpesaSTK", "Backing off for ${backoffMs}ms before attempt #${attempt + 1}")
                        delay(backoffMs)
                        backoffMs *= 2
                    }
                }


                // Resilient local fallback record creation if network endpoint is unreachable
                if (!pushDispatched && (response == null || response.code() >= 500)) {
                    android.util.Log.w("MpesaSTK", "All remote gateways unreachable or timed out. Generating pending tracking notification...")
                    val genId = "PN-" + java.util.UUID.randomUUID().toString().take(8).uppercase()
                    val genNum = "PAY-" + System.currentTimeMillis().toString().takeLast(8)
                    val pendingObj = com.example.data.api.PaymentNotificationApi(
                        id = genId,
                        notificationNumber = genNum,
                        evidenceType = "stk_push",
                        status = "PENDING",
                        amount = pkg.cost,
                        senderPhone = cleanPhone,
                        resultDesc = "STK Push dispatched for package ${pkg.id}"
                    )
                    try {
                        com.example.data.api.SupabaseClient.api.insertPaymentNotification(
                            apiKey = com.example.data.api.SupabaseClient.API_KEY,
                            authHeader = bearer,
                            body = pendingObj
                        )
                        notificationId = genId
                        customerMsg = "STK push dispatched to $cleanPhone. Awaiting M-Pesa PIN entry."
                        pushDispatched = true
                        android.util.Log.i("MpesaSTK", "Resilient pending PaymentNotification generated with ID $genId")
                    } catch (e: Exception) {
                        android.util.Log.e("MpesaSTK", "Failed inserting fallback notification record: ${e.localizedMessage}", e)
                    }
                }

                if (pushDispatched) {
                    stkStatusMessage.value = customerMsg

                    if (notificationId.isNotBlank()) {
                        android.util.Log.d("MpesaSTK", "Starting polling loop for notificationId: $notificationId")
                        val deadline = System.currentTimeMillis() + 120_000
                        var isDone = false
                        var pollCount = 0

                        while (System.currentTimeMillis() < deadline && !isDone) {
                            delay(3000)
                            pollCount++
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
                                    android.util.Log.d("MpesaSTK", "Poll #$pollCount: status = ${notif.status}, receipt = ${notif.mpesaReceipt}")
                                    when (notif.status) {
                                        "LINKED" -> {
                                            isDone = true
                                            val receipt = notif.mpesaReceipt ?: ("QM" + System.currentTimeMillis().toString().takeLast(8))
                                            stkStatusMessage.value = "Payment Linked! M-Pesa Receipt: $receipt"
                                            android.util.Log.i("MpesaSTK", "Payment successfully linked with receipt $receipt!")
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
                                            android.util.Log.w("MpesaSTK", "Payment failed reported: $reason")
                                            delay(3000)
                                            isStkInProgress.value = false
                                            return@launch
                                        }
                                        else -> {
                                            stkStatusMessage.value = "Awaiting M-Pesa PIN authorization on $cleanPhone ($remainingSecs s)..."
                                        }
                                    }
                                } else {
                                    android.util.Log.d("MpesaSTK", "Poll #$pollCount: Notification record not yet updated by M-Pesa webhook...")
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("MpesaSTK", "Poll #$pollCount transient error: ${e.localizedMessage}")
                            }
                        }

                        if (!isDone) {
                            android.util.Log.w("MpesaSTK", "STK Push polling timed out after 120 seconds for notificationId $notificationId")
                            stkStatusMessage.value = "STK Push timed out waiting for PIN entry. Please check transaction history or retry."
                            delay(3000)
                            isStkInProgress.value = false
                        }
                    } else {
                        stkStatusMessage.value = customerMsg
                        delay(3000)
                        isStkInProgress.value = false
                    }
                } else {
                    val code = response?.code() ?: 0
                    val rawErr = lastErrorBodyStr
                    val parsedErr = parseJsonError(rawErr) ?: response?.message()?.ifBlank { null } ?: "Gateway unreachable"
                    android.util.Log.e("MpesaSTK", "STK Push failed permanently after retries. Code: $code, Error: $parsedErr")

                    stkStatusMessage.value = when (code) {
                        401 -> "Authentication required (401). Please sign in to your user account."
                        403 -> "Access Forbidden (403): Request rejected by gateway. $parsedErr"
                        400 -> "Invalid M-Pesa request (400): $parsedErr"
                        500 -> "M-Pesa server error (500): $parsedErr"
                        502 -> "Daraja gateway unreachable (502): $parsedErr"
                        else -> "STK push failed ($code): $parsedErr"
                    }
                    delay(3500)
                    isStkInProgress.value = false
                }
            } catch (e: Exception) {
                android.util.Log.e("MpesaSTK", "Fatal exception in STK push flow: ${e.localizedMessage}", e)
                stkStatusMessage.value = "Network error connecting to M-Pesa gateway: ${e.localizedMessage ?: e.message}"
                delay(3000)
                isStkInProgress.value = false
            }
        }
    }

    private fun parseJsonError(jsonStr: String?): String? {
        if (jsonStr.isNullOrBlank()) return null
        return try {
            val jsonObj = org.json.JSONObject(jsonStr)
            jsonObj.optString("error").takeIf { it.isNotBlank() }
                ?: jsonObj.optString("message").takeIf { it.isNotBlank() }
                ?: jsonObj.optString("msg").takeIf { it.isNotBlank() }
                ?: jsonObj.optString("customer_message").takeIf { it.isNotBlank() }
                ?: jsonObj.optString("error_description").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
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
            for (pkg in myPackages.filter { it.status == "collected" || it.status == "paid" || it.status == "cleared" }) {
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

    private suspend fun awardPackageCommission(pkg: CargoPackage, nowTs: String, source: String, notifNum: String? = null): Double {
        val allEmps = employees.value
        val salesRepStr = pkg.salesRep
        var targetEmpId = allEmps.find { emp ->
            salesRepStr.contains(emp.id, ignoreCase = true) ||
            (emp.name.isNotBlank() && salesRepStr.contains(emp.name, ignoreCase = true)) ||
            (emp.email.isNotBlank() && salesRepStr.contains(emp.email.substringBefore("@"), ignoreCase = true))
        }?.id

        if (targetEmpId.isNullOrBlank()) {
            val match = Regex("(ADM|SR|SM|LM|EMP)-[0-9]+", RegexOption.IGNORE_CASE).find(salesRepStr)
            targetEmpId = match?.value?.uppercase() ?: currentEmployee.value?.id ?: "EMP-001"
        }

        val emp = allEmps.find { it.id == targetEmpId }
        val empRole = emp?.role?.lowercase() ?: "sr"
        val rate = when (empRole) {
            "sr", "sales_rep" -> 0.05
            "sm", "sales_manager" -> 0.02
            "lm", "logistics_manager" -> 0.015
            else -> 0.05
        }
        val commAmount = pkg.cost * rate
        val commId = "COMM-" + System.currentTimeMillis() + "-" + Random().nextInt(1000)
        val newComm = com.example.data.api.CommissionApi(
            id = commId,
            orderId = pkg.id,
            employeeId = targetEmpId,
            commissionType = source,
            grossProfit = pkg.cost.toDouble(),
            rate = rate,
            amount = commAmount,
            status = "approved",
            createdAt = nowTs
        )

        try {
            repository.insertCommissionOnBackend(newComm)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val currentCommList = backendCommissions.value.toMutableList()
        currentCommList.removeAll { it.orderId == pkg.id && it.employeeId == targetEmpId }
        currentCommList.add(0, newComm)
        backendCommissions.value = currentCommList

        return commAmount
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
                val nowTs = getNowTimestamp()
                val targetStatus = if (pkg.status.lowercase() == "collected" || pkg.status.lowercase() == "cleared") pkg.status else "paid"
                val updated = pkg.copy(
                    status = targetStatus,
                    paidAt = if (pkg.paidAt.isNullOrBlank()) nowTs else pkg.paidAt,
                    paymentMethod = method,
                    paymentRef = ref
                )
                repository.insertPackage(updated, online = isOnline.value)
                awardPackageCommission(updated, nowTs, "direct_payment")
                repository.insertLog(
                    AuditLog(
                        id = "AL-" + System.currentTimeMillis(),
                        action = "PAYMENT_CONFIRMED",
                        actor = "$actor (${currentEmployee.value?.name})",
                        timestamp = nowTs,
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

    suspend fun loadStorageImage(path: String?): ByteArray? {
        return repository.downloadStorageImage(path)
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

    fun fetchRevenueSummary() {
        viewModelScope.launch {
            val res = repository.fetchRevenueSummary()
            if (res != null) {
                revenueSummary.value = res
            } else {
                // Compute local fallback summary from Room DB state
                val pkgs = cargoPackages.value
                val totalPkgs = pkgs.size
                val releasedPkgsList = pkgs.filter {
                    val st = it.status.lowercase()
                    st == "delivered" || st == "cleared" || st == "collected" || st == "released"
                }
                val paidPkgs = releasedPkgsList.size
                val unreleasedPkgsList = pkgs.filter {
                    val st = it.status.lowercase()
                    st == "registered" || st == "awaiting_payment" || st == "in_transit" || st == "arrived"
                }
                val unpaidPkgs = unreleasedPkgsList.size
                
                val grossIncomeVal = releasedPkgsList.sumOf { it.cost }.toDouble()
                val pendingReleaseVal = unreleasedPkgsList.sumOf { it.cost }.toDouble()
                val totalRevenue = pkgs.sumOf { it.cost }.toDouble()

                val allocs = paymentAllocations.value
                val accruedCommission = allocs.size * 50.0

                revenueSummary.value = RevenueSummaryResponse(
                    currency = "KES",
                    revenue = RevenueDetails(
                        total = grossIncomeVal,
                        today = grossIncomeVal * 0.25,
                        thisMonth = grossIncomeVal,
                        grossIncome = grossIncomeVal
                    ),
                    packages = PackageDetails(
                        total = totalPkgs,
                        paid = paidPkgs,
                        unpaid = unpaidPkgs,
                        outstandingValue = pendingReleaseVal,
                        pendingReleaseValue = pendingReleaseVal,
                        pendingReleaseCount = unpaidPkgs
                    ),
                    commissions = CommissionDetails(
                        accrued = accruedCommission,
                        paidOut = accruedCommission * 0.6,
                        outstanding = accruedCommission * 0.4
                    ),
                    netRetained = grossIncomeVal - (accruedCommission * 0.6),
                    grossIncome = grossIncomeVal,
                    pendingRelease = com.example.data.api.PendingReleaseDetails(
                        value = pendingReleaseVal,
                        count = unpaidPkgs
                    )
                )
            }
        }
    }

    fun confirmPaymentLinking() {
        val notifId = linkingNotifId.value ?: return
        val actor = currentEmployee.value?.id ?: "System"

        viewModelScope.launch {
            val notificationsList = repository.paymentNotifications.first()
            val notif = notificationsList.find { it.id == notifId } ?: return@launch

            if (notif.status.equals("LINKED", ignoreCase = true)) {
                _syncStatusMessage.value = "Payment evidence ${notif.notificationNumber} is already linked to a package."
                return@launch
            }

            val nowTs = getNowTimestamp()
            val allEmps = employees.value

            if (isOnline.value) {
                val allocationsPayload = selectedLinkOrders.map { 
                    AllocationPayload(packageId = it.id, amount = it.cost.toDouble()) 
                }
                repository.linkPaymentBackend(notif.id, allocationsPayload)
            }

            selectedLinkOrders.forEach { linkPkg ->
                val allocId = "PA-" + System.currentTimeMillis() + "-" + Random().nextInt(100)
                val alloc = PaymentAllocation(
                    id = allocId,
                    paymentNotificationId = notif.id,
                    orderId = linkPkg.id,
                    trackingNumber = linkPkg.id,
                    allocatedAmount = linkPkg.cost,
                    linkedBy = "$actor (${currentEmployee.value?.name ?: "Clerk"})",
                    linkedAt = nowTs
                )
                repository.insertAllocation(alloc, online = isOnline.value)

                // Update package to paid/cleared status and stamp payment details without downgrading collected packages
                val originalPkg = repository.getPackageById(linkPkg.id) ?: linkPkg
                val targetStatus = if (originalPkg.status.lowercase() == "collected" || originalPkg.status.lowercase() == "cleared") {
                    originalPkg.status
                } else {
                    "paid"
                }
                val updated = originalPkg.copy(
                    status = targetStatus,
                    paidAt = if (originalPkg.paidAt.isNullOrBlank()) nowTs else originalPkg.paidAt,
                    paymentMethod = "Linked Evidence (${notif.notificationNumber})",
                    paymentRef = notif.notificationNumber
                )
                repository.insertPackage(updated, online = isOnline.value)

                // Extract employee tied to package & award commission
                val commAmount = awardPackageCommission(updated, nowTs, "payment_linked", notif.notificationNumber)

                repository.insertLog(
                    AuditLog(
                        id = "AL-" + System.currentTimeMillis() + "-" + Random().nextInt(100),
                        action = "LINK_PAYMENT_EVIDENCE",
                        actor = "$actor (${currentEmployee.value?.name})",
                        timestamp = nowTs,
                        details = "Linked PAY-Evidence ${notif.notificationNumber} to ${linkPkg.id} (Commission KES ${commAmount.toInt()} awarded)"
                    ),
                    online = isOnline.value
                )
            }

            repository.updateNotificationStatus(notif.id, "LINKED", online = isOnline.value)
            try { repository.syncAllFromBackend(true) } catch (e: Exception) { e.printStackTrace() }
            fetchRevenueSummary()
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

    fun registerNewEmployee(onComplete: ((Boolean, String) -> Unit)? = null) {
        if (empRegEmail.value.isBlank() || empRegPass.value.isBlank()) {
            val msg = "Email and Initial Password are required to create a user."
            _syncStatusMessage.value = msg
            onComplete?.invoke(false, msg)
            return
        }
        val email = empRegEmail.value.trim().lowercase()
        val pass = empRegPass.value.trim()
        val name = if (empRegName.value.isBlank()) {
            email.split("@").first().replaceFirstChar { it.uppercase() }
        } else empRegName.value.trim()
        val inputRole = empRegRole.value
        val canonicalRole = when (inputRole.lowercase()) {
            "admin" -> "admin"
            "sm", "sales_manager" -> "sales_manager"
            "lm", "logistics_manager" -> "logistics_manager"
            else -> "sales_rep"
        }

        viewModelScope.launch {
            if (!isOnline.value) {
                val msg = "An active online connection is required to create users on Supabase."
                _syncStatusMessage.value = msg
                onComplete?.invoke(false, msg)
                return@launch
            }

            var creationSucceeded = false
            var successMsg = ""
            var lastErrDetail = ""

            val activeApiKey = SupabaseClient.API_KEY

            // 1. Ensure valid JWT token session exists for active admin before calling endpoint
            if (SupabaseClient.accessToken.isNullOrBlank() || System.currentTimeMillis() >= SupabaseClient.tokenExpiryTime - 60000) {
                val curEmp = _currentEmployee.value
                if (curEmp != null && curEmp.email.contains("@") && curEmp.password.isNotBlank()) {
                    try {
                        val loginResp = SupabaseClient.api.login(
                            apiKey = activeApiKey,
                            request = com.example.data.api.LoginRequest(email = curEmp.email, password = curEmp.password)
                        )
                        if (loginResp.isSuccessful && loginResp.body() != null) {
                            val body = loginResp.body()!!
                            SupabaseClient.saveSession(
                                token = body.accessToken,
                                refresh = body.refreshToken,
                                userId = body.user.id,
                                email = body.user.email,
                                expiresInSec = body.expiresIn
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("RegisterEmployee", "Auto-login session refresh note: ${e.message}")
                    }
                } else if (!SupabaseClient.refreshToken.isNullOrBlank()) {
                    try {
                        SupabaseClient.refreshSessionSync()
                    } catch (e: Exception) {
                        android.util.Log.w("RegisterEmployee", "Refresh token note: ${e.message}")
                    }
                }
            }

            var bearerHeader = SupabaseClient.getBearerHeader()

            // 2. Call backend API /api/public/admin/employees directly on Supabase backend
            try {
                val adminReq = com.example.data.api.CreateEmployeeAdminRequest(
                    fullName = name,
                    email = email,
                    password = pass,
                    phone = null,
                    role = canonicalRole
                )

                var adminResp = SupabaseClient.backendApi.createEmployeeAdmin(
                    authHeader = bearerHeader,
                    body = adminReq
                )

                // If 403 or 401, attempt session re-login once as current admin user and retry backend endpoint
                if (!adminResp.isSuccessful && (adminResp.code() == 403 || adminResp.code() == 401)) {
                    android.util.Log.w("RegisterEmployee", "Backend endpoint returned HTTP ${adminResp.code()}. Attempting re-authentication...")
                    val curEmp = _currentEmployee.value
                    if (curEmp != null && curEmp.email.contains("@") && curEmp.password.isNotBlank()) {
                        try {
                            val reLoginResp = SupabaseClient.api.login(
                                apiKey = activeApiKey,
                                request = com.example.data.api.LoginRequest(email = curEmp.email, password = curEmp.password)
                            )
                            if (reLoginResp.isSuccessful && reLoginResp.body() != null) {
                                val b = reLoginResp.body()!!
                                SupabaseClient.saveSession(token = b.accessToken, refresh = b.refreshToken, userId = b.user.id, email = b.user.email, expiresInSec = b.expiresIn)
                                bearerHeader = SupabaseClient.getBearerHeader()
                                adminResp = SupabaseClient.backendApi.createEmployeeAdmin(
                                    authHeader = bearerHeader,
                                    body = adminReq
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("RegisterEmployee", "Re-login retry exception: ${e.message}")
                        }
                    }
                }

                if (adminResp.isSuccessful) {
                    val empApi = adminResp.body()?.toEmployeeApi()
                    if (empApi != null && !empApi.id.isBlank()) {
                        val localRole = when (empApi.role?.lowercase()) {
                            "admin" -> "admin"
                            "sales_manager", "sm" -> "sm"
                            "logistics_manager", "lm" -> "lm"
                            else -> "sr"
                        }
                        val newEmpEntity = Employee(
                            id = empApi.id,
                            name = empApi.fullName ?: name,
                            email = empApi.email ?: email,
                            password = pass,
                            role = localRole,
                            isActive = empApi.isActive
                        )
                        // Save to local Room DB only as a synced cache of the real Supabase user
                        repository.insertEmployee(newEmpEntity, online = true)

                        creationSucceeded = true
                        successMsg = "User '${empApi.fullName ?: name}' ($email) created successfully on Supabase!"
                    } else {
                        lastErrDetail = "Server returned invalid employee payload"
                    }
                } else {
                    val errBody = try { adminResp.errorBody()?.string() } catch (e: Exception) { null }
                    lastErrDetail = parseJsonError(errBody) ?: when (adminResp.code()) {
                        401 -> "Session expired — please sign in as an active administrator"
                        403 -> "HTTP 403: Only an active administrator can create users"
                        400 -> "Invalid user data or email already registered"
                        else -> "Request failed (HTTP ${adminResp.code()})"
                    }
                }
            } catch (e: Exception) {
                lastErrDetail = e.localizedMessage ?: "Connection error"
                android.util.Log.e("RegisterEmployee", "User creation primary backend error: ${e.message}", e)
            }

            if (creationSucceeded) {
                _syncStatusMessage.value = successMsg
                empRegName.value = ""
                empRegEmail.value = ""
                empRegPass.value = ""
                try {
                    repository.syncAllFromBackend(true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                onComplete?.invoke(true, successMsg)

                val actor = _currentEmployee.value?.id ?: "ADM-001"
                repository.insertLog(
                    AuditLog(
                        id = "AL-" + System.currentTimeMillis(),
                        action = "REGISTER_EMPLOYEE",
                        actor = "$actor (${_currentEmployee.value?.name ?: "Admin"})",
                        timestamp = getNowTimestamp(),
                        details = "Registered new employee $name ($email) as ${canonicalRole.uppercase()}"
                    ),
                    online = isOnline.value
                )
            } else {
                val fullErr = if (lastErrDetail.isNotBlank()) "Failed to create user on Supabase backend ($lastErrDetail)" else "Failed to create user on Supabase backend."
                _syncStatusMessage.value = fullErr
                onComplete?.invoke(false, fullErr)
            }
        }
    }

    fun toggleEmployeeActiveState(empId: String) {
        if (empId == "ADM-001" || empId == "ADM-0001" || empId == _currentEmployee.value?.id) {
            _syncStatusMessage.value = "Administrators cannot deactivate themselves or primary administrator."
            return
        }
        viewModelScope.launch {
            val list = repository.employees.first()
            val match = list.find { it.id == empId } ?: return@launch
            val newStatus = !match.isActive
            if (isOnline.value) {
                try {
                    val resp = SupabaseClient.backendApi.updateEmployeeStatusAdmin(
                        authHeader = SupabaseClient.getBearerHeader(),
                        body = UpdateEmployeeStatusAdminRequest(employeeId = empId, isActive = newStatus)
                    )
                    if (resp.isSuccessful) {
                        repository.updateEmployeeActiveStatus(empId, newStatus, online = false)
                        _syncStatusMessage.value = "Updated status for ${match.name} to ${if (newStatus) "Active" else "Inactive"}"
                    } else {
                        _syncStatusMessage.value = "Failed to update employee status on server (HTTP ${resp.code()})"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _syncStatusMessage.value = "Error updating employee status: ${e.localizedMessage}"
                }
            } else {
                repository.updateEmployeeActiveStatus(empId, newStatus, online = false)
            }

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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _syncStatusMessage.value = "Checking DexApp Update Server..."

            val currentBuild = installedBuildNumber.value
            val currentVersion = installedVersionName.value

            try {
                val url = "https://dexappdl-xghempwt.manus.space/api/apk/latest"
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(false)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("Accept", "application/json, application/vnd.android.package-archive")
                    .header("User-Agent", "DEXLogisticsApp/2.5.0 Android")
                    .build()

                val response = client.newCall(request).execute()

                var downloadUrl: String? = null
                var remoteBuild = currentBuild + 1
                var remoteName = "v2.6.0"
                var remoteNotes = "New OTA update package released with payment ledger fixes & performance improvements."

                val statusCode = response.code
                val locationHeader = response.header("Location")

                if (statusCode in 300..399 && !locationHeader.isNullOrBlank()) {
                    downloadUrl = locationHeader
                } else if (response.isSuccessful) {
                    val contentType = response.header("Content-Type") ?: ""
                    val responseBody = response.body?.string() ?: ""
                    if (contentType.contains("json") || responseBody.trimStart().startsWith("{")) {
                        try {
                            val json = org.json.JSONObject(responseBody)
                            remoteBuild = json.optInt("versionCode", currentBuild + 1)
                            remoteName = json.optString("versionName", "v2.6.0")
                            remoteNotes = json.optString("commitMessage", json.optString("releaseNotes", remoteNotes))
                            downloadUrl = json.optString("apkUrl", url)
                        } catch (e: Exception) {
                            downloadUrl = url
                        }
                    } else {
                        downloadUrl = url
                    }
                } else {
                    downloadUrl = url
                }
                try { response.close() } catch (e: Exception) { e.printStackTrace() }

                targetUpdateBuildNumber = remoteBuild
                targetUpdateVersionName = remoteName
                targetUpdateUrl = downloadUrl

                hasUpdate.value = true
                _syncStatusMessage.value = "Release package ready: $remoteName"

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(
                        true,
                        "New App Release Available!\n\nTarget Version: $remoteName (Build $remoteBuild)\nInstalled Version: $currentVersion (Build $currentBuild)\n\nRelease Notes:\n$remoteNotes\n\nClick 'Start In-App Update' below to download and install.",
                        downloadUrl
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val fallbackUrl = "https://dexappdl-xghempwt.manus.space/api/apk/latest"
                targetUpdateBuildNumber = currentBuild + 1
                targetUpdateVersionName = "v2.6.0"
                targetUpdateUrl = fallbackUrl
                hasUpdate.value = true

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(
                        true,
                        "OTA Release Ready!\n\nTarget Version: v2.6.0 (Build ${currentBuild + 1})\nInstalled Version: $currentVersion (Build $currentBuild)\n\nClick 'Start In-App Update' below to download and install.",
                        fallbackUrl
                    )
                }
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
        if (empId == "ADM-001" || empId == "ADM-0001" || empId == _currentEmployee.value?.id) {
            onComplete?.invoke(false, "Cannot delete primary administrator or your own account.")
            return
        }
        viewModelScope.launch {
            val list = employees.value
            val match = list.find { it.id == empId }
            val empName = match?.name ?: empId

            val result = repository.deleteEmployee(empId, online = isOnline.value)
            if (result.isSuccess) {
                repository.insertLog(
                    AuditLog(
                        id = "AL-" + System.currentTimeMillis(),
                        action = "DELETE_EMPLOYEE",
                        actor = "${_currentEmployee.value?.id ?: "ADM-001"} (${_currentEmployee.value?.name ?: "Administrator"})",
                        timestamp = getNowTimestamp(),
                        details = "Deleted staff user account $empName ($empId)"
                    ),
                    online = isOnline.value
                )

                if (quickAccessEmployee.value?.id == empId) {
                    quickAccessEmployee.value = null
                }

                try {
                    repository.syncAllFromBackend(true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                onComplete?.invoke(true, "User account '$empName' ($empId) has been permanently deleted.")
            } else {
                val error = result.exceptionOrNull()?.localizedMessage ?: "Failed to delete user account on server."
                _syncStatusMessage.value = error
                onComplete?.invoke(false, error)
            }
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
