package com.example.data.api

import com.example.data.*
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// --- API REQ / RESP MODELS ---

data class LoginRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String
)

data class LoginResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "expires_in") val expiresIn: Long,
    @Json(name = "user") val user: UserResponse
)

data class UserResponse(
    @Json(name = "id") val id: String,
    @Json(name = "email") val email: String
)

data class SignupRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String,
    @Json(name = "data") val data: Map<String, String>? = null
)

data class SignupResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "user") val user: UserResponse? = null
)

data class RefreshRequest(
    @Json(name = "refresh_token") val refreshToken: String
)

data class ProfileResponse(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "email") val email: String,
    @Json(name = "is_active") val isActive: Boolean,
    @Json(name = "pin_hash") val pinHash: String? = null,
    @Json(name = "biometric_enabled") val biometricEnabled: Boolean = false
)

data class ProfileUpdate(
    @Json(name = "name") val name: String? = null,
    @Json(name = "pin_hash") val pinHash: String? = null,
    @Json(name = "biometric_enabled") val biometricEnabled: Boolean? = null,
    @Json(name = "is_active") val isActive: Boolean? = null
)

data class UserRoleResponse(
    @Json(name = "user_id") val userId: String,
    @Json(name = "role") val role: String
)

data class EmployeeApi(
    @Json(name = "id") val id: String,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "employee_code") val employeeCode: String? = null,
    @Json(name = "full_name") val fullName: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "role") val role: String? = null,
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "commission_percentage") val commissionPercentage: Double? = null,
    @Json(name = "phone") val phone: String? = null
)

data class EmployeeListAdminResponse(
    @Json(name = "employees") val employees: List<EmployeeApi>? = null
)

data class CreateEmployeeAdminRequest(
    @Json(name = "full_name") val fullName: String,
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "role") val role: String,
    @Json(name = "commission_percentage") val commissionPercentage: Double? = null
)

data class UpdateEmployeeStatusAdminRequest(
    @Json(name = "employee_id") val employeeId: String,
    @Json(name = "is_active") val isActive: Boolean
)

data class DeleteUserAdminRequest(
    @Json(name = "employee_id") val employeeId: String
)

data class CreateEmployeeAdminResponse(
    @Json(name = "employee") val employee: EmployeeApi? = null,
    @Json(name = "id") val id: String? = null,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "employee_code") val employeeCode: String? = null,
    @Json(name = "full_name") val fullName: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "role") val role: String? = null,
    @Json(name = "is_active") val isActive: Boolean = true
) {
    fun toEmployeeApi(): EmployeeApi? {
        if (employee != null) return employee
        if (!id.isNullOrBlank()) {
            return EmployeeApi(
                id = id,
                userId = userId,
                employeeCode = employeeCode,
                fullName = fullName,
                email = email,
                role = role,
                isActive = isActive
            )
        }
        return null
    }
}

data class CustomerApi(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "phone") val phone: String,
    @Json(name = "email") val email: String? = null,
    @Json(name = "address") val address: String? = null
)

data class PackageApi(
    @Json(name = "id") val id: String? = null,
    @Json(name = "tracking_number") val trackingNumber: String,
    @Json(name = "customer_id") val customerId: String? = null,
    @Json(name = "consignee_name") val consigneeName: String? = null,
    @Json(name = "consignee_phone") val consigneePhone: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "weight_kg") val weightKg: Double? = null,
    @Json(name = "pcs") val pcs: Int? = null,
    @Json(name = "mode") val mode: String? = null,
    @Json(name = "origin") val origin: String? = null,
    @Json(name = "destination") val destination: String? = null,
    @Json(name = "amount_due") val amountDue: Int = 0,
    @Json(name = "status") val status: String = "received",
    @Json(name = "received_by_employee_id") val receivedByEmployeeId: String? = null,
    @Json(name = "sales_rep") val salesRep: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "customers") val customers: CustomerApi? = null,
    @Json(name = "payments") val payments: List<PaymentApi>? = null,
    @Json(name = "package_status_history") val packageStatusHistory: List<PackageStatusHistoryApi>? = null
)

data class PaymentApi(
    @Json(name = "id") val id: String? = null,
    @Json(name = "package_id") val packageId: String? = null,
    @Json(name = "amount") val amount: Int? = null,
    @Json(name = "payment_method") val paymentMethod: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "mpesa_receipt") val mpesaReceipt: String? = null,
    @Json(name = "checkout_request_id") val checkoutRequestId: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

data class DeliveryApi(
    @Json(name = "id") val id: String? = null,
    @Json(name = "package_id") val packageId: String,
    @Json(name = "collector_name") val collectorName: String,
    @Json(name = "collector_id_number") val collectorIdNumber: String? = null,
    @Json(name = "collector_phone") val collectorPhone: String? = null,
    @Json(name = "released_by_employee_id") val releasedByEmployeeId: String? = null,
    @Json(name = "signature_url") val signatureUrl: String? = null,
    @Json(name = "proof_photo_url") val proofPhotoUrl: String? = null,
    @Json(name = "delivered_at") val deliveredAt: String? = null
)

