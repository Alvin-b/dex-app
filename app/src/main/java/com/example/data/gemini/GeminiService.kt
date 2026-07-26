package com.example.data.gemini

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import org.json.JSONObject

// --- DATA TRANSFER OBJECTS FOR GEMINI API ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "responseMimeType") val responseMimeType: String? = null,
    @Json(name = "temperature") val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content? = null
)

// --- RETROFIT SERVICE INTERFACE ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

// --- RETROFIT CLIENT ---

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

// --- EXTRACTED MODEL ---

data class ExtractedStickerData(
    val trackingNumber: String,
    val consigneeName: String,
    val consigneePhone: String,
    val origin: String,
    val destination: String,
    val description: String,
    val mode: String,
    val weight: String,
    val pieces: String,
    val cost: String
)

// --- OCR CONTROLLER & HELPER ---

object GeminiOcrHelper {
    private const val TAG = "GeminiOcrHelper"

    // Helper to convert Bitmap to Base64 String
    fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Helper clean functions to ensure missing or placeholder sticker fields (e.g., Tel: 1, Nature of goods: 1)
     * are left blank so staff can fill them in manually.
     */
    private fun cleanPhone(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        val trimmed = phone.trim()
        if (trimmed == "1" || trimmed == "0" || trimmed.length < 6 || trimmed.equals("N/A", ignoreCase = true)) {
            return ""
        }
        return trimmed
    }

