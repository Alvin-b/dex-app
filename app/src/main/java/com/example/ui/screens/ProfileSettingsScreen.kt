package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DexcargoViewModel
import com.example.ui.components.DexButton
import com.example.ui.components.DexTextField
import com.example.ui.components.ScreenHeader
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(viewModel: DexcargoViewModel) {
    val context = LocalContext.current
    val currentEmp by viewModel.currentEmployee.collectAsState()
    val userPhoto by viewModel.userProfilePhotoBitmap.collectAsState()
    var showPhotoDialog by remember { mutableStateOf(false) }

    if (showPhotoDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoDialog = false },
            title = { Text("Profile Photo Options", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose how you would like to set your profile picture:", color = TextSecondary, fontSize = 12.sp)
                    DexButton(
                        text = "📸 Take Photo (Camera)",
                        onClick = {
                            showPhotoDialog = false
                            viewModel.triggerProfileCameraEvent.tryEmit(Unit)
                        },
                        style = OrangeAccent
                    )
                    DexButton(
                        text = "🖼️ Choose Photo (Gallery)",
                        onClick = {
                            showPhotoDialog = false
                            viewModel.triggerProfileGalleryEvent.tryEmit(Unit)
                        },
                        style = BlueAccent,
                        textColor = Color.White
                    )
                    if (userPhoto != null) {
                        DexButton(
                            text = "🗑️ Remove Photo",
                            onClick = {
                                showPhotoDialog = false
                                viewModel.removeProfilePhoto(context)
                            },
                            style = DarkSurfaceVariant,
                            textColor = Color.Red
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
    
    // Manage PIN state locally in fields
    var localPin by remember { mutableStateOf(currentEmp?.pin ?: "") }
    var confirmPin by remember { mutableStateOf(currentEmp?.pin ?: "") }
    var localBiometric by remember { mutableStateOf(currentEmp?.biometricEnabled ?: false) }
    
    // Sync if employee state changes
    LaunchedEffect(currentEmp) {
        currentEmp?.let {
            localPin = it.pin ?: ""
            confirmPin = it.pin ?: ""
            localBiometric = it.biometricEnabled
        }
        viewModel.loadUserProfilePhoto(context)
    }

    val roleLabel = when (currentEmp?.role) {
        "sr" -> "Sales Representative"
        "lm" -> "Logistics Manager"
        "sm" -> "Sales Lead / Manager"
        "admin" -> "System Administrator"
        else -> "Employee"
    }

    val badgeColor = when (currentEmp?.role) {
        "sr" -> OrangeAccent
        "lm" -> BlueAccent
        "sm" -> GreenAccent
        "admin" -> PurpleAccent
        else -> OrangeAccent
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        ScreenHeader(
            title = "Profile & Security Settings",
            onBack = { viewModel.navigateBack() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // PROFILE AVATAR CARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(badgeColor, badgeColor.copy(alpha = 0.4f))
                                )
                            )
                            .border(2.dp, badgeColor, CircleShape)
                            .clickable { showPhotoDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (userPhoto != null) {
                            androidx.compose.foundation.Image(
                                bitmap = userPhoto!!.asImageBitmap(),
                                contentDescription = "Profile Photo",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = currentEmp?.name?.split(" ")?.mapNotNull { it.takeOrNull(1) }?.joinToString("")?.take(2)?.uppercase() ?: "DX",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        // RED DOT indicator for updates
                        val updateAvailable by viewModel.hasUpdate.collectAsState()
                        if (updateAvailable) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                                    .border(2.dp, DarkSurface, CircleShape)
                            )
                        }
                    }

                    TextButton(onClick = { showPhotoDialog = true }) {
                        Text(
                            text = if (userPhoto != null) "📷 Change Photo" else "📷 Upload Profile Photo",
                            color = OrangeAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = currentEmp?.name ?: "No Employee Loaded",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Text(
                            text = roleLabel,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // EMPLOYEE INFO DETAILS CARDS
            Text(
                text = "PERSONAL ACCOUNT INFORMATION",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileInfoRow(label = "Employee ID", value = currentEmp?.id ?: "N/A", icon = Icons.Default.Badge)
                Divider(color = DarkBorder, thickness = 0.8.dp)
                ProfileInfoRow(label = "Email Address", value = currentEmp?.email ?: "N/A", icon = Icons.Default.Email)
                Divider(color = DarkBorder, thickness = 0.8.dp)
                ProfileInfoRow(label = "Active Duty Status", value = if (currentEmp?.isActive == true) "Active & Authorized" else "Disabled", icon = Icons.Default.LockOpen, valueColor = if (currentEmp?.isActive == true) GreenAccent else MaterialTheme.colorScheme.error)
            }

            // SECURITY CONFIGURATION CARD
            Text(
                text = "SECURITY SETUP",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Quick Access Security",
                        color = OrangeAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // BIOMETRICS SWITCH TOGGLE
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBg)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometrics",
                                tint = BlueAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Enable Biometric Login",
                                    color = TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Use fingerprint/face scan for super-fast secure access",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Switch(
                            checked = localBiometric,
                            onCheckedChange = { localBiometric = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OrangeAccent,
                                checkedTrackColor = OrangeAccent.copy(alpha = 0.4f),
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = DarkBorder
                            )
                        )
                    }

                    // CHANGE PIN FIELDS
                    Text(
                        text = "Change Quick-Access PIN",
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    DexTextField(
                        value = localPin,
                        onValueChange = { if (it.length <= 4) localPin = it },
                        label = "Set New 4-Digit PIN",
                        placeholder = "e.g. 1234",
                        trailingIcon = { Icon(Icons.Default.Lock, "PIN", tint = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    DexTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 4) confirmPin = it },
                        label = "Confirm 4-Digit PIN",
                        placeholder = "Re-enter new PIN",
                        trailingIcon = { Icon(Icons.Default.Lock, "Confirm PIN", tint = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // SYSTEM UPDATES & DATABASE SYNC CARD
            Text(
                text = "SYSTEM UPDATES & SYNCHRONIZATION",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BlueAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync",
                                tint = BlueAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Updates & Database Synchronization",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val currVer by viewModel.installedVersionName.collectAsState()
                            val currBuild by viewModel.installedBuildNumber.collectAsState()
                            Text(
                                text = "Installed Version: $currVer (Build $currBuild) • Auto-Sync Enabled",
                                color = GreenAccent,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    LaunchedEffect(Unit) {
                        viewModel.loadInstalledVersion(context)
                    }

                    var isManualSyncing by remember { mutableStateOf(false) }
                    var isCheckingUpdate by remember { mutableStateOf(false) }
                    var updatePromptMessage by remember { mutableStateOf<String?>(null) }
                    var updateDownloadUrl by remember { mutableStateOf<String?>(null) }

                    val isDownloadingRelease by viewModel.isAppUpdateDownloading.collectAsState()
                    val downloadProgress by viewModel.appUpdateProgress.collectAsState()
                    val downloadStatusText by viewModel.appUpdateStatusText.collectAsState()

                    if (updatePromptMessage != null) {
                        AlertDialog(
                            onDismissRequest = { updatePromptMessage = null },
                            title = {
                                Text("New App Release Available (${viewModel.targetUpdateVersionName})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            },
                            text = {
                                Text(
                                    updatePromptMessage ?: "",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        updatePromptMessage = null
                                        viewModel.startInAppUpdateDownload(context) { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenAccent)
                                ) {
                                    Text("Start In-App Update", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { updatePromptMessage = null }) {
                                    Text("Dismiss", color = TextSecondary)
                                }
                            },
                            containerColor = DarkSurface,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    if (isDownloadingRelease) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GreenAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📥 Downloading In-App Release Update...", color = GreenAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("${(downloadProgress * 100).toInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                LinearProgressIndicator(
                                    progress = downloadProgress,
                                    color = GreenAccent,
                                    trackColor = DarkBorder,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                                Text(
                                    text = downloadStatusText,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isCheckingUpdate = true
                                viewModel.checkForAppUpdates(context) { success, msg, url ->
                                    isCheckingUpdate = false
                                    if (success) {
                                        updatePromptMessage = msg
                                        updateDownloadUrl = url
                                    } else {
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isCheckingUpdate && !isManualSyncing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Checking...", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = "Check for Updates",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text("Check for Updates", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                isManualSyncing = true
                                viewModel.manualSyncAllUpdates { success, msg ->
                                    isManualSyncing = false
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = !isManualSyncing && !isCheckingUpdate,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isManualSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Syncing...", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text("Sync Database", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ACTIONS
            Spacer(modifier = Modifier.height(8.dp))

            DexButton(
                text = "💾 Save Profile Settings",
                onClick = {
                    if (localPin.isNotEmpty() && localPin.length != 4) {
                        Toast.makeText(context, "PIN must be exactly 4 digits.", Toast.LENGTH_SHORT).show()
                        return@DexButton
                    }
                    if (localPin != confirmPin) {
                        Toast.makeText(context, "PINs do not match. Please verify.", Toast.LENGTH_SHORT).show()
                        return@DexButton
                    }

                    viewModel.updateProfilePinAndBiometrics(
                        newPin = localPin.ifEmpty { null },
                        newBiometrics = localBiometric
                    ) {
                        Toast.makeText(context, "Profile and security updated successfully!", Toast.LENGTH_SHORT).show()
                        viewModel.navigateBack()
                    }
                },
                style = OrangeAccent,
                textColor = Color(0xFF1A1200),
                modifier = Modifier.fillMaxWidth()
            )

            DexButton(
                text = "🚪 Sign Out & Lock Device",
                onClick = {
                    viewModel.logout()
                    Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                },
                style = DarkSurfaceVariant,
                textColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ProfileInfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            Text(text = label, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
    }
}

// Extension to take first letter safely or null
private fun String.takeOrNull(n: Int): String? = if (this.isNotEmpty()) this.take(n) else null
