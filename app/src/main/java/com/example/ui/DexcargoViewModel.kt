package com.example.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.gemini.GeminiOcrHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
}

class DexcargoViewModel(private val repository: DexcargoRepository) : ViewModel() {

    // --- NAVIGATION ---
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Login)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val navigationStack = mutableListOf<Screen>()

    fun navigateTo(screen: Screen) {
        navigationStack.add(_currentScreen.value)
        _currentScreen.value = screen
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

    val isOnline = MutableStateFlow(true)
    private val _syncStatusMessage = MutableStateFlow("")
    val syncStatusMessage: StateFlow<String> = _syncStatusMessage.asStateFlow()

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
                unsyncedList.forEach { pkg ->
                    val syncedPkg = pkg.copy(syncPending = false)
                    repository.insertPackage(syncedPkg)
                    
                    val actor = currentEmployee.value?.id ?: "System"
                    repository.insertLog(
                        AuditLog(
                            id = "AL-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().take(4),
                            action = "SYNC_OFFLINE_CARGO",
                            actor = "$actor (${currentEmployee.value?.name ?: "Agent"})",
                            timestamp = getNowTimestamp(),
                            details = "Automatically synced package ${pkg.id} (${pkg.consignee}) from local offline storage to cloud servers."
                        )
                    )
                }
                _syncStatusMessage.value = "Auto-Synced ${unsyncedList.size} offline package(s) successfully!"
                delay(4000)
                _syncStatusMessage.value = ""
            }
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

    // STK push timer state
    val stkCountdown = MutableStateFlow(4)
    val stkPhoneNumber = MutableStateFlow("")

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

    // Upload payload form
    val mockImageSelect = MutableStateFlow("mpesa_mock_1.png")
    val mockTextContent = MutableStateFlow("")

    init {
        // Automatically pre-populate default database records if they are empty
        viewModelScope.launch {
            repository.employees.first().let { list ->
                if (list.isEmpty()) {
                    repository.resetDatabaseToDefaults()
                }
            }
        }

        // Quick access detection on startup
        viewModelScope.launch {
            repository.employees.collect { list ->
                if (list.isNotEmpty() && quickAccessEmployee.value == null && _currentScreen.value == Screen.Login) {
                    val activeWithPin = list.find { !it.pin.isNullOrEmpty() }
                    if (activeWithPin != null) {
                        quickAccessEmployee.value = activeWithPin
                        _currentScreen.value = Screen.EnterPin
                    }
                }
            }
        }
    }

    // --- CORE OPERATIONS ---

    fun login(email: String, pass: String): Boolean {
        var success = false
        viewModelScope.launch {
            val empList = repository.employees.first()
            val match = empList.find {
                (it.email.equals(email, ignoreCase = true) || it.id.equals(email, ignoreCase = true)) &&
                        it.password == pass
            }
            if (match != null) {
                if (match.isActive) {
                    _currentEmployee.value = match
                    success = true
                    if (match.pin.isNullOrEmpty()) {
                        pinSetupFirst.value = ""
                        pinSetupSecond.value = ""
                        pinErrorMessage.value = ""
                        biometricOptionEnabled.value = false
                        navigateTo(Screen.SetPin)
                    } else {
                        routeToUserHome()
                    }
                }
            }
        }
        return success
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
            repository.updateEmployeePinAndBiometrics(emp.id, pin1, biometricOptionEnabled.value)
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
            repository.updateEmployeePinAndBiometrics(emp.id, newPin, newBiometrics)
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
        _currentEmployee.value = null
        navigationStack.clear()
        viewModelScope.launch {
            val list = repository.employees.first()
            val activeWithPin = list.find { !it.pin.isNullOrEmpty() }
            if (activeWithPin != null) {
                quickAccessEmployee.value = activeWithPin
                _currentScreen.value = Screen.EnterPin
            } else {
                _currentScreen.value = Screen.Login
            }
        }
    }

    fun resetDemoData() {
        viewModelScope.launch {
            repository.resetDatabaseToDefaults()
            _currentEmployee.value = null
            quickAccessEmployee.value = null
            navigationStack.clear()
            _currentScreen.value = Screen.Login
        }
    }

    fun triggerOcrScan(customBitmap: android.graphics.Bitmap? = null, onFinish: () -> Unit = {}) {
        isPackagePhotoCaptured.value = customBitmap != null
        capturedPhotoUrl.value = if (customBitmap != null) "captured_camera_uri" else ""
        navigateTo(Screen.OcrProcessing)

        viewModelScope.launch {
            val labelId = selectedLabelId.value
            val bitmap = customBitmap ?: GeminiOcrHelper.generateStickerBitmap(labelId)
            val extracted = GeminiOcrHelper.extractStickerData(bitmap, labelId)

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

    fun savePackageRegistry() {
        val actor = currentEmployee.value?.id ?: "System"
        val roleLabel = when (currentEmployee.value?.role) {
            "sr" -> "SR-002 John Kamau"
            else -> "Sales Manager Direct"
        }

        val online = isOnline.value
        val pkg = CargoPackage(
            id = revId.value,
            consignee = revName.value,
            phone = revPhone.value,
            origin = revOrigin.value,
            dest = revDest.value,
            desc = revDesc.value,
            mode = revMode.value,
            weight = revWeight.value.toDoubleOrNull() ?: 1.0,
            pcs = revPcs.value.toIntOrNull() ?: 1,
            cost = revCost.value.toIntOrNull() ?: 3000,
            salesRep = roleLabel,
            status = "registered",
            registeredAt = getNowTimestamp(),
            packagePhotoUrl = capturedPhotoUrl.value,
            packagePhotoCapturedAt = getNowTimestamp(),
            packagePhotoCapturedBy = "$actor (${currentEmployee.value?.name ?: "Agent"})",
            syncPending = !online
        )

        viewModelScope.launch {
            repository.insertPackage(pkg)
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
                )
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
        val actor = currentEmployee.value?.id ?: "System"
        val roleLabel = when (currentEmployee.value?.role) {
            "sr" -> "SR-002 John Kamau"
            else -> "Sales Manager Direct"
        }

        val routeVal = mformRoute.value
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

    fun simulateMpesaStk(phone: String) {
        stkPhoneNumber.value = phone
        navigateTo(Screen.StkWait)
        viewModelScope.launch {
            stkCountdown.value = 4
            while (stkCountdown.value > 0) {
                delay(1000)
                stkCountdown.value--
            }
            confirmPayment("M-Pesa")
        }
    }

    fun submitCashPayment() {
        confirmPayment("Cash")
    }

    private fun confirmPayment(method: String) {
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
                repository.insertPackage(updated)
                repository.insertLog(
                    AuditLog(
                        id = "AL-" + System.currentTimeMillis(),
                        action = "PAYMENT_CONFIRMED",
                        actor = "$actor (${currentEmployee.value?.name})",
                        timestamp = getNowTimestamp(),
                        details = "Confirmed payment of KES ${pkg.cost} for ${pkg.id} via $method (Ref: $ref)"
                    )
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
                repository.insertPackage(updated)
                repository.insertLog(
                    AuditLog(
                        id = "AL-" + System.currentTimeMillis(),
                        action = "CARGO_DELIVERED",
                        actor = "$actor (${currentEmployee.value?.name})",
                        timestamp = getNowTimestamp(),
                        details = "Handed over cargo ${pkg.id} to ${updated.collectorName} (ID: ${updated.collectorId})"
                    )
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

        val notif = PaymentNotification(
            id = "PN-" + System.currentTimeMillis(),
            notificationNumber = notifNumber,
            evidenceType = activeUploadType.value,
            imageUrl = if (isImage) mockImageSelect.value else null,
            textContent = mockTextContent.value,
            uploadedBy = "$actor (${currentEmployee.value?.name})",
            uploadedAt = getNowTimestamp(),
            status = "PENDING"
        )

        viewModelScope.launch {
            repository.insertNotification(notif)
            repository.insertLog(
                AuditLog(
                    id = "AL-" + System.currentTimeMillis(),
                    action = "UPLOAD_PAYMENT_EVIDENCE",
                    actor = "$actor (${currentEmployee.value?.name})",
                    timestamp = getNowTimestamp(),
                    details = "Uploaded $activeUploadType payment evidence for $notifNumber"
                )
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
                repository.insertAllocation(alloc)

                // Update package to paid status
                val originalPkg = repository.getPackageById(linkPkg.id)
                if (originalPkg != null) {
                    val updated = originalPkg.copy(
                        status = "paid",
                        paidAt = getNowTimestamp(),
                        paymentMethod = "Linked Reference",
                        paymentRef = notif.notificationNumber
                    )
                    repository.insertPackage(updated)
                }

                repository.insertLog(
                    AuditLog(
                        id = "AL-" + System.currentTimeMillis() + "-" + Random().nextInt(100),
                        action = "LINK_PAYMENT_EVIDENCE",
                        actor = "$actor (${currentEmployee.value?.name})",
                        timestamp = getNowTimestamp(),
                        details = "Linked PAY-Evidence ${notif.notificationNumber} to ${linkPkg.id}"
                    )
                )
            }

            repository.updateNotificationStatus(notif.id, "LINKED")
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
            createdAt = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        )

        viewModelScope.launch {
            repository.insertMessage(message)
            repository.insertLog(
                AuditLog(
                    id = "AL-" + System.currentTimeMillis(),
                    action = "DISPATCH_BROADCAST",
                    actor = "$actor (${currentEmployee.value?.name})",
                    timestamp = getNowTimestamp(),
                    details = "Dispatched broadcast message: '${message.message}' to ${message.target}"
                )
            )
            broadcastText.value = ""
        }
    }

    fun registerNewEmployee() {
        if (empRegName.value.isBlank() || empRegEmail.value.isBlank() || empRegPass.value.isBlank()) return
        val newId = empRegRole.value.uppercase() + "-" + (100 + Random().nextInt(900))
        val newEmp = Employee(
            id = newId,
            name = empRegName.value,
            email = empRegEmail.value,
            password = empRegPass.value,
            role = empRegRole.value,
            isActive = true
        )

        viewModelScope.launch {
            repository.insertEmployee(newEmp)
            repository.insertLog(
                AuditLog(
                    id = "AL-" + System.currentTimeMillis(),
                    action = "REGISTER_EMPLOYEE",
                    actor = "ADM-001 (Administrator)",
                    timestamp = getNowTimestamp(),
                    details = "Registered new employee ${newEmp.name} (${newEmp.id}) as ${newEmp.role.uppercase()}"
                )
            )
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
            repository.updateEmployeeActiveStatus(empId, newStatus)
            repository.insertLog(
                AuditLog(
                    id = "AL-" + System.currentTimeMillis(),
                    action = "TOGGLE_EMPLOYEE_STATUS",
                    actor = "ADM-001 (Administrator)",
                    timestamp = getNowTimestamp(),
                    details = "Toggled active state of employee ${match.name} (${match.id}) to $newStatus"
                )
            )
        }
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

class DexcargoViewModelFactory(private val repository: DexcargoRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DexcargoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DexcargoViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
