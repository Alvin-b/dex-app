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

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

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
        val firestoreSuccess = try {
            firestore.collection("users")
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

        var empCodeOrId = targetEmp?.id ?: cleanUid
        var targetEmail = targetEmp?.email ?: if (cleanUid.contains("@")) cleanUid else ""
        var authUserId = if (cleanUid.contains("-") && cleanUid.length == 36) cleanUid else (if (targetEmp?.id?.length == 36) targetEmp.id else "")

        // 2. If online and authUserId is not a 36-char UUID, resolve via Supabase REST API
        if (online && authUserId.length != 36) {
            val bearerHeader = SupabaseClient.getBearerHeader()
            val activeApiKey = SupabaseClient.API_KEY

            try {
                if (targetEmail.isNotBlank()) {
                    val profilesByEmail = SupabaseClient.api.getProfileByEmail(apiKey = activeApiKey, authHeader = bearerHeader, emailFilter = "eq.$targetEmail")
                    if (profilesByEmail.isNotEmpty()) {
                        authUserId = profilesByEmail.first().id
                    } else {
                        val empsByEmail = SupabaseClient.api.getEmployeeByEmail(apiKey = activeApiKey, authHeader = bearerHeader, emailFilter = "eq.$targetEmail")
                        if (empsByEmail.isNotEmpty()) {
                            authUserId = empsByEmail.first().userId ?: empsByEmail.first().id
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("AdminRepository", "Could not resolve auth user UUID online: ${e.message}")
            }
        }

        if (online) {
            val bearerHeader = SupabaseClient.getBearerHeader()
            val activeApiKey = SupabaseClient.API_KEY

            // Primary: Call backend endpoint /api/admin/delete-user
            try {
                val deleteReq = DeleteUserAdminRequest(employeeId = empCodeOrId)
                val deleteResp = SupabaseClient.backendApi.deleteUserAdminEndpoint(
                    apiKey = activeApiKey,
                    authHeader = bearerHeader,
                    body = deleteReq
                )
                Log.d("AdminRepository", "Backend deleteUserAdminEndpoint result: HTTP ${deleteResp.code()}")
            } catch (e: Exception) {
                Log.w("AdminRepository", "backendApi deleteUserAdminEndpoint exception: ${e.message}")
            }

            // A) Delete from Supabase Auth auth.users
            if (authUserId.length == 36) {
                try {
                    val authResp = SupabaseClient.api.deleteAuthUserAdmin(
                        userId = authUserId,
                        apiKey = activeApiKey,
                        authHeader = bearerHeader
                    )
                    Log.d("AdminRepository", "Delete auth.users result: HTTP ${authResp.code()}")
                } catch (e: Exception) {
                    Log.e("AdminRepository", "Exception deleting auth user $authUserId: ${e.message}", e)
                }
            }

            // B) Delete from public.employees table across all identifiers
            try {
                if (empCodeOrId.isNotBlank()) {
                    SupabaseClient.api.deleteEmployeeRest(apiKey = activeApiKey, authHeader = bearerHeader, idFilter = "eq.$empCodeOrId")
                }
                if (authUserId.isNotBlank() && authUserId != empCodeOrId) {
                    SupabaseClient.api.deleteEmployeeRest(apiKey = activeApiKey, authHeader = bearerHeader, idFilter = "eq.$authUserId")
                }
                if (targetEmail.isNotBlank()) {
                    SupabaseClient.api.deleteEmployeeRestByEmail(apiKey = activeApiKey, authHeader = bearerHeader, emailFilter = "eq.$targetEmail")
                }
            } catch (e: Exception) {
                Log.e("AdminRepository", "Exception deleting from employees table: ${e.message}", e)
            }

            // C) Delete from public.profiles table
            try {
                if (authUserId.isNotBlank()) {
                    SupabaseClient.api.deleteProfile(apiKey = activeApiKey, authHeader = bearerHeader, idFilter = "eq.$authUserId")
                }
                if (empCodeOrId.isNotBlank() && empCodeOrId != authUserId) {
                    SupabaseClient.api.deleteProfile(apiKey = activeApiKey, authHeader = bearerHeader, idFilter = "eq.$empCodeOrId")
                }
                if (targetEmail.isNotBlank()) {
                    SupabaseClient.api.deleteProfileByEmail(apiKey = activeApiKey, authHeader = bearerHeader, emailFilter = "eq.$targetEmail")
                }
            } catch (e: Exception) {
                Log.e("AdminRepository", "Exception deleting from profiles table: ${e.message}", e)
            }

            // D) Delete from public.user_roles table
            try {
                if (authUserId.isNotBlank()) {
                    SupabaseClient.api.deleteUserRole(apiKey = activeApiKey, authHeader = bearerHeader, userIdFilter = "eq.$authUserId")
                }
                if (empCodeOrId.isNotBlank() && empCodeOrId != authUserId) {
                    SupabaseClient.api.deleteUserRole(apiKey = activeApiKey, authHeader = bearerHeader, userIdFilter = "eq.$empCodeOrId")
                }
            } catch (e: Exception) {
                Log.e("AdminRepository", "Exception deleting from user_roles table: ${e.message}", e)
            }
        }

        // E) Clean up Firebase Firestore document if active
        try {
            if (empCodeOrId.isNotBlank()) firestore.collection("users").document(empCodeOrId).delete().await()
            if (authUserId.isNotBlank() && authUserId != empCodeOrId) firestore.collection("users").document(authUserId).delete().await()
        } catch (e: Exception) {
            Log.w("AdminRepository", "Firestore cleanup note: ${e.localizedMessage}")
        }

        // F) Clean up local Room database entries
        try {
            if (empCodeOrId.isNotBlank()) database.employeeDao().deleteEmployeeById(empCodeOrId)
            if (cleanUid.isNotBlank()) database.employeeDao().deleteEmployeeById(cleanUid)
            if (authUserId.isNotBlank()) database.employeeDao().deleteEmployeeById(authUserId)
        } catch (e: Exception) {
            Log.e("AdminRepository", "Local Room DB deletion exception: ${e.localizedMessage}")
        }

        return Result.success(true)
    }
}
