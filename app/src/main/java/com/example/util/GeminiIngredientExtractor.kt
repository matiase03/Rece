package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.model.RecipeIngredient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class ExtractedRecipeData(
    val recipeTitle: String? = null,
    val category: String? = null,
    val ingredients: List<RecipeIngredient> = emptyList(),
    val instructions: String? = null,
    val rawText: String? = null
)

object GeminiIngredientExtractor {

    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val API_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Extracts ingredients from a recipe image bitmap or URI using Gemini 3.5 Flash multimodal AI.
     */
    suspend fun extractFromImageUri(context: Context, imageUri: Uri): Result<ExtractedRecipeData> = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadAndScaleBitmap(context, imageUri)
                ?: return@withContext Result.failure(Exception("No se pudo cargar la imagen seleccionada"))

            val base64 = bitmapToBase64(bitmap)
            extractFromBase64(base64)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun extractFromBitmap(bitmap: Bitmap): Result<ExtractedRecipeData> = withContext(Dispatchers.IO) {
        try {
            val scaledBitmap = scaleBitmap(bitmap, 1280)
            val base64 = bitmapToBase64(scaledBitmap)
            extractFromBase64(base64)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun extractFromBase64(base64Image: String): Result<ExtractedRecipeData> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                Exception("Falta configurar la clave GEMINI_API_KEY en el panel de Secretos de AI Studio.")
            )
        }

        val prompt = """
            Eres un asistente culinario experto. Analiza minuciosamente la imagen adjunta, que corresponde a una receta (puede ser una página de un libro de cocina, una captura de pantalla, una foto de una revista o una receta escrita a mano).
            
            Tu objetivo principal es EXTRAER ÚNICAMENTE LOS INGREDIENTES con sus cantidades exactas y unidades de medida.
            
            Instrucciones para la extracción de ingredientes:
            1. 'name': Nombre claro del ingrediente (ej. 'Harina 0000', 'Azúcar común', 'Huevos', 'Manteca fría', 'Leche entera').
            2. 'amount': Cantidad numérica decimal (ej. 250, 2.5, 0.5, 1). Si está en fracciones como 1/2 escribe 0.5, si es 3/4 escribe 0.75, si no especifica pon 1.0.
            3. 'unit': Unidad de medida estandarizada (ej. 'g', 'kg', 'ml', 'cc', 'l', 'cda', 'cdita', 'unidad', 'taza', 'pizca', 'pote').
            4. 'notes': Cualquier aclaración de preparación (ej. 'tamizada', 'a temperatura ambiente', 'en cubos', 'rallada', 'opcional').
            
            Si en la imagen se puede leer claramente el título de la receta, extráelo en 'recipeTitle'.
            Si reconoces la categoría culinaria adecuada ('Pastelería y Tortas', 'Panadería y Masas', 'Platos Principales', 'Repostería y Galletas', 'Postres', 'Salsas y Aderezos', 'Bebidas'), indícala en 'category'.
            
            Responde EXCLUSIVAMENTE con un objeto JSON válido con la siguiente estructura (sin texto adicional):
            {
              "recipeTitle": "Nombre de la Receta si figura",
              "category": "Pastelería y Tortas",
              "ingredients": [
                {
                  "name": "Harina de trigo",
                  "amount": 250.0,
                  "unit": "g",
                  "notes": "tamizada"
                }
              ]
            }
        """.trimIndent()

        // Build Gemini REST Request JSON
        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray()
            val contentObj = JSONObject().apply {
                val partsArray = JSONArray()
                // Text part
                partsArray.put(JSONObject().put("text", prompt))
                // Inline Image Data part
                partsArray.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", base64Image)
                        }
                    )
                )
                put("parts", partsArray)
            }
            contentsArray.put(contentObj)
            put("contents", contentsArray)

            // Generation config
            val genConfig = JSONObject().apply {
                put("temperature", 0.2)
                put(
                    "responseFormat",
                    JSONObject().apply {
                        put("text", JSONObject().put("mimeType", "application/json"))
                    }
                )
            }
            put("generationConfig", genConfig)
        }

        val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val url = "$API_ENDPOINT?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBodyString = response.body?.string()

        if (!response.isSuccessful || responseBodyString == null) {
            val errorMsg = "Error en la API de Gemini (${response.code}): ${responseBodyString ?: response.message}"
            return@withContext Result.failure(Exception(errorMsg))
        }

        try {
            val rootJson = JSONObject(responseBodyString)
            val candidates = rootJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val responseText = parts?.optJSONObject(0)?.optString("text") ?: ""

            val parsedData = parseGeminiJsonResponse(responseText)
            Result.success(parsedData)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Error al procesar la respuesta de ingredientes: ${e.localizedMessage}"))
        }
    }

    private fun parseGeminiJsonResponse(rawText: String): ExtractedRecipeData {
        // Clean markdown codeblocks if present
        var cleanJson = rawText.trim()
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.removePrefix("```json").trim()
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.removePrefix("```").trim()
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.removeSuffix("```").trim()
        }

        val jsonObj = JSONObject(cleanJson)
        val title = jsonObj.optString("recipeTitle").takeIf { it.isNotBlank() }
        val category = jsonObj.optString("category").takeIf { it.isNotBlank() }

        val ingredientsList = mutableListOf<RecipeIngredient>()
        val ingArray = jsonObj.optJSONArray("ingredients")
        if (ingArray != null) {
            for (i in 0 until ingArray.length()) {
                val item = ingArray.optJSONObject(i) ?: continue
                val name = item.optString("name", "").trim()
                if (name.isBlank()) continue

                val rawAmount = item.opt("amount")
                val amount = when (rawAmount) {
                    is Number -> rawAmount.toDouble()
                    is String -> parseFractionOrNumber(rawAmount)
                    else -> 1.0
                }

                val unit = item.optString("unit", "unidad").trim().ifBlank { "unidad" }
                val notes = item.optString("notes", "").trim()

                ingredientsList.add(
                    RecipeIngredient(
                        name = name,
                        amount = if (amount <= 0.0) 1.0 else amount,
                        unit = unit,
                        notes = notes
                    )
                )
            }
        }

        return ExtractedRecipeData(
            recipeTitle = title,
            category = category,
            ingredients = ingredientsList,
            rawText = rawText
        )
    }

    private fun parseFractionOrNumber(str: String): Double {
        val clean = str.trim()
        if (clean.contains("/")) {
            val parts = clean.split("/")
            if (parts.size == 2) {
                val num = parts[0].trim().toDoubleOrNull()
                val den = parts[1].trim().toDoubleOrNull()
                if (num != null && den != null && den != 0.0) {
                    return num / den
                }
            }
        }
        return clean.toDoubleOrNull() ?: 1.0
    }

    private fun loadAndScaleBitmap(context: Context, uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            inputStream.close()

            val maxDimension = 1280
            var sampleSize = 1
            while (boundsOptions.outWidth / sampleSize > maxDimension || boundsOptions.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val secondStream = context.contentResolver.openInputStream(uri) ?: return null
            val decodedBitmap = BitmapFactory.decodeStream(secondStream, null, decodeOptions)
            secondStream.close()
            return decodedBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            inputStream?.close()
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) {
            return bitmap
        }
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (bitmap.width > bitmap.height) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt()
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