    private fun cleanText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val trimmed = text.trim()
        if (trimmed == "1" || trimmed == "0" || trimmed.equals("N/A", ignoreCase = true) || trimmed.equals("none", ignoreCase = true)) {
            return ""
        }
        return trimmed
    }

    private fun cleanCost(cost: String?): String {
        if (cost.isNullOrBlank()) return ""
        val trimmed = cost.trim()
        if (trimmed.contains("RMB", ignoreCase = true) || trimmed.contains("USD", ignoreCase = true) || trimmed == "1" || trimmed == "0" || trimmed == "0.00") {
            return ""
        }
        return trimmed
    }

    private fun isValidPersonName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val trimmed = name.trim()
        if (trimmed == "1" || trimmed == "0" || trimmed.length < 2) return false
        val uppercase = trimmed.uppercase()
        val invalidKeywords = listOf(
            "COMPANY", "SHIPPER", "TEL", "PHONE", "WEIGHT", "PCS", "NATURE",
            "FREIGHT", "EXPRESS", "LOGISTICS", "HONG KONG", "GUANGZHOU", "NAIROBI",
            "LIMITED", "CO.", "CO.,", "LTD", "ADD", "ROUTE", "CARRIER", "TOTAL",
            "CHARGE", "PAYMENT", "REMARK", "DECLARED", "INSURANCE", "AMOUNT", "AFA", "DEX"
        )
        if (invalidKeywords.any { uppercase.contains(it) }) return false
        return trimmed.any { it.isLetter() }
    }

    /**
     * Performs LOCAL ON-DEVICE OCR using Google ML Kit Vision Text Recognition directly on the picture bitmap.
     */
    suspend fun extractTextWithMlKit(bitmap: Bitmap): ExtractedStickerData = withContext(Dispatchers.IO) {
        try {
            val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
            )
            val visionText = com.google.android.gms.tasks.Tasks.await(recognizer.process(inputImage))
            val rawText = visionText.text ?: ""
            Log.d(TAG, "ML Kit Local OCR Recognized Raw Text:\n$rawText")

            return@withContext parseRawTextToStickerData(rawText)
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit OCR failed: ${e.message}", e)
            return@withContext ExtractedStickerData(
                trackingNumber = "",
                consigneeName = "",
                consigneePhone = "",
                origin = "",
                destination = "",
                description = "",
                mode = "",
                weight = "",
                pieces = "",
                cost = ""
            )
        }
    }

    fun isStickerLabel(rawText: String): Boolean {
        val uppercaseText = rawText.uppercase()
        val indicators = listOf(
            "CONSIGNEE", "C/N", "TRACKING", "WAYBILL", "AWB", "NATURE", "GOODS",
            "WEIGHT", "PCS", "PIECES", "HKG", "CAN", "NBO", "FREIGHT", "SHIPPER",
            "TEL:", "PHONE:", "DESTINATION", "ORIGIN", "DEX", "TOTAL WEIGHT"
        )
        val matchCount = indicators.count { uppercaseText.contains(it) }
        val hasLongDigits = Regex("""\b\d{8,14}\b""").containsMatchIn(rawText)
        return matchCount >= 2 || (matchCount >= 1 && hasLongDigits)
    }

    /**
     * Parses raw extracted text from a physical package sticker photo into structured sticker data.
     * Leaves missing/unrecognized fields completely blank ("") for clean manual form entry without hallucinations.
     */
    fun parseRawTextToStickerData(rawText: String): ExtractedStickerData {
        if (!isStickerLabel(rawText)) {
            return ExtractedStickerData(
                trackingNumber = "",
                consigneeName = "",
                consigneePhone = "",
                origin = "",
                destination = "",
                description = "",
                mode = "",
                weight = "",
                pieces = "",
                cost = ""
            )
        }

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val fullText = rawText.uppercase()

        // 1. Extract Tracking Number
        var tracking = ""
        val trackingRegex = Regex("""\b(126\d{8,12}|DEX-\d+|SF\d{10,14}|AFA\d+|\d{8,14})\b""", RegexOption.IGNORE_CASE)
        val matchTracking = trackingRegex.find(rawText)
        if (matchTracking != null) {
            tracking = matchTracking.value.trim()
        } else {
            val digitLine = lines.firstOrNull { l ->
                l.count { it.isDigit() } >= 8 &&
                !l.contains("Tel", ignoreCase = true) &&
                !l.contains("Phone", ignoreCase = true) &&
                !l.contains("Cost", ignoreCase = true)
            }
            if (digitLine != null) {
                tracking = digitLine.replace(Regex("""[^\d]"""), "").trim()
            }
        }

        // 2. Extract Consignee Name (Strict label-value binding)
        var consigneeName = ""
        for (i in lines.indices) {
            val line = lines[i]
            val isConsigneeLabel = line.contains("Consignee", ignoreCase = true) ||
                    line.contains("C/N:", ignoreCase = true) ||
                    line.contains("C/N ", ignoreCase = true) ||
                    line.contains("Receiver", ignoreCase = true) ||
                    line.contains("To:", ignoreCase = true)
            if (isConsigneeLabel) {
                var extractedVal = line.substringAfter(":", line.substringAfter("Consignee", ""))
                    .replace(Regex("""(?i)Consignee|C/N|Receiver|Name|To|Company"""), "")
                    .trim().removePrefix(":").removePrefix("-").trim()
                
                // Strip out phone numbers if appended on same line
                if (extractedVal.contains("Tel", ignoreCase = true) || extractedVal.contains("Phone", ignoreCase = true)) {
                    extractedVal = extractedVal.substringBefore("Tel", extractedVal.substringBefore("Phone")).trim()
                }

                if (isValidPersonName(extractedVal)) {
                    consigneeName = extractedVal
                    break
                } else if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1]
                    if (isValidPersonName(nextLine)) {
                        consigneeName = nextLine
                        break
                    }
                }
            }
        }
        consigneeName = cleanText(consigneeName)

        // 3. Extract Consignee Phone
        var phone = ""
        val phoneRegex = Regex("""(?:Tel|Phone|Mobile|Contact|Cell)[:\s]*([0-9\+\s-]{7,15})""", RegexOption.IGNORE_CASE)
        val phoneMatch = phoneRegex.find(rawText)
        if (phoneMatch != null) {
            phone = phoneMatch.groupValues[1].trim()
        } else {
            for (line in lines) {
                if (line.contains("Tel", ignoreCase = true) || line.contains("Phone", ignoreCase = true)) {
                    val p = line.substringAfter(":", "").replace(Regex("""[^\d\+]"""), "").trim()
                    if (p.length >= 7) {
                        phone = p
                        break
                    }
                }
            }
        }
        phone = cleanPhone(phone)

        // 4. Extract Route / Origin / Destination via Sticker Template Matching
        var origin = ""
        var dest = ""

        // Check explicit sticker origin key-value template labels
        val originRegex = Regex("""(?:ORIGIN|FROM|SHIPPER ORIGIN|DEPARTURE)[:\s]*([A-Za-z\s\(\)]+)""", RegexOption.IGNORE_CASE)
        val originMatch = originRegex.find(rawText)
        if (originMatch != null) {
            val matchedVal = originMatch.groupValues[1].uppercase()
            if (matchedVal.contains("CAN") || matchedVal.contains("GUANGZHOU")) origin = "Guangzhou (CAN)"
            else if (matchedVal.contains("HKG") || matchedVal.contains("HONG KONG")) origin = "Hong Kong (HKG)"
            else if (matchedVal.contains("PEK") || matchedVal.contains("BEIJING")) origin = "Beijing (PEK)"
            else if (matchedVal.contains("PVG") || matchedVal.contains("SHANGHAI")) origin = "Shanghai (PVG)"
            else if (matchedVal.contains("SZX") || matchedVal.contains("SHENZHEN")) origin = "Shenzhen (SZX)"
        }

        // Check explicit sticker destination key-value template labels
        val destRegex = Regex("""(?:DESTINATION|DEST|TO|SHIP TO|DELIVER TO)[:\s]*([A-Za-z\s\(\)]+)""", RegexOption.IGNORE_CASE)
        val destMatch = destRegex.find(rawText)
        if (destMatch != null) {
            val matchedVal = destMatch.groupValues[1].uppercase()
            if (matchedVal.contains("NBO") || matchedVal.contains("NAIROBI") || matchedVal.contains("KENYA")) dest = "Nairobi (NBO)"
            else if (matchedVal.contains("MBA") || matchedVal.contains("MOMBASA")) dest = "Mombasa (MBA)"
            else if (matchedVal.contains("KIS") || matchedVal.contains("KISUMU")) dest = "Kisumu (KIS)"
        }

        // Fallback to route indicator templates if not matched via explicit labels
        if (origin.isBlank() || dest.isBlank()) {
            if (fullText.contains("CAN-NBO")) {
                if (origin.isBlank()) origin = "Guangzhou (CAN)"
                if (dest.isBlank()) dest = "Nairobi (NBO)"
            } else if (fullText.contains("HKG-NBO")) {
                if (origin.isBlank()) origin = "Hong Kong (HKG)"
                if (dest.isBlank()) dest = "Nairobi (NBO)"
            } else {
                if (origin.isBlank()) {
                    origin = when {
                        fullText.contains("GUANGZHOU") || fullText.contains("CAN") -> "Guangzhou (CAN)"
                        fullText.contains("HONG KONG") || fullText.contains("HKG") -> "Hong Kong (HKG)"
                        fullText.contains("BEIJING") || fullText.contains("PEK") -> "Beijing (PEK)"
                        fullText.contains("SHANGHAI") || fullText.contains("PVG") -> "Shanghai (PVG)"
                        fullText.contains("SHENZHEN") || fullText.contains("SZX") -> "Shenzhen (SZX)"
                        else -> ""
                    }
                }
                if (dest.isBlank()) {
                    dest = when {
                        fullText.contains("NAIROBI") || fullText.contains("NBO") -> "Nairobi (NBO)"
                        fullText.contains("MOMBASA") || fullText.contains("MBA") -> "Mombasa (MBA)"
                        fullText.contains("KISUMU") || fullText.contains("KIS") -> "Kisumu (KIS)"
                        else -> ""
                    }
                }
            }
        }

        // 5. Extract Nature of Goods / Description
        var desc = ""
        val descRegex = Regex("""(?:Nature of (?:the )?goods|Description|Goods|Item|Cargo|Commodity)[:\s]*([^\n\r]+)""", RegexOption.IGNORE_CASE)
        val descMatch = descRegex.find(rawText)
        if (descMatch != null) {
            desc = descMatch.groupValues[1].trim()
        } else {
            for (line in lines) {
                if (line.contains("Nature of", ignoreCase = true) || line.contains("Goods", ignoreCase = true)) {
                    desc = line.substringAfter(":").trim()
                    break
                }
            }
        }
        desc = cleanText(desc)

        // 6. Extract Mode
        var mode = ""
        if (fullText.contains("SEA FREIGHT") || fullText.contains("SEA")) {
            mode = "Sea Freight"
        } else if (fullText.contains("AIR FREIGHT") || fullText.contains("AIR") || fullText.contains("EXPRESS")) {
            mode = "Air Freight"
        }

        // 7. Extract Weight
        var weight = ""
        val weightRegex = Regex("""(?:Total Weight\(kg\)|Total Weight|Weight|WT|Gross Wt)[:\s]*([\d\.]+)""", RegexOption.IGNORE_CASE)
        val weightMatch = weightRegex.find(rawText)
        if (weightMatch != null) {
            weight = weightMatch.groupValues[1].trim()
        } else {
            val kgMatch = Regex("""([\d\.]+)\s*kg""", RegexOption.IGNORE_CASE).find(rawText)
            if (kgMatch != null) {
                weight = kgMatch.groupValues[1].trim()
            }
        }

        // 8. Extract PCS
        var pcs = ""
        val pcsRegex = Regex("""(?:PCS|Pieces|Qty)[:\s]*(\d+)""", RegexOption.IGNORE_CASE)
        val pcsMatch = pcsRegex.find(rawText)
        if (pcsMatch != null) {
            pcs = pcsMatch.groupValues[1].trim()
        } else if (fullText.contains("1/1")) {
            pcs = "1"
        }

        // 9. Extract Cost / Total Charge
        var cost = ""
        val costRegex = Regex("""(?:Total Charge|Freight Charge|Charge|Cost)[:\s]*([0-9\.]+)""", RegexOption.IGNORE_CASE)
        val costMatch = costRegex.find(rawText)
        if (costMatch != null) {
            cost = costMatch.groupValues[1].trim()
        }
        cost = cleanCost(cost)

        val rawExtracted = ExtractedStickerData(
            trackingNumber = tracking,
            consigneeName = consigneeName,
            consigneePhone = phone,
            origin = origin,
            destination = dest,
            description = desc,
            mode = mode,
            weight = weight,
            pieces = pcs,
            cost = cost
        )

        return CargoStickerSanitizer.sanitizeAndMap(rawExtracted)
    }

    /**
     * Extracts shipping label details from a camera bitmap.
     * Calls backend POST /api/public/gemini-ocr using the staff access token,
     * falling back to on-device ML Kit Text Recognition if offline or on network error.
     */
    suspend fun extractStickerData(bitmap: Bitmap, labelId: Int, isCustomPhoto: Boolean = false): ExtractedStickerData = withContext(Dispatchers.IO) {
        val b64Image = bitmap.toBase64()
        val authHeader = com.example.data.api.SupabaseClient.getBearerHeader()

        try {
            val response = com.example.data.api.SupabaseClient.api.ocrGemini(
                authHeader = authHeader,
                apiKey = com.example.data.api.SupabaseClient.API_KEY,
                body = mapOf(
                    "image_base64" to b64Image,
                    "image" to b64Image,
                    "label_id" to labelId.toString()
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val resMap = response.body()!!
                val rawExtracted = ExtractedStickerData(
                    trackingNumber = (resMap["tracking_number"] ?: resMap["trackingNumber"] ?: "").toString(),
                    consigneeName = (resMap["consignee_name"] ?: resMap["consigneeName"] ?: resMap["consignee"] ?: "").toString(),
                    consigneePhone = (resMap["consignee_phone"] ?: resMap["consigneePhone"] ?: resMap["phone"] ?: "").toString(),
                    origin = (resMap["origin"] ?: "").toString(),
                    destination = (resMap["destination"] ?: resMap["dest"] ?: "").toString(),
                    description = (resMap["description"] ?: resMap["desc"] ?: "").toString(),
                    mode = (resMap["mode"] ?: "").toString(),
                    weight = (resMap["weight_kg"] ?: resMap["weight"] ?: "").toString(),
                    pieces = (resMap["pcs"] ?: resMap["pieces"] ?: "").toString(),
                    cost = (resMap["amount_due"] ?: resMap["cost"] ?: "").toString()
                )

                val extracted = CargoStickerSanitizer.sanitizeAndMap(rawExtracted)

                if (extracted.trackingNumber.isNotBlank() || extracted.consigneePhone.isNotBlank() || extracted.consigneeName.isNotBlank()) {
                    Log.i(TAG, "Backend Gemini OCR successfully extracted sticker data: $extracted")
                    return@withContext extracted
                }
            } else {
                Log.w(TAG, "Backend Gemini OCR returned HTTP ${response.code()}. Falling back to local ML Kit OCR...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Backend Gemini OCR endpoint failed: ${e.message}. Falling back to ML Kit...", e)
        }

        // On-device ML Kit fallback
        val mlKitExtracted = extractTextWithMlKit(bitmap)
        Log.i(TAG, "ML Kit Local OCR Result: $mlKitExtracted")
        return@withContext mlKitExtracted
    }

    /**
     * Programmatically generates a high-fidelity cargo shipping sticker Bitmap
     * modeled after real AFA / SF Express package stickers.
     */
    fun generateStickerBitmap(labelId: Int): Bitmap {
        val width = 600
        val height = 720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val bgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        
        val borderPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRect(12f, 12f, (width - 12).toFloat(), (height - 12).toFloat(), borderPaint)

        val headerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.RED
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRect(12f, 12f, (width - 12).toFloat(), 90f, headerPaint)

        val headerTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 32f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText("DEXCARGO LOGISTICS - AIR/SEA", (width / 2).toFloat(), 62f, headerTextPaint)

        val boldLabelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 22f
            isFakeBoldText = true
        }

        val labelTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 20f
        }

        val dottedPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }

        if (labelId == 1) {
            canvas.drawText("Tracking #: DEX-NBO-88219", 30f, 125f, boldLabelPaint)
            canvas.drawLine(20f, 135f, 580f, 135f, dottedPaint)

            canvas.drawText("Shipper: Guangzhou Hub Depot", 30f, 165f, labelTextPaint)
            canvas.drawText("Company: Dex Cargo China Ltd", 30f, 190f, labelTextPaint)
            canvas.drawText("Tel: +86 20 8331 9900", 30f, 215f, labelTextPaint)

            canvas.drawLine(20f, 255f, 580f, 255f, dottedPaint)

            canvas.drawText("Consignee: John Kamau", 30f, 285f, boldLabelPaint)
            canvas.drawText("Tel: +254 712 345 678", 30f, 310f, labelTextPaint)
            canvas.drawText("Address: Luthuli Ave, Nairobi", 30f, 335f, labelTextPaint)

            canvas.drawLine(20f, 375f, 580f, 375f, dottedPaint)

            canvas.drawText("Nature of Goods: Electronics & Accessories", 30f, 405f, labelTextPaint)
            canvas.drawText("Total Weight: 14.50 kg", 30f, 435f, boldLabelPaint)
            canvas.drawText("PCS: 2", 30f, 465f, boldLabelPaint)
            canvas.drawText("Mode: Air Freight (Express)", 30f, 495f, labelTextPaint)

            canvas.drawLine(20f, 540f, 580f, 540f, dottedPaint)

            canvas.drawText("Payment Type: Freight Collect", 30f, 570f, labelTextPaint)
            canvas.drawText("Total Charge: KES 18,500", 260f, 570f, boldLabelPaint)

            canvas.drawLine(20f, 645f, 580f, 645f, dottedPaint)

            canvas.drawText("Remark: Handle with care - Fragile", 30f, 680f, labelTextPaint)
        } else {
            val afaLogoPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.RED
                textSize = 36f
                isFakeBoldText = true
            }

            val headerRoutePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 32f
                isFakeBoldText = true
            }

            val trackingNoPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 18f
            }

            canvas.drawText("AFA", 30f, 55f, afaLogoPaint)
            canvas.drawText("CAN-NBO", 30f, 100f, headerRoutePaint)

            val barcodePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                strokeWidth = 3f
            }
            var barX = 180f
            val rand = java.util.Random(126070655250L)
            for (i in 0..35) {
                barcodePaint.strokeWidth = if (rand.nextBoolean()) 6f else 2f
                canvas.drawLine(barX, 40f, barX, 90f, barcodePaint)
                barX += if (rand.nextBoolean()) 8f else 4f
            }
            canvas.drawText("126070655250", 180f, 120f, trackingNoPaint)
            canvas.drawText("1/1", 520f, 100f, headerRoutePaint)

            canvas.drawLine(20f, 135f, 580f, 135f, dottedPaint)

            canvas.drawText("Shipper:Guangzhou Cargo Hub", 30f, 165f, labelTextPaint)
            canvas.drawText("Company:Express Logistics", 30f, 190f, labelTextPaint)
            canvas.drawText("Tel:1", 30f, 215f, labelTextPaint)

            canvas.drawLine(20f, 255f, 580f, 255f, dottedPaint)

            canvas.drawText("Consignee:Charles Ombongi", 30f, 285f, boldLabelPaint)
            canvas.drawText("Tel:1", 30f, 310f, labelTextPaint)

            canvas.drawLine(20f, 375f, 580f, 375f, dottedPaint)

            canvas.drawText("Nature of the goods: 1", 30f, 405f, labelTextPaint)
            canvas.drawText("Total Weight(kg): 0.5", 30f, 435f, boldLabelPaint)
            canvas.drawText("PCS: 1", 30f, 465f, boldLabelPaint)

            canvas.drawLine(20f, 540f, 580f, 540f, dottedPaint)

            canvas.drawText("Payment Type:PP", 30f, 570f, labelTextPaint)
            canvas.drawText("Total Charge:45.00RMB", 260f, 570f, boldLabelPaint)

            canvas.drawLine(20f, 645f, 580f, 645f, dottedPaint)

            canvas.drawText("Remark:SF511988877112", 30f, 680f, labelTextPaint)
        }
        
        return bitmap
    }
}

