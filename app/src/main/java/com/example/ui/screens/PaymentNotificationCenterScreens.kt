package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.*
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
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

    val gmsScanner = remember {
        val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options)
    }

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
    var isScanningEnabled by remember { mutableStateOf(false) }

    // Filter unpaid registered packages
    val unpaidPackages = remember(packages) {
        packages.filter { it.status == "registered" }
    }

    val filteredPackages = remember(unpaidPackages, searchQuery.value) {
        if (searchQuery.value.isBlank()) unpaidPackages
        else {
            unpaidPackages.filter {
                it.id.contains(searchQuery.value, ignoreCase = true) ||
                        it.consignee.contains(searchQuery.value, ignoreCase = true)
            }
        }
    }

    // Scanner animation
    val infiniteTransition = rememberInfiniteTransition(label = "laser_link")
    val laserYOffset by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_link"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(bottom = 76.dp)
    ) {
        ScreenHeader(
            title = "Link Payment Evidence",
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

            // ALLOCATED / SELECTED TARGET ORDERS
            item {
                SectionTitle(text = "Allocated Packages (${viewModel.selectedLinkOrders.size})")
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
                        Text("No packages selected yet. Check items from the list below or use the scanner.", color = TextMuted, fontSize = 11.sp, fontStyle = FontStyle.Italic)
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    CargoThumbnail(
                                        pkg = itemPkg,
                                        onClick = {},
                                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(itemPkg.id, color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Text("${itemPkg.consignee} · Bal: KES ${itemPkg.cost.toLocaleString()}", color = TextSecondary, fontSize = 9.5.sp)
                                    }
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

            // BARCODE SCANNER AND SEARCH CONTROLS
            item {
                Divider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))
                SectionTitle(text = "Search & Barcode Scan")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DexTextField(
                        value = searchQuery.value,
                        onValueChange = { searchQuery.value = it },
                        label = "",
                        placeholder = "Type tracking no. or customer name...",
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            gmsScanner.startScan()
                                .addOnSuccessListener { barcode ->
                                    val rawValue = barcode.rawValue
                                    if (!rawValue.isNullOrBlank()) {
                                        val matched = unpaidPackages.find { it.id == rawValue.trim() }
                                        if (matched != null) {
                                            if (viewModel.selectedLinkOrders.any { it.id == matched.id }) {
                                                Toast.makeText(context, "Package ${matched.id} already selected!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.addOrderToLink(matched)
                                                Toast.makeText(context, "Barcode Scanned! Matched package ${matched.id} ✅", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Scanned: $rawValue (Not found in unpaid packages)", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Scan cancelled/failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(OrangeAccent.copy(alpha = 0.2f))
                            .border(1.dp, OrangeAccent, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Toggle Barcode Scanner Viewfinder",
                            tint = OrangeAccent
                        )
                    }
                }
            }

            // COLLAPSIBLE VIEWPORT SCANNER FOR FASTER SEARCH
            if (isScanningEnabled) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurface)
                            .border(1.dp, OrangeAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "REAL BARCODE SCANNER ACTIVE",
                            color = OrangeAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        // VIEWPORT
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF07090F))
                                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    gmsScanner.startScan()
                                        .addOnSuccessListener { barcode ->
                                            val rawValue = barcode.rawValue
                                            if (!rawValue.isNullOrBlank()) {
                                                val matched = unpaidPackages.find { it.id == rawValue.trim() }
                                                if (matched != null) {
                                                    if (viewModel.selectedLinkOrders.any { it.id == matched.id }) {
                                                        Toast.makeText(context, "Package ${matched.id} already selected!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        viewModel.addOrderToLink(matched)
                                                        Toast.makeText(context, "Barcode Scanned! Matched package ${matched.id} ✅", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Scanned: $rawValue (Not found in unpaid packages)", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(context, "Scan cancelled/failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 3.dp.toPx()
                                val lineLength = 16.dp.toPx()
                                val padding = 20.dp.toPx()

                                val w = size.width
                                val h = size.height
                                val rectHeight = h - padding * 2

                                val left = padding
                                val right = w - padding
                                val top = padding
                                val bottom = h - padding

                                // Top-Left
                                drawLine(color = OrangeAccent, start = Offset(left, top), end = Offset(left + lineLength, top), strokeWidth = strokeWidth)
                                drawLine(color = OrangeAccent, start = Offset(left, top), end = Offset(left, top + lineLength), strokeWidth = strokeWidth)
                                // Top-Right
                                drawLine(color = OrangeAccent, start = Offset(right, top), end = Offset(right - lineLength, top), strokeWidth = strokeWidth)
                                drawLine(color = OrangeAccent, start = Offset(right, top), end = Offset(right, top + lineLength), strokeWidth = strokeWidth)
                                // Bottom-Left
                                drawLine(color = OrangeAccent, start = Offset(left, bottom), end = Offset(left + lineLength, bottom), strokeWidth = strokeWidth)
                                drawLine(color = OrangeAccent, start = Offset(left, bottom), end = Offset(left, bottom - lineLength), strokeWidth = strokeWidth)
                                // Bottom-Right
                                drawLine(color = OrangeAccent, start = Offset(right, bottom), end = Offset(right - lineLength, bottom), strokeWidth = strokeWidth)
                                drawLine(color = OrangeAccent, start = Offset(right, bottom), end = Offset(right, bottom - lineLength), strokeWidth = strokeWidth)

                                // Animated laser line
                                val laserY = top + (rectHeight * laserYOffset)
                                drawLine(
                                    color = OrangeAccent,
                                    start = Offset(left + 8.dp.toPx(), laserY),
                                    end = Offset(right - 8.dp.toPx(), laserY),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.QrCodeScanner, "viewfinder", tint = OrangeAccent, modifier = Modifier.size(34.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Tap Viewfinder to scan with camera", color = OrangeAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        DexButton(
                            text = "📷 Launch Barcode Camera",
                            onClick = {
                                gmsScanner.startScan()
                                    .addOnSuccessListener { barcode ->
                                        val rawValue = barcode.rawValue
                                        if (!rawValue.isNullOrBlank()) {
                                            val matched = unpaidPackages.find { it.id == rawValue.trim() }
                                            if (matched != null) {
                                                if (viewModel.selectedLinkOrders.any { it.id == matched.id }) {
                                                    Toast.makeText(context, "Package ${matched.id} already selected!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    viewModel.addOrderToLink(matched)
                                                    Toast.makeText(context, "Barcode Scanned! Matched package ${matched.id} ✅", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Scanned: $rawValue (Not found in unpaid packages)", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(context, "Scan cancelled/failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            },
                            style = OrangeAccent
                        )
                    }
                }
            }

            // CHOOSABLE UNPAID PACKAGES LIST
            item {
                SectionTitle(text = "Choose Packages from List (One or Many)")
            }

            if (filteredPackages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No matching registered packages found.", color = TextMuted, fontSize = 11.sp)
                    }
                }
            } else {
                items(filteredPackages) { pkg ->
                    val isSelected = viewModel.selectedLinkOrders.any { it.id == pkg.id }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) DarkSurfaceVariant else DarkSurface)
                            .border(
                                1.dp,
                                if (isSelected) OrangeAccent.copy(alpha = 0.8f) else DarkBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (isSelected) {
                                    viewModel.removeOrderFromLink(pkg.id)
                                } else {
                                    viewModel.addOrderToLink(pkg)
                                }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked == true) {
                                    viewModel.addOrderToLink(pkg)
                                } else {
                                    viewModel.removeOrderFromLink(pkg.id)
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = OrangeAccent,
                                uncheckedColor = TextSecondary,
                                checkmarkColor = Color.Black
                            )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        CargoThumbnail(
                            pkg = pkg,
                            onClick = {},
                            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(6.dp))
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pkg.id,
                                color = TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${pkg.consignee} · Bal: KES ${pkg.cost.toLocaleString()}",
                                color = TextSecondary,
                                fontSize = 9.5.sp
                            )
                            Text(
                                text = "Route: ${pkg.origin} ➔ ${pkg.dest}",
                                color = TextMuted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // LINK SUBMIT ACTION
            item {
                Spacer(modifier = Modifier.height(14.dp))
                DexButton(
                    text = "Confirm Linking & Audit Actions",
                    onClick = { viewModel.confirmPaymentLinking() },
                    enabled = viewModel.selectedLinkOrders.isNotEmpty()
                )
                Spacer(modifier = Modifier.height(10.dp))
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
    val context = LocalContext.current

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

            var showPromptDialog by remember { mutableStateOf(false) }

            if (showPromptDialog) {
                AlertDialog(
                    onDismissRequest = { showPromptDialog = false },
                    title = { Text("Upload Evidence Image", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                    text = { Text("Choose how you would like to select or capture your payment evidence image:", color = TextSecondary, fontSize = 12.sp) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showPromptDialog = false
                                viewModel.triggerEvidenceGalleryEvent.tryEmit(Unit)
                            }
                        ) {
                            Text("📁 GALLERY / PHONE STORAGE", color = OrangeAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showPromptDialog = false
                                viewModel.triggerEvidenceCameraEvent.tryEmit(Unit)
                            }
                        ) {
                            Text("📷 USE CAMERA", color = OrangeAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    },
                    containerColor = DarkSurface
                )
            }

            if (uploadType == "IMAGE") {
                Column {
                    Text("UPLOAD EVIDENCE IMAGE", color = TextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                            .clickable { showPromptDialog = true }
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isUploaded = mockImage.startsWith("base64:")
                            val label = if (isUploaded) "Real Image Captured Successfully ✅" else "Click to select or capture image..."
                            Text(label, color = if (isUploaded) GreenAccent else TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, "expand", tint = TextSecondary, modifier = Modifier.size(16.dp))
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DexTextField(
                        value = mockText,
                        onValueChange = { viewModel.mockTextContent.value = it },
                        label = "SMS Text / WhatsApp Confirmation",
                        placeholder = "Paste confirmation texts here..."
                    )

                    Button(
                        onClick = {
                            try {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clipData = clipboard.primaryClip
                                if (clipData != null && clipData.itemCount > 0) {
                                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                                    viewModel.mockTextContent.value = text
                                    Toast.makeText(context, "Copied text pasted successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not access clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📋 Paste Copied Text From Clipboard", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
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
    if (imageName.startsWith("base64:")) {
        val base64Str = imageName.substringAfter("base64:")
        val bitmap = remember(base64Str) {
            try {
                val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Uploaded Evidence",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Fit
            )
            return
        }
    }

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
