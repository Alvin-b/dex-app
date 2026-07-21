package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.data.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SupabaseAuthRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "supabase_auth_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Graceful fallback to regular shared preferences if Android Keystore is corrupted or unavailable (e.g. Robolectric tests)
            context.getSharedPreferences("supabase_auth_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val _currentEmployee = MutableStateFlow<Employee?>(null)
    val currentEmployee: StateFlow<Employee?> = _currentEmployee.asStateFlow()

    init {
        // Load initial session on startup from secure preferences
        val accessToken = prefs.getString("access_token", null)
        val refreshToken = prefs.getString("refresh_token", null)
        val userId = prefs.getString("user_id", null)
        val userEmail = prefs.getString("user_email", null)
        val expiryTime = prefs.getLong("token_expiry_time", 0L)

        if (accessToken != null && refreshToken != null && userId != null && userEmail != null) {
            SupabaseClient.accessToken = accessToken
            SupabaseClient.refreshToken = refreshToken
            SupabaseClient.currentUserId = userId
            SupabaseClient.currentUserEmail = userEmail
            SupabaseClient.tokenExpiryTime = expiryTime
        }

        // Set session listener to persist dynamic token updates (like auto-refresh) securely
        SupabaseClient.onSessionChanged = { token, refresh, uId, uEmail, expTime ->
            prefs.edit().apply {
                if (token != null && refresh != null && uId != null && uEmail != null) {
                    putString("access_token", token)
                    putString("refresh_token", refresh)
                    putString("user_id", uId)
                    putString("user_email", uEmail)
                    putLong("token_expiry_time", expTime)
                } else {
                    remove("access_token")
                    remove("refresh_token")
                    remove("user_id")
                    remove("user_email")
                    remove("token_expiry_time")
                }
                apply()
            }
        }
    }

    suspend fun signIn(email: String, pass: String): Result<Employee> = withContext(Dispatchers.IO) {
        try {
            val response = SupabaseClient.api.login(
                apiKey = SupabaseClient.API_KEY,
                request = LoginRequest(email, pass)
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                
                // Store tokens in SupabaseClient and SharedPreferences (handled via onSessionChanged)
                saveSession(
                    token = body.accessToken,
                    refresh = body.refreshToken,
                    userId = body.user.id,
                    email = body.user.email,
                    expiresInSec = body.expiresIn
                )

                // Fetch Profile and Role from backend
                val profilesList = SupabaseClient.api.getProfile(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = SupabaseClient.getBearerHeader(),
                    idFilter = "eq.${body.user.id}"
                )
                val profile = profilesList.firstOrNull() ?: ProfileResponse(
                    id = body.user.id,
                    name = email.split("@").first().replaceFirstChar { it.uppercase() },
                    email = body.user.email,
                    isActive = true,
                    pinHash = null,
                    biometricEnabled = false
                )

                val rolesList = SupabaseClient.api.getUserRoles(
                    apiKey = SupabaseClient.API_KEY,
                    authHeader = SupabaseClient.getBearerHeader(),
                    userIdFilter = "eq.${body.user.id}"
                )
                val role = rolesList.firstOrNull()?.role ?: "sr"

                val employee = Employee(
                    id = profile.id,
                    name = profile.name,
                    email = profile.email,
                    password = pass, // Cache password for offline fallback
                    role = role,
                    isActive = profile.isActive,
                    pin = profile.pinHash,
                    biometricEnabled = profile.biometricEnabled
                )

                // Cache in local DB
                database.employeeDao().insertEmployee(employee)
                _currentEmployee.value = employee
                Result.success(employee)
            } else {
                // Try offline fallback ONLY if employee exists locally with matching cached password
                val localEmp = database.employeeDao().getAllEmployees().first().find {
                    it.email.equals(email, ignoreCase = true) && it.password == pass
                }
                if (localEmp != null && localEmp.isActive) {
                    _currentEmployee.value = localEmp
                    Result.success(localEmp)
                } else {
                    Result.failure(Exception("Authentication failed (Code: ${response.code()})"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Offline fallback on exception (like no network)
            try {
                val localEmp = database.employeeDao().getAllEmployees().first().find {
                    it.email.equals(email, ignoreCase = true) && it.password == pass
                }
                if (localEmp != null && localEmp.isActive) {
                    _currentEmployee.value = localEmp
                    Result.success(localEmp)
                } else {
                    Result.failure(e)
                }
            } catch (localEx: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun restoreSessionOnStartup(): Employee? = withContext(Dispatchers.IO) {
        val userId = SupabaseClient.currentUserId ?: return@withContext null
        try {
            // First look in local DB cache
            val localEmp = database.employeeDao().getEmployeeById(userId)
            if (localEmp != null && localEmp.isActive) {
                _currentEmployee.value = localEmp
                return@withContext localEmp
            }

            // Otherwise, fetch from Supabase if online
            val profilesList = SupabaseClient.api.getProfile(
                apiKey = SupabaseClient.API_KEY,
                authHeader = SupabaseClient.getBearerHeader(),
                idFilter = "eq.$userId"
            )
            val profile = profilesList.firstOrNull() ?: return@withContext null
            val rolesList = SupabaseClient.api.getUserRoles(
                apiKey = SupabaseClient.API_KEY,
                authHeader = SupabaseClient.getBearerHeader(),
                userIdFilter = "eq.$userId"
            )
            val role = rolesList.firstOrNull()?.role ?: "sr"

            val employee = Employee(
                id = profile.id,
                name = profile.name,
                email = profile.email,
                password = "password", // fallback
                role = role,
                isActive = profile.isActive,
                pin = profile.pinHash,
                biometricEnabled = profile.biometricEnabled
            )
            database.employeeDao().insertEmployee(employee)
            _currentEmployee.value = employee
            employee
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun signOut() {
        clearSession()
        _currentEmployee.value = null
    }

    private fun saveSession(token: String, refresh: String, userId: String, email: String, expiresInSec: Long) {
        SupabaseClient.saveSession(token, refresh, userId, email, expiresInSec)
    }

    private fun clearSession() {
        SupabaseClient.clearSession()
    }
}