object CargoStickerSanitizer {
    /**
     * Sanitizes extracted field data against known cargo sticker patterns before assigning to form fields.
     */
    fun sanitizeAndMap(raw: ExtractedStickerData): ExtractedStickerData {
        return ExtractedStickerData(
            trackingNumber = sanitizeTracking(raw.trackingNumber),
            consigneeName = sanitizeName(raw.consigneeName),
            consigneePhone = sanitizePhone(raw.consigneePhone),
            origin = sanitizeOrigin(raw.origin),
            destination = sanitizeDestination(raw.destination),
            description = sanitizeDescription(raw.description),
            mode = sanitizeMode(raw.mode),
            weight = sanitizeWeight(raw.weight),
            pieces = sanitizePieces(raw.pieces),
            cost = sanitizeCost(raw.cost)
        )
    }

    private fun sanitizeTracking(input: String): String {
        if (input.isBlank()) return ""
        val clean = input.uppercase().replace(Regex("""[^A-Z0-9-]"""), "").trim()
        return if (clean.length >= 4) clean else ""
    }

    private fun sanitizeName(input: String): String {
        if (input.isBlank()) return ""
        val clean = input.replace(Regex("""(?i)\b(consignee|c/n|to|receiver|tel|phone|contact|name|company)\b"""), "")
            .replace(Regex("""[:\-#]"""), " ")
            .trim()
        return if (clean.length >= 2 && !clean.all { it.isDigit() }) clean else ""
    }

