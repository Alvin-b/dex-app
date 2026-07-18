package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import java.util.Random
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CargoPackage
import com.example.data.PaymentNotification
import com.example.ui.DexcargoViewModel
import com.example.ui.Screen
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun PaymentNotificationCenterScreen(viewModel: DexcargoViewModel) {
    val tab by viewModel.activePaymentTab.collectAsState()
    val notifications by viewModel.paymentNotifications.collectAsState()
    val allocations by viewModel.paymentAllocations.collectAsState()
    val currentEmp by viewModel.currentEmployee.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(bottom = 76.dp)
    ) {
        // HEADER
        ScreenHeader(
            title = "Payment Notification Center",
            actions = {
                currentEmp?.let {
                    RoleBadge(role = it.role, id = it.id.split("-")[1])
                }
            }
        )

        // TAB NAVIGATION
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            TabItemLine(
                label = "Pending Inbox",
                active = tab == "inbox",
                onClick = { viewModel.activePaymentTab.value = "inbox" },
                modifier = Modifier.weight(1f)
            )
            TabItemLine(
                label = "Audit Ledger",
                active = tab == "audit",
                onClick = { viewModel.activePaymentTab.value = "audit" },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (tab == "inbox") {
            // PENDING INBOX VIEW
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ADMIN UPLOAD BOX
                if (currentEmp?.role == "admin") {
                    item {
                        AdminUploadCard(viewModel = viewModel)
                    }
                }

                val pendings = notifications.filter { it.status == "PENDING" }
                if (pendings.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("No pending payment notifications.", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                } else {
                    items(pendings) { item ->
                        PendingNotificationRow(item = item, viewModel = viewModel)
                    }
                }
            }
        } else {
            // AUDIT LEDGER VIEW
            val linkeds = notifications.filter { it.status == "LINKED" }
            if (linkeds.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No audited payment ledger records.", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(linkeds) { item ->
                        AuditedNotificationRow(item = item, allocations = allocations.filter { it.paymentNotificationId == item.id })
                    }
                }
            }
        }
    }
}

