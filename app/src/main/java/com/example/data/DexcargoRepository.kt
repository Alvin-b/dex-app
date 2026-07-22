package com.example.data

import com.example.data.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody

class DexcargoRepository(private val database: AppDatabase) {

    val employees: Flow<List<Employee>> = database.employeeDao().getAllEmployees()
    val cargoPackages: Flow<List<CargoPackage>> = database.cargoPackageDao().getAllPackages()
    val paymentNotifications: Flow<List<PaymentNotification>> = database.paymentNotificationDao().getAllNotifications()
    val paymentAllocations: Flow<List<PaymentAllocation>> = database.paymentAllocationDao().getAllAllocations()
    val auditLogs: Flow<List<AuditLog>> = database.auditLogDao().getAllLogs()
    val broadcastMessages: Flow<List<BroadcastMessage>> = database.broadcastMessageDao().getAllMessages()

    suspend fun getEmployeeById(id: String): Employee? = database.employeeDao().getEmployeeById(id)
    
    suspend fun insertEmployee(employee: Employee) = database.employeeDao().insertEmployee(employee)
    
    suspend fun updateEmployeeActiveStatus(id: String, isActive: Boolean, online: Boolean = false) {
        database.employeeDao().updateEmployeeActiveStatus(id, isActive)
        if (online && SupabaseClient.accessToken != null) {
            try {
                SupabaseClient.api.updateProfile(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = SupabaseClient.getBearerHeader(),
                    idFilter = "eq.$id",
                    profile = ProfileUpdate(isActive = isActive)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    suspend fun updateEmployeePinAndBiometrics(id: String, pin: String?, biometricEnabled: Boolean, online: Boolean = false) {
        database.employeeDao().updateEmployeePinAndBiometrics(id, pin, biometricEnabled)
        if (online && SupabaseClient.accessToken != null) {
            try {
                SupabaseClient.api.updateProfile(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = SupabaseClient.getBearerHeader(),
                    idFilter = "eq.$id",
                    profile = ProfileUpdate(pinHash = pin, biometricEnabled = biometricEnabled)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getPackageById(id: String): CargoPackage? = database.cargoPackageDao().getPackageById(id)
    
    suspend fun insertPackage(cargoPackage: CargoPackage, online: Boolean = false) {
        database.cargoPackageDao().insertPackage(cargoPackage)
        if (online && SupabaseClient.accessToken != null) {
            try {
                SupabaseClient.api.insertCargoPackage(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = SupabaseClient.getBearerHeader(),
                    body = cargoPackage.toApi()
                )
                // If successfully written to server, clear sync pending
                database.cargoPackageDao().insertPackage(cargoPackage.copy(syncPending = false))
            } catch (e: Exception) {
                e.printStackTrace()
                database.cargoPackageDao().insertPackage(cargoPackage.copy(syncPending = true))
            }
        } else if (!online) {
            database.cargoPackageDao().insertPackage(cargoPackage.copy(syncPending = true))
        }
    }

    suspend fun insertNotification(notification: PaymentNotification, online: Boolean = false) {
        database.paymentNotificationDao().insertNotification(notification)
        if (online && SupabaseClient.accessToken != null) {
            try {
                SupabaseClient.api.insertPaymentNotification(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = SupabaseClient.getBearerHeader(),
                    body = notification.toApi()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    suspend fun updateNotificationStatus(id: String, status: String, online: Boolean = false) {
        database.paymentNotificationDao().updateNotificationStatus(id, status)
        if (online && SupabaseClient.accessToken != null) {
            try {
                SupabaseClient.api.updatePaymentNotification(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = SupabaseClient.getBearerHeader(),
                    idFilter = "eq.$id",
                    body = mapOf("status" to status)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getAllAllocationsForPackage(orderId: String): Flow<List<PaymentAllocation>> = database.paymentAllocationDao().getAllAllocationsForPackage(orderId)
    
    suspend fun insertAllocation(allocation: PaymentAllocation, online: Boolean = false) {
        database.paymentAllocationDao().insertAllocation(allocation)
        if (online && SupabaseClient.accessToken != null) {
            try {
                SupabaseClient.api.insertPaymentAllocation(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = SupabaseClient.getBearerHeader(),
                    body = allocation.toApi()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun insertLog(log: AuditLog, online: Boolean = false) {
        database.auditLogDao().insertLog(log)
        if (online && SupabaseClient.accessToken != null) {
            try {
                SupabaseClient.api.insertAuditLog(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = SupabaseClient.getBearerHeader(),
                    body = log.toApi()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun insertMessage(message: BroadcastMessage, online: Boolean = false) {
        database.broadcastMessageDao().insertMessage(message)
        if (online && SupabaseClient.accessToken != null) {
            try {
                SupabaseClient.api.insertBroadcastMessage(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = SupabaseClient.getBearerHeader(),
                    body = message.toApi()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun uploadPhoto(packageId: String, filename: String, bytes: ByteArray, online: Boolean): String? {
        if (online && SupabaseClient.accessToken != null) {
            try {
                val reqBody = RequestBody.create(
                    "image/jpeg".toMediaTypeOrNull(),
                    bytes
                )
                val response = SupabaseClient.api.uploadPackagePhoto(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = SupabaseClient.getBearerHeader(),
                    packageId = packageId,
                    filename = filename,
                    photoBytes = reqBody
                )
                if (response.isSuccessful) {
                    return "package-photos/$packageId/$filename"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    suspend fun uploadProofPhoto(filename: String, bytes: ByteArray, online: Boolean): String? {
        if (online && SupabaseClient.accessToken != null) {
            try {
                val reqBody = RequestBody.create(
                    "image/jpeg".toMediaTypeOrNull(),
                    bytes
                )
                val response = SupabaseClient.api.uploadProofPhoto(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = SupabaseClient.getBearerHeader(),
                    filename = filename,
                    photoBytes = reqBody
                )
                if (response.isSuccessful) {
                    return "proofs/$filename"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    suspend fun uploadSignaturePhoto(filename: String, bytes: ByteArray, online: Boolean): String? {
        if (online && SupabaseClient.accessToken != null) {
            try {
                val reqBody = RequestBody.create(
                    "image/png".toMediaTypeOrNull(),
                    bytes
                )
                val response = SupabaseClient.api.uploadSignaturePhoto(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = SupabaseClient.getBearerHeader(),
                    filename = filename,
                    photoBytes = reqBody
                )
                if (response.isSuccessful) {
                    return "signatures/$filename"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    suspend fun downloadPhoto(packageId: String, filename: String): ByteArray? {
        if (SupabaseClient.accessToken == null) return null
        return try {
            val responseBody = SupabaseClient.api.downloadPackagePhoto(
                apiKey = SupabaseClient.API_KEY,
                authHeader = SupabaseClient.getBearerHeader(),
                packageId = packageId,
                filename = filename
            )
            responseBody.bytes()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- COMMISSION OPERATIONS (SECTION 8) ---

    suspend fun getCommissionsFromBackend(employeeId: String? = null, status: String? = null): List<CommissionApi> {
        if (SupabaseClient.accessToken == null) return emptyList()
        return try {
            val filterEmp = employeeId?.let { "eq.$it" }
            val filterStatus = status?.let { "eq.$it" }
            SupabaseClient.api.getCommissions(
                apiKey = SupabaseClient.API_KEY,
                authHeader = SupabaseClient.getBearerHeader(),
                employeeFilter = filterEmp,
                statusFilter = filterStatus
            )
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun approveCommissionOnBackend(id: String): Boolean {
        if (SupabaseClient.accessToken == null) return false
        return try {
            val response = SupabaseClient.api.approveCommission(
                apiKey = SupabaseClient.API_KEY,
                authHeader = SupabaseClient.getBearerHeader(),
                body = mapOf("_id" to id)
            )
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun markCommissionPaidOnBackend(id: String, reference: String): Boolean {
        if (SupabaseClient.accessToken == null) return false
        return try {
            val response = SupabaseClient.api.markCommissionPaid(
                apiKey = SupabaseClient.API_KEY,
                authHeader = SupabaseClient.getBearerHeader(),
                body = mapOf("_id" to id, "_reference" to reference)
            )
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getCommissionRatesFromBackend(): List<CommissionRateApi> {
        if (SupabaseClient.accessToken == null) return emptyList()
        return try {
            SupabaseClient.api.getCommissionRates(
                apiKey = SupabaseClient.API_KEY,
                authHeader = SupabaseClient.getBearerHeader()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // --- SYNC FROM BACKEND LOGIC ---

    suspend fun syncAllFromBackend(online: Boolean) {
        if (!online || SupabaseClient.accessToken == null) return
        
        val apiKey = SupabaseClient.API_KEY
        val authHeader = SupabaseClient.getBearerHeader()

        // 1. Sync profiles & roles -> Employees
        try {
            val profiles = SupabaseClient.api.getAllProfiles(apiKey, authHeader)
            val allRoles = SupabaseClient.api.getAllUserRoles(apiKey, authHeader)
            val rolesMap = allRoles.associateBy { it.userId }

            val employeeList = profiles.map { profile ->
                val rawRole = rolesMap[profile.id]?.role ?: "sr"
                val role = if (rawRole == "clerk") "lm" else rawRole
                Employee(
                    id = profile.id,
                    name = profile.name,
                    email = profile.email,
                    password = "password",
                    role = role,
                    isActive = profile.isActive,
                    pin = profile.pinHash,
                    biometricEnabled = profile.biometricEnabled
                )
            }
            if (employeeList.isNotEmpty()) {
                database.employeeDao().insertEmployees(employeeList)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Sync cargo_packages
        try {
            val packagesApi = SupabaseClient.api.getCargoPackages(apiKey, authHeader)
            val packagesEntities = packagesApi.map { it.toEntity(syncPending = false) }
            if (packagesEntities.isNotEmpty()) {
                val allLocal = database.cargoPackageDao().getAllPackages().firstOrNull() ?: emptyList()
                val pendingIds = allLocal.filter { it.syncPending }.map { it.id }.toSet()
                val toInsert = packagesEntities.filter { it.id !in pendingIds }
                if (toInsert.isNotEmpty()) {
                    database.cargoPackageDao().insertPackages(toInsert)
                }

                // Asynchronously check and cache package photos starting with package-photos/ for offline access
                val allUpdatedLocal = database.cargoPackageDao().getAllPackages().firstOrNull() ?: emptyList()
                allUpdatedLocal.forEach { pkg ->
                    if (!pkg.packagePhotoUrl.isNullOrBlank() &&
                        !pkg.packagePhotoUrl.startsWith("base64:") &&
                        pkg.packagePhotoUrl != "simulated_url"
                    ) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val parts = pkg.packagePhotoUrl.split("/")
                                if (parts.size >= 3) {
                                    val packageId = parts[1]
                                    val filename = parts[2]
                                    val bytes = downloadPhoto(packageId, filename)
                                    if (bytes != null) {
                                        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                                        val updatedPkg = pkg.copy(packagePhotoUrl = "base64:$b64")
                                        database.cargoPackageDao().insertPackage(updatedPkg)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Sync payment_notifications
        try {
            val notificationsApi = SupabaseClient.api.getPaymentNotifications(apiKey, authHeader)
            val notificationsEntities = notificationsApi.map { it.toEntity() }
            if (notificationsEntities.isNotEmpty()) {
                database.paymentNotificationDao().insertNotifications(notificationsEntities)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Sync payment_allocations
        try {
            val allocationsApi = SupabaseClient.api.getPaymentAllocations(apiKey, authHeader)
            val allocationsEntities = allocationsApi.map { it.toEntity() }
            if (allocationsEntities.isNotEmpty()) {
                database.paymentAllocationDao().insertAllocations(allocationsEntities)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 5. Sync audit_logs
        try {
            val logsApi = SupabaseClient.api.getAuditLogs(apiKey, authHeader)
            val logsEntities = logsApi.map { it.toEntity() }
            if (logsEntities.isNotEmpty()) {
                database.auditLogDao().insertLogs(logsEntities)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 6. Sync broadcast_messages
        try {
            val messagesApi = SupabaseClient.api.getBroadcastMessages(apiKey, authHeader)
            val messagesEntities = messagesApi.map { it.toEntity() }
            if (messagesEntities.isNotEmpty()) {
                database.broadcastMessageDao().insertMessages(messagesEntities)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun resetDatabaseToDefaults() {
        database.cargoPackageDao().clearAllPackages()
        database.paymentNotificationDao().clearAllNotifications()
        database.paymentAllocationDao().clearAllAllocations()
        database.auditLogDao().clearAllLogs()
        database.broadcastMessageDao().clearAllMessages()

        // Populate baseline Employees
        val defaultEmployees = listOf(
            Employee("SR-002", "John Kamau", "john@dexcargo.com", "password", "sr", true),
            Employee("LM-001", "Mary Wanjiku", "mary@dexcargo.com", "password", "lm", true),
            Employee("SM-001", "Peter Mwangi", "peter@dexcargo.com", "password", "sm", true),
            Employee("ADM-001", "Administrator User", "admin@dexcargo.com", "password", "admin", true)
        )
        database.employeeDao().insertEmployees(defaultEmployees)

        // Populate baseline Packages
        val defaultPackages = listOf(
            CargoPackage(
                id = "1260707534975",
                consignee = "David Ochieng",
                phone = "0700112233",
                origin = "Nairobi (NBO)",
                dest = "Kigali (KGL)",
                desc = "Refurbished Laptops",
                mode = "Air Freight",
                weight = 2.4,
                pcs = 2,
                cost = 5600,
                salesRep = "John Kamau",
                status = "collected",
                registeredAt = "2026-07-10 10:24 AM",
                paidAt = "2026-07-10 11:15 AM",
                collectedAt = "2026-07-11 02:40 PM",
                collectorName = "David Ochieng",
                collectorId = "ID-29402941",
                collectorPhone = "0700112233",
                paymentMethod = "M-Pesa",
                paymentRef = "QM5A8J2K8F"
            ),
            CargoPackage(
                id = "1260707534982",
                consignee = "Mary Wanjiku",
                phone = "0711223344",
                origin = "Guangzhou (CAN)",
                dest = "Nairobi (NBO)",
                desc = "Salon Equipment & Driers",
                mode = "Sea Freight",
                weight = 12.0,
                pcs = 3,
                cost = 3800,
                salesRep = "John Kamau",
                status = "paid",
                registeredAt = "2026-07-11 09:12 AM",
                paidAt = "2026-07-11 04:30 PM",
                paymentMethod = "M-Pesa",
                paymentRef = "QM6L4B9X2D"
            ),
            CargoPackage(
                id = "1260707534988",
                consignee = "John Doe",
                phone = "0722334455",
                origin = "Hong Kong (HKG)",
                dest = "Nairobi (NBO)",
                desc = "Audio Monitors",
                mode = "Air Freight",
                weight = 5.6,
                pcs = 1,
                cost = 4200,
                salesRep = "Charles Ombongi",
                status = "paid",
                registeredAt = "2026-07-12 08:14 AM",
                paidAt = "2026-07-12 09:00 AM",
                paymentMethod = "Cash",
                paymentRef = "CASH-72648"
            ),
            CargoPackage(
                id = "1260707534922",
                consignee = "Brian Otieno",
                phone = "0715667788",
                origin = "Shanghai (SHA)",
                dest = "Nairobi (NBO)",
                desc = "Spare Machinery Parts",
                mode = "Air Freight",
                weight = 3.2,
                pcs = 1,
                cost = 2800,
                salesRep = "Grace Akinyi",
                status = "registered",
                registeredAt = "2026-07-12 09:30 AM"
            )
        )
        database.cargoPackageDao().insertPackages(defaultPackages)

        // Populate baseline Payment Notifications
        val defaultNotifications = listOf(
            PaymentNotification(
                id = "PN-1",
                notificationNumber = "PAY-20260714-0001",
                evidenceType = "IMAGE",
                imageUrl = "mpesa_mock_1.png",
                uploadedBy = "ADM-001 (Admin)",
                uploadedAt = "2026-07-14 10:15 AM",
                status = "PENDING",
                amount = 5600,
                senderPhone = "0711223344",
                timestamp = "2026-07-14 10:15 AM"
            ),
            PaymentNotification(
                id = "PN-2",
                notificationNumber = "PAY-20260714-0002",
                evidenceType = "TEXT",
                textContent = "M-PESA CONFIRMATION:\nTxn: QNA8B7J3D8\nAmount: KES 1,400\nFrom: Charles Ombongi\nRef: Package CAN-NBO payment confirmation.",
                uploadedBy = "ADM-001 (Admin)",
                uploadedAt = "2026-07-14 11:20 AM",
                status = "PENDING",
                amount = 1400,
                senderPhone = "0722334455",
                timestamp = "2026-07-14 11:20 AM"
            ),
            PaymentNotification(
                id = "PN-3",
                notificationNumber = "PAY-20260710-0001",
                evidenceType = "IMAGE",
                imageUrl = "bank_transfer.jpg",
                uploadedBy = "ADM-001 (Admin)",
                uploadedAt = "2026-07-10 11:00 AM",
                status = "LINKED",
                amount = 5600,
                senderPhone = "0733445566",
                timestamp = "2026-07-10 11:00 AM"
            )
        )
        database.paymentNotificationDao().insertNotifications(defaultNotifications)

        // Populate baseline Allocations
        val defaultAllocations = listOf(
            PaymentAllocation(
                id = "PA-1",
                paymentNotificationId = "PN-3",
                orderId = "1260707534975",
                trackingNumber = "1260707534975",
                allocatedAmount = 5600,
                linkedBy = "LM-001 (Mary Wanjiku)",
                linkedAt = "2026-07-10 11:15 AM",
                notificationNumber = "PAY-20260710-0001"
            )
        )
        database.paymentAllocationDao().insertAllocations(defaultAllocations)

        // Populate baseline Audit Logs
        val defaultAuditLogs = listOf(
            AuditLog("AL-1", "CREATE_PAYMENT_NOTIFICATION", "ADM-001 (Admin)", "2026-07-14 10:15 AM", "Uploaded image payment evidence for PAY-20260714-0001"),
            AuditLog("AL-2", "CREATE_PAYMENT_NOTIFICATION", "ADM-001 (Admin)", "2026-07-14 11:20 AM", "Uploaded text notes evidence for PAY-20260714-0002"),
            AuditLog("AL-3", "CREATE_PAYMENT_NOTIFICATION", "ADM-001 (Admin)", "2026-07-10 11:00 AM", "Uploaded image payment evidence for PAY-20260710-0001"),
            AuditLog("AL-4", "LINK_PAYMENT_NOTIFICATION", "LM-001 (Mary Wanjiku)", "2026-07-10 11:15 AM", "Linked PAY-20260710-0001 to 1260707534975, Allocated KES 5,600")
        )
        database.auditLogDao().insertLogs(defaultAuditLogs)

        // Populate baseline Broadcast Alerts
        val defaultBroadcasts = listOf(
            BroadcastMessage("BM-1", "Welcome to DEXCARGO Logistics Gateway. Secure credentials protocol active.", "all", "Just now", "System Administrator", "2026-07-19 12:00 PM")
        )
        database.broadcastMessageDao().insertMessages(defaultBroadcasts)
    }
}
