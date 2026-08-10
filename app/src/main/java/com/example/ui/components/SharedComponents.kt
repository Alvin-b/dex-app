package com.example.ui.components

import com.example.data.api.SupabaseClient
import com.example.data.DexcargoRepository
import com.example.data.AppDatabase
import com.example.data.CargoPackage
import com.example.ui.DexcargoViewModel
import androidx.compose.runtime.LaunchedEffect

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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.ui.theme.*

object NetworkMonitor {
    val isOnline = MutableStateFlow(true)
    val isSyncing = MutableStateFlow(false)
}

fun loadSafeBitmapResource(context: android.content.Context, resId: Int, maxDimension: Int = 600): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeResource(context.resources, resId, options)
        
        val width = options.outWidth
        val height = options.outHeight
        var sampleSize = 1
        if (width > maxDimension || height > maxDimension) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while ((halfWidth / sampleSize) >= (maxDimension / 2) || (halfHeight / sampleSize) >= (maxDimension / 2)) {
                sampleSize *= 2
            }
        }
        
        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, resId, decodeOptions)
        bmp?.asImageBitmap()
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}

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
    val isOnline by NetworkMonitor.isOnline.collectAsState()
    val isSyncing by NetworkMonitor.isSyncing.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (!isOnline) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF5A1E1E))
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Text(
                            text = "OFFLINE",
                            color = Color(0xFFFCA5A5),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (isSyncing) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E3A5F))
                        .border(1.dp, BlueAccent, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CircularProgressIndicator(
                            color = BlueAccent,
                            strokeWidth = 1.dp,
                            modifier = Modifier.size(8.dp)
                        )
                        Text(
                            text = "SYNCING",
                            color = Color(0xFF93C5FD),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
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

fun generateLocalSimulatedPackageBitmap(id: String): android.graphics.Bitmap {
    val width = 400
    val height = 300
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    // Background Slate
    val bgPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#0F1424")
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
    
    // Package cardboard box outline
    val boxPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#C68B59")
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRoundRect(80f, 60f, 320f, 240f, 16f, 16f, boxPaint)
    
    // Tape line
    val tapePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#FF9800")
        strokeWidth = 14f
    }
    canvas.drawLine(80f, 150f, 320f, 150f, tapePaint)
    
    // Shipping Label on the box
    val labelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
    }
    canvas.drawRect(120f, 80f, 280f, 135f, labelPaint)
    
    // Text on label
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 13f
        isAntiAlias = true
    }
    canvas.drawText("DEX CARGO", 140f, 100f, textPaint)
    
    val smallTextPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.DKGRAY
        textSize = 10f
        isAntiAlias = true
    }
    canvas.drawText("ID: $id", 130f, 120f, smallTextPaint)
    
    return bitmap
}

@Composable
fun StorageImage(
    storagePathOrUrl: String?,
    loadImage: suspend (String?) -> ByteArray?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var bytes by remember(storagePathOrUrl) {
        mutableStateOf<ByteArray?>(null)
    }
    var loading by remember(storagePathOrUrl) {
        mutableStateOf(!storagePathOrUrl.isNullOrBlank())
    }
    var failed by remember(storagePathOrUrl) {
        mutableStateOf(false)
    }

    LaunchedEffect(storagePathOrUrl) {
        loading = !storagePathOrUrl.isNullOrBlank()
        failed = false
        bytes = null

        if (!storagePathOrUrl.isNullOrBlank()) {
            bytes = loadImage(storagePathOrUrl)
            failed = bytes == null
        }

        loading = false
    }

    val bitmap = remember(bytes) {
        bytes?.let {
            try {
                android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = OrangeAccent
            )

            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )

            else -> Text(
                text = "Photo unavailable",
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun RemoteStorageImage(
    storagePathOrUrl: String?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    packageId: String? = null,
    fallbackBitmap: android.graphics.Bitmap? = null,
    repository: com.example.data.DexcargoRepository? = null,
    onClick: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeRepo = remember(repository, context) {
        repository ?: com.example.data.DexcargoRepository(com.example.data.AppDatabase.getDatabase(context))
    }

    StorageImage(
        storagePathOrUrl = storagePathOrUrl,
        loadImage = { path -> activeRepo.downloadStorageImage(path, packageId = packageId) },
        contentDescription = contentDescription ?: "Storage Image",
        modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentScale = contentScale
    )
}

@Composable
fun CargoThumbnail(
    pkg: CargoPackage,
    viewModel: DexcargoViewModel? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier.size(52.dp),
    allowExpand: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeRepo = remember(context) {
        DexcargoRepository(AppDatabase.getDatabase(context))
    }
    val loadImageFunc: suspend (String?) -> ByteArray? = { path ->
        if (viewModel != null) {
            viewModel.loadStorageImage(path)
        } else {
            activeRepo.downloadStorageImage(path, packageId = pkg.id)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
            .then(if (allowExpand) Modifier.clickable { isExpanded = true } else Modifier.clickable { onClick() }),
        contentAlignment = Alignment.Center
    ) {
        StorageImage(
            storagePathOrUrl = pkg.packagePhotoUrl,
            loadImage = loadImageFunc,
            contentDescription = "Photo for package ${pkg.id}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (allowExpand) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Expand",
                        tint = OrangeAccent,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "ZOOM",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }

    // EXPANDED PHOTO DIALOG
    if (isExpanded) {
        Dialog(
            onDismissRequest = { isExpanded = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, OrangeAccent, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F111A))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PACKAGE REGISTRATION PHOTO",
                                color = OrangeAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Tracking: ${pkg.id}",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        IconButton(
                            onClick = { isExpanded = false },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant)
                        ) {
                            Text("✕", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    // Full Image Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        StorageImage(
                            storagePathOrUrl = pkg.packagePhotoUrl,
                            loadImage = loadImageFunc,
                            contentDescription = "Full package photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    // Photo Info & Metadata Footer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Consignee:", color = TextSecondary, fontSize = 11.sp)
                            Text(pkg.consignee, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (!pkg.packagePhotoCapturedAt.isNullOrBlank()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Captured At:", color = TextSecondary, fontSize = 11.sp)
                                Text(pkg.packagePhotoCapturedAt, color = TextMuted, fontSize = 10.5.sp)
                            }
                        }
                        if (!pkg.packagePhotoCapturedBy.isNullOrBlank()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Captured By:", color = TextSecondary, fontSize = 11.sp)
                                Text(pkg.packagePhotoCapturedBy, color = TextMuted, fontSize = 10.5.sp)
                            }
                        }
                    }

                    DexButton(
                        text = "Close Preview",
                        onClick = { isExpanded = false },
                        style = DarkSurfaceVariant,
                        textColor = TextPrimary
                    )
                }
            }
        }
    }
}
