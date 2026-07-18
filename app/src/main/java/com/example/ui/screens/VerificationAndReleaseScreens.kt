package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CargoPackage
import com.example.ui.DexcargoViewModel
import com.example.ui.Screen
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun CustomerVerificationScreen(viewModel: DexcargoViewModel) {
    val currentPackageId by viewModel.selectedPackageId.collectAsState()
    val packages by viewModel.cargoPackages.collectAsState()

    val nationalId by viewModel.verNationalId.collectAsState()
    val collectorName by viewModel.verCollectorName.collectAsState()
    val collectorPhone by viewModel.verCollectorPhone.collectAsState()

    val pkg = remember(packages, currentPackageId) {
        packages.find { it.id == currentPackageId }
    }

    val consigneeName = pkg?.consignee ?: "Beatrice Wangui"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        ScreenHeader(
            title = "Identity Verification",
            onBack = { viewModel.navigateBack() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(OrangeAccentBg)
                    .border(1.dp, OrangeAccent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "⚠ COMPLIANCE: Ensure client details match government-issued identification before releasing cargo.",
                    color = OrangeAccent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 14.sp
                )
            }

            // Consignee target
            Column {
                Text(
                    text = "CONSIGNEE REGISTERED NAME",
                    color = TextSecondary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp),
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(consigneeName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            DexTextField(
                value = nationalId,
                onValueChange = { viewModel.verNationalId.value = it },
                label = "National ID Number / Passport",
                placeholder = "Enter ID number of collector..."
            )

            DexTextField(
                value = collectorName,
                onValueChange = { viewModel.verCollectorName.value = it },
                label = "Collector Name (if different from consignee)",
                placeholder = "Leave empty if consignee is harvesting package"
            )

            DexTextField(
                value = collectorPhone,
                onValueChange = { viewModel.verCollectorPhone.value = it },
                label = "Recipient Mobile Confirmation",
                placeholder = "e.g. +254 712 345 678",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            Spacer(modifier = Modifier.weight(1f))

            DexButton(
                text = "Proceed to Signature Capture",
                onClick = {
                    if (nationalId.isNotBlank()) {
                        viewModel.navigateTo(Screen.SignatureCapture)
                    }
                },
                enabled = nationalId.isNotBlank(),
                style = OrangeAccent
            )
        }
    }
}

@Composable
fun SignatureCaptureScreen(viewModel: DexcargoViewModel) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        ScreenHeader(
            title = "Consignee Acknowledgment",
            onBack = { viewModel.navigateBack() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Ask the client to draw their signature in the area below to confirm package collection.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )

            // SIGNATURE CANVAS CONTAINER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            ) {
                if (strokes.isEmpty() && currentStroke.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Touch & draw signature inside this box",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    strokes.add(currentStroke)
                                    currentStroke = emptyList()
                                }
                            )
                        }
                ) {
                    // Draw saved strokes
                    strokes.forEach { stroke ->
                        for (i in 0 until stroke.size - 1) {
                            drawLine(
                                color = Color.Black,
                                start = stroke[i],
                                end = stroke[i + 1],
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Draw current active stroke
                    if (currentStroke.size > 1) {
                        for (i in 0 until currentStroke.size - 1) {
                            drawLine(
                                color = Color.Black,
                                start = currentStroke[i],
                                end = currentStroke[i + 1],
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        strokes.clear()
                        currentStroke = emptyList()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text("Clear Pad", color = TextSecondary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        // Complete signature capture
                        viewModel.submitHandoverCollection("captured_signature_points")
                    },
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    enabled = strokes.isNotEmpty()
                ) {
                    Text("Confirm Collection", color = Color(0xFF1A1200), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CollectionSuccessScreen(viewModel: DexcargoViewModel) {
    val currentPackageId by viewModel.selectedPackageId.collectAsState()
    val packages by viewModel.cargoPackages.collectAsState()

    val pkg = remember(packages, currentPackageId) {
        packages.find { it.id == currentPackageId }
    }

    val id = pkg?.id ?: "1260707534987"
    val collector = pkg?.collectorName ?: "Beatrice Wangui"
    val nationalId = pkg?.collectorId ?: "ID-8729384"
    val collectedTime = pkg?.collectedAt ?: "Today, 11:02 AM"

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
            Text("📦", fontSize = 30.sp)
        }

        Text(
            text = "Collection Completed",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Cargo has been registered as hand-delivered. System status updated to ARCHIVED / CLEARED.",
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
                ReceiptRow(lbl = "Tracking Code", value = id, isMono = true)
                ReceiptRow(lbl = "Collected By", value = collector)
                ReceiptRow(lbl = "Identification", value = nationalId)
                ReceiptRow(lbl = "Collected At", value = collectedTime)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        DexButton(
            text = "Back to Directory List",
            onClick = { viewModel.navigateTo(Screen.PackageList) },
            style = BlueAccent,
            textColor = Color.White
        )
    }
}

@Composable
fun CommissionsScreen(viewModel: DexcargoViewModel) {
    val activeFilter by viewModel.activeCommissionFilter.collectAsState()
    val packages by viewModel.cargoPackages.collectAsState()

    val clearedPackages = remember(packages) {
        packages.filter { it.status != "registered" }
    }

    val (earned, paid, outstanding) = when (activeFilter) {
        "month" -> Triple(24680, 12400, 12280)
        "last" -> Triple(20870, 20870, 0)
        else -> Triple(82450, 70170, 12280)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(bottom = 76.dp)
    ) {
        ScreenHeader(
            title = "My Commissions",
            onBack = { viewModel.navigateBack() }
        )

        // TAB BAR FILTERS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TabItem(label = "This Month", active = activeFilter == "month", onClick = { viewModel.activeCommissionFilter.value = "month" })
            TabItem(label = "Last Month", active = activeFilter == "last", onClick = { viewModel.activeCommissionFilter.value = "last" })
            TabItem(label = "All Time", active = activeFilter == "all", onClick = { viewModel.activeCommissionFilter.value = "all" })
        }

        // STATS CARDS STRIP
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Earned", color = TextSecondary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Text("KES ${earned.toLocaleString()}", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Paid Out", color = TextSecondary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Text("KES ${paid.toLocaleString()}", color = GreenAccent, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Outstanding", color = TextSecondary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Text("KES ${outstanding.toLocaleString()}", color = OrangeAccent, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // LOG BREAKDOWNS
        SectionTitle(text = "Override & Sales Log")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Earned Commission Breakdown".uppercase(), color = TextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                clearedPackages.forEach { p ->
                    val overrideVal = (p.cost * 0.1).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(p.id, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text("Registered ${p.registeredAt}", color = TextMuted, fontSize = 9.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("+ KES ${overrideVal.toLocaleString()}", color = OrangeAccent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(GreenAccentBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Audited", color = GreenAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
