package com.example.ui.screens
 
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DexcargoViewModel
import com.example.ui.components.DexButton
import com.example.ui.theme.*
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

fun triggerRealBiometric(
    activity: androidx.fragment.app.FragmentActivity,
    onSuccess: () -> Unit,
    onFallback: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onFallback(errString.toString())
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("DEXCARGO Biometric Sign-In")
        .setSubtitle("Confirm fingerprint or face scan")
        .setNegativeButtonText("Use PIN Fallback")
        .build()

    try {
        biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
        onFallback(e.localizedMessage ?: "Biometric error")
    }
}

@Composable
fun SetPinScreen(viewModel: DexcargoViewModel) {
    var step by remember { mutableStateOf(1) } // 1: First PIN, 2: Confirm PIN
    val pinFirst by viewModel.pinSetupFirst.collectAsState()
    val pinSecond by viewModel.pinSetupSecond.collectAsState()
    val errorMsg by viewModel.pinErrorMessage.collectAsState()
    val biometricOption by viewModel.biometricOptionEnabled.collectAsState()
    val currentEmp by viewModel.currentEmployee.collectAsState()

    val currentInput = if (step == 1) pinFirst else pinSecond

    fun onKeyClick(key: String) {
        if (currentInput.length < 4) {
            val newVal = currentInput + key
            if (step == 1) {
                viewModel.pinSetupFirst.value = newVal
                if (newVal.length == 4) {
                    step = 2
                    viewModel.pinErrorMessage.value = ""
                }
            } else {
                viewModel.pinSetupSecond.value = newVal
                if (newVal.length == 4) {
                    val success = viewModel.verifyAndSavePin()
                    if (!success) {
                        step = 1
                    }
                }
            }
        }
    }

    fun onBackspace() {
        if (currentInput.isNotEmpty()) {
            val newVal = currentInput.dropLast(1)
            if (step == 1) {
                viewModel.pinSetupFirst.value = newVal
            } else {
                viewModel.pinSetupSecond.value = newVal
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // HEADER & BRANDING
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BlueAccent.copy(alpha = 0.15f))
                .border(1.dp, BlueAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = "Lock", tint = BlueAccent, modifier = Modifier.size(28.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (step == 1) "Set a Quick-Access PIN" else "Confirm your PIN",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (step == 1) "Enter a 4-digit PIN for quick access on this device." else "Re-enter your 4-digit PIN to confirm.",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // DOTS INDICATORS
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            for (i in 0 until 4) {
                val isActive = i < currentInput.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isActive) OrangeAccent else Color.Transparent)
                        .border(
                            2.dp,
                            if (isActive) OrangeAccent else TextMuted.copy(alpha = 0.5f),
                            CircleShape
                        )
                )
            }
        }

        // ERROR DISPLAY
        if (errorMsg.isNotEmpty()) {
            Text(
                text = errorMsg,
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(18.dp))
        }

        // BIOMETRIC OPTION (ONLY ON CONFIRMATION STEP)
        if (step == 2) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    .clickable { viewModel.biometricOptionEnabled.value = !biometricOption}
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Checkbox(
                    checked = biometricOption,
                    onCheckedChange = { viewModel.biometricOptionEnabled.value = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = OrangeAccent,
                        uncheckedColor = TextSecondary,
                        checkmarkColor = Color(0xFF1A1200)
                    )
                )
                Text(
                    text = "Enable biometric sign-in (Fingerprint/Face)",
                    color = TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // NUMERIC KEYPAD
        NumericKeypad(
            onKeyClick = ::onKeyClick,
            onBackspace = ::onBackspace,
            modifier = Modifier.fillMaxWidth(0.9f)
        )

        Spacer(modifier = Modifier.weight(1f))

        // NAVIGATION BUTTONS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            if (step == 2) {
                Button(
                    onClick = {
                        step = 1
                        viewModel.pinSetupSecond.value = ""
                        viewModel.pinErrorMessage.value = ""
                    },
                    modifier = Modifier.fillMaxWidth(0.6f),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Back to Start", color = TextSecondary, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { viewModel.logout() },
                    modifier = Modifier.fillMaxWidth(0.6f),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", color = TextSecondary, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun EnterPinScreen(viewModel: DexcargoViewModel) {
    val quickEmp by viewModel.quickAccessEmployee.collectAsState()
    val enteredPin by viewModel.enteredPin.collectAsState()
    val errorMsg by viewModel.pinErrorMessage.collectAsState()
    val context = LocalContext.current

    fun onKeyClick(key: String) {
        if (enteredPin.length < 4) {
            val newVal = enteredPin + key
            viewModel.enteredPin.value = newVal
            if (newVal.length == 4) {
                viewModel.loginWithPinCode(newVal)
            }
        }
    }

    fun onBackspace() {
        if (enteredPin.isNotEmpty()) {
            viewModel.enteredPin.value = enteredPin.dropLast(1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        // EMPLOYEE AVATAR PROFILE BUBBLE
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(BlueAccent.copy(alpha = 0.16f))
                .border(2.dp, BlueAccent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = quickEmp?.name?.take(2)?.uppercase() ?: "DX",
                color = BlueAccent,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome Back, ${quickEmp?.name ?: "Employee"}",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Enter Quick-Access PIN",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        // DOTS INDICATORS
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            for (i in 0 until 4) {
                val isActive = i < enteredPin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isActive) OrangeAccent else Color.Transparent)
                        .border(
                            2.dp,
                            if (isActive) OrangeAccent else TextMuted.copy(alpha = 0.5f),
                            CircleShape
                        )
                )
            }
        }

        // ERROR DISPLAY
        if (!errorMsg.isNullOrEmpty()) {
            Text(
                text = errorMsg,
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(20.dp))
        }

        Spacer(modifier = Modifier.height(30.dp))

        // NUMERIC KEYPAD
        NumericKeypad(
            onKeyClick = ::onKeyClick,
            onBackspace = ::onBackspace,
            modifier = Modifier.fillMaxWidth(0.9f)
        )

        Spacer(modifier = Modifier.weight(1f))

        // SWITCH USER / EMAIL LOGIN FALLBACK
        TextButton(
            onClick = { viewModel.switchToEmailLogin() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Switch Account / Use Email & Password",
                color = BlueAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

// --- SUB-COMPONENT: KEYPAD ---

@Composable
fun NumericKeypad(
    onKeyClick: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "backspace")
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.8f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (key.isEmpty()) Color.Transparent else DarkSurface)
                            .border(
                                1.dp,
                                if (key.isEmpty()) Color.Transparent else DarkBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = key.isNotEmpty()) {
                                if (key == "backspace") onBackspace() else onKeyClick(key)
                            }
                            .testTag(if (key == "backspace") "pin_key_backspace" else "pin_key_$key"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "backspace") {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Backspace",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (key.isNotEmpty()) {
                            Text(
                                text = key,
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
