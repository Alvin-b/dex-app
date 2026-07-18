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
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(badgeColor, badgeColor.copy(alpha = 0.4f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentEmp?.name?.split(" ")?.mapNotNull { it.takeOrNull(1) }?.joinToString("")?.take(2)?.uppercase() ?: "DX",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
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
