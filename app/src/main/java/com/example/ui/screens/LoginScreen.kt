package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Employee
import com.example.ui.DexcargoViewModel
import com.example.ui.components.DexButton
import com.example.ui.components.DexTextField
import com.example.ui.theme.*

@Composable
fun LoginScreen(viewModel: DexcargoViewModel) {
    val empRegEmailState = remember { mutableStateOf("") }
    val empRegPassState = remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.4f))

            // REAL COMPANY BRANDING LOGO
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.dex_brand_logo),
                contentDescription = "DEX Logistics Logo",
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, OrangeAccent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "DEX LOGISTICS",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = "Secure Terminal Operations Portal",
                color = TextSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 3.dp)
            )

            Spacer(modifier = Modifier.weight(0.5f))

            // EMAIL / ID INPUT
            DexTextField(
                value = empRegEmailState.value,
                onValueChange = { empRegEmailState.value = it },
                label = "Employee Email / ID",
                placeholder = "john@dexcargo.com",
                testTag = "login_emp_id"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // PASSWORD
            DexTextField(
                value = empRegPassState.value,
                onValueChange = { empRegPassState.value = it },
                label = "Password",
                placeholder = "••••••••",
                visualTransformation = PasswordVisualTransformation(),
                readOnly = false,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Show Password",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // SUBMIT BUTTON
            DexButton(
                text = "Authenticate Session",
                onClick = {
                    viewModel.login(empRegEmailState.value, empRegPassState.value)
                },
                style = BlueAccent,
                textColor = Color.White,
                testTag = "login_submit"
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "SYS GATEWAY v2.4.0-NBO",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
