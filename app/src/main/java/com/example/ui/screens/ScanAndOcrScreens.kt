package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DexcargoViewModel
import com.example.ui.Screen
import com.example.ui.components.DexButton
import com.example.ui.components.DexTextField
import com.example.ui.components.ScreenHeader
import com.example.ui.components.SectionTitle
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun ScanStickerScreen(viewModel: DexcargoViewModel) {
    val selectedLabelId by viewModel.selectedLabelId.collectAsState()
    val context = LocalContext.current
    var selectedModeTab by remember { mutableStateOf(0) } // 0 = Automated OCR, 1 = Manual Form

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        ScreenHeader(
            title = "Register",
            onBack = { viewModel.navigateBack() }
        )

        // Custom Mode Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selectedModeTab == 0) OrangeAccent else Color.Transparent)
                    .clickable { selectedModeTab = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🤖 Automated Scan",
                    color = if (selectedModeTab == 0) Color(0xFF1A1200) else TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selectedModeTab == 1) OrangeAccent else Color.Transparent)
                    .clickable { selectedModeTab = 1 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📋 Manual Form",
                    color = if (selectedModeTab == 1) Color(0xFF1A1200) else TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            if (selectedModeTab == 0) {
                // VIEWPORT VIEWFINDER BOX (Drawn beautifully on Canvas)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0A101C))
                        .border(1.dp, DarkBorder, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Simulated laser line scanning
                    val infiniteTransition = rememberInfiniteTransition(label = "laser")
                    val laserY by infiniteTransition.animateFloat(
                        initialValue = 0.15f,
                        targetValue = 0.85f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2200, easing = EaseInOut),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "laserY"
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Draw camera grid
                        val cols = 4
                        val rows = 3
                        val cellW = size.width / cols
                        val cellH = size.height / rows
                        for (i in 1 until cols) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(cellW * i, 0f),
                                end = Offset(cellW * i, size.height),
                                strokeWidth = 1f
                            )
                        }
                        for (i in 1 until rows) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(0f, cellH * i),
                                end = Offset(size.width, cellH * i),
                                strokeWidth = 1f
                            )
                        }

                        // Laser scan line
                        drawLine(
                            color = OrangeAccent,
                            start = Offset(size.width * 0.1f, size.height * laserY),
                            end = Offset(size.width * 0.9f, size.height * laserY),
                            strokeWidth = 3.dp.toPx()
                        )
                    }

                    // INNER PAPER STICKER GRAPHIC
                    Box(
                        modifier = Modifier
                            .size(width = 230.dp, height = 145.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF4F6FB))
                            .border(2.dp, OrangeAccent.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedLabelId == 1) "HKG-NBO" else "CAN-NBO",
                                    color = Color(0xFF0A0B14),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "OCR PHOTO",
                                    color = BlueAccent,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                StickerRow(lbl = "Tracking", valStr = if (selectedLabelId == 1) "1260707534987" else "126070655250")
                                StickerRow(lbl = "Consignee", valStr = if (selectedLabelId == 1) "Beatrice-Pheobe Wangui" else "Charles Ombongi")
                                StickerRow(lbl = "Tel / Goods", valStr = "Tel: 1 / Nature: 1 (Manual Entry)")
                                StickerRow(lbl = "Route", valStr = if (selectedLabelId == 1) "HKG to NBO" else "CAN to NBO")
                                StickerRow(lbl = "Weight", valStr = if (selectedLabelId == 1) "1.0 kg / 1 PCS" else "0.5 kg / 1 PCS")
                            }

                            // Barcode lines
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(18.dp)
                            ) {
                                val pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f, 6f, 3f), 0f)
                                drawRoundRect(
                                    color = Color(0xFF0A0B14).copy(alpha = 0.18f),
                                    size = size,
                                    style = Stroke(width = size.height, pathEffect = pathEffect)
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Take a clear photo of the full package sticker. OCR will extract the shipment details.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ACTIVE SELECTOR INFO
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "STICKER PHOTO OCR CONTROLS",
                                color = OrangeAccent,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Real camera captures stickers and processes them via Gemini OCR. You can also simulate the captured sticker scan.",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DexButton(
                                    text = "📸 Real Camera",
                                    onClick = {
                                        viewModel.triggerStickerCameraEvent.tryEmit(Unit)
                                    },
                                    style = OrangeAccent,
                                    textColor = Color(0xFF1A1200),
                                    modifier = Modifier.weight(1.0f)
                                )
                                DexButton(
                                    text = "⚡ Simulation",
                                    onClick = { viewModel.triggerOcrScan(null) },
                                    style = DarkSurfaceVariant,
                                    textColor = TextPrimary,
                                    modifier = Modifier.weight(1.0f)
                                )
                            }
                        }
                    }
                }
            } else {
                // MANUAL ENTRY BOX
                val manualId = remember { mutableStateOf("") }
                val manualName = remember { mutableStateOf("") }
                val manualPhone = remember { mutableStateOf("") }
                val manualCost = remember { mutableStateOf("") }
                val manualCbm = remember { mutableStateOf("") }
                val manualMode = remember { mutableStateOf("Sea Freight") }
                val manualSalesRep = remember { mutableStateOf(viewModel.getDefaultSalesRep()) }
                var expandedDropdown by remember { mutableStateOf(false) }
                var expandedRepDropdown by remember { mutableStateOf(false) }
                val employeesList by viewModel.employees.collectAsState()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "📋 Manual Cargo Registry",
                        color = OrangeAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    DexTextField(
                        value = manualId.value,
                        onValueChange = { manualId.value = it },
                        label = "Tracking Number (AFA ID)",
                        placeholder = "e.g. 1260707534987"
                    )

                    DexTextField(
                        value = manualName.value,
                        onValueChange = { manualName.value = it },
                        label = "Consignee / Client Name",
                        placeholder = "e.g. Mary Atieno"
                    )

                    DexTextField(
                        value = manualPhone.value,
                        onValueChange = { manualPhone.value = it },
                        label = "Client Phone Number",
                        placeholder = "e.g. 0712345678"
                    )

                    // ASSIGNED SALES REP / EMPLOYEE IN CHARGE
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "ASSIGNED EMPLOYEE (EARNS COMMISSION)",
                            color = TextSecondary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DexTextField(
                                value = manualSalesRep.value,
                                onValueChange = { manualSalesRep.value = it },
                                label = "",
                                placeholder = "e.g. John Kamau",
                                modifier = Modifier.weight(1f)
                            )
                            Box {
                                IconButton(
                                    onClick = { expandedRepDropdown = true },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DarkSurfaceVariant)
                                        .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Employee",
                                        tint = OrangeAccent
                                    )
                                }
                                DropdownMenu(
                                    expanded = expandedRepDropdown,
                                    onDismissRequest = { expandedRepDropdown = false },
                                    modifier = Modifier.background(DarkSurfaceVariant)
                                ) {
                                    if (employeesList.isNotEmpty()) {
                                        employeesList.forEach { emp ->
                                            DropdownMenuItem(
                                                text = { Text("${emp.name} (${emp.role.uppercase()})", color = TextPrimary, fontSize = 12.sp) },
                                                onClick = {
                                                    manualSalesRep.value = emp.name
                                                    expandedRepDropdown = false
                                                }
                                            )
                                        }
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text("John Kamau", color = TextPrimary, fontSize = 12.sp) },
                                            onClick = {
                                                manualSalesRep.value = "John Kamau"
                                                expandedRepDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Grace Akinyi", color = TextPrimary, fontSize = 12.sp) },
                                            onClick = {
                                                manualSalesRep.value = "Grace Akinyi"
                                                expandedRepDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DexTextField(
                        value = manualCbm.value,
                        onValueChange = { manualCbm.value = it },
                        label = "Package Size (CBM) - For Sea Freight",
                        placeholder = "e.g. 0.05 CBM or 1.2 CBM"
                    )

                    DexTextField(
                        value = manualCost.value,
                        onValueChange = { manualCost.value = it },
                        label = "Total Price (KES)",
                        placeholder = "e.g. 3000"
                    )

                    // FREIGHT MODE DROPDOWN
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "MODE OF FREIGHT",
                            color = TextSecondary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                .clickable { expandedDropdown = true }
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(manualMode.value, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown Arrow",
                                    tint = TextSecondary
                                )
                            }
                            DropdownMenu(
                                expanded = expandedDropdown,
                                onDismissRequest = { expandedDropdown = false },
                                modifier = Modifier.background(DarkSurfaceVariant)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sea Freight", color = TextPrimary) },
                                    onClick = {
                                        manualMode.value = "Sea Freight"
                                        expandedDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Air Freight", color = TextPrimary) },
                                    onClick = {
                                        manualMode.value = "Air Freight"
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            if (manualId.value.isBlank()) {
                                Toast.makeText(context, "Please enter a Tracking Number.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            // Prefill review variables in VM
                            viewModel.revId.value = manualId.value
                            viewModel.revName.value = manualName.value
                            viewModel.revPhone.value = manualPhone.value
                            viewModel.revOrigin.value = "Guangzhou (CAN)"
                            viewModel.revDest.value = "Nairobi (NBO)"
                            viewModel.revDesc.value = "General Goods"
                            viewModel.revMode.value = manualMode.value
                            viewModel.revWeight.value = "1.0"
                            viewModel.revPcs.value = "1"
                            viewModel.revCost.value = manualCost.value
                            viewModel.revCbm.value = manualCbm.value
                            viewModel.revSalesRep.value = manualSalesRep.value.ifBlank { viewModel.getDefaultSalesRep() }

                            // Reset photo states
                            viewModel.isPackagePhotoCaptured.value = false
                            viewModel.capturedPackageBitmap.value = null
                            viewModel.capturedPhotoUrl.value = ""

                            // Move directly to TakePackagePhoto screen
                            viewModel.navigateTo(Screen.TakePackagePhoto)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("📷 Next: Take Photo", color = Color(0xFF1A1200), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun OcrProcessingScreen(viewModel: DexcargoViewModel) {
    var progress by remember { mutableStateOf(0) }
    var currentStep by remember { mutableStateOf(1) }

    LaunchedEffect(Unit) {
        while (progress < 100) {
            delay(100)
            progress += 5
            currentStep = when {
                progress < 25 -> 1
                progress < 50 -> 2
                progress < 75 -> 3
                else -> 4
            }
        }
        viewModel.navigateTo(Screen.OcrReview)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeader(title = "OCR Data Extraction")

        Spacer(modifier = Modifier.height(30.dp))

        // CIRCULAR RING COMPONENT
        Box(
            modifier = Modifier.size(118.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxSize(),
                color = BlueAccent,
                strokeWidth = 6.dp,
                trackColor = Color(0xFF1A1F33),
            )
            Text(
                text = "$progress%",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (currentStep) {
                1 -> "Preparing Sticker Photo..."
                2 -> "Reading Printed Shipment Fields..."
                3 -> "Validating Extracted Details..."
                else -> "Preparing Backend Payload..."
            },
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(28.dp))

        // STEPS FEEDBACK LIST
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OcrStepRow(num = 1, text = "Preparing Sticker Photo", status = getStepStatus(1, currentStep))
            OcrStepRow(num = 2, text = "Reading Printed Shipment Fields", status = getStepStatus(2, currentStep))
            OcrStepRow(num = 3, text = "Validating Extracted Details", status = getStepStatus(3, currentStep))
            OcrStepRow(num = 4, text = "Preparing Backend Registration", status = getStepStatus(4, currentStep))
        }
    }
}

@Composable
fun OcrReviewScreen(viewModel: DexcargoViewModel) {
    val revId by viewModel.revId.collectAsState()
    val revName by viewModel.revName.collectAsState()
    val revPhone by viewModel.revPhone.collectAsState()
    val revOrigin by viewModel.revOrigin.collectAsState()
    val revDest by viewModel.revDest.collectAsState()
    val revDesc by viewModel.revDesc.collectAsState()
    val revMode by viewModel.revMode.collectAsState()
    val revWeight by viewModel.revWeight.collectAsState()
    val revPcs by viewModel.revPcs.collectAsState()
    val revCost by viewModel.revCost.collectAsState()
    val revCbm by viewModel.revCbm.collectAsState()
    val revSalesRep by viewModel.revSalesRep.collectAsState()
    val employeesList by viewModel.employees.collectAsState()
    var expandedRepDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (viewModel.revSalesRep.value.isBlank()) {
            viewModel.revSalesRep.value = viewModel.getDefaultSalesRep()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        ScreenHeader(
            title = "Review Extracted Information",
            onBack = { viewModel.navigateTo(Screen.ScanSticker) }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(GreenAccentBg)
                    .border(1.dp, GreenAccent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✔", color = GreenAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        "Sticker details extracted (Tracking, Name, Route, Weight, PCS). Missing sticker fields (Phone, Description, Charge KES) are left blank for manual entry.",
                        color = GreenAccent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            DexTextField(value = revId, onValueChange = { viewModel.revId.value = it }, label = "Tracking Number (AFA ID)", placeholder = "e.g. 1260707534987")
            DexTextField(value = revName, onValueChange = { viewModel.revName.value = it }, label = "Consignee Name", placeholder = "Client Name e.g. Beatrice-Pheobe Wangui")
            DexTextField(value = revPhone, onValueChange = { viewModel.revPhone.value = it }, label = "Consignee Phone (Required - Manual Entry)", placeholder = "Enter client phone e.g. 0712345678")

            // ASSIGNED SALES REP / EMPLOYEE IN CHARGE
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "ASSIGNED EMPLOYEE (COMMISSION ALLOCATION)",
                    color = TextSecondary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DexTextField(
                        value = revSalesRep,
                        onValueChange = { viewModel.revSalesRep.value = it },
                        label = "",
                        placeholder = "e.g. John Kamau",
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(
                            onClick = { expandedRepDropdown = true },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Employee",
                                tint = OrangeAccent
                            )
                        }
                        DropdownMenu(
                            expanded = expandedRepDropdown,
                            onDismissRequest = { expandedRepDropdown = false },
                            modifier = Modifier.background(DarkSurfaceVariant)
                        ) {
                            if (employeesList.isNotEmpty()) {
                                employeesList.forEach { emp ->
                                    DropdownMenuItem(
                                        text = { Text("${emp.name} (${emp.role.uppercase()})", color = TextPrimary, fontSize = 12.sp) },
                                        onClick = {
                                            viewModel.revSalesRep.value = emp.name
                                            expandedRepDropdown = false
                                        }
                                    )
                                }
                            } else {
                                DropdownMenuItem(
                                    text = { Text("John Kamau", color = TextPrimary, fontSize = 12.sp) },
                                    onClick = {
                                        viewModel.revSalesRep.value = "John Kamau"
                                        expandedRepDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Grace Akinyi", color = TextPrimary, fontSize = 12.sp) },
                                    onClick = {
                                        viewModel.revSalesRep.value = "Grace Akinyi"
                                        expandedRepDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DexTextField(value = revOrigin, onValueChange = { viewModel.revOrigin.value = it }, label = "Origin Hub", placeholder = "HKG / CAN", modifier = Modifier.weight(1f))
                DexTextField(value = revDest, onValueChange = { viewModel.revDest.value = it }, label = "Destination", placeholder = "NBO", modifier = Modifier.weight(1f))
            }

            DexTextField(value = revDesc, onValueChange = { viewModel.revDesc.value = it }, label = "Cargo Description (Manual Entry)", placeholder = "Enter cargo description e.g. Women's Shoes")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1.5f)) {
                    Text("MODE", color = TextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    var expandedDropdown by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                            .clickable { expandedDropdown = true }
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Text(revMode, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        DropdownMenu(expanded = expandedDropdown, onDismissRequest = { expandedDropdown = false }, modifier = Modifier.background(DarkSurfaceVariant)) {
                            DropdownMenuItem(text = { Text("Air Freight", color = TextPrimary) }, onClick = { viewModel.revMode.value = "Air Freight"; expandedDropdown = false })
                            DropdownMenuItem(text = { Text("Sea Freight", color = TextPrimary) }, onClick = { viewModel.revMode.value = "Sea Freight"; expandedDropdown = false })
                        }
                    }
                }
                DexTextField(value = revWeight, onValueChange = { viewModel.revWeight.value = it }, label = "Weight (kg)", placeholder = "1.0", modifier = Modifier.weight(1f))
                DexTextField(value = revPcs, onValueChange = { viewModel.revPcs.value = it }, label = "Pieces", placeholder = "1", modifier = Modifier.weight(1f))
            }

            DexTextField(value = revCost, onValueChange = { viewModel.revCost.value = it }, label = "Charge Amount KES (Required - Manual Entry)", placeholder = "Enter price in KES e.g. 4200")

            DexTextField(
                value = revCbm,
                onValueChange = { viewModel.revCbm.value = it },
                label = if (revMode == "Sea Freight") "Package Size in CBM (Sea Cargo)" else "Package Size / Volume (CBM)",
                placeholder = "e.g. 0.05 CBM or 1.2 CBM"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.navigateTo(Screen.ScanSticker) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { viewModel.navigateTo(Screen.TakePackagePhoto) },
                    modifier = Modifier.weight(2.0f),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Next: Take Package Photo", color = Color(0xFF1A1200), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TakePackagePhotoScreen(viewModel: DexcargoViewModel) {
    val isCaptured by viewModel.isPackagePhotoCaptured.collectAsState()
    val capturedBitmap by viewModel.capturedPackageBitmap.collectAsState()
    val context = LocalContext.current
    val revId by viewModel.revId.collectAsState()

    // Automatically trigger the camera on entering the screen if not already captured
    LaunchedEffect(Unit) {
        if (!isCaptured && capturedBitmap == null) {
            viewModel.triggerPackageCameraEvent.tryEmit(Unit)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        ScreenHeader(
            title = "Capture Package Photo",
            onBack = { viewModel.navigateBack() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CAMERA VIEWPORT
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF0F111A))
                    .border(1.dp, if (isCaptured) GreenAccent else DarkBorder, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isCaptured && capturedBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = capturedBitmap!!.asImageBitmap(),
                        contentDescription = "Captured Package Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )

                    // Overlay badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("✅", fontSize = 12.sp)
                            Text("PHOTO CAPTURED", color = GreenAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Display camera prompt
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Camera Icon",
                            tint = OrangeAccent,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "NO PACKAGE PHOTO CAPTURED YET",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Tap 'Open Camera' or 'Choose Gallery' below to capture a clear photo of the physical package.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Dotted overlay border
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.15f),
                        size = size,
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                    )
                }
            }

            Text(
                text = "Tracking ID: $revId",
                color = OrangeAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // CAPTURE OPTIONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DexButton(
                    text = if (isCaptured) "📷 Retake Camera Photo" else "📷 Open Camera",
                    onClick = {
                        viewModel.isPackagePhotoCaptured.value = false
                        viewModel.capturedPackageBitmap.value = null
                        viewModel.triggerPackageCameraEvent.tryEmit(Unit)
                    },
                    style = OrangeAccent,
                    textColor = Color(0xFF1A1200),
                    modifier = Modifier.weight(1f)
                )

                DexButton(
                    text = "🖼️ Gallery",
                    onClick = {
                        viewModel.isPackagePhotoCaptured.value = false
                        viewModel.capturedPackageBitmap.value = null
                        viewModel.triggerEvidenceGalleryEvent.tryEmit(Unit)
                    },
                    style = DarkSurfaceVariant,
                    textColor = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            DexButton(
                text = "Register Package to Hub",
                onClick = { viewModel.savePackageRegistry() },
                enabled = isCaptured,
                style = OrangeAccent
            )
        }
    }
}

@Composable
fun RegistrationSuccessScreen(viewModel: DexcargoViewModel) {
    val id by viewModel.revId.collectAsState()
    val name by viewModel.revName.collectAsState()
    val weight by viewModel.revWeight.collectAsState()
    val pcs by viewModel.revPcs.collectAsState()
    val cost by viewModel.revCost.collectAsState()
    val origin by viewModel.revOrigin.collectAsState()
    val dest by viewModel.revDest.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Success Icon Circle
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GreenAccent),
            contentAlignment = Alignment.Center
        ) {
            Text("✔", color = Color(0xFF042F1F), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            text = "Package Registered Successfully",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Sticker OCR data has been submitted to the backend and registered in the cargo system. SMS notification dispatched to consignee.",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // SLATE RECEIPT DETAILS BOX
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReceiptRow(lbl = "Tracking No.", value = id, isMono = true)
                ReceiptRow(lbl = "Consignee", value = name)
                ReceiptRow(lbl = "Weight / PCS", value = "$weight kg / $pcs PCS")
                ReceiptRow(lbl = "Cost Charge", value = "KES ${(cost.toIntOrNull() ?: 0).toLocaleString()}", valueColor = OrangeAccent)
                ReceiptRow(lbl = "Routing", value = "$origin → $dest")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        DexButton(
            text = "View Registered Details",
            onClick = {
                viewModel.selectedPackageId.value = id
                viewModel.navigateTo(Screen.PackageDetails)
            },
            style = BlueAccent,
            textColor = Color.White
        )

        Button(
            onClick = { viewModel.navigateTo(Screen.ScanSticker) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Text("Register Another Package", color = TextSecondary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// --- SUB-COMPONENTS FOR SCAN AND OCR SCREENS ---

@Composable
fun StickerRow(lbl: String, valStr: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = lbl, color = Color(0xFF404870), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
        Text(text = valStr, color = Color(0xFF0A0B14), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun OcrStepRow(num: Int, text: String, status: StepStatus) {
    val (color, iconLabel) = when (status) {
        StepStatus.Wait -> Pair(TextMuted, num.toString())
        StepStatus.Active -> Pair(BlueAccent, num.toString())
        StepStatus.Done -> Pair(GreenAccent, "✔")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (status == StepStatus.Active) Color.Transparent else color.copy(alpha = 0.16f))
                .border(
                    if (status == StepStatus.Active) 2.dp else 0.dp,
                    if (status == StepStatus.Active) BlueAccent else Color.Transparent,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(iconLabel, color = if (status == StepStatus.Active) BlueAccent else color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = text,
            color = if (status == StepStatus.Wait) TextMuted else TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ReceiptRow(lbl: String, value: String, isMono: Boolean = false, valueColor: Color = TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(lbl, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.SansSerif
        )
    }
}

enum class StepStatus { Wait, Active, Done }

private fun getStepStatus(stepNum: Int, currentStep: Int): StepStatus {
    return when {
        stepNum < currentStep -> StepStatus.Done
        stepNum == currentStep -> StepStatus.Active
        else -> StepStatus.Wait
    }
}

@Composable
fun BarcodeScannerScreen(viewModel: DexcargoViewModel) {
    val packages by viewModel.cargoPackages.collectAsState()
    val context = LocalContext.current
    var inputTracking by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }

    // Initialize Real Play Services Barcode Scanner
    val gmsScanner = remember {
        val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options)
    }

    // Laser scanning animation
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserYOffset by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(bottom = 76.dp)
    ) {
        ScreenHeader(
            title = "Barcode Scanner",
            onBack = { viewModel.navigateBack() }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "ALIGN BARCODE WITHIN THE VIEWPORT",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            // Viewfinder scan card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F111A))
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .clickable {
                            isScanning = true
                            gmsScanner.startScan()
                                .addOnSuccessListener { barcode ->
                                    isScanning = false
                                    val rawValue = barcode.rawValue
                                    if (!rawValue.isNullOrBlank()) {
                                        inputTracking = rawValue
                                        scanError = null
                                        val foundPkg = packages.find { it.id == rawValue.trim() }
                                        if (foundPkg != null) {
                                            viewModel.packageSearchQuery.value = foundPkg.id
                                            viewModel.selectedPackageId.value = foundPkg.id
                                            Toast.makeText(context, "Barcode Scanned: $rawValue ✅", Toast.LENGTH_SHORT).show()
                                            viewModel.navigateTo(Screen.PackageDetails)
                                        } else {
                                            scanError = "Scanned code '$rawValue' but package not found in system."
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    isScanning = false
                                    scanError = "Scan cancelled/failed: ${e.message}"
                                }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Corners
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 3.dp.toPx()
                        val lineLength = 24.dp.toPx()
                        val padding = 30.dp.toPx()

                        val w = size.width
                        val h = size.height

                        val rectHeight = h - padding * 2

                        val left = padding
                        val right = w - padding
                        val top = padding
                        val bottom = h - padding

                        // Top-Left corner
                        drawLine(color = OrangeAccent, start = Offset(left, top), end = Offset(left + lineLength, top), strokeWidth = strokeWidth)
                        drawLine(color = OrangeAccent, start = Offset(left, top), end = Offset(left, top + lineLength), strokeWidth = strokeWidth)

                        // Top-Right corner
                        drawLine(color = OrangeAccent, start = Offset(right, top), end = Offset(right - lineLength, top), strokeWidth = strokeWidth)
                        drawLine(color = OrangeAccent, start = Offset(right, top), end = Offset(right, top + lineLength), strokeWidth = strokeWidth)

                        // Bottom-Left corner
                        drawLine(color = OrangeAccent, start = Offset(left, bottom), end = Offset(left + lineLength, bottom), strokeWidth = strokeWidth)
                        drawLine(color = OrangeAccent, start = Offset(left, bottom), end = Offset(left, bottom - lineLength), strokeWidth = strokeWidth)

                        // Bottom-Right corner
                        drawLine(color = OrangeAccent, start = Offset(right, bottom), end = Offset(right - lineLength, bottom), strokeWidth = strokeWidth)
                        drawLine(color = OrangeAccent, start = Offset(right, bottom), end = Offset(right, bottom - lineLength), strokeWidth = strokeWidth)

                        // Laser line animation
                        val laserY = top + (rectHeight * laserYOffset)
                        drawLine(
                            color = OrangeAccent,
                            start = Offset(left + 10.dp.toPx(), laserY),
                            end = Offset(right - 10.dp.toPx(), laserY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }

                    if (isScanning) {
                        CircularProgressIndicator(color = OrangeAccent, strokeWidth = 3.dp)
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Camera viewfinder",
                                tint = OrangeAccent,
                                modifier = Modifier.size(54.dp)
                            )
                            Text(
                                "TAP HERE TO SCAN WITH CAMERA",
                                color = OrangeAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Uses ML Kit high-performance laser scanner",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Real Scanner Action Button
            item {
                DexButton(
                    text = "📷 Launch Real Camera Scanner",
                    onClick = {
                        isScanning = true
                        gmsScanner.startScan()
                            .addOnSuccessListener { barcode ->
                                isScanning = false
                                val rawValue = barcode.rawValue
                                if (!rawValue.isNullOrBlank()) {
                                    inputTracking = rawValue
                                    scanError = null
                                    val foundPkg = packages.find { it.id == rawValue.trim() }
                                    if (foundPkg != null) {
                                        viewModel.packageSearchQuery.value = foundPkg.id
                                        viewModel.selectedPackageId.value = foundPkg.id
                                        Toast.makeText(context, "Barcode Scanned: $rawValue ✅", Toast.LENGTH_SHORT).show()
                                        viewModel.navigateTo(Screen.PackageDetails)
                                    } else {
                                        scanError = "Scanned code '$rawValue' but package not found in system."
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                isScanning = false
                                scanError = "Scan cancelled/failed: ${e.message}"
                            }
                    },
                    style = OrangeAccent
                )
            }

            // Input Fields
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "MANUAL BARCODE SCANNER ENTRY",
                        color = OrangeAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    DexTextField(
                        value = inputTracking,
                        onValueChange = {
                            inputTracking = it
                            scanError = null
                        },
                        label = "Enter Tracking Number",
                        placeholder = "e.g. 1260707534987"
                    )

                    if (scanError != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x11FF3B30))
                                .border(1.dp, Color(0xFFFF3B30), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️ $scanError",
                                color = Color(0xFFFF3B30),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    DexButton(
                        text = "🔍 Search Package ID",
                        onClick = {
                            if (inputTracking.isBlank()) {
                                scanError = "Please enter a tracking number"
                                return@DexButton
                            }
                            val foundPkg = packages.find { it.id == inputTracking.trim() }
                            if (foundPkg != null) {
                                viewModel.packageSearchQuery.value = foundPkg.id
                                viewModel.selectedPackageId.value = foundPkg.id
                                Toast.makeText(context, "Package Found: ${foundPkg.id} ✅", Toast.LENGTH_SHORT).show()
                                viewModel.navigateTo(Screen.PackageDetails)
                            } else {
                                scanError = "Package not found"
                            }
                        },
                        style = OrangeAccent
                    )
                }
            }
        }
    }
}
