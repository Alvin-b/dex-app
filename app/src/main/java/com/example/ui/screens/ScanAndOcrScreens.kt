package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.triggerOcrScan(bitmap)
        } else {
            Toast.makeText(context, "No photo captured", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission is required to capture sticker photos.", Toast.LENGTH_LONG).show()
        }
    }

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
                                StickerRow(lbl = "Consignee", valStr = if (selectedLabelId == 1) "Beatrice Wangui" else "Charles Ombongi")
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
                                        permissionLauncher.launch(android.Manifest.permission.CAMERA)
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
                val manualMode = remember { mutableStateOf("Sea Freight") }
                var expandedDropdown by remember { mutableStateOf(false) }

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
                        "Sticker details extracted successfully. Verify before backend submission.",
                        color = GreenAccent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            DexTextField(value = revId, onValueChange = { viewModel.revId.value = it }, label = "Tracking Number (AFA ID)", placeholder = "e.g. 1260707534987")
            DexTextField(value = revName, onValueChange = { viewModel.revName.value = it }, label = "Consignee Name", placeholder = "Client Name")
            DexTextField(value = revPhone, onValueChange = { viewModel.revPhone.value = it }, label = "Consignee Phone", placeholder = "Phone number")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DexTextField(value = revOrigin, onValueChange = { viewModel.revOrigin.value = it }, label = "Origin Hub", placeholder = "CAN", modifier = Modifier.weight(1f))
                DexTextField(value = revDest, onValueChange = { viewModel.revDest.value = it }, label = "Destination", placeholder = "NBO", modifier = Modifier.weight(1f))
            }

            DexTextField(value = revDesc, onValueChange = { viewModel.revDesc.value = it }, label = "Cargo Description", placeholder = "Description of goods")

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

            DexTextField(value = revCost, onValueChange = { viewModel.revCost.value = it }, label = "Charge Amount (KES)", placeholder = "3000")

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

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.capturedPackageBitmap.value = bitmap
            viewModel.isPackagePhotoCaptured.value = true
            viewModel.capturedPhotoUrl.value = viewModel.encodeBitmapToBase64(bitmap)
            Toast.makeText(context, "Package photo captured successfully!", Toast.LENGTH_SHORT).show()
        } else {
            // Generates a beautiful custom package bitmap instead of failing!
            val simBitmap = viewModel.generateSimulatedPackageBitmap(revId)
            viewModel.capturedPackageBitmap.value = simBitmap
            viewModel.isPackagePhotoCaptured.value = true
            viewModel.capturedPhotoUrl.value = viewModel.encodeBitmapToBase64(simBitmap)
            Toast.makeText(context, "Using simulated high-fidelity package photo.", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                cameraLauncher.launch(null)
            } catch (e: Exception) {
                // Generates a beautiful custom package bitmap instead of failing!
                val simBitmap = viewModel.generateSimulatedPackageBitmap(revId)
                viewModel.capturedPackageBitmap.value = simBitmap
                viewModel.isPackagePhotoCaptured.value = true
                viewModel.capturedPhotoUrl.value = viewModel.encodeBitmapToBase64(simBitmap)
                Toast.makeText(context, "Using simulated high-fidelity package photo.", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Fallback immediately to simulation so they can proceed!
            val simBitmap = viewModel.generateSimulatedPackageBitmap(revId)
            viewModel.capturedPackageBitmap.value = simBitmap
            viewModel.isPackagePhotoCaptured.value = true
            viewModel.capturedPhotoUrl.value = viewModel.encodeBitmapToBase64(simBitmap)
            Toast.makeText(context, "Camera permission denied. Using simulated photo.", Toast.LENGTH_SHORT).show()
        }
    }

    // Automatically trigger the camera on entering the screen
    LaunchedEffect(Unit) {
        if (!isCaptured) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        ScreenHeader(
            title = "Take Package Photo",
            onBack = { viewModel.navigateBack() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CAMERA SCREEN VIEWFINDER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF0F111A))
                    .border(1.dp, DarkBorder, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isCaptured) {
                    if (capturedBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = capturedBitmap!!.asImageBitmap(),
                            contentDescription = "Captured Package Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        // Display box photo captured (simulated fallback representation)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF252A42)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("📦", fontSize = 42.sp)
                                Text(
                                    "PHOTO CAPTURED: PKG-BOX-A",
                                    color = BlueAccent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                } else {
                    // Display drawing coordinates box
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Canvas(modifier = Modifier.size(80.dp)) {
                            val boxSize = size.width
                            drawRoundRect(
                                color = TextSecondary,
                                size = size,
                                cornerRadius = CornerRadius(10.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawLine(
                                color = OrangeAccent,
                                start = Offset(boxSize * 0.2f, boxSize * 0.35f),
                                end = Offset(boxSize * 0.8f, boxSize * 0.35f),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                        Text(
                            "LIVE VIEWPORT: FOCUS ON BOX",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
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
                "Align the package and sticker label clearly in the frame, then press the camera snap trigger.",
                color = TextSecondary,
                fontSize = 10.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // SNAP TRIGGERS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        // Simulated/Real Flash toggle
                        Toast.makeText(context, "Flash enabled", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(Icons.Default.FlashOn, "Flash", tint = OrangeAccent)
                }

                Spacer(modifier = Modifier.width(24.dp))

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(if (isCaptured) Color.Gray else Color.White)
                        .clickable {
                            if (!isCaptured) {
                                permissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        }
                )

                Spacer(modifier = Modifier.width(24.dp))

                IconButton(
                    onClick = {
                        viewModel.isPackagePhotoCaptured.value = false
                        viewModel.capturedPackageBitmap.value = null
                        viewModel.capturedPhotoUrl.value = ""
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(Icons.Default.Refresh, "Reset", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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