    private fun sanitizePhone(input: String): String {
        if (input.isBlank()) return ""
        val clean = input.replace(Regex("""[^\d\+]"""), "").trim()
        return if (clean.length >= 7) clean else ""
    }

    private fun sanitizeOrigin(input: String): String {
        val upper = input.uppercase()
        return when {
            upper.contains("CAN") || upper.contains("GUANGZHOU") -> "Guangzhou (CAN)"
            upper.contains("HKG") || upper.contains("HONG KONG") -> "Hong Kong (HKG)"
            upper.contains("PEK") || upper.contains("BEIJING") -> "Beijing (PEK)"
            upper.contains("PVG") || upper.contains("SHANGHAI") -> "Shanghai (PVG)"
            upper.contains("SZX") || upper.contains("SHENZHEN") -> "Shenzhen (SZX)"
            upper.contains("DXB") || upper.contains("DUBAI") -> "Dubai (DXB)"
            else -> ""
        }
    }

    private fun sanitizeDestination(input: String): String {
        val upper = input.uppercase()
        return when {
            upper.contains("NBO") || upper.contains("NAIROBI") || upper.contains("KENYA") -> "Nairobi (NBO)"
            upper.contains("MBA") || upper.contains("MOMBASA") -> "Mombasa (MBA)"
            upper.contains("KIS") || upper.contains("KISUMU") -> "Kisumu (KIS)"
            upper.contains("EBB") || upper.contains("ENTEBBE") -> "Entebbe (EBB)"
            upper.contains("DAR") -> "Dar es Salaam (DAR)"
            upper.contains("KGL") || upper.contains("KIGALI") -> "Kigali (KGL)"
            else -> ""
        }
    }

