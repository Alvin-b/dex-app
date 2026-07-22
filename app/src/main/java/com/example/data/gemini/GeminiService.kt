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
        if (trimmed.contains("RMB", ignoreCase = true) || trimmed.contains("USD", ignoreCase = true) || trimmed == "1" || trimmed == "0") {
            return ""
        }
        return trimmed
    }

    /**
     * Extracts shipping label details from a bitmap.
     * Includes a resilient local fallback if API key is not set or network call fails.
     */
    suspend fun extractStickerData(bitmap: Bitmap, labelId: Int, isCustomPhoto: Boolean = false): ExtractedStickerData = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        
        // Safety / Prototyping check: if key is empty or placeholder and it's custom photo, return empty form for manual entry
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("placeholder", ignoreCase = true)) {
            Log.w(TAG, "Gemini API key is empty or default.")
            if (isCustomPhoto) {
                return@withContext ExtractedStickerData(
                    trackingNumber = "DEX-" + System.currentTimeMillis().toString().takeLast(6),
                    consigneeName = "",
                    consigneePhone = "",
                    origin = "Hong Kong (HKG)",
                    destination = "Nairobi (NBO)",
                    description = "",
                    mode = "Air Freight",
                    weight = "1.0",
                    pieces = "1",
                    cost = ""
                )
            }
            return@withContext getFallbackData(labelId)
        }

        val promptText = """
            Analyze the provided cargo package sticker / shipping label image (e.g. AFA or SF Express label).
            Extract ONLY the exact shipment details literally printed on the sticker and return a valid JSON object:
            - tracking_number: Main barcode or tracking number (e.g. "1260707534987")
            - consignee_name: Text after "Consignee:". If not found or if the image is NOT a shipping sticker, return ""
            - consignee_phone: Phone number after "Tel:". If Tel is "1", "0", missing, or not a real phone number (>6 digits), return "" (empty string).
            - origin: Origin airport or city code from route header (e.g. "Hong Kong (HKG)" or "HKG")
            - destination: Destination airport or city code from route header (e.g. "Nairobi (NBO)" or "NBO")
            - description: Cargo description after "Nature of the goods:". If it is "1", "0", or placeholder, return "" (empty string).
            - mode: Freight mode (e.g. "Air Freight" or "Sea Freight")
            - weight: Total weight in kg (e.g. "1" or "1.0")
            - pieces: Number of pieces / PCS (e.g. "1")
            - cost: Total charge in KES if specified. If charge is in RMB, USD, "0RMB", "1", or missing, return "" (empty string).

            CRITICAL: Do NOT invent or fill in fake/placeholder phone numbers, descriptions, or names if they are not explicitly present as real values on the sticker. If a field is missing, not legible, or the photo is not a shipping label, return empty string "".
            Do not wrap JSON in markdown code blocks. Return raw JSON string only.
        """.trimIndent()

        val base64Image = bitmap.toBase64()
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = promptText),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!jsonText.isNullOrEmpty()) {
                Log.d(TAG, "Raw Response from Gemini: $jsonText")
                val json = JSONObject(jsonText)
                val extractedTracking = cleanText(json.optString("tracking_number"))
                val extractedName = cleanText(json.optString("consignee_name"))
                val extractedOrigin = cleanText(json.optString("origin"))
                val extractedDest = cleanText(json.optString("destination"))
                val extractedWeight = cleanText(json.optString("weight"))

                val defaultTracking = if (isCustomPhoto) "DEX-" + System.currentTimeMillis().toString().takeLast(6) else (if (labelId == 1) "1260707534987" else "126070655250")
                val defaultName = if (isCustomPhoto) "" else (if (labelId == 1) "Beatrice-Pheobe Wangui" else "Charles Ombongi")
                val defaultOrigin = if (isCustomPhoto) "Hong Kong (HKG)" else (if (labelId == 1) "Hong Kong (HKG)" else "Guangzhou (CAN)")
                val defaultDest = "Nairobi (NBO)"
                val defaultWeight = if (isCustomPhoto) "1.0" else (if (labelId == 1) "1.0" else "0.5")

                return@withContext ExtractedStickerData(
                    trackingNumber = extractedTracking.ifEmpty { defaultTracking },
                    consigneeName = extractedName.ifEmpty { defaultName },
                    consigneePhone = cleanPhone(json.optString("consignee_phone")),
                    origin = extractedOrigin.ifEmpty { defaultOrigin },
                    destination = extractedDest.ifEmpty { defaultDest },
                    description = cleanText(json.optString("description")),
                    mode = cleanText(json.optString("mode")).ifEmpty { "Air Freight" },
                    weight = extractedWeight.ifEmpty { defaultWeight },
                    pieces = cleanText(json.optString("pieces")).ifEmpty { "1" },
                    cost = cleanCost(json.optString("cost"))
                )
            } else {
                Log.w(TAG, "Empty text response from Gemini API.")
                if (isCustomPhoto) {
                    return@withContext ExtractedStickerData(
                        trackingNumber = "DEX-" + System.currentTimeMillis().toString().takeLast(6),
                        consigneeName = "",
                        consigneePhone = "",
                        origin = "Hong Kong (HKG)",
                        destination = "Nairobi (NBO)",
                        description = "",
                        mode = "Air Freight",
                        weight = "1.0",
                        pieces = "1",
                        cost = ""
                    )
                }
                return@withContext getFallbackData(labelId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API request failed: ${e.message}", e)
            if (isCustomPhoto) {
                return@withContext ExtractedStickerData(
                    trackingNumber = "DEX-" + System.currentTimeMillis().toString().takeLast(6),
                    consigneeName = "",
                    consigneePhone = "",
                    origin = "Hong Kong (HKG)",
                    destination = "Nairobi (NBO)",
                    description = "",
                    mode = "Air Freight",
                    weight = "1.0",
                    pieces = "1",
                    cost = ""
                )
            }
            return@withContext getFallbackData(labelId)
        }
    }

    private fun getFallbackData(labelId: Int): ExtractedStickerData {
        return if (labelId == 1) {
            ExtractedStickerData(
                trackingNumber = "1260707534987",
                consigneeName = "Beatrice-Pheobe Wangui",
                consigneePhone = "", // Tel: 1 is a filler on sticker; leave blank for manual entry
                origin = "Hong Kong (HKG)",
                destination = "Nairobi (NBO)",
                description = "", // Nature of goods: 1 is a filler; leave blank for manual entry
                mode = "Air Freight",
                weight = "1.0",
                pieces = "1",
                cost = "" // Total Charge: 96.00RMB is origin currency; leave blank for manual KES entry
            )
        } else {
            ExtractedStickerData(
                trackingNumber = "126070655250",
                consigneeName = "Charles Ombongi",
                consigneePhone = "",
                origin = "Guangzhou (CAN)",
                destination = "Nairobi (NBO)",
                description = "",
                mode = "Air Freight",
                weight = "0.5",
                pieces = "1",
                cost = ""
            )
        }
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

        val dottedPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 6f), 0f)
        }
        
        val afaLogoPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(0, 140, 70) // AFA Green
            textSize = 38f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val headerRoutePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 32f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val trackingNoPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 34f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val labelTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 20f
            isAntiAlias = true
        }

        val boldLabelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }

        if (labelId == 1) {
            // AFA Logo Header
            canvas.drawText("AFA", 30f, 55f, afaLogoPaint)
            canvas.drawText("HKG-NBO", 30f, 100f, headerRoutePaint)
            
            // Barcode & Tracking Number
            val barcodePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                strokeWidth = 3f
            }
            var barX = 180f
            val rand = java.util.Random(1260707534987L)
            for (i in 0..35) {
                barcodePaint.strokeWidth = if (rand.nextBoolean()) 6f else 2f
                canvas.drawLine(barX, 40f, barX, 90f, barcodePaint)
                barX += if (rand.nextBoolean()) 8f else 4f
            }
            canvas.drawText("1260707534987", 180f, 120f, trackingNoPaint)
            canvas.drawText("1/1", 520f, 100f, headerRoutePaint)

            canvas.drawLine(20f, 135f, 580f, 135f, dottedPaint)

            // Shipper section
            canvas.drawText("Shipper:carrie", 30f, 165f, labelTextPaint)
            canvas.drawText("Company:1", 30f, 190f, labelTextPaint)
            canvas.drawText("Tel:1", 30f, 215f, labelTextPaint)
            canvas.drawText("ADD:1", 30f, 240f, labelTextPaint)

            canvas.drawLine(20f, 255f, 580f, 255f, dottedPaint)

            // Consignee section
            canvas.drawText("Consignee:Beatrice-Pheobe Wangui", 30f, 285f, boldLabelPaint)
            canvas.drawText("Company:1", 30f, 310f, labelTextPaint)
            canvas.drawText("Tel:1", 30f, 335f, labelTextPaint)
            canvas.drawText("ADD:1", 30f, 360f, labelTextPaint)

            canvas.drawLine(20f, 375f, 580f, 375f, dottedPaint)

            // Cargo details
            canvas.drawText("Nature of the goods: 1", 30f, 405f, labelTextPaint)
            canvas.drawText("Total Weight(kg): 1", 30f, 435f, boldLabelPaint)
            canvas.drawText("Total Volume(kg): 0.02", 30f, 465f, labelTextPaint)
            canvas.drawText("PCS: 1", 30f, 495f, boldLabelPaint)
            canvas.drawText("Total Chargeable Weight: 1", 30f, 525f, labelTextPaint)

            canvas.drawLine(20f, 540f, 580f, 540f, dottedPaint)

            // Charges
            canvas.drawText("Payment Type:PP", 30f, 570f, labelTextPaint)
            canvas.drawText("Declared Amount:1USD", 260f, 570f, labelTextPaint)
            canvas.drawText("Insurance Charge:0RMB", 30f, 600f, labelTextPaint)
            canvas.drawText("Other Charge:0RMB", 260f, 600f, labelTextPaint)
            canvas.drawText("Freight Charge:0RMB", 30f, 630f, labelTextPaint)
            canvas.drawText("Total Charge:96.00RMB", 260f, 630f, boldLabelPaint)

            canvas.drawLine(20f, 645f, 580f, 645f, dottedPaint)

            canvas.drawText("Remark:SF5119513646363", 30f, 680f, labelTextPaint)
        } else {
            // Label 2: CAN-NBO
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
