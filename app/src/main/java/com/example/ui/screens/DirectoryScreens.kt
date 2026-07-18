package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CargoPackage
import com.example.ui.DexcargoViewModel
import com.example.ui.Screen
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun PackageListScreen(viewModel: DexcargoViewModel) {
    val packages by viewModel.cargoPackages.collectAsState()
    val listFilter by viewModel.packageListFilter.collectAsState()
    val query by viewModel.packageSearchQuery.collectAsState()
    val context = LocalContext.current

    val filteredList = remember(packages, listFilter, query) {
        if (query.isNotBlank()) {
            packages.filter {
                it.id.contains(query, ignoreCase = true) ||
                        it.consignee.contains(query, ignoreCase = true) ||
                        it.phone.contains(query, ignoreCase = true) ||
                        it.desc.contains(query, ignoreCase = true)
            }
        } else {
            packages.filter {
                when (listFilter) {
                    "all" -> it.status == "registered"
                    "sea" -> it.status == "registered" && it.mode == "Sea Freight"
                    "air" -> it.status == "registered" && it.mode == "Air Freight"
                    "cleared" -> it.status == "collected" || it.status == "cleared"
                    else -> true
                }
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
            title = "Package Listings",
            onBack = { viewModel.navigateBack() }
        )

        // SEARCH BAR WITH ACCENT CODES
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DexTextField(
                value = query,
                onValueChange = { viewModel.packageSearchQuery.value = it },
                label = "",
                placeholder = "Search tracking, client name, phone...",
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    .clickable {
                        // Find first registered package and query it
                        val unpaid = packages.find { it.status == "registered" }
                        if (unpaid != null) {
                            viewModel.packageSearchQuery.value = unpaid.id
                            Toast.makeText(context, "Barcode scanned: ${unpaid.id}", Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.QrCodeScanner, "Simulate Scanner", tint = OrangeAccent, modifier = Modifier.size(16.dp))
            }
        }

        // TABS FOR IN-BOND STATUS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .border(0.dp, Color.Transparent),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TabItem(label = "All Unpaid", active = listFilter == "all", onClick = { viewModel.packageListFilter.value = "all" })
            TabItem(label = "Sea Unpaid", active = listFilter == "sea", onClick = { viewModel.packageListFilter.value = "sea" })
            TabItem(label = "Air Unpaid", active = listFilter == "air", onClick = { viewModel.packageListFilter.value = "air" })
            TabItem(label = "Cleared", active = listFilter == "cleared", onClick = { viewModel.packageListFilter.value = "cleared" })
        }

        Divider(color = DarkBorder, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

        // LIST RECORDS
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No package records found.", color = TextMuted, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList) { pkg ->
                    PackageCardRow(pkg = pkg, onClick = {
                        viewModel.selectedPackageId.value = pkg.id
                        viewModel.navigateTo(Screen.PackageDetails)
                    })
                }
            }
        }
    }
}

