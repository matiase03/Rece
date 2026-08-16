package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
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

object GroqIngredientExtractor {

    private const val MODEL_NAME = "llama-3.2-11b-vision-preview"
    private const val API_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * Extracts ingredients from a recipe image bitmap or URI using Groq's Llama 3.2 Vision model.
     */
    suspend fun extractFromImageUri(
        context: Context,
        imageUri: Uri,
        apiKeyOverride: String? = null
    ): Result<ExtractedRecipeData> = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadAndScaleBitmap(context, imageUri)
                ?: return@withContext Result.failure(Exception("No se pudo cargar la imagen seleccionada"))

            val base64 = bitmapToBase64(bitmap)
            val apiKey = apiKeyOverride?.takeIf { it.isNotBlank() } ?: AiSettingsManager.resolveGroqApiKey(context)

            extractFromBase64(base64, apiKey)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun extractFromBitmap(
        context: Context,
        bitmap: Bitmap,
        apiKeyOverride: String? = null
    ): Result<ExtractedRecipeData> = withContext(Dispatchers.IO) {
        try {
            val scaledBitmap = scaleBitmap(bitmap, 1280)
            val base64 = bitmapToBase64(scaledBitmap)
            val apiKey = apiKeyOverride?.takeIf { it.isNotBlank() } ?: AiSettingsManager.resolveGroqApiKey(context)

            extractFromBase64(base64, apiKey)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun extractFromBase64(
        base64Image: String,
        apiKey: String
    ): Result<ExtractedRecipeData> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                Exception("Falta configurar la clave de Groq (GROQ_API_KEY). Puedes configurarla en Ajustes de IA o como secreto en GitHub.")
            )
        }

        val prompt = """
            Eres un asistente culinario experto en análisis visual de recetas. Analiza minuciosamente la imagen adjunta (puede ser una captura de pantalla de una aplicación de recetas con tarjetas/columnas, la página de un libro de cocina, una revista o una receta escrita a mano).
            
            Tu objetivo principal es EXTRAER ÚNICAMENTE LOS INGREDIENTES con sus cantidades exactas y unidades de medida.
            
            Reglas de extracción:
            1. Presta especial atención a formatos organizados en columnas o tarjetas (ej. una tarjeta con '500g Harina 000' y otra con '500g Harina 0000'). Relaciona cada número y unidad con su ingrediente correspondiente.
            2. 'name': Nombre claro del ingrediente (ej. 'Harina 000', 'Harina 0000', 'Huevos', 'Agua', 'Sal', 'Azúcar', 'Levadura', 'Manteca').
            3. 'amount': Cantidad numérica decimal (ej. 500, 2, 460, 30, 20, 100, 1.5, 0.5). Si está en fracciones como 1/2 escribe 0.5, si es 3/4 escribe 0.75, si es 1/4 escribe 0.25. Si no se especifica cantidad, usa 1.0.
            4. 'unit': Unidad de medida estandarizada (ej. 'g', 'kg', 'ml', 'cc', 'l', 'cda', 'cdita', 'unidad', 'huevos', 'taza', 'pizca', 'sobre'). Si son huevos o unidades, pon 'unidad'.
            5. 'notes': Cualquier aclaración de preparación que figure (ej. 'por bollo', 'fresca', 'tibia', 'a temperatura ambiente', 'derretida').
            
            Si en la imagen se puede leer claramente el título de la receta (ej. 'Lomo', 'Pan de Campo', 'Tarta de Manzana'), extráelo en 'recipeTitle'.
            Si reconoces la categoría culinaria adecuada ('Panadería y Masas', 'Pastelería y Tortas', 'Platos Principales', 'Repostería y Galletas', 'Postres', 'Salsas y Aderezos', 'Bebidas'), indícala en 'category'.
            
            Responde EXCLUSIVAMENTE con un JSON válido con esta estructura:
            {
              "recipeTitle": "Nombre de la receta",
              "category": "Panadería y Masas",
              "ingredients": [
                {
                  "name": "Harina 000",
                  "amount": 500.0,
                  "unit": "g",
                  "notes": ""
                }
              ]
            }
        """.trimIndent()

        try {
            // Build OpenAI/Groq format JSON
            val messagesArray = JSONArray().apply {
                val userMessage = JSONObject().apply {
                    put("role", "user")
                    val contentArray = JSONArray().apply {
                        // Text prompt
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", prompt)
                        })
                        // Image URL object with Base64 data
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64Image")
                            })
                        })
                    }
                    put("content", contentArray)
                }
                put(userMessage)
            }

            val requestJson = JSONObject().apply {
                put("model", MODEL_NAME)
                put("messages", messagesArray)
                put("temperature", 0.1)
                put("response_format", JSONObject().apply {
                    put("type", "json_object")
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(API_ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBodyString = response.body?.string()

            if (!response.isSuccessful || responseBodyString == null) {
                val errorMsg = when (response.code) {
                    401 -> "Clave de Groq inválida. Verifica tu GROQ_API_KEY en Ajustes."
                    429 -> "Límite de solicitudes en Groq alcanzado temporalmente. Intenta nuevamente en unos instantes o cambia a Gemini."
                    else -> "Error en Groq (${response.code}): ${responseBodyString ?: response.message}"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val rootJson = JSONObject(responseBodyString)
            val choices = rootJson.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val message = firstChoice?.optJSONObject("message")
            val contentString = message?.optString("content") ?: ""

            val parsedData = parseGroqJsonResponse(contentString)
            Result.success(parsedData)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Error al procesar respuesta de Groq: ${e.localizedMessage}"))
        }
    }

    private fun parseGroqJsonResponse(rawText: String): ExtractedRecipeData {
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