    private fun sanitizeDescription(input: String): String {
        if (input.isBlank()) return ""
        val clean = input.replace(Regex("""(?i)\b(nature of goods|description|goods|item|cargo|commodity)\b"""), "")
            .replace(Regex("""[:\-#]"""), " ")
            .trim()
        return if (clean.length >= 2) clean else ""
    }

    private fun sanitizeMode(input: String): String {
        val upper = input.uppercase()
        return when {
            upper.contains("SEA") -> "Sea Freight"
            upper.contains("AIR") || upper.contains("EXPRESS") -> "Air Freight"
            else -> ""
        }
    }

    private fun sanitizeWeight(input: String): String {
        if (input.isBlank()) return ""
        val numMatch = Regex("""([\d\.]+)""").find(input)
        val num = numMatch?.value?.toDoubleOrNull()
        return if (num != null && num > 0.0) {
            if (num == num.toLong().toDouble()) num.toLong().toString() else num.toString()
        } else ""
    }

    private fun sanitizePieces(input: String): String {
        if (input.isBlank()) return ""
        val numMatch = Regex("""(\d+)""").find(input)
        val num = numMatch?.value?.toIntOrNull()
        return if (num != null && num > 0) num.toString() else ""
    }

    private fun sanitizeCost(input: String): String {
        if (input.isBlank()) return ""
        val numMatch = Regex("""([\d\.]+)""").find(input)
        val num = numMatch?.value?.toDoubleOrNull()
        return if (num != null && num >= 0.0) {
            if (num == num.toLong().toDouble()) num.toLong().toString() else String.format(java.util.Locale.US, "%.2f", num)
        } else ""
    }
}