@Composable
fun PackageDetailsScreen(viewModel: DexcargoViewModel) {
    val selectedId by viewModel.selectedPackageId.collectAsState()
    val packages by viewModel.cargoPackages.collectAsState()
    val currentEmp by viewModel.currentEmployee.collectAsState()
    val allocations by viewModel.paymentAllocations.collectAsState()
    val notifications by viewModel.paymentNotifications.collectAsState()

    val context = LocalContext.current

    val pkg = remember(packages, selectedId) {
        packages.find { it.id == selectedId }
    }

    if (pkg == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Package not found", color = TextPrimary)
        }
        return
    }

    val linkedAllocations = remember(allocations, pkg.id) {
        allocations.filter { it.orderId == pkg.id }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(bottom = 76.dp)
    ) {
        ScreenHeader(
            title = pkg.id,
            onBack = { viewModel.navigateBack() },
            actions = {
                StatusPill(status = pkg.status)
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Visual Banner Area
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CargoThumbnail(
                        pkg = pkg,
                        onClick = {},
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        allowExpand = true
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Tracking ID",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = pkg.id,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        Text(
                            text = "Consignee",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = pkg.consignee,
                            color = OrangeAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Parameters Breakdown List
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    DetailsRow(lbl = "Client Consignee", value = pkg.consignee)
                    DetailsRow(lbl = "Phone Number", value = pkg.phone)
                    DetailsRow(lbl = "Route Coordinates", value = "${pkg.origin} → ${pkg.dest}")
                    DetailsRow(lbl = "Shipping Method", value = pkg.mode)
                    DetailsRow(lbl = "Weight / PCS", value = "${pkg.weight} kg / ${pkg.pcs} Pieces")
                    DetailsRow(lbl = "Description of Goods", value = pkg.desc)
                    DetailsRow(lbl = "Registry Agent ID", value = pkg.salesRep)
                    DetailsRow(lbl = "Total Charges", value = "KES ${pkg.cost.toLocaleString()}", valueColor = OrangeAccent, isBoldValue = true)
                }
            }

            // Linked Evidence allocations
            if (linkedAllocations.isNotEmpty()) {
                item {
                    SectionTitle(text = "Linked Payment Evidence")
                }
                items(linkedAllocations) { alloc ->
                    val notif = notifications.find { it.id == alloc.paymentNotificationId }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(notif?.notificationNumber ?: "Evidence Ref", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Linked by: ${alloc.linkedBy.split(" ")[0]}", color = TextSecondary, fontSize = 9.sp)
                                Text("Allocated: KES ${alloc.allocatedAmount.toLocaleString()}", color = OrangeAccent, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Copy messaging panel
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NotifyBtn(
                        lbl = "SMS ALERT",
                        icon = Icons.Default.MailOutline,
                        color = BlueAccent,
                        onClick = {
                            val msg = "Habari, Cargo ${pkg.id} is ready at NBO warehouse. Bal: KES ${if (pkg.status == "registered") pkg.cost else 0}."
                            Toast.makeText(context, "Mock SMS: $msg", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    NotifyBtn(
                        lbl = "COPY MSG EN",
                        icon = Icons.Default.ContentCopy,
                        color = OrangeAccent,
                        onClick = {
                            val msg = "Hello ${pkg.consignee}, your package has arrived and is ready for pickup at DEX Cargo.\n\n**Tracking No:** ${pkg.id}\n**Amount Due:** KES ${pkg.cost.toLocaleString()}\n\nPlease visit our warehouse to collect it. Thank you."
                            try {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("DEX Message EN", msg)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "English Message Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to copy message", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    NotifyBtn(
                        lbl = "COPY MSG ZH",
                        icon = Icons.Default.ContentCopy,
                        color = OrangeAccent,
                        onClick = {
                            val msg = "您好 ${pkg.consignee}，您的包裹已到达并可在德克斯货运 DEX Cargo 提取。\n\n**快递单号:** ${pkg.id}\n**应付金额:** KES ${pkg.cost.toLocaleString()}\n\n请到我们的仓库提取包裹。谢谢。"
                            try {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("DEX Message ZH", msg)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Chinese Message Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to copy message", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Context-aware Dynamic Actions
            item {
                Spacer(modifier = Modifier.height(12.dp))
                when (pkg.status) {
                    "registered" -> {
                        // All roles can open payment
                        DexButton(
                            text = "💳 Collect KES ${pkg.cost.toLocaleString()} Payment",
                            onClick = {
                                viewModel.paymentMethod.value = "mpesa"
                                viewModel.customerPhone.value = pkg.phone
                                viewModel.navigateTo(Screen.PaymentGateway)
                            },
                            style = OrangeAccent
                        )
                    }
                    "paid" -> {
                        // Handover releases
                        DexButton(
                            text = "📦 Complete Handover Collection",
                            onClick = {
                                viewModel.verNationalId.value = ""
                                viewModel.verCollectorName.value = ""
                                viewModel.verCollectorPhone.value = pkg.phone
                                viewModel.navigateTo(Screen.CustomerVerification)
                            },
                            style = GreenAccent,
                            textColor = Color(0xFF042F1F)
                        )
                    }
                    else -> {
                        // Collected cleared state
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurface)
                                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Collector", color = TextSecondary, fontSize = 11.sp)
                                    Text(pkg.collectorName ?: pkg.consignee, color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Identification", color = TextSecondary, fontSize = 11.sp)
                                    Text(pkg.collectorId ?: "N/A", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Delivered At", color = TextSecondary, fontSize = 11.sp)
                                    Text(pkg.collectedAt ?: "N/A", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentGatewayScreen(viewModel: DexcargoViewModel) {
    val method by viewModel.paymentMethod.collectAsState()
    val phone by viewModel.customerPhone.collectAsState()
    val currentPackageId by viewModel.selectedPackageId.collectAsState()
    val packages by viewModel.cargoPackages.collectAsState()

    val pkg = remember(packages, currentPackageId) {
        packages.find { it.id == currentPackageId }
    }

    val cost = pkg?.cost ?: 4200

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        ScreenHeader(
            title = "Process Shipping Charge",
            onBack = { viewModel.navigateBack() }
        )

        // PAYMENT CHOICE CARDS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PaymentChoiceCard(
                label = "Safaricom M-Pesa",
                icon = "📱",
                active = method == "mpesa",
                onClick = { viewModel.paymentMethod.value = "mpesa" },
                modifier = Modifier.weight(1f)
            )
            PaymentChoiceCard(
                label = "Cash Deposit",
                icon = "💵",
                active = method == "cash",
                onClick = { viewModel.paymentMethod.value = "cash" },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (method == "mpesa") {
            // MPESA LIP PAYBILL VIEW
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0B2B12), Color(0xFF041006))
                            )
                        )
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("M-PESA", color = Color(0xFF10B981), fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.SansSerif)
                        Text("LIPA NA M-PESA · PAYBILL ONLINE", color = Color(0xFF6EE7B7).copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("KES ${cost.toLocaleString()}", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Charges inclusive of tax", color = TextSecondary, fontSize = 11.sp)
                    }
                }

                DexTextField(
                    value = phone,
                    onValueChange = { viewModel.customerPhone.value = it },
                    label = "M-Pesa Customer Phone Number",
                    placeholder = "e.g. 0712345678",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                DexButton(
                    text = "Dispatch STK Push Prompt",
                    onClick = { viewModel.simulateMpesaStk(phone) },
                    style = OrangeAccent
                )
            }
        } else {
            // CASH COLLECTOR VIEW
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Cash Amount Collected", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("KES ${cost.toLocaleString()}", color = GreenAccent, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                val cashierId = viewModel.currentEmployee.collectAsState().value?.id ?: "SR-002"
                DexTextField(
                    value = cashierId,
                    onValueChange = {},
                    label = "Logistics Clerk / Collector Employee ID",
                    placeholder = "Collector ID",
                    readOnly = true
                )

                DexButton(
                    text = "Mark as Paid in Cash",
                    onClick = { viewModel.submitCashPayment() },
                    style = BlueAccent,
                    textColor = Color.White
                )
            }
        }
    }
}

@Composable
fun StkWaitScreen(viewModel: DexcargoViewModel) {
    val phone by viewModel.stkPhoneNumber.collectAsState()
    val countdown by viewModel.stkCountdown.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator(color = GreenAccent, strokeWidth = 4.dp, modifier = Modifier.size(42.dp))

            Text("Awaiting M-Pesa PIN Entry", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                text = "An STK push request has been dispatched to Safaricom network for $phone.",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 240.dp)
            )
            Text(
                text = "Simulation will auto-confirm in $countdown seconds...",
                color = OrangeAccent,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PaymentSuccessScreen(viewModel: DexcargoViewModel) {
    val packages by viewModel.cargoPackages.collectAsState()
    val selectedId by viewModel.selectedPackageId.collectAsState()

    val pkg = remember(packages, selectedId) {
        packages.find { it.id == selectedId }
    }

    val ref = pkg?.paymentRef ?: "QM7X2K4L5P"
    val amt = pkg?.cost ?: 4200
    val time = pkg?.paidAt ?: "Today, 10:34 AM"
    val mode = pkg?.paymentMethod ?: "M-Pesa Express"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GreenAccent),
            contentAlignment = Alignment.Center
        ) {
            Text("💳", fontSize = 30.sp)
        }

        Text(
            text = "Payment Confirmed",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Transaction cleared by Safaricom ledger core. Package has been tagged as in-warehouse and ready for collection.",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReceiptRow(lbl = "Transaction Reference", value = ref, isMono = true, valueColor = GreenAccent)
                ReceiptRow(lbl = "Amount Paid", value = "KES ${amt.toLocaleString()}")
                ReceiptRow(lbl = "Payment Mode", value = mode)
                ReceiptRow(lbl = "Clearance Time", value = time)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        DexButton(
            text = "📦 Proceed to Identity Verification & Handover",
            onClick = {
                viewModel.verNationalId.value = ""
                viewModel.verCollectorName.value = ""
                viewModel.verCollectorPhone.value = pkg?.phone ?: ""
                viewModel.navigateTo(Screen.CustomerVerification)
            },
            style = GreenAccent,
            textColor = Color(0xFF042F1F)
        )

        Spacer(modifier = Modifier.height(8.dp))

        DexButton(
            text = "Back to Package Details",
            onClick = { viewModel.navigateTo(Screen.PackageDetails) },
            style = BlueAccent,
            textColor = Color.White
        )
    }
}

// --- SUB-COMPONENTS ---

@Composable
fun TabItem(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) OrangeAccentBg else Color.Transparent)
            .border(1.dp, if (active) OrangeAccent.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (active) OrangeAccent else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TabItemLine(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(),
            color = if (active) OrangeAccent else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (active) OrangeAccent else Color.Transparent)
        )
    }
}

@Composable
fun PackageCardRow(pkg: CargoPackage, onClick: () -> Unit) {
    val modeClassColor = if (pkg.mode == "Air Freight") OrangeAccent else BlueAccent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CargoThumbnail(pkg = pkg, onClick = onClick)

        Spacer(modifier = Modifier.width(11.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(pkg.id, color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(pkg.consignee, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${pkg.mode.replace(" Freight", "")} · ${pkg.weight} kg", color = TextMuted, fontSize = 10.sp)
        }

        Column(horizontalAlignment = Alignment.End) {
            Text("KES ${pkg.cost.toLocaleString()}", color = OrangeAccent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(4.dp))
            StatusPill(status = pkg.status)
        }
    }
}

@Composable
fun DetailsRow(lbl: String, value: String, valueColor: Color = TextPrimary, isBoldValue: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.dp, Color.Transparent)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(lbl, color = TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.5.sp,
            fontWeight = if (isBoldValue) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontFamily = if (lbl.contains("Tracking")) FontFamily.Monospace else FontFamily.SansSerif
        )
    }
}

@Composable
fun NotifyBtn(lbl: String, icon: ImageVector, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, "Notify", tint = color, modifier = Modifier.size(12.dp))
            Text(lbl, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
fun PaymentChoiceCard(label: String, icon: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) BlueAccentBg else DarkSurface)
            .border(1.dp, if (active) BlueAccent.copy(alpha = 0.45f) else DarkBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(3.dp))
            Text(label, color = if (active) BlueAccent else TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
