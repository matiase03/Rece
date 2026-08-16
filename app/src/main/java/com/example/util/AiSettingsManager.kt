package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

enum class AiProvider(val displayName: String, val description: String) {
    GROQ(
        displayName = "Groq (Llama 3.2 Vision)",
        description = "Ultra rápido y alta disponibilidad (Recomendado por defecto)"
    ),
    GEMINI(
        displayName = "Google Gemini (3.5 Flash)",
        description = "Modelo multimodal oficial de Google AI Studio"
    )
}

object AiSettingsManager {

    private const val PREFS_NAME = "ai_provider_preferences"
    private const val KEY_ACTIVE_PROVIDER = "key_active_provider"
    private const val KEY_CUSTOM_GROQ_KEY = "key_custom_groq_key"
    private const val KEY_CUSTOM_GEMINI_KEY = "key_custom_gemini_key"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getActiveProvider(context: Context): AiProvider {
        val name = getPrefs(context).getString(KEY_ACTIVE_PROVIDER, AiProvider.GROQ.name)
        return try {
            AiProvider.valueOf(name ?: AiProvider.GROQ.name)
        } catch (e: Exception) {
            AiProvider.GROQ
        }
    }

    fun setActiveProvider(context: Context, provider: AiProvider) {
        getPrefs(context).edit().putString(KEY_ACTIVE_PROVIDER, provider.name).apply()
    }

    fun getCustomGroqKey(context: Context): String {
        return getPrefs(context).getString(KEY_CUSTOM_GROQ_KEY, "") ?: ""
    }

    fun setCustomGroqKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_GROQ_KEY, key.trim()).apply()
    }

    fun getCustomGeminiKey(context: Context): String {
        return getPrefs(context).getString(KEY_CUSTOM_GEMINI_KEY, "") ?: ""
    }

    fun setCustomGeminiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_GEMINI_KEY, key.trim()).apply()
    }

    /**
     * Resolves effective Groq API key:
     * 1. In-app custom key if entered
     * 2. BuildConfig.GROQ_API_KEY from .env / GitHub secrets
     */
    fun resolveGroqApiKey(context: Context): String {
        val customKey = getCustomGroqKey(context)
        if (customKey.isNotBlank()) return customKey

        val buildConfigKey = try {
            val field = BuildConfig::class.java.getField("GROQ_API_KEY")
            field.get(null) as? String ?: ""
        } catch (e: Throwable) {
            ""
        }

        if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GROQ_API_KEY") {
            return buildConfigKey
        }
        return ""
    }

    /**
     * Resolves effective Gemini API key:
     * 1. In-app custom key if entered
     * 2. BuildConfig.GEMINI_API_KEY from .env / GitHub secrets
     */
    fun resolveGeminiApiKey(context: Context): String {
        val customKey = getCustomGeminiKey(context)
        if (customKey.isNotBlank()) return customKey

        val buildConfigKey = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            field.get(null) as? String ?: ""
        } catch (e: Throwable) {
            ""
        }

        if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") {
            return buildConfigKey
        }
        return ""
    }

    fun isKeyConfiguredForProvider(context: Context, provider: AiProvider): Boolean {
        return when (provider) {
            AiProvider.GROQ -> resolveGroqApiKey(context).isNotBlank()
            AiProvider.GEMINI -> resolveGeminiApiKey(context).isNotBlank()
        }
    }
}
