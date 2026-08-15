package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.repository.RecipeRepository
import com.example.model.Recipe
import com.example.model.RecipeIngredient
import com.example.util.PdfGenerator
import com.example.util.PrintUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface AppScreen {
    object RecipeList : AppScreen
    object DigitalCookbook : AppScreen
    data class RecipeDetail(val recipeId: Long) : AppScreen
    data class AddEditRecipe(val recipeId: Long? = null) : AppScreen
}

class RecipeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RecipeRepository

    init {
        val db = AppDatabase.getInstance(application)
        repository = RecipeRepository(db.recipeDao())
    }

    // Navigation State
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.RecipeList)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Search and Filter State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Todas")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _onlyFavorites = MutableStateFlow(false)
    val onlyFavorites: StateFlow<Boolean> = _onlyFavorites.asStateFlow()

    // Export PDF Dialog State
    private val _pdfExportRecipe = MutableStateFlow<Recipe?>(null)
    val pdfExportRecipe: StateFlow<Recipe?> = _pdfExportRecipe.asStateFlow()

    private val _pdfMultiplierLimit = MutableStateFlow(3.0f)
    val pdfMultiplierLimit: StateFlow<Float> = _pdfMultiplierLimit.asStateFlow()

    private val _isGeneratingPdf = MutableStateFlow(false)
    val isGeneratingPdf: StateFlow<Boolean> = _isGeneratingPdf.asStateFlow()

    // Message Toast / Snackbar
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val allRecipes: StateFlow<List<Recipe>> = repository.allRecipes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredRecipes: StateFlow<List<Recipe>> = combine(
        repository.allRecipes,
        _searchQuery,
        _selectedCategory,
        _onlyFavorites
    ) { recipes, query, category, favOnly ->
        recipes.filter { recipe ->
            val matchesCategory = (category == "Todas" || recipe.category.equals(category, ignoreCase = true))
            val matchesFav = (!favOnly || recipe.isFavorite)
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                val q = query.trim().lowercase()
                recipe.title.lowercase().contains(q) ||
                    recipe.category.lowercase().contains(q) ||
                    recipe.ingredients.any { it.name.lowercase().contains(q) } ||
                    recipe.instructions.lowercase().contains(q)
            }
            matchesCategory && matchesFav && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun toggleOnlyFavorites() {
        _onlyFavorites.value = !_onlyFavorites.value
    }

    fun toggleFavorite(recipe: Recipe) {
        viewModelScope.launch {
            repository.toggleFavorite(recipe.id, !recipe.isFavorite)
        }
    }

    fun saveRecipe(recipe: Recipe, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveRecipe(recipe)
            _userMessage.value = "Receta guardada con éxito"
            onComplete()
        }
    }

    fun deleteRecipe(recipe: Recipe, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteRecipe(recipe.id)
            _userMessage.value = "Receta eliminada"
            onComplete()
        }
    }

    fun duplicateRecipe(recipe: Recipe) {
        viewModelScope.launch {
            repository.duplicateRecipe(recipe)
            _userMessage.value = "Receta duplicada"
        }
    }

    fun openPdfExportDialog(recipe: Recipe) {
        _pdfExportRecipe.value = recipe
        _pdfMultiplierLimit.value = recipe.multiplierLimit.coerceIn(1.0f, 16.0f)
    }

    fun closePdfExportDialog() {
        _pdfExportRecipe.value = null
    }

    fun setPdfMultiplierLimit(limit: Float) {
        _pdfMultiplierLimit.value = limit.coerceIn(1.0f, 16.0f)
    }

    fun printRecipePdf(recipe: Recipe, limit: Float) {
        viewModelScope.launch {
            _isGeneratingPdf.value = true
            try {
                val file = PdfGenerator.generateRecipePdf(getApplication(), recipe, limit)
                PrintUtils.printPdf(getApplication(), file, recipe.title)
            } catch (e: Exception) {
                e.printStackTrace()
                _userMessage.value = "Error al preparar impresión: ${e.localizedMessage}"
            } finally {
                _isGeneratingPdf.value = false
            }
        }
    }

    fun shareRecipePdf(recipe: Recipe, limit: Float) {
        viewModelScope.launch {
            _isGeneratingPdf.value = true
            try {
                val file = PdfGenerator.generateRecipePdf(getApplication(), recipe, limit)
                PrintUtils.sharePdf(getApplication(), file, recipe.title)
            } catch (e: Exception) {
                e.printStackTrace()
                _userMessage.value = "Error al generar PDF: ${e.localizedMessage}"
            } finally {
                _isGeneratingPdf.value = false
            }
        }
    }

    fun downloadRecipePdf(recipe: Recipe, limit: Float) {
        viewModelScope.launch {
            _isGeneratingPdf.value = true
            try {
                val file = PdfGenerator.generateRecipePdf(getApplication(), recipe, limit)
                PrintUtils.saveToDownloads(getApplication(), file, recipe.title)
            } catch (e: Exception) {
                e.printStackTrace()
                _userMessage.value = "Error al guardar PDF: ${e.localizedMessage}"
            } finally {
                _isGeneratingPdf.value = false
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
