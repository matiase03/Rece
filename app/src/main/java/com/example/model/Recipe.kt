package com.example.model

import java.util.UUID

data class RecipeIngredient(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Double,
    val unit: String,
    val notes: String = ""
) {
    fun getScaledAmount(multiplier: Double): Double {
        return amount * multiplier
    }

    companion object {
        fun formatAmount(value: Double): String {
            if (value <= 0.0) return "0"
            // If whole number
            if (value % 1.0 == 0.0) {
                return value.toLong().toString()
            }
            // Format to at most 2 decimal places without trailing zeros
            val formatted = String.format(java.util.Locale.US, "%.2f", value)
            return formatted.trimEnd('0').trimEnd('.')
        }

        val COMMON_UNITS = listOf(
            "g", "kg", "ml", "cc", "l", "taza", "cda", "cdita", "unidad",
            "pizca", "diente", "hoja", "chorrito", "paquete", "pote"
        )
    }
}

data class Recipe(
    val id: Long = 0,
    val title: String,
    val category: String,
    val baseYield: String = "4 porciones",
    val prepTimeMinutes: Int = 20,
    val cookTimeMinutes: Int = 30,
    val difficulty: String = "Intermedio",
    val ingredients: List<RecipeIngredient> = emptyList(),
    val instructions: String = "",
    val notes: String = "",
    val multiplierLimit: Float = 3.0f,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        val CATEGORIES = listOf(
            "Todas",
            "Panadería y Masas",
            "Pastelería y Tortas",
            "Repostería y Galletas",
            "Platos Principales",
            "Salsas y Guarniciones",
            "Postres Dulces",
            "Bebidas y Tragos",
            "Otras Recetas"
        )

        val DEFAULT_CATEGORIES_FOR_CREATION = listOf(
            "Panadería y Masas",
            "Pastelería y Tortas",
            "Repostería y Galletas",
            "Platos Principales",
            "Salsas y Guarniciones",
            "Postres Dulces",
            "Bebidas y Tragos",
            "Otras Recetas"
        )
    }
}
