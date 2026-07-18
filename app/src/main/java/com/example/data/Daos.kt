package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees")
    fun getAllEmployees(): Flow<List<Employee>>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getEmployeeById(id: String): Employee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployees(employees: List<Employee>)

    @Query("UPDATE employees SET isActive = :isActive WHERE id = :id")
    suspend fun updateEmployeeActiveStatus(id: String, isActive: Boolean)

    @Query("UPDATE employees SET pin = :pin, biometricEnabled = :biometricEnabled WHERE id = :id")
    suspend fun updateEmployeePinAndBiometrics(id: String, pin: String?, biometricEnabled: Boolean)
}

@Dao
interface CargoPackageDao {
    @Query("SELECT * FROM cargo_packages")
    fun getAllPackages(): Flow<List<CargoPackage>>

    @Query("SELECT * FROM cargo_packages WHERE id = :id")
    suspend fun getPackageById(id: String): CargoPackage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackage(cargoPackage: CargoPackage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackages(packages: List<CargoPackage>)

    @Query("DELETE FROM cargo_packages")
    suspend fun clearAllPackages()
}

@Dao
interface PaymentNotificationDao {
    @Query("SELECT * FROM payment_notifications")
    fun getAllNotifications(): Flow<List<PaymentNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: PaymentNotification)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<PaymentNotification>)

    @Query("UPDATE payment_notifications SET status = :status WHERE id = :id")
    suspend fun updateNotificationStatus(id: String, status: String)

    @Query("DELETE FROM payment_notifications")
    suspend fun clearAllNotifications()
}

@Dao
interface PaymentAllocationDao {
    @Query("SELECT * FROM payment_allocations")
    fun getAllAllocations(): Flow<List<PaymentAllocation>>

    @Query("SELECT * FROM payment_allocations WHERE orderId = :orderId")
    fun getAllAllocationsForPackage(orderId: String): Flow<List<PaymentAllocation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllocation(allocation: PaymentAllocation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllocations(allocations: List<PaymentAllocation>)

    @Query("DELETE FROM payment_allocations")
    suspend fun clearAllAllocations()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<AuditLog>)

    @Query("DELETE FROM audit_logs")
    suspend fun clearAllLogs()
}

@Dao
interface BroadcastMessageDao {
    @Query("SELECT * FROM broadcast_messages ORDER BY id DESC")
    fun getAllMessages(): Flow<List<BroadcastMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: BroadcastMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<BroadcastMessage>)

    @Query("DELETE FROM broadcast_messages")
    suspend fun clearAllMessages()
}
