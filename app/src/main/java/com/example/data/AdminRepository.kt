package com.example.data

import android.util.Log
import com.example.data.api.DeleteUserAdminRequest
import com.example.data.api.ProfileResponse
import com.example.data.api.SupabaseClient
import com.example.data.api.UserRoleResponse
import com.google.firebase.firestore.FirebaseFirestore
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

        // Find associated local employee entity to extract empId, authUserId, and email
        val localEmps = try { database.employeeDao().getAllEmployees().first() } catch (e: Exception) { emptyList() }
        val targetEmp = localEmps.find {
            it.id.equals(cleanUid, ignoreCase = true) ||
            it.email.equals(cleanUid, ignoreCase = true) ||
            (it.userId != null && it.userId.equals(cleanUid, ignoreCase = true))
        }

        val empId = targetEmp?.id ?: cleanUid
        val authUserId = targetEmp?.userId ?: cleanUid
        val targetEmail = targetEmp?.email ?: if (cleanUid.contains("@")) cleanUid else ""

        if (online) {
            var deletedOnServer = false

            // Stage 1: Try Backend Endpoint with User Bearer Header
            try {
                val authHeader = SupabaseClient.getBearerHeader()
                val response = SupabaseClient.backendApi.deleteUserAdminEndpoint(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = authHeader,
                    body = DeleteUserAdminRequest(employeeId = empId)
                )

                if (response.isSuccessful) {
                    deletedOnServer = true
                    Log.d("AdminRepository", "Backend deleteUser succeeded for employee_id $empId")
                } else {
                    Log.w("AdminRepository", "Backend deleteUser returned HTTP ${response.code()}. Retrying with Service Role key...")
                }
            } catch (e: Exception) {
                Log.w("AdminRepository", "Backend deleteUser exception: ${e.localizedMessage}")
            }

            // Stage 2: Retry Backend Endpoint with Service Role Key Header
            if (!deletedOnServer) {
                try {
                    val serviceHeader = SupabaseClient.getServiceRoleBearerHeader()
                    val response = SupabaseClient.backendApi.deleteUserAdminEndpoint(
                        apiKey = SupabaseClient.SERVICE_ROLE_KEY,
                        authHeader = serviceHeader,
                        body = DeleteUserAdminRequest(employeeId = empId)
                    )

                    if (response.isSuccessful) {
                        deletedOnServer = true
                        Log.d("AdminRepository", "Backend deleteUser with Service Role key succeeded for $empId")
                    } else {
                        Log.w("AdminRepository", "Backend deleteUser with Service Role key returned HTTP ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.w("AdminRepository", "Backend deleteUser with Service Role key exception: ${e.localizedMessage}")
                }
            }

            // Stage 3: Direct Supabase REST Deletion using SERVICE_ROLE_KEY across employees, profiles, user_roles
            try {
                val serviceHeader = SupabaseClient.getServiceRoleBearerHeader()
                val serviceKey = SupabaseClient.SERVICE_ROLE_KEY

                // Delete from public.employees table
                SupabaseClient.api.deleteEmployeeRest(apiKey = serviceKey, authHeader = serviceHeader, idFilter = "eq.$empId")
                if (authUserId != empId) {
                    SupabaseClient.api.deleteEmployeeRest(apiKey = serviceKey, authHeader = serviceHeader, idFilter = "eq.$authUserId")
                }
                if (targetEmail.isNotBlank()) {
                    SupabaseClient.api.deleteEmployeeRestByEmail(apiKey = serviceKey, authHeader = serviceHeader, emailFilter = "eq.$targetEmail")
                }

                // Delete from public.profiles table
                SupabaseClient.api.deleteProfile(apiKey = serviceKey, authHeader = serviceHeader, idFilter = "eq.$authUserId")
                SupabaseClient.api.deleteProfile(apiKey = serviceKey, authHeader = serviceHeader, idFilter = "eq.$empId")
                if (targetEmail.isNotBlank()) {
                    SupabaseClient.api.deleteProfileByEmail(apiKey = serviceKey, authHeader = serviceHeader, emailFilter = "eq.$targetEmail")
                }

                // Delete from public.user_roles table
                SupabaseClient.api.deleteUserRole(apiKey = serviceKey, authHeader = serviceHeader, userIdFilter = "eq.$authUserId")
                if (authUserId != empId) {
                    SupabaseClient.api.deleteUserRole(apiKey = serviceKey, authHeader = serviceHeader, userIdFilter = "eq.$empId")
                }

                deletedOnServer = true
                Log.d("AdminRepository", "Direct Supabase REST cleanup executed for $empId / $authUserId")
            } catch (e: Exception) {
                Log.e("AdminRepository", "Direct Supabase REST deletion exception: ${e.localizedMessage}", e)
            }

            // Stage 4: Supabase Auth Admin Deletion from auth.users (removes from Lovable Users panel)
            if (authUserId.isNotBlank() && authUserId.contains("-")) {
                try {
                    val serviceHeader = SupabaseClient.getServiceRoleBearerHeader()
                    val serviceKey = SupabaseClient.SERVICE_ROLE_KEY
                    val authResp = SupabaseClient.api.deleteAuthUserAdmin(
                        userId = authUserId,
                        apiKey = serviceKey,
                        authHeader = serviceHeader
                    )
                    if (authResp.isSuccessful) {
                        Log.d("AdminRepository", "Successfully deleted user $authUserId from Supabase Auth (auth.users)")
                    } else {
                        Log.w("AdminRepository", "Delete auth user returned HTTP ${authResp.code()}")
                    }
                } catch (e: Exception) {
                    Log.w("AdminRepository", "Exception deleting auth user $authUserId: ${e.localizedMessage}")
                }
            }
        }

        // Delete linked Firestore document if present
        try {
            firestore.collection("users").document(empId).delete().await()
            if (authUserId != empId) {
                firestore.collection("users").document(authUserId).delete().await()
            }
            Log.d("AdminRepository", "Firestore document cleanup completed for $empId")
        } catch (e: Exception) {
            Log.w("AdminRepository", "Firestore cleanup note: ${e.localizedMessage}")
        }

        // Remove local Room DB entry
        try {
            database.employeeDao().deleteEmployeeById(empId)
            database.employeeDao().deleteEmployeeById(cleanUid)
            if (authUserId.isNotBlank()) {
                database.employeeDao().deleteEmployeeById(authUserId)
            }
            Log.d("AdminRepository", "Deleted employee $empId from local Room database.")
        } catch (e: Exception) {
            Log.e("AdminRepository", "Failed deleting local database employee $empId: ${e.localizedMessage}", e)
        }

        return Result.success(true)
    }
}
