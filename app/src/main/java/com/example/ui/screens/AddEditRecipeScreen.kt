package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.Recipe
import com.example.model.RecipeIngredient
import com.example.ui.components.AiSettingsDialog
import com.example.util.AiProvider
import com.example.util.AiSettingsManager
import com.example.util.RecipeAiExtractor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRecipeScreen(
    initialRecipe: Recipe?,
    onBack: () -> Unit,
    onSaveRecipe: (Recipe) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var title by remember { mutableStateOf(initialRecipe?.title ?: "") }
    var category by remember { mutableStateOf(initialRecipe?.category ?: "Panadería y Masas") }
    var customCategory by remember { mutableStateOf("") }
    var isCustomCategorySelected by remember { mutableStateOf(false) }

    var baseYield by remember { mutableStateOf(initialRecipe?.baseYield ?: "4 porciones") }
    var prepTimeMinutes by remember { mutableIntStateOf(initialRecipe?.prepTimeMinutes ?: 20) }
    var cookTimeMinutes by remember { mutableIntStateOf(initialRecipe?.cookTimeMinutes ?: 30) }
    var difficulty by remember { mutableStateOf(initialRecipe?.difficulty ?: "Fácil") }
    var instructions by remember { mutableStateOf(initialRecipe?.instructions ?: "") }
    var notes by remember { mutableStateOf(initialRecipe?.notes ?: "") }
    var multiplierLimit by remember { mutableFloatStateOf(initialRecipe?.multiplierLimit ?: 3.0f) }

    // Start completely empty if creating a new recipe
    val ingredientsList = remember {
        mutableStateListOf<RecipeIngredient>().apply {
            if (initialRecipe != null && initialRecipe.ingredients.isNotEmpty()) {
                addAll(initialRecipe.ingredients)
            }
        }
    }

    // AI Photo Extraction State
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isScanningImage by remember { mutableStateOf(false) }
    var scanSuccessMessage by remember { mutableStateOf<String?>(null) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }

    val activeAiProvider = remember(showAiSettingsDialog) {
        AiSettingsManager.getActiveProvider(context)
    }

    if (showAiSettingsDialog) {
        AiSettingsDialog(
            onDismissRequest = { showAiSettingsDialog = false },
            onSettingsSaved = {
                scanErrorMessage = null
            }
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            scanSuccessMessage = null
            scanErrorMessage = null
        }
    }

    var titleError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (initialRecipe == null) "Nueva Receta" else "Editar Receta",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("add_edit_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                titleError = true
                                return@Button
                            }
                            val finalCategory = if (isCustomCategorySelected && customCategory.isNotBlank()) {
                                customCategory.trim()
                            } else {
                                category
                            }

                            val cleanIngredients = ingredientsList.filter { it.name.isNotBlank() }

                            val recipeToSave = (initialRecipe ?: Recipe(title = "", category = "")).copy(
                                title = title.trim(),
                                category = finalCategory,
                                baseYield = baseYield.trim().ifBlank { "Base" },
                                prepTimeMinutes = prepTimeMinutes,
                                cookTimeMinutes = cookTimeMinutes,
                                difficulty = difficulty,
                                ingredients = cleanIngredients,
                                instructions = instructions.trim(),
                                notes = notes.trim(),
                                multiplierLimit = multiplierLimit
                            )
                            onSaveRecipe(recipeToSave)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_recipe_top_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI Image Ingredient Extraction Card
            item {
                Spacer(modifier = Modifier.height(2.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (activeAiProvider == AiProvider.GROQ) Icons.Default.Bolt else Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier
                                            .padding(5.dp)
                                            .size(18.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Escanear Receta con IA",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Motor: ${if (activeAiProvider == AiProvider.GROQ) "Groq (Llama 3.2 Vision)" else "Google Gemini"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Quick settings button
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clickable { showAiSettingsDialog = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Configurar IA",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Cambiar",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (activeAiProvider == AiProvider.GROQ) {
                                "Extrae automáticamente ingredientes, cantidades y unidades desde fotos, tarjetas o libros con la velocidad ultra rápida de Groq."
                            } else {
                                "Extrae automáticamente ingredientes, cantidades y unidades desde fotos o libros con Google Gemini Multimodal."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Image preview & Action controls
                        if (selectedImageUri != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Vista previa de receta",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .testTag("selected_recipe_image_preview")
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Imagen seleccionada",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Lista para procesar con ${if (activeAiProvider == AiProvider.GROQ) "Groq" else "Gemini"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        selectedImageUri = null
                                        scanSuccessMessage = null
                                        scanErrorMessage = null
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Quitar imagen",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Scan button
                            Button(
                                onClick = {
                                    val uri = selectedImageUri ?: return@Button
                                    coroutineScope.launch {
                                        isScanningImage = true
                                        scanSuccessMessage = null
                                        scanErrorMessage = null
                                        try {
                                            val result = RecipeAiExtractor.extractFromImageUri(context, uri)
                                            result.onSuccess { data ->
                                                if (data.ingredients.isNotEmpty()) {
                                                    if (title.isBlank() && !data.recipeTitle.isNullOrBlank()) {
                                                        title = data.recipeTitle
                                                        titleError = false
                                                    }
                                                    if (!data.category.isNullOrBlank() && !isCustomCategorySelected) {
                                                        category = data.category
                                                    }

                                                    ingredientsList.clear()
                                                    ingredientsList.addAll(data.ingredients)

                                                    scanSuccessMessage = "¡Se extrajeron ${data.ingredients.size} ingredientes con éxito!"
                                                } else {
                                                    scanErrorMessage = "No se detectaron ingredientes en la imagen. Verificá que la foto sea legible."
                                                }
                                            }.onFailure { err ->
                                                scanErrorMessage = err.localizedMessage ?: "Error al analizar imagen"
                                            }
                                        } finally {
                                            isScanningImage = false
                                        }
                                    }
                                },
                                enabled = !isScanningImage,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scan_ingredients_ai_btn")
                            ) {
                                if (isScanningImage) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analizando foto con ${if (activeAiProvider == AiProvider.GROQ) "Groq" else "Gemini"}...")
                                } else {
                                    Icon(
                                        imageVector = if (activeAiProvider == AiProvider.GROQ) Icons.Default.Bolt else Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Extraer Ingredientes con ${if (activeAiProvider == AiProvider.GROQ) "Groq" else "Gemini"}",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            // Picker trigger button
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch("image/*")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pick_recipe_image_btn")
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Seleccionar Foto de Receta / Libro", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Scanning progress bar
                        if (isScanningImage) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        // Scan Success feedback
                        if (scanSuccessMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = scanSuccessMessage ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Scan Error feedback with quick settings action
                        if (scanErrorMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = scanErrorMessage ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { showAiSettingsDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Configurar Claves / Proveedor", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                // Basic Info Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Información General",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )

                        // Recipe Name
                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                title = it
                                if (it.isNotBlank()) titleError = false
                            },
                            label = { Text("Nombre de la receta *") },
                            placeholder = { Text("Ej. Tarta de Ricota y Limón") },
                            isError = titleError,
                            supportingText = if (titleError) {
                                { Text("El nombre de la receta es obligatorio", color = MaterialTheme.colorScheme.error) }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recipe_name_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Category Selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Categoría",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val catScroll = rememberScrollState()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(catScroll),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Recipe.DEFAULT_CATEGORIES_FOR_CREATION.forEach { cat ->
                                    FilterChip(
                                        selected = !isCustomCategorySelected && category == cat,
                                        onClick = {
                                            isCustomCategorySelected = false
                                            category = cat
                                        },
                                        label = { Text(cat) }
                                    )
                                }
                                FilterChip(
                                    selected = isCustomCategorySelected,
                                    onClick = { isCustomCategorySelected = true },
                                    label = { Text("+ Otra") }
                                )
                            }

                            if (isCustomCategorySelected) {
                                OutlinedTextField(
                                    value = customCategory,
                                    onValueChange = { customCategory = it },
                                    label = { Text("Nombre de nueva categoría") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        // Base Yield & Difficulty
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = baseYield,
                                onValueChange = { baseYield = it },
                                label = { Text("Rendimiento / Porciones") },
                                placeholder = { Text("Ej. 4 personas / 1 molde 24cm") },
                                modifier = Modifier.weight(1.2f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Difficulty
                            Column(modifier = Modifier.weight(0.8f)) {
                                Text(
                                    text = "Dificultad",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val diffScroll = rememberScrollState()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(diffScroll),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("Fácil", "Intermedio", "Avanzado").forEach { d ->
                                        FilterChip(
                                            selected = difficulty == d,
                                            onClick = { difficulty = d },
                                            label = { Text(d, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }

                        // Times (Prep and Cook)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = prepTimeMinutes.toString(),
                                onValueChange = { prepTimeMinutes = it.toIntOrNull() ?: 0 },
                                label = { Text("Prep (min)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = cookTimeMinutes.toString(),
                                onValueChange = { cookTimeMinutes = it.toIntOrNull() ?: 0 },
                                label = { Text("Cocción (min)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Ingredients Section
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ingredientes & Cantidades",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "${ingredientsList.size} items",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Podés agregar ingredientes manualmente o usar el botón de escaneo con IA.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Ingredients rows or empty helper
                        if (ingredientsList.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Aún no hay ingredientes. Presioná 'Agregar Ingrediente' o subí una imagen para extraerlos automáticamente.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            ingredientsList.forEachIndexed { index, ingredient ->
                                IngredientEditRow(
                                    ingredient = ingredient,
                                    onUpdate = { updated: RecipeIngredient ->
                                        ingredientsList[index] = updated
                                    },
                                    onDelete = {
                                        ingredientsList.removeAt(index)
                                    },
                                    canDelete = true
                                )
                                if (index < ingredientsList.size - 1) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                ingredientsList.add(
                                    RecipeIngredient(
                                        name = "",
                                        amount = 100.0,
                                        unit = "g"
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_ingredient_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Agregar Ingrediente", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Steps / Instructions Section
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Instrucciones de Preparación",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = instructions,
                            onValueChange = { instructions = it },
                            placeholder = { Text("Paso 1: Mezclar los secos...\nPaso 2: Incorporar los líquidos...\nPaso 3: Hornear a 180°C por 30 minutos.") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .testTag("recipe_instructions_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Notes / Tips Section
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Consejos & Notas del Chef",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            placeholder = { Text("Ej. Conservar en heladera hasta 4 días. Para mayor brillo pincelar con almíbar tibio.") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .testTag("recipe_notes_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // PDF Multiplier Default Table Limit
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Límite por defecto para tabla PDF:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "x${RecipeIngredient.formatAmount(multiplierLimit.toDouble())}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "Podrás cambiar esto en cualquier momento antes de exportar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Slider(
                            value = multiplierLimit,
                            onValueChange = { multiplierLimit = it },
                            valueRange = 1.0f..16.0f,
                            steps = 29,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // Bottom Save Button
            item {
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            titleError = true
                            return@Button
                        }
                        val finalCategory = if (isCustomCategorySelected && customCategory.isNotBlank()) {
                            customCategory.trim()
                        } else {
                            category
                        }
                        val cleanIngredients = ingredientsList.filter { it.name.isNotBlank() }

                        val recipeToSave = (initialRecipe ?: Recipe(title = "", category = "")).copy(
                            title = title.trim(),
                            category = finalCategory,
                            baseYield = baseYield.trim().ifBlank { "Base" },
                            prepTimeMinutes = prepTimeMinutes,
                            cookTimeMinutes = cookTimeMinutes,
                            difficulty = difficulty,
                            ingredients = cleanIngredients,
                            instructions = instructions.trim(),
                            notes = notes.trim(),
                            multiplierLimit = multiplierLimit
                        )
                        onSaveRecipe(recipeToSave)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_recipe_bottom_btn")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Guardar Receta en Recetario",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun IngredientEditRow(
    ingredient: RecipeIngredient,
    onUpdate: (RecipeIngredient) -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Ingredient Name
                OutlinedTextField(
                    value = ingredient.name,
                    onValueChange = { onUpdate(ingredient.copy(name = it)) },
                    placeholder = { Text("Ingrediente (ej. Harina)") },
                    modifier = Modifier.weight(1.5f),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Amount
                OutlinedTextField(
                    value = if (ingredient.amount > 0.0) RecipeIngredient.formatAmount(ingredient.amount) else "",
                    onValueChange = {
                        val num = it.toDoubleOrNull() ?: 0.0
                        onUpdate(ingredient.copy(amount = num))
                    },
                    placeholder = { Text("Cant.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(0.9f),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Delete Icon
                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Quitar ingrediente",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Units chip row + custom unit
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val unitScroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(unitScroll),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RecipeIngredient.COMMON_UNITS.take(8).forEach { u ->
                        FilterChip(
                            selected = ingredient.unit.equals(u, ignoreCase = true),
                            onClick = { onUpdate(ingredient.copy(unit = u)) },
                            label = { Text(u, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Custom unit field if needed
                OutlinedTextField(
                    value = ingredient.unit,
                    onValueChange = { onUpdate(ingredient.copy(unit = it)) },
                    placeholder = { Text("Unidad") },
                    modifier = Modifier.width(90.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // Optional note (e.g. "tamizada")
            OutlinedTextField(
                value = ingredient.notes,
                onValueChange = { onUpdate(ingredient.copy(notes = it)) },
                placeholder = { Text("Nota opcional (ej. a temperatura ambiente, rallada)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}
