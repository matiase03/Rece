package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Recipe
import com.example.model.RecipeIngredient

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String,
    val baseYield: String,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val difficulty: String,
    val ingredientsJson: String,
    val instructions: String,
    val notes: String,
    val multiplierLimit: Float = 3.0f,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Recipe {
        return Recipe(
            id = id,
            title = title,
            category = category,
            baseYield = baseYield,
            prepTimeMinutes = prepTimeMinutes,
            cookTimeMinutes = cookTimeMinutes,
            difficulty = difficulty,
            ingredients = Converters.jsonToIngredients(ingredientsJson),
            instructions = instructions,
            notes = notes,
            multiplierLimit = multiplierLimit,
            isFavorite = isFavorite,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(recipe: Recipe): RecipeEntity {
            return RecipeEntity(
                id = recipe.id,
                title = recipe.title,
                category = recipe.category,
                baseYield = recipe.baseYield,
                prepTimeMinutes = recipe.prepTimeMinutes,
                cookTimeMinutes = recipe.cookTimeMinutes,
                difficulty = recipe.difficulty,
                ingredientsJson = Converters.ingredientsToJson(recipe.ingredients),
                instructions = recipe.instructions,
                notes = recipe.notes,
                multiplierLimit = recipe.multiplierLimit,
                isFavorite = recipe.isFavorite,
                createdAt = recipe.createdAt,
                updatedAt = recipe.updatedAt
            )
        }
    }
}