@Composable
fun LinkPaymentScreen(viewModel: DexcargoViewModel) {
    val currentNotifId by viewModel.linkingNotifId.collectAsState()
    val notifications by viewModel.paymentNotifications.collectAsState()
    val packages by viewModel.cargoPackages.collectAsState()

    val context = LocalContext.current

    val notif = remember(notifications, currentNotifId) {
        notifications.find { it.id == currentNotifId }
    }

    if (notif == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Notification not found", color = TextPrimary)
        }
        return
    }

    val searchQuery = remember { mutableStateOf("") }
    val searchResults = remember(packages, searchQuery.value) {
        if (searchQuery.value.isBlank()) emptyList()
        else {
            packages.filter {
                it.id.contains(searchQuery.value, ignoreCase = true) ||
                        it.consignee.contains(searchQuery.value, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(bottom = 76.dp)
    ) {
        ScreenHeader(
            title = "Link Payment",
            onBack = { viewModel.navigateBack() }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // EVIDENCE HEADER PREVIEW
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text("Notification Ref: ${notif.notificationNumber}", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (notif.evidenceType == "IMAGE") {
                        MockSvgWidget(imageName = notif.imageUrl ?: "mpesa")
                    } else {
                        Text(
                            text = notif.textContent ?: "",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBg)
                                .padding(8.dp)
                        )
                    }
                }
            }

            // ALLOCATED ORDERS CONTAINER
            item {
                SectionTitle(text = "Allocated Target Orders")
                if (viewModel.selectedLinkOrders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No packages selected yet.", color = TextMuted, fontSize = 11.sp, fontStyle = FontStyle.Italic)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        viewModel.selectedLinkOrders.forEach { itemPkg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceVariant)
                                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(itemPkg.id, color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Text("${itemPkg.consignee} · Bal: KES ${itemPkg.cost.toLocaleString()}", color = TextSecondary, fontSize = 9.5.sp)
                                }
                                IconButton(
                                    onClick = { viewModel.removeOrderFromLink(itemPkg.id) },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Close, "Remove", tint = RedAccent, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // SEARCH FORM AND ADD BUTTONS
            item {
                Divider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))
                SectionTitle(text = "Search & Add Packages")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DexTextField(
                        value = searchQuery.value,
                        onValueChange = { searchQuery.value = it },
                        label = "",
                        placeholder = "Enter tracking no. or customer...",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        val unpaid = packages.find { it.status == "registered" }
                        if (unpaid != null) {
                            viewModel.addOrderToLink(unpaid)
                            Toast.makeText(context, "Barcode read: matched package ${unpaid.id}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text("📷 Scan Package Barcode", color = OrangeAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // SEARCH SELECTION PREVIEW
            if (searchResults.isNotEmpty()) {
                items(searchResults) { match ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(match.id, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("${match.consignee} · Outstanding KES ${match.cost.toLocaleString()}", color = TextSecondary, fontSize = 9.5.sp)
                        }
                        IconButton(
                            onClick = { viewModel.addOrderToLink(match) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Add, "Add", tint = BlueAccent)
                        }
                    }
                }
            }

            // LINK SUBMIT
            item {
                Spacer(modifier = Modifier.height(12.dp))
                DexButton(
                    text = "Confirm Linking & Audit Actions",
                    onClick = { viewModel.confirmPaymentLinking() },
                    enabled = viewModel.selectedLinkOrders.isNotEmpty()
                )
            }
        }
    }
}

// --- SUB-COMPONENTS FOR PAYMENT CENTER ---

@Composable
fun AdminUploadCard(viewModel: DexcargoViewModel) {
    val uploadType by viewModel.activeUploadType.collectAsState()
    val mockImage by viewModel.mockImageSelect.collectAsState()
    val mockText by viewModel.mockTextContent.collectAsState()

    var dropdownExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("UPLOAD PAYMENT EVIDENCE", color = OrangeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.activeUploadType.value = "IMAGE" },
                    colors = ButtonDefaults.buttonColors(containerColor = if (uploadType == "IMAGE") BlueAccentBg else Color.Transparent),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📷 Image", color = if (uploadType == "IMAGE") BlueAccent else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { viewModel.activeUploadType.value = "TEXT" },
                    colors = ButtonDefaults.buttonColors(containerColor = if (uploadType == "TEXT") BlueAccentBg else Color.Transparent),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📝 SMS / Notes", color = if (uploadType == "TEXT") BlueAccent else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (uploadType == "IMAGE") {
                Column {
                    Text("SELECT EVIDENCE IMAGE", color = TextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val label = when (mockImage) {
                                "mpesa_mock_1.png" -> "M-Pesa Screenshot (KES 4,200)"
                                "mpesa_mock_2.png" -> "M-Pesa Screenshot (KES 1,400)"
                                "bank_transfer.jpg" -> "Equity Bank Deposit Slip"
                                else -> "Merchant Receipt (KES 2,800)"
                            }
                            Text(label, color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, "expand", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }

                        DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }, modifier = Modifier.background(DarkSurfaceVariant)) {
                            DropdownMenuItem(text = { Text("M-Pesa Screenshot (KES 4,200)", color = TextPrimary, fontSize = 11.5.sp) }, onClick = { viewModel.mockImageSelect.value = "mpesa_mock_1.png"; dropdownExpanded = false })
                            DropdownMenuItem(text = { Text("M-Pesa Screenshot (KES 1,400)", color = TextPrimary, fontSize = 11.5.sp) }, onClick = { viewModel.mockImageSelect.value = "mpesa_mock_2.png"; dropdownExpanded = false })
                            DropdownMenuItem(text = { Text("Equity Bank Deposit Slip (KES 5,600)", color = TextPrimary, fontSize = 11.5.sp) }, onClick = { viewModel.mockImageSelect.value = "bank_transfer.jpg"; dropdownExpanded = false })
                            DropdownMenuItem(text = { Text("Merchant Receipt (KES 2,800)", color = TextPrimary, fontSize = 11.5.sp) }, onClick = { viewModel.mockImageSelect.value = "receipt_custom.png"; dropdownExpanded = false })
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    DexTextField(
                        value = mockText,
                        onValueChange = { viewModel.mockTextContent.value = it },
                        label = "Add Optional Message / Memo",
                        placeholder = "e.g. Cleared for Cargo KG-098 and KG-099"
                    )
                }
            } else {
                DexTextField(
                    value = mockText,
                    onValueChange = { viewModel.mockTextContent.value = it },
                    label = "SMS Text / WhatsApp Confirmation",
                    placeholder = "Paste confirmation texts here..."
                )
            }

            Button(
                onClick = { viewModel.uploadMockPaymentEvidence() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Upload to Pending Inbox", color = Color(0xFF1A1200), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PendingNotificationRow(item: PaymentNotification, viewModel: DexcargoViewModel) {
    val currentEmp by viewModel.currentEmployee.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, OrangeAccent.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.notificationNumber, color = OrangeAccent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(OrangeAccentBg)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("PENDING", color = OrangeAccent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (item.evidenceType == "IMAGE") {
                MockSvgWidget(imageName = item.imageUrl ?: "mpesa")
                if (!item.textContent.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.textContent,
                        color = OrangeAccent,
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkBg)
                            .padding(8.dp)
                    )
                }
            } else {
                Text(
                    text = item.textContent ?: "",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBg)
                        .padding(6.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text("Uploaded by: ${item.uploadedBy}", color = TextMuted, fontSize = 8.5.sp)
            Text("Date: ${item.uploadedAt}", color = TextMuted, fontSize = 8.5.sp)

            // Dynamic Action button for non-admins
            if (currentEmp?.role != "admin") {
                Spacer(modifier = Modifier.height(6.dp))
                DexButton(
                    text = "🔗 Link Payment",
                    onClick = { viewModel.selectLinkNotification(item.id) },
                    style = BlueAccent,
                    textColor = Color.White
                )
            }
        }
    }
}

@Composable
fun AuditedNotificationRow(item: PaymentNotification, allocations: List<com.example.data.PaymentAllocation>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, GreenAccent.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.notificationNumber, color = GreenAccent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(GreenAccentBg)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("AUDITED", color = GreenAccent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (item.evidenceType == "IMAGE") {
                MockSvgWidget(imageName = item.imageUrl ?: "mpesa")
                if (!item.textContent.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.textContent,
                        color = GreenAccent,
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkBg)
                            .padding(8.dp)
                    )
                }
            } else {
                Text(
                    text = item.textContent ?: "",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBg)
                        .padding(6.dp)
                )
            }

            if (allocations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Allocated Orders (${allocations.size}):", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                allocations.forEach { alloc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkBg)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(alloc.trackingNumber, color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(alloc.linkedBy.split(" ")[0], color = TextSecondary, fontSize = 9.sp)
                        Text("KES ${alloc.allocatedAmount.toLocaleString()}", color = GreenAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text("Uploaded by: ${item.uploadedBy} on ${item.uploadedAt}", color = TextMuted, fontSize = 8.5.sp)
        }
    }
}

@Composable
fun MockSvgWidget(imageName: String) {
    val color = when {
        imageName.contains("mpesa") -> GreenAccent
        imageName.contains("bank") -> BlueAccent
        else -> PurpleAccent
    }

    val label = when {
        imageName.contains("mpesa") -> "M-PESA CONFIRMATION"
        imageName.contains("bank") -> "BANK DEPOSIT SLIP"
        else -> "MERCHANT RECEIPT"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F111A))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (imageName.contains("1")) "KES 4,200" else if (imageName.contains("2")) "KES 1,400" else "KES 5,600",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text("Ref: TXN${(1000000 + Random().nextInt(9000000))}", color = TextMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
