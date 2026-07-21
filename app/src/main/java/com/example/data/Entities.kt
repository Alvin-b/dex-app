package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val password: String,
    val role: String, // "sr" (Sales Rep), "lm" (Logistics Manager), "sm" (Sales Lead), "admin" (Admin)
    val isActive: Boolean = true,
    val pin: String? = null,
    val biometricEnabled: Boolean = false
)

@Entity(tableName = "cargo_packages")
data class CargoPackage(
    @PrimaryKey val id: String, // Tracking number, e.g. 1260707534987
    val consignee: String,
    val phone: String,
    val origin: String,
    val dest: String,
    val desc: String,
    val mode: String, // "Air Freight", "Sea Freight"
    val weight: Double,
    val pcs: Int,
    val cost: Int,
    val salesRep: String,
    val status: String, // "registered", "paid", "collected"
    val registeredAt: String,
    val paidAt: String? = null,
    val collectedAt: String? = null,
    val collectorName: String? = null,
    val collectorId: String? = null,
    val collectorPhone: String? = null,
    val paymentMethod: String? = null,
    val paymentRef: String? = null,
    val packagePhotoUrl: String? = null,
    val packagePhotoCapturedAt: String? = null,
    val packagePhotoCapturedBy: String? = null,
    val signaturePoints: String? = null, // Comma-separated coordinates or path for drawing signature
    val syncPending: Boolean = false
)

@Entity(tableName = "payment_notifications")
data class PaymentNotification(
    @PrimaryKey val id: String, // e.g. PN-12345
    val notificationNumber: String, // e.g. PAY-20260714-0001
    val evidenceType: String, // "IMAGE" or "TEXT"
    val imageUrl: String? = null,
    val textContent: String? = null,
    val uploadedBy: String,
    val uploadedAt: String,
    val status: String, // "PENDING" or "LINKED"
    val amount: Int? = null,
    val senderPhone: String? = null,
    val timestamp: String? = null
)

@Entity(tableName = "payment_allocations")
data class PaymentAllocation(
    @PrimaryKey val id: String,
    val paymentNotificationId: String,
    val orderId: String,
    val trackingNumber: String,
    val allocatedAmount: Int,
    val linkedBy: String,
    val linkedAt: String,
    val notificationNumber: String? = null
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey val id: String,
    val action: String,
    val actor: String,
    val timestamp: String,
    val details: String
)

@Entity(tableName = "broadcast_messages")
data class BroadcastMessage(
    @PrimaryKey val id: String,
    val message: String,
    val target: String, // "all", "sr", "lm", "sm"
    val createdAt: String,
    val sender: String? = null,
    val timestamp: String? = null
)