data class PackageStatusHistoryApi(
    @Json(name = "id") val id: String? = null,
    @Json(name = "package_id") val packageId: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "changed_by") val changedBy: String? = null,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

data class CargoPackageApi(
    @Json(name = "id") val id: String,
    @Json(name = "consignee") val consignee: String,
    @Json(name = "phone") val phone: String,
    @Json(name = "origin") val origin: String,
    @Json(name = "dest") val dest: String,
    @Json(name = "descr") val descr: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "mode") val mode: String,
    @Json(name = "weight") val weight: Double,
    @Json(name = "pcs") val pcs: Int,
    @Json(name = "cost") val cost: Int,
    @Json(name = "sales_rep") val salesRep: String,
    @Json(name = "status") val status: String,
    @Json(name = "registered_at") val registeredAt: String,
    @Json(name = "paid_at") val paidAt: String? = null,
    @Json(name = "collected_at") val collectedAt: String? = null,
    @Json(name = "cleared_at") val clearedAt: String? = null,
    @Json(name = "collector_name") val collectorName: String? = null,
    @Json(name = "collector_id") val collectorId: String? = null,
    @Json(name = "collector_phone") val collectorPhone: String? = null,
    @Json(name = "payment_method") val paymentMethod: String? = null,
    @Json(name = "payment_ref") val paymentRef: String? = null,
    @Json(name = "package_photo_url") val packagePhotoUrl: String? = null,
    @Json(name = "package_photo_captured_at") val packagePhotoCapturedAt: String? = null,
    @Json(name = "package_photo_captured_by") val packagePhotoCapturedBy: String? = null,
    @Json(name = "signature_points") val signaturePoints: String? = null
)

data class PaymentNotificationApi(
    @Json(name = "id") val id: String,
    @Json(name = "notification_number") val notificationNumber: String? = null,
    @Json(name = "evidence_type") val evidenceType: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "text_content") val textContent: String? = null,
    @Json(name = "uploaded_by") val uploadedBy: String? = null,
    @Json(name = "uploaded_at") val uploadedAt: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "amount") val amount: Int? = null,
    @Json(name = "sender_phone") val senderPhone: String? = null,
    @Json(name = "timestamp") val timestamp: String? = null,
    @Json(name = "mpesa_receipt") val mpesaReceipt: String? = null,
    @Json(name = "result_code") val resultCode: String? = null,
    @Json(name = "result_desc") val resultDesc: String? = null
)

data class PaymentAllocationApi(
    @Json(name = "id") val id: String,
    @Json(name = "payment_notification_id") val paymentNotificationId: String,
    @Json(name = "order_id") val orderId: String,
    @Json(name = "tracking_number") val trackingNumber: String,
    @Json(name = "allocated_amount") val allocatedAmount: Int,
    @Json(name = "linked_by") val linkedBy: String,
    @Json(name = "linked_at") val linkedAt: String,
    @Json(name = "notification_number") val notificationNumber: String? = null
)

data class AuditLogApi(
    @Json(name = "id") val id: String,
    @Json(name = "action") val action: String,
    @Json(name = "actor") val actor: String,
    @Json(name = "timestamp") val timestamp: String,
    @Json(name = "details") val details: String
)

data class AppReleaseApi(
    @Json(name = "id") val id: String? = null,
    @Json(name = "version_name") val versionName: String? = null,
    @Json(name = "build_number") val buildNumber: Int? = null,
    @Json(name = "release_notes") val releaseNotes: String? = null,
    @Json(name = "download_url") val downloadUrl: String? = null
)

data class BroadcastMessageApi(
    @Json(name = "id") val id: String,
    @Json(name = "message") val message: String,
    @Json(name = "target") val target: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "sender") val sender: String? = null,
    @Json(name = "timestamp") val timestamp: String? = null
)

data class CommissionApi(
    @Json(name = "id") val id: String,
    @Json(name = "order_id") val orderId: String,
    @Json(name = "employee_id") val employeeId: String,
    @Json(name = "commission_type") val commissionType: String,
    @Json(name = "gross_profit") val grossProfit: Double,
    @Json(name = "rate") val rate: Double,
    @Json(name = "amount") val amount: Double,
    @Json(name = "status") val status: String,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "approved_at") val approvedAt: String? = null,
    @Json(name = "paid_at") val paidAt: String? = null,
    @Json(name = "payment_reference") val paymentReference: String? = null,
    @Json(name = "reference") val reference: String? = null
)

data class CommissionRateApi(
    @Json(name = "id") val id: String,
    @Json(name = "role") val role: String,
    @Json(name = "rate") val rate: Double
)

