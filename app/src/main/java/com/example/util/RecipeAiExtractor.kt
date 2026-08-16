package com.example.util

import android.content.Context
import android.net.Uri

object RecipeAiExtractor {

    suspend fun extractFromImageUri(
        context: Context,
        imageUri: Uri,
        providerOverride: AiProvider? = null
    ): Result<ExtractedRecipeData> {
        val activeProvider = providerOverride ?: AiSettingsManager.getActiveProvider(context)

        return when (activeProvider) {
            AiProvider.GROQ -> {
                GroqIngredientExtractor.extractFromImageUri(context, imageUri)
            }
            AiProvider.GEMINI -> {
                GeminiIngredientExtractor.extractFromImageUri(context, imageUri)
            }
        }
    }
}
