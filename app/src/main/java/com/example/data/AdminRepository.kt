package com.example.data

import android.util.Log
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

        // 2. Write user object to Firestore
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

        val firestoreSuccess = try {
            firestore.collection("users")
                .document(uid)
                .set(userMap)
                .await()
            Log.d("AdminRepository", "Firestore write succeeded for UID: $uid")
            true
        } catch (e: Exception) {
            Log.e("AdminRepository", "Firestore write FAILED for UID $uid: ${e.localizedMessage}", e)
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
            Result.failure(Exception("Firestore document set failed for UID $uid"))
        }
    }

    /**
     * Admin deleteUser:
     * 1. Removes Firestore document
     * 2. Triggers cloud function to revoke user refresh tokens
     * 3. Cleans up Supabase profile & user roles
     * 4. Deletes from local Room DB
     */
    suspend fun deleteUser(uid: String, online: Boolean = true): Result<Boolean> {
        if (uid == "ADM-001") {
            val msg = "Security rule: Cannot delete primary administrator account (ADM-001)."
            Log.e("AdminRepository", msg)
            return Result.failure(IllegalArgumentException(msg))
        }

        Log.d("AdminRepository", "Initiating Admin deleteUser for UID: $uid")

        // Local DB removal
        try {
            database.employeeDao().deleteEmployeeById(uid)
            Log.d("AdminRepository", "Deleted employee $uid from local Room database.")
        } catch (e: Exception) {
            Log.e("AdminRepository", "Failed deleting local database employee $uid: ${e.localizedMessage}", e)
        }

        // Firestore removal
        try {
            firestore.collection("users").document(uid).delete().await()
            Log.d("AdminRepository", "Firestore document deleted for UID $uid")

            val queryDocs = firestore.collection("users").whereEqualTo("id", uid).get().await()
            for (doc in queryDocs.documents) {
                doc.reference.delete().await()
            }
            Log.d("AdminRepository", "Removed secondary matching Firestore documents for id $uid")
        } catch (e: Exception) {
            Log.e("AdminRepository", "Error deleting Firestore user document for $uid: ${e.localizedMessage}", e)
        }

        // Cloud Function token revocation & Supabase deletion
        if (online) {
            try {
                triggerRevokeUserTokensCloudFunction(uid)
            } catch (e: Exception) {
                Log.e("AdminRepository", "Token revocation trigger failed for UID $uid: ${e.localizedMessage}", e)
            }

            try {
                val bearer = SupabaseClient.getBearerHeader()
                SupabaseClient.api.deleteProfile(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = bearer,
                    idFilter = "eq.$uid"
                )
                SupabaseClient.api.deleteUserRole(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = bearer,
                    userIdFilter = "eq.$uid"
                )
                Log.d("AdminRepository", "Supabase profile and user roles deleted for $uid")
            } catch (e: Exception) {
                Log.e("AdminRepository", "Error deleting Supabase profile/role for $uid: ${e.localizedMessage}", e)
            }
        }

        return Result.success(true)
    }

    private fun triggerRevokeUserTokensCloudFunction(uid: String) {
        try {
            val bearer = SupabaseClient.getBearerHeader()
            val client = OkHttpClient()
            val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = RequestBody.create(jsonMediaType, "{\"user_id\":\"$uid\",\"action\":\"revoke_refresh_tokens\"}")
            val request = Request.Builder()
                .url("https://project--5e9b81ad-6c63-4331-af7a-01008019e17f.lovable.app/api/public/revoke-user-tokens")
                .addHeader("Authorization", bearer)
                .addHeader("apikey", SupabaseClient.API_KEY)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.e("AdminRepository", "Cloud function token revocation callback failure for $uid: ${e.message}", e)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use {
                        if (response.isSuccessful) {
                            Log.d("AdminRepository", "Cloud function triggered token revocation successfully for UID $uid")
                        } else {
                            Log.w("AdminRepository", "Cloud function token revocation returned HTTP ${response.code} for $uid")
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("AdminRepository", "Failed launching cloud function to revoke tokens for $uid: ${e.localizedMessage}", e)
        }
    }
}
