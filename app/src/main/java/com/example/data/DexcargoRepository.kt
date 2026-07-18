package com.example.data

import kotlinx.coroutines.flow.Flow

class DexcargoRepository(private val database: AppDatabase) {

    val employees: Flow<List<Employee>> = database.employeeDao().getAllEmployees()
    val cargoPackages: Flow<List<CargoPackage>> = database.cargoPackageDao().getAllPackages()
    val paymentNotifications: Flow<List<PaymentNotification>> = database.paymentNotificationDao().getAllNotifications()
    val paymentAllocations: Flow<List<PaymentAllocation>> = database.paymentAllocationDao().getAllAllocations()
    val auditLogs: Flow<List<AuditLog>> = database.auditLogDao().getAllLogs()
    val broadcastMessages: Flow<List<BroadcastMessage>> = database.broadcastMessageDao().getAllMessages()

    suspend fun getEmployeeById(id: String): Employee? = database.employeeDao().getEmployeeById(id)
    suspend fun insertEmployee(employee: Employee) = database.employeeDao().insertEmployee(employee)
    suspend fun updateEmployeeActiveStatus(id: String, isActive: Boolean) = database.employeeDao().updateEmployeeActiveStatus(id, isActive)
    suspend fun updateEmployeePinAndBiometrics(id: String, pin: String?, biometricEnabled: Boolean) = database.employeeDao().updateEmployeePinAndBiometrics(id, pin, biometricEnabled)

    suspend fun getPackageById(id: String): CargoPackage? = database.cargoPackageDao().getPackageById(id)
    suspend fun insertPackage(cargoPackage: CargoPackage) = database.cargoPackageDao().insertPackage(cargoPackage)

    suspend fun insertNotification(notification: PaymentNotification) = database.paymentNotificationDao().insertNotification(notification)
    suspend fun updateNotificationStatus(id: String, status: String) = database.paymentNotificationDao().updateNotificationStatus(id, status)

    fun getAllAllocationsForPackage(orderId: String): Flow<List<PaymentAllocation>> = database.paymentAllocationDao().getAllAllocationsForPackage(orderId)
    suspend fun insertAllocation(allocation: PaymentAllocation) = database.paymentAllocationDao().insertAllocation(allocation)

    suspend fun insertLog(log: AuditLog) = database.auditLogDao().insertLog(log)

    suspend fun insertMessage(message: BroadcastMessage) = database.broadcastMessageDao().insertMessage(message)

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
                salesRep = "SR-002 John Kamau",
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
                salesRep = "SR-002 John Kamau",
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
                salesRep = "Sales Manager Direct",
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
                salesRep = "SR-003 Grace Akinyi",
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
                status = "PENDING"
            ),
            PaymentNotification(
                id = "PN-2",
                notificationNumber = "PAY-20260714-0002",
                evidenceType = "TEXT",
                textContent = "M-PESA CONFIRMATION:\nTxn: QNA8B7J3D8\nAmount: KES 1,400\nFrom: Charles Ombongi\nRef: Package CAN-NBO payment confirmation.",
                uploadedBy = "ADM-001 (Admin)",
                uploadedAt = "2026-07-14 11:20 AM",
                status = "PENDING"
            ),
            PaymentNotification(
                id = "PN-3",
                notificationNumber = "PAY-20260710-0001",
                evidenceType = "IMAGE",
                imageUrl = "bank_transfer.jpg",
                uploadedBy = "ADM-001 (Admin)",
                uploadedAt = "2026-07-10 11:00 AM",
                status = "LINKED"
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
                linkedAt = "2026-07-10 11:15 AM"
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
            BroadcastMessage("BM-1", "Welcome to DEXCARGO Logistics Gateway. Secure credentials protocol active.", "all", "Just now")
        )
        database.broadcastMessageDao().insertMessages(defaultBroadcasts)
    }
}
