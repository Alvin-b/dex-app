package com.example.data

import android.util.Log
import com.example.data.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody

class DexcargoRepository(private val database: AppDatabase) {

    val adminRepository = AdminRepository(database)

    init {
        initFirestoreRealtimeSync()
    }

    private fun initFirestoreRealtimeSync() {
        try {
            val firestore = try {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
            } catch (t: Throwable) {
                Log.w("DexcargoRepository", "FirebaseApp not initialized for real-time sync: ${t.message}")
                null
            } ?: return

            // Real-time listener for users / employees
            firestore.collection("users").addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                CoroutineScope(Dispatchers.IO).launch {
                    val firestoreEmployees = snapshot.documents.mapNotNull { doc ->
                        val id = doc.id
                        val name = doc.getString("name") ?: doc.getString("email") ?: id
                        val email = doc.getString("email") ?: ""
                        val role = doc.getString("role") ?: "sr"
                        val isActive = doc.getBoolean("isActive") ?: true
                        val pin = doc.getString("pin")
                        val biometricEnabled = doc.getBoolean("biometricEnabled") ?: false
                        val password = doc.getString("password") ?: "password"
                        if (email.isNotBlank()) {
                            Employee(id, name, email, password, role, isActive, pin, biometricEnabled)
                        } else null
                    }
                    if (firestoreEmployees.isNotEmpty()) {
                        database.employeeDao().insertEmployees(firestoreEmployees)
                    }
                }
            }

            // Real-time listener for cargo_packages
            firestore.collection("cargo_packages").addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                CoroutineScope(Dispatchers.IO).launch {
                    val firestorePackages = snapshot.documents.mapNotNull { doc ->
                        val id = doc.id
                        val consignee = doc.getString("consignee") ?: return@mapNotNull null
                        val phone = doc.getString("phone") ?: ""
                        val origin = doc.getString("origin") ?: ""
                        val dest = doc.getString("dest") ?: doc.getString("destination") ?: ""
                        val desc = doc.getString("desc") ?: doc.getString("description") ?: ""
                        val mode = doc.getString("mode") ?: "Air Freight"
                        val weight = doc.getDouble("weight") ?: 0.0
                        val pcs = doc.getLong("pcs")?.toInt() ?: 1
                        val cost = doc.getLong("cost")?.toInt() ?: 0
                        val salesRep = doc.getString("salesRep") ?: ""
                        val status = doc.getString("status") ?: "registered"
                        val registeredAt = doc.getString("registeredAt") ?: ""
                        val paidAt = doc.getString("paidAt")
                        val collectedAt = doc.getString("collectedAt")
                        val collectorName = doc.getString("collectorName")
                        val collectorId = doc.getString("collectorId")
                        val collectorPhone = doc.getString("collectorPhone")
                        val paymentMethod = doc.getString("paymentMethod")
                        val paymentRef = doc.getString("paymentRef")
                        val packagePhotoUrl = doc.getString("packagePhotoUrl")

                        CargoPackage(
                            id = id,
                            consignee = consignee,
                            phone = phone,
                            origin = origin,
                            dest = dest,
                            desc = desc,
                            mode = mode,
                            weight = weight,
                            pcs = pcs,
                            cost = cost,
                            salesRep = salesRep,
                            status = status,
                            registeredAt = registeredAt,
                            paidAt = paidAt,
                            collectedAt = collectedAt,
                            collectorName = collectorName,
                            collectorId = collectorId,
                            collectorPhone = collectorPhone,
                            paymentMethod = paymentMethod,
                            paymentRef = paymentRef,
                            packagePhotoUrl = packagePhotoUrl,
                            syncPending = false
                        )
                    }
                    if (firestorePackages.isNotEmpty()) {
                        val localMap = database.cargoPackageDao().getAllPackages().firstOrNull()?.associateBy { it.id } ?: emptyMap()
                        val merged = firestorePackages.map { incoming ->
                            val local = localMap[incoming.id]
                            if (local != null && !local.packagePhotoUrl.isNullOrBlank() && local.packagePhotoUrl.startsWith("base64:") &&
                                (incoming.packagePhotoUrl.isNullOrBlank() || !incoming.packagePhotoUrl.startsWith("base64:"))
                            ) {
                                incoming.copy(packagePhotoUrl = local.packagePhotoUrl)
                            } else {
                                incoming
                            }
                        }
                        database.cargoPackageDao().insertPackages(merged)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val employees: Flow<List<Employee>> = database.employeeDao().getAllEmployees()
    val cargoPackages: Flow<List<CargoPackage>> = database.cargoPackageDao().getAllPackages()
    val paymentNotifications: Flow<List<PaymentNotification>> = database.paymentNotificationDao().getAllNotifications()
    val paymentAllocations: Flow<List<PaymentAllocation>> = database.paymentAllocationDao().getAllAllocations()
    val auditLogs: Flow<List<AuditLog>> = database.auditLogDao().getAllLogs()
    val broadcastMessages: Flow<List<BroadcastMessage>> = database.broadcastMessageDao().getAllMessages()

    suspend fun getEmployeeById(id: String): Employee? = database.employeeDao().getEmployeeById(id)
    
    suspend fun insertEmployee(employee: Employee, online: Boolean = false) {
        adminRepository.saveUserToFirestoreAndDatabase(employee, online)
    }

    suspend fun updateEmployeeActiveStatus(id: String, isActive: Boolean, online: Boolean = false) {
        database.employeeDao().updateEmployeeActiveStatus(id, isActive)
        if (online) {
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
        if (online) {
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

    suspend fun deleteEmployee(id: String, online: Boolean = true): Result<Boolean> {
        return adminRepository.deleteUser(id, online)
    }

    suspend fun getPackageById(id: String): CargoPackage? = database.cargoPackageDao().getPackageById(id)
    
    suspend fun insertPackage(cargoPackage: CargoPackage, online: Boolean = false) {
        val existing = database.cargoPackageDao().getPackageById(cargoPackage.id)
        val pkgToSave = if (existing != null && !existing.packagePhotoUrl.isNullOrBlank() && existing.packagePhotoUrl.startsWith("base64:") &&
            (cargoPackage.packagePhotoUrl.isNullOrBlank() || !cargoPackage.packagePhotoUrl.startsWith("base64:"))
        ) {
            cargoPackage.copy(packagePhotoUrl = existing.packagePhotoUrl)
        } else {
            cargoPackage
        }
        database.cargoPackageDao().insertPackage(pkgToSave)

        // Save to Firestore real-time collection
        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val fsMap = mapOf(
                "id" to pkgToSave.id,
                "consignee" to pkgToSave.consignee,
                "phone" to pkgToSave.phone,
                "origin" to pkgToSave.origin,
                "dest" to pkgToSave.dest,
                "desc" to pkgToSave.desc,
                "mode" to pkgToSave.mode,
                "weight" to pkgToSave.weight,
                "pcs" to pkgToSave.pcs,
                "cost" to pkgToSave.cost,
                "salesRep" to pkgToSave.salesRep,
                "status" to pkgToSave.status,
                "registeredAt" to pkgToSave.registeredAt,
                "paidAt" to pkgToSave.paidAt,
                "collectedAt" to pkgToSave.collectedAt,
                "collectorName" to pkgToSave.collectorName,
                "collectorId" to pkgToSave.collectorId,
                "collectorPhone" to pkgToSave.collectorPhone,
                "paymentMethod" to pkgToSave.paymentMethod,
                "paymentRef" to pkgToSave.paymentRef,
                "packagePhotoUrl" to pkgToSave.packagePhotoUrl
            )
            firestore.collection("cargo_packages").document(pkgToSave.id).set(fsMap, com.google.firebase.firestore.SetOptions.merge())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (online) {
            try {
                // 1. Upsert Customer
                try {
                    SupabaseClient.api.upsertCustomer(
                        apiKey = SupabaseClient.API_KEY,
                        authHeader = SupabaseClient.getBearerHeader(),
                        body = CustomerApi(
                            name = pkgToSave.consignee,
                            phone = pkgToSave.phone
                        )
                    )
                } catch (e: Exception) { e.printStackTrace() }

                // 2. Insert into canonical packages table
                try {
                    SupabaseClient.api.insertPackage(
                        apiKey = SupabaseClient.API_KEY,
                        authHeader = SupabaseClient.getBearerHeader(),
                        prefer = "resolution=merge-duplicates,return=representation",
                        body = pkgToSave.toPackageApi(
                            employeeId = SupabaseClient.currentUserId
                        )
                    )
                } catch (e: Exception) { e.printStackTrace() }

                // 2b. Explicitly update canonical packages table status and fields by tracking number
                try {
                    val statusForPackagesTable = when (pkgToSave.status.lowercase()) {
                        "paid", "cleared" -> "paid"
                        "collected" -> "collected"
                        else -> pkgToSave.status
                    }
                    SupabaseClient.api.updatePackageByTrackingNumber(
                        apiKey = SupabaseClient.API_KEY,
                        authHeader = SupabaseClient.getBearerHeader(),
                        trackingNumberFilter = "eq.${pkgToSave.id}",
                        body = mapOf(
                            "status" to statusForPackagesTable,
                            "package_photo_url" to pkgToSave.packagePhotoUrl
                        )
                    )
                } catch (e: Exception) { e.printStackTrace() }

                // 2c. Insert payment record into payments table if package is paid
                if (pkgToSave.status.lowercase() == "paid" || pkgToSave.status.lowercase() == "cleared" || !pkgToSave.paidAt.isNullOrBlank()) {
                    try {
                        val payApi = PaymentApi(
                            id = "PAY-" + System.currentTimeMillis() + "-" + (1000..9999).random(),
                            packageId = pkgToSave.id,
                            amount = pkgToSave.cost.toInt(),
                            paymentMethod = pkgToSave.paymentMethod ?: "M-PESA",
                            mpesaReceipt = pkgToSave.paymentRef,
                            status = "completed",
                            createdAt = pkgToSave.paidAt ?: java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        )
                        SupabaseClient.api.insertPayment(
                            apiKey = SupabaseClient.API_KEY,
                            authHeader = SupabaseClient.getBearerHeader(),
                            body = payApi
                        )
                    } catch (e: Exception) { e.printStackTrace() }
                }

                // 3. Update cargo_packages table directly on Supabase backend
                val backendStatus = if (pkgToSave.status == "collected") "cleared" else pkgToSave.status
                try {
                    SupabaseClient.api.updateCargoPackage(
                        apiKey = SupabaseClient.API_KEY,
                        authHeader = SupabaseClient.getBearerHeader(),
                        idFilter = "eq.${pkgToSave.id}",
                        body = mapOf(
                            "status" to backendStatus,
                            "paid_at" to pkgToSave.paidAt,
                            "collected_at" to pkgToSave.collectedAt,
                            "cleared_at" to (pkgToSave.collectedAt ?: pkgToSave.paidAt),
                            "payment_method" to pkgToSave.paymentMethod,
                            "payment_ref" to pkgToSave.paymentRef
                        )
                    )
                } catch (e: Exception) { e.printStackTrace() }

                try {
                    SupabaseClient.api.insertCargoPackage(
                        apiKey = SupabaseClient.API_KEY,
                        authHeader = SupabaseClient.getBearerHeader(),
                        prefer = "resolution=merge-duplicates,return=representation",
                        body = pkgToSave.toApi()
                    )
                } catch (e: Exception) { e.printStackTrace() }

                // Clear sync pending on local DB
                database.cargoPackageDao().insertPackage(pkgToSave.copy(syncPending = false))
            } catch (e: Exception) {
                e.printStackTrace()
                database.cargoPackageDao().insertPackage(pkgToSave.copy(syncPending = true))
            }
        } else {
            database.cargoPackageDao().insertPackage(pkgToSave.copy(syncPending = true))
        }
    }

    suspend fun insertNotification(notification: PaymentNotification, online: Boolean = false) {
        database.paymentNotificationDao().insertNotification(notification)
        if (online) {
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
        if (online) {
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
        if (online) {
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
        if (online) {
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
        if (online) {
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
        if (online) {
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
        if (online) {
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
        if (online) {
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

    fun normalizeStoragePath(value: String): String {
        val trimmed = value.trim()

        if (trimmed.startsWith("base64:")) return trimmed

        // Supports a complete Supabase URL as well as a stored relative path.
        val afterObject = trimmed.substringAfter("/storage/v1/object/", trimmed)
        return afterObject
            .substringAfter("storage/v1/object/", afterObject)
            .trimStart('/')
    }

    suspend fun downloadStorageImage(storagePathOrUrl: String?, packageId: String? = null): ByteArray? {
        if (storagePathOrUrl.isNullOrBlank()) return null

        val trimmedRaw = storagePathOrUrl.trim()
        if (trimmedRaw == "simulated_url" ||
            trimmedRaw == "captured_camera_uri" ||
            trimmedRaw == "captured_signature_points" ||
            trimmedRaw == "null" ||
            trimmedRaw == "undefined" ||
            trimmedRaw.startsWith("content://") ||
            trimmedRaw.startsWith("file://") ||
            trimmedRaw.startsWith("android.resource://") ||
            trimmedRaw.contains("captured_camera_uri")
        ) {
            return null
        }

        if (trimmedRaw.startsWith("base64:")) {
            return try {
                val cleanBase64 = trimmedRaw
                    .removePrefix("base64:")
                    .substringAfter("base64,")
                    .substringAfter("data:image/jpeg;base64,")
                    .substringAfter("data:image/png;base64,")
                    .trim()
                android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            } catch (e: Exception) {
                null
            }
        }

        var path = trimmedRaw

        if (path.contains("/storage/v1/object/")) {
            path = path.substringAfter("/storage/v1/object/")
        }

        path = path.removePrefix("/")
        path = path.removePrefix("authenticated/").removePrefix("public/").removePrefix("/")

        if (!path.startsWith("package-photos/") && !path.startsWith("proofs/")) {
            if (path == "captured_camera_uri" || path == "simulated_url" || path == "captured_signature_points") {
                return null
            }
            if (!packageId.isNullOrBlank()) {
                val pkgId = packageId.trim()
                path = if (path.isNotBlank()) "package-photos/$pkgId/$path" else "package-photos/$pkgId/photo.jpg"
            } else if (path.isNotBlank()) {
                path = "package-photos/$path"
            }
        }

        if (!path.startsWith("package-photos/") && !path.startsWith("proofs/")) {
            android.util.Log.e("DEX_IMAGE", "Unexpected Storage path: $path")
            return null
        }

        if (path.contains("captured_camera_uri") || path.contains("simulated_url")) {
            return null
        }

        return try {
            val response = SupabaseClient.api.downloadStorageObject(
                apiKey = SupabaseClient.API_KEY,
                authHeader = SupabaseClient.getBearerHeader(),
                objectPath = path
            )

            if (response.isSuccessful) {
                val bytes = response.body()?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    return bytes
                }
            } else {
                android.util.Log.e("DEX_IMAGE", "Storage download failed: ${response.code()} for $path")
            }
            null
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            android.util.Log.e("DEX_IMAGE", "Could not download: $path", error)
            null
        }
    }

    suspend fun downloadPhoto(packageId: String, filename: String): ByteArray? {
        return downloadStorageImage("package-photos/$packageId/$filename", packageId = packageId)
    }

    // --- COMMISSION OPERATIONS (SECTION 8) ---

    suspend fun getCommissionsFromBackend(employeeId: String? = null, status: String? = null): List<CommissionApi> {
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

    suspend fun insertCommissionOnBackend(commission: CommissionApi): Boolean {
        return try {
            val response = SupabaseClient.api.insertCommission(
                apiKey = SupabaseClient.API_KEY,
                authHeader = SupabaseClient.getBearerHeader(),
                body = commission
            )
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun approveCommissionOnBackend(id: String): Boolean {
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
        if (!online) return
        
        val apiKey = SupabaseClient.API_KEY
        val authHeader = SupabaseClient.getBearerHeader()

        // First: Push any unsynced local pending packages to cloud
        try {
            val localPackages = database.cargoPackageDao().getAllPackages().firstOrNull() ?: emptyList()
            val pendingPackages = localPackages.filter { it.syncPending }
            for (pending in pendingPackages) {
                try {
                    SupabaseClient.api.insertCargoPackage(apiKey = apiKey, authHeader = authHeader, body = pending.toApi())
                    database.cargoPackageDao().insertPackage(pending.copy(syncPending = false))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 1. Sync employees
        try {
            var empApiList = try {
                SupabaseClient.api.getAllEmployees(apiKey, authHeader)
            } catch (e: Exception) {
                emptyList()
            }

            val existingLocalEmps = database.employeeDao().getAllEmployees().firstOrNull() ?: emptyList()

            if (empApiList.isEmpty() && !SupabaseClient.accessToken.isNullOrBlank()) {
                val currentEmail = SupabaseClient.currentUserEmail
                val currentLocalEmp = existingLocalEmps.find { it.email.equals(currentEmail, ignoreCase = true) }
                val isAdminUser = currentLocalEmp == null || currentLocalEmp.role.equals("admin", ignoreCase = true) || currentLocalEmp.role.equals("ADM", ignoreCase = true)

                if (isAdminUser) {
                    val adminResp = try {
                        SupabaseClient.backendApi.getAdminEmployees(authHeader = authHeader)
                    } catch (e: Exception) {
                        null
                    }
                    if (adminResp != null && adminResp.isSuccessful) {
                        empApiList = adminResp.body()?.employees ?: emptyList()
                    }
                }
            }
            if (empApiList.isNotEmpty()) {
                val existingLocalEmps = database.employeeDao().getAllEmployees().firstOrNull() ?: emptyList()
                val existingById = existingLocalEmps.associateBy { it.id }
                val mappedEmps = empApiList.map { empApi ->
                    val existing = existingById[empApi.id] ?: existingLocalEmps.find { it.email.equals(empApi.email, ignoreCase = true) }
                    val preservedPass = existing?.password?.takeIf { it.isNotBlank() } ?: "password"
                    val preservedPin = existing?.pin
                    val preservedBiometrics = existing?.biometricEnabled ?: false
                    empApi.toEntity(password = preservedPass, existingPin = preservedPin, existingBiometric = preservedBiometrics)
                }
                database.employeeDao().insertEmployees(mappedEmps)
            } else {
                val profiles = SupabaseClient.api.getAllProfiles(apiKey, authHeader)
                val allRoles = SupabaseClient.api.getAllUserRoles(apiKey, authHeader)
                val rolesMap = allRoles.associateBy { it.userId }

                val existingLocalEmps = database.employeeDao().getAllEmployees().firstOrNull() ?: emptyList()
                val existingById = existingLocalEmps.associateBy { it.id }
                val existingByEmail = existingLocalEmps.associateBy { it.email.lowercase() }

                val employeeList = profiles.map { profile ->
                    val rawRole = rolesMap[profile.id]?.role ?: "sr"
                    val role = if (rawRole == "clerk") "lm" else rawRole
                    val existing = existingById[profile.id] ?: existingByEmail[profile.email.lowercase()]
                    val preservedPassword = existing?.password?.takeIf { it.isNotBlank() } ?: "password"

                    Employee(
                        id = profile.id,
                        name = profile.name,
                        email = profile.email,
                        password = preservedPassword,
                        role = role,
                        isActive = profile.isActive,
                        pin = profile.pinHash ?: existing?.pin,
                        biometricEnabled = profile.biometricEnabled ?: existing?.biometricEnabled ?: false
                    )
                }
                if (employeeList.isNotEmpty()) {
                    database.employeeDao().insertEmployees(employeeList)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Sync packages
        try {
            val canonicalPackages = try {
                SupabaseClient.api.getPackages(apiKey = apiKey, authHeader = authHeader)
            } catch (e: Exception) {
                emptyList()
            }

            val legacyPackages = try {
                SupabaseClient.api.getCargoPackages(apiKey, authHeader)
            } catch (e: Exception) {
                emptyList()
            }

            // cargo_packages.id should match packages.tracking_number.
            val legacyPhotoByTrackingNumber = legacyPackages.associateBy(
                keySelector = { it.id },
                valueTransform = { it.packagePhotoUrl }
            )

            val packagesEntities = if (canonicalPackages.isNotEmpty()) {
                canonicalPackages.map { packageApi ->
                    val canonicalEntity = packageApi.toEntity(syncPending = false)

                    canonicalEntity.copy(
                        packagePhotoUrl = packageApi.packagePhotoUrl
                            ?: legacyPhotoByTrackingNumber[packageApi.trackingNumber]
                            ?: canonicalEntity.packagePhotoUrl
                    )
                }
            } else {
                legacyPackages.map { it.toEntity(syncPending = false) }
            }

            if (packagesEntities.isNotEmpty()) {
                val localMap = database.cargoPackageDao().getAllPackages().firstOrNull()?.associateBy { it.id } ?: emptyMap()
                val mergedEntities = packagesEntities.map { incoming ->
                    val local = localMap[incoming.id]
                    var finalPkg = incoming
                    if (local != null) {
                        // Preserve paid or collected status from local database if backend still returns registered or received
                        val localIsPaid = local.status == "paid" || local.status == "collected" || local.status == "cleared" || !local.paidAt.isNullOrBlank()
                        val incomingIsRegistered = incoming.status == "registered" || incoming.status == "received" || incoming.status.isBlank()
                        if (localIsPaid && incomingIsRegistered) {
                            finalPkg = finalPkg.copy(
                                status = local.status,
                                paidAt = local.paidAt ?: finalPkg.paidAt,
                                paymentMethod = local.paymentMethod ?: finalPkg.paymentMethod,
                                paymentRef = local.paymentRef ?: finalPkg.paymentRef,
                                collectedAt = local.collectedAt ?: finalPkg.collectedAt,
                                collectorName = local.collectorName ?: finalPkg.collectorName,
                                collectorId = local.collectorId ?: finalPkg.collectorId,
                                collectorPhone = local.collectorPhone ?: finalPkg.collectorPhone
                            )
                        }
                        if (!local.packagePhotoUrl.isNullOrBlank() && local.packagePhotoUrl.startsWith("base64:") &&
                            (incoming.packagePhotoUrl.isNullOrBlank() || !incoming.packagePhotoUrl.startsWith("base64:"))
                        ) {
                            finalPkg = finalPkg.copy(packagePhotoUrl = local.packagePhotoUrl)
                        }
                    }
                    android.util.Log.d(
                        "DEX_PACKAGE_PHOTO",
                        "Tracking=${finalPkg.id}, photo=${finalPkg.packagePhotoUrl}, status=${finalPkg.status}"
                    )
                    finalPkg
                }
                database.cargoPackageDao().insertPackages(mergedEntities)

                // Asynchronously check and cache package photos and payment evidence for offline access
                val allUpdatedLocal = database.cargoPackageDao().getAllPackages().firstOrNull() ?: emptyList()
                allUpdatedLocal.forEach { pkg ->
                    val rawUrl = pkg.packagePhotoUrl
                    if (!rawUrl.isNullOrBlank() &&
                        !rawUrl.startsWith("base64:") &&
                        rawUrl != "simulated_url"
                    ) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val bytes = downloadStorageImage(rawUrl, packageId = pkg.id)
                                if (bytes != null && bytes.isNotEmpty()) {
                                    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                                    val updatedPkg = pkg.copy(packagePhotoUrl = "base64:$b64")
                                    database.cargoPackageDao().insertPackage(updatedPkg)
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

                // Asynchronously check and cache payment notification evidence images for offline access
                val allNotifs = database.paymentNotificationDao().getAllNotifications().firstOrNull() ?: emptyList()
                allNotifs.forEach { notif ->
                    val rawUrl = notif.imageUrl
                    if (!rawUrl.isNullOrBlank() && !rawUrl.startsWith("base64:")) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val bytes = downloadStorageImage(rawUrl)
                                if (bytes != null && bytes.isNotEmpty()) {
                                    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                                    val updatedNotif = notif.copy(imageUrl = "base64:$b64")
                                    database.paymentNotificationDao().insertNotification(updatedNotif)
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

    suspend fun linkPaymentBackend(notificationId: String, allocations: List<AllocationPayload>): Boolean {
        return try {
            val authHeader = SupabaseClient.getBearerHeader()
            if (authHeader.isBlank()) return false
            val req = LinkPaymentRequest(notificationId, allocations)
            val res = SupabaseClient.backendApi.linkPayment(authHeader = authHeader, req = req)
            if (res.isSuccessful && res.body()?.ok == true) {
                Log.i("DexcargoRepo", "linkPayment backend succeeded: allocated ${res.body()?.allocatedTotal}")
                true
            } else {
                Log.e("DexcargoRepo", "linkPayment backend failed: ${res.code()} ${res.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("DexcargoRepo", "linkPayment error", e)
            false
        }
    }

    suspend fun fetchRevenueSummary(): RevenueSummaryResponse? {
        return try {
            val authHeader = SupabaseClient.getBearerHeader()
            if (authHeader.isBlank()) return null
            val res = SupabaseClient.backendApi.revenueSummary(authHeader = authHeader)
            if (res.isSuccessful) {
                res.body()
            } else {
                Log.e("DexcargoRepo", "fetchRevenueSummary failed: ${res.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e("DexcargoRepo", "fetchRevenueSummary error", e)
            null
        }
    }
}