data class StkPushRequest(
    @Json(name = "phone") val phone: String,
    @Json(name = "phone_number") val phoneNumber: String = phone,
    @Json(name = "PhoneNumber") val darajaPhoneNumber: String = phone,
    @Json(name = "amount") val amount: Int,
    @Json(name = "Amount") val darajaAmount: Int = amount,
    @Json(name = "tracking_number") val trackingNumber: String,
    @Json(name = "account_reference") val accountReference: String = trackingNumber,
    @Json(name = "AccountReference") val darajaAccountReference: String = trackingNumber,
    @Json(name = "description") val description: String? = null,
    @Json(name = "TransactionDesc") val darajaTransactionDesc: String? = description
)

data class StkPushResponse(
    @Json(name = "ok") val ok: Boolean = false,
    @Json(name = "notification_id") val notificationId: String? = null,
    @Json(name = "notification_number") val notificationNumber: String? = null,
    @Json(name = "checkout_request_id") val checkoutRequestId: String? = null,
    @Json(name = "merchant_request_id") val merchantRequestId: String? = null,
    @Json(name = "customer_message") val customerMessage: String? = null,
    @Json(name = "error") val error: String? = null
)

interface MpesaApi {
    @GET("api/public/admin/employees")
    suspend fun getAdminEmployees(
        @Header("Authorization") authHeader: String
    ): Response<EmployeeListAdminResponse>

    @POST("api/public/admin/employees")
    suspend fun createEmployeeAdmin(
        @Header("Authorization") authHeader: String,
        @Body body: CreateEmployeeAdminRequest
    ): Response<CreateEmployeeAdminResponse>

    @PATCH("api/public/admin/employees")
    suspend fun updateEmployeeStatusAdmin(
        @Header("Authorization") authHeader: String,
        @Body body: UpdateEmployeeStatusAdminRequest
    ): Response<Map<String, Any?>>

    @POST("api/public/admin/delete-user")
    suspend fun deleteUserAdminEndpoint(
        @Header("Authorization") authHeader: String,
        @Body body: DeleteUserAdminRequest
    ): Response<Map<String, Any?>>

    @POST("api/mpesa-stk-push")
    suspend fun stkPush(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Body req: StkPushRequest
    ): Response<StkPushResponse>

    @POST("api/public/gemini-ocr")
    suspend fun geminiOcr(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Body req: Map<String, String>
    ): Response<Map<String, Any?>>

    @POST("api/public/send-sms")
    suspend fun sendSms(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Body req: Map<String, String>
    ): Response<Map<String, Any?>>

    @POST
    suspend fun stkPushDynamic(
        @Url url: String,
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Body req: StkPushRequest
    ): Response<StkPushResponse>
}

// --- RETROFIT INTERFACE ---

interface SupabaseApi {
    @POST("auth/v1/token?grant_type=password")
    suspend fun login(
        @Header("apikey") apiKey: String,
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshToken(
        @Header("apikey") apiKey: String,
        @Body request: RefreshRequest
    ): Response<LoginResponse>

    @POST("auth/v1/signup")
    suspend fun signup(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String? = null,
        @Body request: SignupRequest
    ): Response<SignupResponse>

    @POST("auth/v1/logout")
    suspend fun logout(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): Response<Unit>

    @GET("rest/v1/profiles")
    suspend fun getProfile(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("id") idFilter: String,
        @Query("select") select: String = "*"
    ): List<ProfileResponse>

    @GET("rest/v1/profiles")
    suspend fun getProfileByEmail(
        @Header("apikey") apiKey: String = SupabaseClient.SERVICE_ROLE_KEY,
        @Header("Authorization") authHeader: String = SupabaseClient.getServiceRoleBearerHeader(),
        @Query("email") emailFilter: String,
        @Query("select") select: String = "*"
    ): List<ProfileResponse>

    @GET("rest/v1/employees")
    suspend fun getEmployeeByEmail(
        @Header("apikey") apiKey: String = SupabaseClient.SERVICE_ROLE_KEY,
        @Header("Authorization") authHeader: String = SupabaseClient.getServiceRoleBearerHeader(),
        @Query("email") emailFilter: String,
        @Query("select") select: String = "*"
    ): List<EmployeeApi>

    @GET("rest/v1/profiles")
    suspend fun getAllProfiles(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("select") select: String = "*"
    ): List<ProfileResponse>

    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("id") idFilter: String,
        @Body profile: ProfileUpdate
    ): Response<Unit>

    @GET("rest/v1/user_roles")
    suspend fun getUserRoles(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("user_id") userIdFilter: String,
        @Query("select") select: String = "*"
    ): List<UserRoleResponse>

    @GET("rest/v1/user_roles")
    suspend fun getAllUserRoles(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("select") select: String = "*"
    ): List<UserRoleResponse>

    @POST("rest/v1/user_roles")
    suspend fun createUserRole(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body role: UserRoleResponse
    ): Response<Unit>

    @POST("rest/v1/profiles")
    suspend fun createProfile(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body profile: ProfileResponse
    ): Response<Unit>

