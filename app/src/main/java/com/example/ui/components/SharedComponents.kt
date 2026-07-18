package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CargoPackage
import com.example.ui.theme.*

@Composable
fun StatusPill(status: String) {
    val (bgColor, textColor, label) = when (status) {
        "registered" -> Triple(OrangeAccentBg, OrangeAccent, "Awaiting Pay")
        "paid" -> Triple(GreenAccentBg, GreenAccent, "Paid & Ready")
        "collected" -> Triple(Color(0x1F94A3B8), TextSecondary, "Cleared / Done")
        else -> Triple(Color(0x1F94A3B8), TextSecondary, "Unknown")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
    }
}

@Composable
fun RoleBadge(role: String, id: String) {
    val (bgColor, textColor, label) = when (role) {
        "sr" -> Triple(OrangeAccentBg, OrangeAccent, "SR-$id")
        "lm" -> Triple(BlueAccentBg, BlueAccent, "LM-$id")
        "sm" -> Triple(GreenAccentBg, GreenAccent, "SM-$id")
        "admin" -> Triple(PurpleAccentBg, PurpleAccent, "ADM-$id")
        else -> Triple(Color(0x1F94A3B8), TextSecondary, "GUEST-$id")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DexButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: Color = OrangeAccent,
    textColor: Color = Color.White,
    testTag: String = ""
) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(style, style.copy(alpha = 0.85f))
    )

    Box(
        modifier = modifier
            .testTag(testTag)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (enabled) {
                    Modifier.background(gradientBrush)
                } else {
                    Modifier.background(Color(0x1F94A3B8))
                }
            )
            .clickable(enabled = enabled) { onClick() }
            .border(
                1.dp,
                if (enabled) style.copy(alpha = 0.45f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) textColor else TextSecondary.copy(alpha = 0.5f),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DexTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    readOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    testTag: String = ""
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
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
                .background(DarkSurfaceVariant)
                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = placeholder,
                        color = TextMuted,
                        fontSize = 13.5.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                readOnly = readOnly,
                trailingIcon = trailingIcon,
                singleLine = true
            )
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (actions != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

@Composable
fun SectionTitle(text: String, actionText: String? = null, onActionClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(),
            color = TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                color = OrangeAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onActionClick() }
            )
        }
    }
}

@Composable
fun CargoThumbnail(
    pkg: CargoPackage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(52.dp)
) {
    var isExpanded by remember { mutableStateOf(false) }

    val bitmap = remember(pkg.packagePhotoUrl) {
        if (pkg.packagePhotoUrl.isNullOrBlank() || pkg.packagePhotoUrl == "simulated_url") null
        else {
            try {
                val decodedBytes = android.util.Base64.decode(pkg.packagePhotoUrl, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
            .clickable {
                if (bitmap != null) {
                    isExpanded = true
                } else {
                    onClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Package Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            val strokeColor = TextSecondary
            val accentColor = OrangeAccent
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw background
                drawRect(color = DarkSurfaceVariant)

                // Draw box outlines (simulated cargo package)
                val center = Offset(size.width / 2, size.height / 2)
                val boxWidth = size.width * 0.5f
                val boxHeight = size.height * 0.45f
                val rectX = center.x - boxWidth / 2
                val rectY = center.y - boxHeight / 2

                drawRect(
                    color = strokeColor,
                    topLeft = Offset(rectX, rectY),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Draw box tape or labels
                drawLine(
                    color = accentColor,
                    start = Offset(rectX + 3.dp.toPx(), rectY + boxHeight / 2),
                    end = Offset(rectX + boxWidth - 3.dp.toPx(), rectY + boxHeight / 2),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }

    // EXPANDED PHOTO DIALOG
    if (isExpanded && bitmap != null) {
        Dialog(
            onDismissRequest = { isExpanded = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .border(2.dp, OrangeAccent, RoundedCornerShape(16.dp))
                    .clickable { isExpanded = false }, // Clicking inside/outside goes back
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Expanded Package Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Floating close indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("✕ Close", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}
