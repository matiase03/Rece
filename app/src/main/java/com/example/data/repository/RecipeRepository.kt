package com.example.data.repository

import com.example.data.local.RecipeDao
import com.example.data.local.RecipeEntity
import com.example.model.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecipeRepository(private val recipeDao: RecipeDao) {

    val allRecipes: Flow<List<Recipe>> = recipeDao.getAllRecipes().map { list ->
        list.map { it.toDomain() }
    }

    fun getRecipeById(id: Long): Flow<Recipe?> {
        return recipeDao.getRecipeById(id).map { it?.toDomain() }
    }

    suspend fun getRecipeByIdOnce(id: Long): Recipe? {
        return recipeDao.getRecipeByIdOnce(id)?.toDomain()
    }

    suspend fun saveRecipe(recipe: Recipe): Long {
        val entity = RecipeEntity.fromDomain(
            recipe.copy(
                updatedAt = System.currentTimeMillis()
            )
        )
        return if (recipe.id == 0L) {
            recipeDao.insertRecipe(entity)
        } else {
            recipeDao.updateRecipe(entity)
            recipe.id
        }
    }

    suspend fun deleteRecipe(id: Long) {
        recipeDao.deleteRecipeById(id)
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        recipeDao.toggleFavorite(id, isFavorite)
    }

    suspend fun duplicateRecipe(recipe: Recipe): Long {
        val copy = recipe.copy(
            id = 0,
            title = "${recipe.title} (Copia)",
            isFavorite = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return recipeDao.insertRecipe(RecipeEntity.fromDomain(copy))
    }
}