    @DELETE("rest/v1/profiles")
    suspend fun deleteProfile(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("id") idFilter: String
    ): Response<Unit>

    @DELETE("rest/v1/employees")
    suspend fun deleteEmployeeRest(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String = SupabaseClient.getBearerHeader(),
        @Query("id") idFilter: String
    ): Response<Unit>

    @DELETE("rest/v1/employees")
    suspend fun deleteEmployeeRestByEmail(
        @Header("apikey") apiKey: String = SupabaseClient.SERVICE_ROLE_KEY,
        @Header("Authorization") authHeader: String = SupabaseClient.getServiceRoleBearerHeader(),
        @Query("email") emailFilter: String
    ): Response<Unit>

    @DELETE("rest/v1/profiles")
    suspend fun deleteProfileByEmail(
        @Header("apikey") apiKey: String = SupabaseClient.SERVICE_ROLE_KEY,
        @Header("Authorization") authHeader: String = SupabaseClient.getServiceRoleBearerHeader(),
        @Query("email") emailFilter: String
    ): Response<Unit>

    @POST("rest/v1/employees")
    suspend fun insertEmployeeRest(
        @Header("apikey") apiKey: String = SupabaseClient.SERVICE_ROLE_KEY,
        @Header("Authorization") authHeader: String = SupabaseClient.getServiceRoleBearerHeader(),
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Unit>

    @POST("rest/v1/user_roles")
    suspend fun insertUserRoleRest(
        @Header("apikey") apiKey: String = SupabaseClient.SERVICE_ROLE_KEY,
        @Header("Authorization") authHeader: String = SupabaseClient.getServiceRoleBearerHeader(),
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Unit>

    @POST("auth/v1/admin/users")
    suspend fun createAuthUserAdmin(
        @Header("apikey") apiKey: String = SupabaseClient.SERVICE_ROLE_KEY,
        @Header("Authorization") authHeader: String = SupabaseClient.getServiceRoleBearerHeader(),
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Map<String, Any?>>

    @DELETE("rest/v1/user_roles")
    suspend fun deleteUserRole(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("user_id") userIdFilter: String
    ): Response<Unit>

    @GET("rest/v1/employees")
    suspend fun getEmployeeByUserId(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Query("user_id") userIdFilter: String,
        @Query("select") select: String = "id,user_id,employee_code,full_name,email,role,is_active,commission_percentage,phone"
    ): List<EmployeeApi>

    @GET("rest/v1/employees")
    suspend fun getAllEmployees(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Query("select") select: String = "id,user_id,employee_code,full_name,email,role,is_active,commission_percentage,phone"
    ): List<EmployeeApi>

    @GET("api/public/admin/employees")
    suspend fun getAdminEmployees(
        @Header("Authorization") authHeader: String
    ): Response<EmployeeListAdminResponse>

    @POST("api/public/admin/employees")
    suspend fun createEmployeeAdmin(
        @Header("Authorization") authHeader: String,
        @Body body: CreateEmployeeAdminRequest
    ): Response<CreateEmployeeAdminResponse>

    @PATCH("api/public/admin/employees")
    suspend fun updateEmployeeStatusAdmin(
        @Header("Authorization") authHeader: String,
        @Body body: UpdateEmployeeStatusAdminRequest
    ): Response<Map<String, Any?>>

    @POST("api/public/admin/delete-user")
    suspend fun deleteUserAdminEndpoint(
        @Header("Authorization") authHeader: String,
        @Body body: DeleteUserAdminRequest
    ): Response<Map<String, Any?>>

    @POST("api/mpesa-stk-push")
    suspend fun stkPush(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Body req: StkPushRequest
    ): Response<StkPushResponse>

    @POST("api/admin/delete-user")
    suspend fun deleteUser(
        @Query("user_id") userId: String,
        @Header("apikey") apiKey: String = SupabaseClient.SERVICE_ROLE_KEY,
        @Header("Authorization") authHeader: String = SupabaseClient.getServiceRoleBearerHeader()
    ): Response<Unit>

    @POST("api/admin/delete-user")
    suspend fun deleteUserAdmin(
        @Header("apikey") apiKey: String = SupabaseClient.SERVICE_ROLE_KEY,
        @Header("Authorization") authHeader: String,
        @Body body: Map<String, String>
    ): Response<Unit>

    @DELETE("auth/v1/admin/users/{userId}")
    suspend fun deleteAuthUserAdmin(
        @Header("apikey") apiKey: String = SupabaseClient.SERVICE_ROLE_KEY,
        @Header("Authorization") authHeader: String,
        @Path("userId") userId: String
    ): Response<Unit>

    @POST("api/public/gemini-ocr")
    suspend fun ocrGemini(
        @Header("Authorization") authHeader: String,
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Body body: Map<String, String>
    ): Response<Map<String, Any?>>

    @GET("rest/v1/packages")
    suspend fun getPackages(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Query("select") select: String = "*,customers(*),payments(*),package_status_history(*)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 100
    ): List<PackageApi>

    @GET("rest/v1/packages")
    suspend fun getPackageByTrackingNumber(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Query("tracking_number") trackingNumberFilter: String,
        @Query("select") select: String = "*,customers(*),payments(*),package_status_history(*)"
    ): List<PackageApi>

    @POST("rest/v1/packages")
    suspend fun insertPackage(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: PackageApi
    ): Response<List<PackageApi>>

    @PATCH("rest/v1/packages")
    suspend fun updatePackage(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Query("id") idFilter: String,
        @Body body: Map<String, Any?>
    ): Response<Unit>

    @POST("rest/v1/rpc/transition_package_status")
    suspend fun transitionPackageStatus(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Body body: Map<String, String>
    ): Response<Unit>

    @GET("rest/v1/customers")
    suspend fun getCustomers(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Query("phone") phoneFilter: String,
        @Query("select") select: String = "*"
    ): List<CustomerApi>

    @POST("rest/v1/customers")
    suspend fun upsertCustomer(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
        @Body body: CustomerApi
    ): Response<List<CustomerApi>>

    @POST("rest/v1/deliveries")
    suspend fun insertDelivery(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: DeliveryApi
    ): Response<Unit>

    @GET("rest/v1/payments")
    suspend fun getPaymentsByCheckoutId(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Query("checkout_request_id") checkoutFilter: String,
        @Query("select") select: String = "*"
    ): List<PaymentApi>

    @GET("rest/v1/payments")
    suspend fun getPaymentsByPackageId(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String,
        @Query("package_id") packageIdFilter: String,
        @Query("select") select: String = "*"
    ): List<PaymentApi>

    @GET("rest/v1/cargo_packages")
    suspend fun getCargoPackages(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "registered_at.desc",
        @Query("limit") limit: Int = 50
    ): List<CargoPackageApi>

    @GET("rest/v1/cargo_packages")
    suspend fun getCargoPackageById(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("id") idFilter: String,
        @Query("select") select: String = "*"
    ): List<CargoPackageApi>

    @POST("rest/v1/cargo_packages")
    suspend fun insertCargoPackage(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: CargoPackageApi
    ): Response<List<CargoPackageApi>>

    @POST("rest/v1/cargo_packages")
    suspend fun insertCargoPackages(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
        @Body body: List<CargoPackageApi>
    ): Response<List<CargoPackageApi>>

    @PATCH("rest/v1/cargo_packages")
    suspend fun updateCargoPackage(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("id") idFilter: String,
        @Body body: Map<String, Any?>
    ): Response<Unit>

    @GET("rest/v1/payment_notifications")
    suspend fun getPaymentNotifications(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "uploaded_at.desc"
    ): List<PaymentNotificationApi>

    @GET("rest/v1/payment_notifications")
    suspend fun getPaymentNotificationById(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("id") idFilter: String,
        @Query("select") select: String = "status,mpesa_receipt,result_code,result_desc,amount,sender_phone"
    ): List<PaymentNotificationApi>

    @POST("rest/v1/payment_notifications")
    suspend fun insertPaymentNotification(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Body body: PaymentNotificationApi
    ): Response<Unit>

    @PATCH("rest/v1/payment_notifications")
    suspend fun updatePaymentNotification(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("id") idFilter: String,
        @Body body: Map<String, Any?>
    ): Response<Unit>

    @GET("rest/v1/payment_allocations")
    suspend fun getPaymentAllocations(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("select") select: String = "*"
    ): List<PaymentAllocationApi>

    @POST("rest/v1/payment_allocations")
    suspend fun insertPaymentAllocation(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Body body: PaymentAllocationApi
    ): Response<Unit>

    @GET("rest/v1/app_releases")
    suspend fun getLatestRelease(
        @Header("apikey") apiKey: String = SupabaseClient.API_KEY,
        @Header("Authorization") authHeader: String = "Bearer ${SupabaseClient.API_KEY}",
        @Query("select") select: String = "*",
        @Query("order") order: String = "build_number.desc",
        @Query("limit") limit: Int = 1
    ): List<AppReleaseApi>

    @GET("rest/v1/audit_logs")
    suspend fun getAuditLogs(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "timestamp.desc"
    ): List<AuditLogApi>

    @POST("rest/v1/audit_logs")
    suspend fun insertAuditLog(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Body body: AuditLogApi
    ): Response<Unit>

    @GET("rest/v1/broadcast_messages")
    suspend fun getBroadcastMessages(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "id.desc"
    ): List<BroadcastMessageApi>

    @POST("rest/v1/broadcast_messages")
    suspend fun insertBroadcastMessage(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Body body: BroadcastMessageApi
    ): Response<Unit>

    @POST("storage/v1/object/package-photos/{packageId}/{filename}")
    suspend fun uploadPackagePhoto(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Content-Type") contentType: String = "image/jpeg",
        @Path("packageId") packageId: String,
        @Path("filename") filename: String,
        @Body photoBytes: RequestBody
    ): Response<Unit>

    @POST("storage/v1/object/proofs/{filename}")
    suspend fun uploadProofPhoto(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Content-Type") contentType: String = "image/jpeg",
        @Path("filename") filename: String,
        @Body photoBytes: RequestBody
    ): Response<Unit>

    @POST("storage/v1/object/signatures/{filename}")
    suspend fun uploadSignaturePhoto(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Content-Type") contentType: String = "image/png",
        @Path("filename") filename: String,
        @Body photoBytes: RequestBody
    ): Response<Unit>

    @GET("storage/v1/object/package-photos/{packageId}/{filename}")
    suspend fun downloadPackagePhoto(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Path("packageId") packageId: String,
        @Path("filename") filename: String
    ): okhttp3.ResponseBody

    @GET("rest/v1/commissions")
    suspend fun getCommissions(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("employee_id") employeeFilter: String? = null,
        @Query("status") statusFilter: String? = null,
        @Query("order") order: String = "created_at.desc"
    ): List<CommissionApi>

    @POST("rest/v1/rpc/approve_commission")
    suspend fun approveCommission(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Body body: Map<String, String>
    ): Response<Unit>

    @POST("rest/v1/rpc/mark_commission_paid")
    suspend fun markCommissionPaid(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Body body: Map<String, String>
    ): Response<Unit>

    @GET("rest/v1/commission_rates")
    suspend fun getCommissionRates(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("select") select: String = "*"
    ): List<CommissionRateApi>
}

// --- SINGLETON CLIENT CONTEXT ---

object SupabaseClient {
    const val BASE_URL = "https://bxbpuqzrbvkfrmwohqwd.supabase.co/"
    const val API_KEY = "sb_publishable_2aAwawQ3-zBwZTu3lE6n6Q__-g2fWsN"
    const val SERVICE_ROLE_KEY = API_KEY

    fun getServiceRoleBearerHeader(): String = getBearerHeader()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Current Session Holder
    @Volatile var accessToken: String? = null
    @Volatile var refreshToken: String? = null
    @Volatile var currentUserId: String? = null
    @Volatile var currentUserEmail: String? = null
    @Volatile var tokenExpiryTime: Long = 0L

    @Volatile var onSessionChanged: ((token: String?, refresh: String?, userId: String?, email: String?, expiry: Long) -> Unit)? = null

    @Volatile private var isRefreshing = false

    fun saveSession(token: String, refresh: String, userId: String, email: String, expiresInSec: Long) {
        accessToken = token
        refreshToken = refresh
        currentUserId = userId
        currentUserEmail = email
        val expiry = System.currentTimeMillis() + (expiresInSec * 1000)
        tokenExpiryTime = expiry
        onSessionChanged?.invoke(token, refresh, userId, email, expiry)
    }

    fun clearSession() {
        accessToken = null
        refreshToken = null
        currentUserId = null
        currentUserEmail = null
        tokenExpiryTime = 0L
        onSessionChanged?.invoke(null, null, null, null, 0L)
    }

    fun refreshSessionSync(): Boolean {
        val currentRefresh = refreshToken ?: return false
        if (isRefreshing) return false
        isRefreshing = true
        try {
            val syncClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val adapter = moshi.adapter(RefreshRequest::class.java)
            val jsonBody = adapter.toJson(RefreshRequest(currentRefresh))

            val requestBody = RequestBody.create(
                "application/json; charset=utf-8".toMediaTypeOrNull(),
                jsonBody
            )

            val request = okhttp3.Request.Builder()
                .url("${BASE_URL}auth/v1/token?grant_type=refresh_token")
                .header("apikey", API_KEY)
                .post(requestBody)
                .build()

            val response = syncClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val loginResponseAdapter = moshi.adapter(LoginResponse::class.java)
                    val loginResponse = loginResponseAdapter.fromJson(responseBody)
                    if (loginResponse != null) {
                        saveSession(
                            token = loginResponse.accessToken,
                            refresh = loginResponse.refreshToken,
                            userId = loginResponse.user.id,
                            email = loginResponse.user.email,
                            expiresInSec = loginResponse.expiresIn
                        )
                        return true
                    }
                }
            } else {
                if (response.code == 400) {
                    clearSession()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isRefreshing = false
        }
        return false
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val isAuthUrl = path.contains("/auth/v1/")

            var processedRequest = request
            val authHeader = request.header("Authorization")
            if (!isAuthUrl && authHeader != null && authHeader.startsWith("Bearer ")) {
                val now = System.currentTimeMillis()
                val isExpiring = tokenExpiryTime > 0L && (tokenExpiryTime - now < 30000)

                if (isExpiring) {
                    val success = refreshSessionSync()
                    if (success) {
                        processedRequest = request.newBuilder()
                            .header("Authorization", getBearerHeader())
                            .build()
                    }
                }
            }

            var response = chain.proceed(processedRequest)

            if (!isAuthUrl && response.code == 401) {
                response.close()
                val success = refreshSessionSync()
                if (success) {
                    val retryRequest = request.newBuilder()
                        .header("Authorization", getBearerHeader())
                        .build()
                    response = chain.proceed(retryRequest)
                }
            }

            response
        }
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val isBackend = url.contains("lovable.app") || url.contains("/api/")

            if (isBackend) {
                val reqBodyStr = try {
                    val buffer = okio.Buffer()
                    request.body?.writeTo(buffer)
                    buffer.readUtf8()
                } catch (e: Exception) {
                    "[Error reading request body: ${e.message}]"
                }
                Log.d("BACKEND_HTTP", "===> HTTP ${request.method} $url\nHeaders: ${request.headers}\nBody: $reqBodyStr")
            }

            val response = chain.proceed(request)

            if (isBackend) {
                val responseBodyStr = try {
                    val source = response.body?.source()
                    source?.request(Long.MAX_VALUE)
                    val buffer = source?.buffer
                    buffer?.clone()?.readUtf8() ?: "[Empty body]"
                } catch (e: Exception) {
                    "[Error reading response body: ${e.message}]"
                }
                val logMsg = "<=== HTTP ${response.code} ${response.message} $url\nHeaders: ${response.headers}\nResponse Body: $responseBodyStr"
                if (response.isSuccessful) {
                    Log.d("BACKEND_HTTP", logMsg)
                } else {
                    Log.e("BACKEND_HTTP", logMsg)
                }
            }

            response
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: SupabaseApi = retrofit.create(SupabaseApi::class.java)

    private val mpesaRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://project--5e9b81ad-6c63-4331-af7a-01008019e17f.lovable.app/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val backendApi: MpesaApi = mpesaRetrofit.create(MpesaApi::class.java)
    val mpesaApi: MpesaApi = backendApi

    fun getBearerHeader(): String {
        val token = accessToken
        return if (!token.isNullOrBlank()) "Bearer $token" else ""
    }

    fun isUserLoggedIn(): Boolean {
        return accessToken != null
    }
}

// --- EXTENSION MAPPER FUNCTIONS ---

fun CargoPackage.toApi(): CargoPackageApi = CargoPackageApi(
    id = id,
    consignee = consignee,
    phone = phone,
    origin = origin,
    dest = dest,
    descr = desc, // Entities: desc -> Api: descr
    description = desc,
    mode = mode,
    weight = weight,
    pcs = pcs,
    cost = cost,
    salesRep = salesRep,
    status = if (status == "collected") "cleared" else status,
    registeredAt = registeredAt,
    paidAt = paidAt,
    collectedAt = collectedAt,
    clearedAt = collectedAt,
    collectorName = collectorName,
    collectorId = collectorId,
    collectorPhone = collectorPhone,
    paymentMethod = paymentMethod,
    paymentRef = paymentRef,
    packagePhotoUrl = packagePhotoUrl,
    packagePhotoCapturedAt = packagePhotoCapturedAt,
    packagePhotoCapturedBy = packagePhotoCapturedBy,
    signaturePoints = signaturePoints
)

fun CargoPackageApi.toEntity(syncPending: Boolean = false): CargoPackage = CargoPackage(
    id = id,
    consignee = consignee,
    phone = phone,
    origin = origin,
    dest = dest,
    desc = descr ?: description ?: "",
    mode = mode,
    weight = weight,
    pcs = pcs,
    cost = cost,
    salesRep = salesRep,
    status = if (status == "cleared") "cleared" else status,
    registeredAt = registeredAt,
    paidAt = paidAt,
    collectedAt = collectedAt ?: clearedAt,
    collectorName = collectorName,
    collectorId = collectorId,
    collectorPhone = collectorPhone,
    paymentMethod = paymentMethod,
    paymentRef = paymentRef,
    packagePhotoUrl = packagePhotoUrl,
    packagePhotoCapturedAt = packagePhotoCapturedAt,
    packagePhotoCapturedBy = packagePhotoCapturedBy,
    signaturePoints = signaturePoints,
    syncPending = syncPending
)

fun PaymentNotification.toApi(): PaymentNotificationApi = PaymentNotificationApi(
    id = id,
    notificationNumber = notificationNumber,
    evidenceType = evidenceType,
    imageUrl = imageUrl,
    textContent = textContent,
    uploadedBy = uploadedBy,
    uploadedAt = uploadedAt,
    status = status,
    amount = amount,
    senderPhone = senderPhone,
    timestamp = timestamp
)

fun PaymentNotificationApi.toEntity(): PaymentNotification = PaymentNotification(
    id = id,
    notificationNumber = notificationNumber ?: "",
    evidenceType = evidenceType ?: "STK",
    imageUrl = imageUrl,
    textContent = textContent,
    uploadedBy = uploadedBy ?: "System",
    uploadedAt = uploadedAt ?: "",
    status = status,
    amount = amount,
    senderPhone = senderPhone,
    timestamp = timestamp
)

fun PaymentAllocation.toApi(): PaymentAllocationApi = PaymentAllocationApi(
    id = id,
    paymentNotificationId = paymentNotificationId,
    orderId = orderId,
    trackingNumber = trackingNumber,
    allocatedAmount = allocatedAmount,
    linkedBy = linkedBy,
    linkedAt = linkedAt,
    notificationNumber = notificationNumber ?: paymentNotificationId
)

fun PaymentAllocationApi.toEntity(): PaymentAllocation = PaymentAllocation(
    id = id,
    paymentNotificationId = paymentNotificationId,
    orderId = orderId,
    trackingNumber = trackingNumber,
    allocatedAmount = allocatedAmount,
    linkedBy = linkedBy,
    linkedAt = linkedAt,
    notificationNumber = notificationNumber ?: paymentNotificationId
)

fun AuditLog.toApi(): AuditLogApi = AuditLogApi(
    id = id,
    action = action,
    actor = actor,
    timestamp = timestamp,
    details = details
)

fun AuditLogApi.toEntity(): AuditLog = AuditLog(
    id = id,
    action = action,
    actor = actor,
    timestamp = timestamp,
    details = details
)

fun BroadcastMessage.toApi(): BroadcastMessageApi = BroadcastMessageApi(
    id = id,
    message = message,
    target = target,
    createdAt = createdAt,
    sender = sender,
    timestamp = timestamp
)

fun BroadcastMessageApi.toEntity(): BroadcastMessage = BroadcastMessage(
    id = id,
    message = message,
    target = target,
    createdAt = createdAt,
    sender = sender,
    timestamp = timestamp
)

fun EmployeeApi.toEntity(
    password: String = "password",
    existingPin: String? = null,
    existingBiometric: Boolean = false
): Employee {
    val localRole = when (role?.lowercase()) {
        "admin" -> "admin"
        "sales_manager", "sm" -> "sm"
        "logistics_manager", "lm" -> "lm"
        "sales_rep", "sr" -> "sr"
        else -> role ?: "sr"
    }
    return Employee(
        id = id,
        name = fullName ?: email?.split("@")?.first()?.replaceFirstChar { it.uppercase() } ?: id,
        email = email ?: "",
        password = password,
        role = localRole,
        isActive = isActive,
        pin = existingPin,
        biometricEnabled = existingBiometric
    )
}

fun ProfileResponse.toEmployee(role: String, originalPasswordPlain: String = "password"): Employee = Employee(
    id = id,
    name = name,
    email = email,
    password = originalPasswordPlain,
    role = role,
    isActive = isActive,
    pin = pinHash,
    biometricEnabled = biometricEnabled
)

fun PackageApi.toEntity(syncPending: Boolean = false): CargoPackage {
    val completedPayment = payments?.firstOrNull { 
        val st = it.status?.lowercase()
        st == "completed" || st == "paid" || st == "success" 
    }
    val displayStatus = when (status.lowercase()) {
        "paid", "ready_for_collection" -> "paid"
        "collected", "cleared" -> "collected"
        else -> "registered"
    }
    return CargoPackage(
        id = trackingNumber,
        consignee = customers?.name ?: consigneeName ?: "Consignee",
        phone = customers?.phone ?: consigneePhone ?: "",
        origin = origin ?: "Guangzhou, CN",
        dest = destination ?: "Nairobi, KE",
        desc = description ?: "",
        mode = mode ?: "Sea Freight",
        weight = weightKg ?: 0.0,
        pcs = pcs ?: 1,
        cost = amountDue,
        salesRep = salesRep ?: "Unassigned",
        status = displayStatus,
        registeredAt = createdAt ?: "",
        paidAt = completedPayment?.createdAt,
        collectedAt = null,
        collectorName = null,
        collectorId = null,
        collectorPhone = null,
        paymentMethod = completedPayment?.paymentMethod ?: "M-PESA",
        paymentRef = completedPayment?.mpesaReceipt,
        packagePhotoUrl = null,
        syncPending = syncPending
    )
}

fun CargoPackage.toPackageApi(packageUuid: String? = null, customerId: String? = null, employeeId: String? = null): PackageApi {
    val canonicalStatus = when (status.lowercase()) {
        "registered" -> "received"
        "paid", "cleared" -> "paid"
        "collected" -> "collected"
        else -> status
    }
    return PackageApi(
        id = packageUuid,
        trackingNumber = id,
        customerId = customerId,
        consigneeName = consignee,
        consigneePhone = phone,
        description = desc,
        weightKg = weight,
        pcs = pcs,
        mode = mode,
        origin = origin,
        destination = dest,
        amountDue = cost,
        status = canonicalStatus,
        receivedByEmployeeId = employeeId,
        salesRep = salesRep,
        createdAt = registeredAt
    )
}

