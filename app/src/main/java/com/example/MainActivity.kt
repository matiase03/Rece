package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ExportPdfBottomSheet
import com.example.ui.screens.AddEditRecipeScreen
import com.example.ui.screens.DigitalCookbookScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RecipeDetailScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.RecipeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: RecipeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainApp(viewModel: RecipeViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val allRecipes by viewModel.allRecipes.collectAsStateWithLifecycle()
    val filteredRecipes by viewModel.filteredRecipes.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val onlyFavorites by viewModel.onlyFavorites.collectAsStateWithLifecycle()

    val pdfExportRecipe by viewModel.pdfExportRecipe.collectAsStateWithLifecycle()
    val pdfMultiplierLimit by viewModel.pdfMultiplierLimit.collectAsStateWithLifecycle()
    val isGeneratingPdf by viewModel.isGeneratingPdf.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    // System Back button handling
    BackHandler(enabled = currentScreen !is AppScreen.RecipeList) {
        viewModel.navigateTo(AppScreen.RecipeList)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val screen = currentScreen) {
                is AppScreen.RecipeList -> {
                    HomeScreen(
                        recipes = filteredRecipes,
                        searchQuery = searchQuery,
                        selectedCategory = selectedCategory,
                        onlyFavorites = onlyFavorites,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onCategoryChange = { viewModel.setSelectedCategory(it) },
                        onToggleOnlyFavorites = { viewModel.toggleOnlyFavorites() },
                        onToggleRecipeFavorite = { viewModel.toggleFavorite(it) },
                        onSelectRecipe = { viewModel.navigateTo(AppScreen.RecipeDetail(it.id)) },
                        onAddRecipe = { viewModel.navigateTo(AppScreen.AddEditRecipe(null)) },
                        onExportPdf = { viewModel.openPdfExportDialog(it) },
                        onOpenDigitalCookbook = { viewModel.navigateTo(AppScreen.DigitalCookbook) }
                    )
                }

                is AppScreen.RecipeDetail -> {
                    val recipe = allRecipes.find { it.id == screen.recipeId }
                    if (recipe != null) {
                        RecipeDetailScreen(
                            recipe = recipe,
                            onBack = { viewModel.navigateTo(AppScreen.RecipeList) },
                            onEdit = { viewModel.navigateTo(AppScreen.AddEditRecipe(recipe.id)) },
                            onDelete = {
                                viewModel.deleteRecipe(recipe) {
                                    viewModel.navigateTo(AppScreen.RecipeList)
                                }
                            },
                            onDuplicate = { viewModel.duplicateRecipe(recipe) },
                            onToggleFavorite = { viewModel.toggleFavorite(recipe) },
                            onExportPdf = { viewModel.openPdfExportDialog(recipe) },
                            onOpenDigitalCookbook = { viewModel.navigateTo(AppScreen.DigitalCookbook) }
                        )
                    } else {
                        // Fallback if recipe was deleted
                        viewModel.navigateTo(AppScreen.RecipeList)
                    }
                }

                is AppScreen.AddEditRecipe -> {
                    val recipe = screen.recipeId?.let { id -> allRecipes.find { it.id == id } }
                    AddEditRecipeScreen(
                        initialRecipe = recipe,
                        onBack = {
                            if (screen.recipeId != null) {
                                viewModel.navigateTo(AppScreen.RecipeDetail(screen.recipeId))
                            } else {
                                viewModel.navigateTo(AppScreen.RecipeList)
                            }
                        },
                        onSaveRecipe = { toSave ->
                            viewModel.saveRecipe(toSave) {
                                viewModel.navigateTo(AppScreen.RecipeList)
                            }
                        }
                    )
                }

                is AppScreen.DigitalCookbook -> {
                    DigitalCookbookScreen(
                        recipes = allRecipes,
                        onBack = { viewModel.navigateTo(AppScreen.RecipeList) },
                        onExportPdf = { viewModel.openPdfExportDialog(it) }
                    )
                }
            }

            // PDF Export Bottom Sheet
            pdfExportRecipe?.let { recipe ->
                ExportPdfBottomSheet(
                    recipe = recipe,
                    multiplierLimit = pdfMultiplierLimit,
                    isGenerating = isGeneratingPdf,
                    onMultiplierChange = { viewModel.setPdfMultiplierLimit(it) },
                    onPrint = { viewModel.printRecipePdf(recipe, pdfMultiplierLimit) },
                    onShare = { viewModel.shareRecipePdf(recipe, pdfMultiplierLimit) },
                    onDownload = { viewModel.downloadRecipePdf(recipe, pdfMultiplierLimit) },
                    onDismiss = { viewModel.closePdfExportDialog() }
                )
            }
        }
    }
}
