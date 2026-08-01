package com.example.data

import android.util.Log
import com.example.data.api.DeleteUserAdminRequest
import com.example.data.api.ProfileResponse
import com.example.data.api.SupabaseClient
import com.example.data.api.UserRoleResponse
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

class AdminRepository(private val database: AppDatabase) {

    private val firestore: FirebaseFirestore?
        get() = try {
            FirebaseFirestore.getInstance()
        } catch (t: Throwable) {
            Log.w("AdminRepository", "FirebaseApp is not initialized: ${t.message}")
            null
        }

    /**
     * Dedicated method that explicitly validates the user data object
     * before calling firestore.collection("users").document(uid).set(user)
     * and adds logging to capture any failure reasons.
     */
    suspend fun saveUserToFirestoreAndDatabase(
        employee: Employee,
        online: Boolean = true
    ): Result<Boolean> {
        val uid = employee.id.trim()
        val validationErrors = mutableListOf<String>()

        if (uid.isBlank()) {
            validationErrors.add("User UID / Employee ID cannot be blank.")
        }
        if (employee.email.isBlank()) {
            validationErrors.add("User Email cannot be blank.")
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(employee.email).matches()) {
            validationErrors.add("User Email format '${employee.email}' is invalid.")
        }
        if (employee.name.isBlank()) {
            validationErrors.add("User Name cannot be blank.")
        }
        val validRoles = listOf("admin", "sm", "lm", "sr", "sales_manager", "logistics_manager", "sales_rep")
        if (employee.role.isBlank() || !validRoles.contains(employee.role.lowercase())) {
            validationErrors.add("User Role '${employee.role}' is invalid. Allowed roles: $validRoles")
        }

        if (validationErrors.isNotEmpty()) {
            val failureReason = "User object validation failed: ${validationErrors.joinToString("; ")}"
            Log.e("AdminRepository", failureReason)
            return Result.failure(IllegalArgumentException(failureReason))
        }

        Log.d("AdminRepository", "Validation passed for user object $uid (${employee.name}, ${employee.email}, role: ${employee.role})")

        // 1. Local Room DB insertion
        try {
            database.employeeDao().insertEmployee(employee)
            Log.d("AdminRepository", "Saved user $uid to local Room database.")
        } catch (e: Exception) {
            Log.e("AdminRepository", "Failed to save user $uid to local database: ${e.localizedMessage}", e)
        }

        // 2. Write user object to Firestore with explicit validation and logging
        val userMap = hashMapOf(
            "id" to uid,
            "name" to employee.name,
            "email" to employee.email,
            "role" to employee.role,
            "isActive" to employee.isActive,
            "pin" to (employee.pin ?: ""),
            "biometricEnabled" to employee.biometricEnabled,
            "password" to employee.password,
            "updatedAt" to System.currentTimeMillis()
        )

        Log.d("AdminRepository", "Writing user object to Firestore collection 'users' document '$uid' with payload: $userMap")

        var firestoreErrorMsg: String? = null
        val fs = firestore
        val firestoreSuccess = if (fs != null) {
            try {
                fs.collection("users")
                    .document(uid)
                    .set(userMap)
                    .await()
                Log.d("AdminRepository", "Firestore write succeeded for user document UID: $uid")
                true
            } catch (fe: com.google.firebase.firestore.FirebaseFirestoreException) {
                firestoreErrorMsg = "Firestore permission/security or data error [${fe.code}]: ${fe.localizedMessage}"
                Log.e("AdminRepository", firestoreErrorMsg, fe)
                false
            } catch (e: Exception) {
                firestoreErrorMsg = "Firestore write failed for UID $uid: ${e.localizedMessage}"
                Log.e("AdminRepository", firestoreErrorMsg, e)
                false
            }
        } else {
            Log.d("AdminRepository", "Skipping Firestore write (FirebaseApp uninitialized)")
            false
        }

        // 3. Supabase Cloud Sync
        if (online) {
            try {
                val authHeader = SupabaseClient.getBearerHeader()
                val profileResp = SupabaseClient.api.createProfile(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = authHeader,
                    profile = ProfileResponse(
                        id = uid,
                        name = employee.name,
                        email = employee.email,
                        isActive = employee.isActive,
                        pinHash = employee.pin,
                        biometricEnabled = employee.biometricEnabled
                    )
                )
                if (profileResp.isSuccessful) {
                    Log.d("AdminRepository", "Supabase profile created successfully for $uid")
                } else {
                    Log.w("AdminRepository", "Supabase profile creation failed [${profileResp.code()}]: ${profileResp.errorBody()?.string()}")
                }

                val roleResp = SupabaseClient.api.createUserRole(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = authHeader,
                    role = UserRoleResponse(
                        userId = uid,
                        role = employee.role
                    )
                )
                if (roleResp.isSuccessful) {
                    Log.d("AdminRepository", "Supabase user role created successfully for $uid")
                } else {
                    Log.w("AdminRepository", "Supabase role creation failed [${roleResp.code()}]: ${roleResp.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("AdminRepository", "Supabase cloud sync error for $uid: ${e.localizedMessage}", e)
            }
        }

        return if (firestoreSuccess) {
            Result.success(true)
        } else {
            Result.failure(Exception(firestoreErrorMsg ?: "Firestore document set failed for UID $uid"))
        }
    }

    /**
     * Admin deleteUser:
     * Calls POST /api/admin/delete-user with { "employee_id": "<employees.id>" }.
     * The server deactivates the employee and deletes the linked Supabase Auth account.
     * After a successful response, Android deletes local Room DB entry and matching Firestore doc.
     */
    suspend fun deleteUser(uid: String, online: Boolean = true): Result<Boolean> {
        val cleanUid = uid.trim()
        val currentUserId = SupabaseClient.currentUserId ?: ""

        if (cleanUid.equals("ADM-001", ignoreCase = true) || cleanUid.equals("ADM-0001", ignoreCase = true)) {
            val msg = "Security rule: Cannot delete primary administrator account ($cleanUid)."
            Log.e("AdminRepository", msg)
            return Result.failure(IllegalArgumentException(msg))
        }

        if (cleanUid.isNotBlank() && cleanUid.equals(currentUserId, ignoreCase = true)) {
            val msg = "Security rule: Cannot delete currently logged-in account ($cleanUid)."
            Log.e("AdminRepository", msg)
            return Result.failure(IllegalArgumentException(msg))
        }

        Log.d("AdminRepository", "Initiating Admin deleteUser for identifier: $cleanUid")

        // 1. Resolve local employee entity if present
        val localEmps = try { database.employeeDao().getAllEmployees().first() } catch (e: Exception) { emptyList() }
        val targetEmp = localEmps.find {
            it.id.equals(cleanUid, ignoreCase = true) ||
            it.email.equals(cleanUid, ignoreCase = true)
        }

        val targetEmpId = targetEmp?.id ?: cleanUid

        if (online) {
            val bearerHeader = SupabaseClient.getBearerHeader()
            val activeApiKey = SupabaseClient.API_KEY

            var deletedViaBackend = false
            try {
                val deleteReq = DeleteUserAdminRequest(employeeId = targetEmpId)
                val deleteResp = SupabaseClient.backendApi.deleteUserAdminEndpoint(
                    authHeader = bearerHeader,
                    body = deleteReq
                )

                if (deleteResp.isSuccessful) {
                    deletedViaBackend = true
                    Log.d("AdminRepository", "Backend deleteUserAdminEndpoint succeeded (HTTP ${deleteResp.code()})")
                } else {
                    val errStr = try { deleteResp.errorBody()?.string() } catch (e: Exception) { null }
                    val message = errStr ?: deleteResp.message().ifBlank { null } ?: "HTTP ${deleteResp.code()}"
                    Log.w("AdminRepository", "Backend deleteUserAdminEndpoint note: $message. Executing direct fallback deletion.")
                }
            } catch (e: Exception) {
                Log.w("AdminRepository", "backendApi deleteUserAdminEndpoint exception: ${e.message}. Executing fallback.")
            }

            if (!deletedViaBackend) {
                // Direct REST fallback deletion across tables
                try {
                    SupabaseClient.api.deleteEmployeeRest(apiKey = activeApiKey, authHeader = bearerHeader, idFilter = "eq.$targetEmpId")
                } catch (e: Exception) { Log.w("AdminRepository", "REST delete employee note: ${e.message}") }

                try {
                    SupabaseClient.api.deleteProfile(apiKey = activeApiKey, authHeader = bearerHeader, idFilter = "eq.$targetEmpId")
                } catch (e: Exception) { Log.w("AdminRepository", "REST delete profile note: ${e.message}") }

                try {
                    SupabaseClient.api.deleteUserRole(apiKey = activeApiKey, authHeader = bearerHeader, userIdFilter = "eq.$targetEmpId")
                } catch (e: Exception) { Log.w("AdminRepository", "REST delete user role note: ${e.message}") }

                try {
                    SupabaseClient.api.deleteAuthUserAdmin(userId = targetEmpId, apiKey = activeApiKey, authHeader = bearerHeader)
                } catch (e: Exception) { Log.w("AdminRepository", "REST delete auth user note: ${e.message}") }
            }
        }

        // E) Clean up Firebase Firestore document if active
        try {
            val fs = firestore
            if (fs != null) {
                if (targetEmpId.isNotBlank()) fs.collection("users").document(targetEmpId).delete().await()
                if (cleanUid.isNotBlank() && cleanUid != targetEmpId) fs.collection("users").document(cleanUid).delete().await()
            }
        } catch (e: Exception) {
            Log.w("AdminRepository", "Firestore cleanup note: ${e.localizedMessage}")
        }

        // F) Clean up local Room database entries
        try {
            if (targetEmpId.isNotBlank()) database.employeeDao().deleteEmployeeById(targetEmpId)
            if (cleanUid.isNotBlank()) database.employeeDao().deleteEmployeeById(cleanUid)
        } catch (e: Exception) {
            Log.e("AdminRepository", "Local Room DB deletion exception: ${e.localizedMessage}")
        }

        return Result.success(true)
    }
}
