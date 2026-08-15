package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.model.Recipe
import com.example.model.RecipeIngredient
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {

    // Standard A4 dimensions in PostScript points (72 points per inch)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_LEFT = 40f
    private const val MARGIN_RIGHT = 555f
    private const val MARGIN_TOP = 45f
    private const val MARGIN_BOTTOM = 800f
    private const val CONTENT_WIDTH = MARGIN_RIGHT - MARGIN_LEFT

    /**
     * Generates a PDF file for a recipe with the specified multiplier limit.
     */
    fun generateRecipePdf(context: Context, recipe: Recipe, multiplierLimit: Float): File {
        val pdfDocument = PdfDocument()
        val pages = mutableListOf<PdfDocument.Page>()

        var currentPageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(40, 28, 20)
            textSize = 18f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(120, 80, 50)
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val headerMetaPaint = Paint().apply {
            color = Color.rgb(80, 70, 60)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val sectionHeadingPaint = Paint().apply {
            color = Color.rgb(160, 60, 30)
            textSize = 12f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 25, 20)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val bodyBoldPaint = Paint().apply {
            color = Color.rgb(30, 25, 20)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val tableHeaderPaint = Paint().apply {
            color = Color.rgb(255, 255, 255)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val tableCellPaint = Paint().apply {
            color = Color.rgb(30, 25, 20)
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.rgb(210, 195, 180)
            strokeWidth = 0.8f
            isAntiAlias = true
        }

        val accentLinePaint = Paint().apply {
            color = Color.rgb(184, 74, 40)
            strokeWidth = 1.8f
            isAntiAlias = true
        }

        var currentY = MARGIN_TOP

        fun drawHeader(canvas: Canvas) {
            // Header top brand / Recetario stamp
            canvas.drawText("RECETARIO PERSONAL", MARGIN_LEFT, currentY, subtitlePaint)
            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val dateWidth = headerMetaPaint.measureText(dateStr)
            canvas.drawText(dateStr, MARGIN_RIGHT - dateWidth, currentY, headerMetaPaint)
            currentY += 12f

            // Recipe Title
            canvas.drawText(recipe.title, MARGIN_LEFT, currentY + 12f, titlePaint)
            currentY += 26f

            // Accent underline
            canvas.drawLine(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY, accentLinePaint)
            currentY += 12f

            // Metadata row: Categoría | Rendimiento | Prep | Cocción | Dificultad
            val metaParts = mutableListOf<String>()
            metaParts.add("Categoría: ${recipe.category}")
            metaParts.add("Rendimiento: ${recipe.baseYield}")
            metaParts.add("Prep: ${recipe.prepTimeMinutes} min")
            if (recipe.cookTimeMinutes > 0) {
                metaParts.add("Cocción: ${recipe.cookTimeMinutes} min")
            }
            metaParts.add("Dificultad: ${recipe.difficulty}")

            val metaText = metaParts.joinToString("  •  ")
            canvas.drawText(metaText, MARGIN_LEFT, currentY, headerMetaPaint)
            currentY += 8f
            canvas.drawLine(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY, linePaint)
            currentY += 16f
        }

        fun drawFooter(canvas: Canvas, pageNum: Int) {
            canvas.drawLine(MARGIN_LEFT, MARGIN_BOTTOM + 5f, MARGIN_RIGHT, MARGIN_BOTTOM + 5f, linePaint)
            val footerText = "Recetario Digital • Documento generado para impresión y cocina"
            canvas.drawText(footerText, MARGIN_LEFT, MARGIN_BOTTOM + 18f, headerMetaPaint)
            val pageStr = "Pág. $pageNum"
            val pWidth = headerMetaPaint.measureText(pageStr)
            canvas.drawText(pageStr, MARGIN_RIGHT - pWidth, MARGIN_BOTTOM + 18f, headerMetaPaint)
        }

        fun checkPageBreak(neededHeight: Float) {
            if (currentY + neededHeight > MARGIN_BOTTOM) {
                drawFooter(canvas, currentPageNumber)
                pdfDocument.finishPage(currentPage)
                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                currentY = MARGIN_TOP
                drawHeader(canvas)
            }
        }

        // 1. Initial Page Header
        drawHeader(canvas)

        // 2. Section: INGREDIENTES ORIGINALES (Formato Grande)
        checkPageBreak(30f)
        canvas.drawText("1. INGREDIENTES (Receta Base)", MARGIN_LEFT, currentY, sectionHeadingPaint)
        currentY += 14f

        val ingredientBgRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + (recipe.ingredients.size * 15f) + 12f)
        val cardPaint = Paint().apply {
            color = Color.rgb(250, 246, 240)
            style = Paint.Style.FILL
        }
        val cardBorder = Paint().apply {
            color = Color.rgb(228, 218, 206)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        canvas.drawRoundRect(ingredientBgRect, 4f, 4f, cardPaint)
        canvas.drawRoundRect(ingredientBgRect, 4f, 4f, cardBorder)
        currentY += 12f

        for (ing in recipe.ingredients) {
            checkPageBreak(16f)
            val amountFormatted = RecipeIngredient.formatAmount(ing.amount)
            val bullet = "▪"
            canvas.drawText(bullet, MARGIN_LEFT + 10f, currentY, sectionHeadingPaint)
            val amtUnit = "$amountFormatted ${ing.unit}".trim()
            canvas.drawText(amtUnit, MARGIN_LEFT + 24f, currentY, bodyBoldPaint)
            val nameNotes = if (ing.notes.isNotBlank()) "${ing.name} (${ing.notes})" else ing.name
            val amtWidth = bodyBoldPaint.measureText(amtUnit)
            canvas.drawText(nameNotes, MARGIN_LEFT + 32f + amtWidth, currentY, bodyPaint)
            currentY += 15f
        }
        currentY += 12f

        // 3. Section: PREPARACIÓN / INSTRUCCIONES
        if (recipe.instructions.isNotBlank()) {
            checkPageBreak(30f)
            canvas.drawText("2. PREPARACIÓN PASO A PASO", MARGIN_LEFT, currentY, sectionHeadingPaint)
            currentY += 16f

            val instructionLines = recipe.instructions.split("\n")
            for (line in instructionLines) {
                if (line.isBlank()) {
                    currentY += 6f
                    continue
                }
                // Wrap text according to content width
                val wrapped = wrapText(line, bodyPaint, CONTENT_WIDTH - 15f)
                for (wrappedLine in wrapped) {
                    checkPageBreak(14f)
                    canvas.drawText(wrappedLine, MARGIN_LEFT + 8f, currentY, bodyPaint)
                    currentY += 13f
                }
                currentY += 4f
            }
            currentY += 8f
        }

        // 4. Section: NOTAS Y CONSEJOS (si existen)
        if (recipe.notes.isNotBlank()) {
            checkPageBreak(35f)
            val noteLines = wrapText(recipe.notes, bodyPaint, CONTENT_WIDTH - 24f)
            val noteBoxHeight = (noteLines.size * 13f) + 20f
            val noteRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + noteBoxHeight)
            val noteBgPaint = Paint().apply {
                color = Color.rgb(255, 250, 240)
                style = Paint.Style.FILL
            }
            val noteBorderPaint = Paint().apply {
                color = Color.rgb(230, 200, 160)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRoundRect(noteRect, 4f, 4f, noteBgPaint)
            canvas.drawRoundRect(noteRect, 4f, 4f, noteBorderPaint)

            canvas.drawText("CONSEJOS & NOTAS:", MARGIN_LEFT + 10f, currentY + 12f, subtitlePaint)
            var noteY = currentY + 24f
            for (nLine in noteLines) {
                canvas.drawText(nLine, MARGIN_LEFT + 10f, noteY, bodyPaint)
                noteY += 13f
            }
            currentY += noteBoxHeight + 16f
        }

        // 5. Section: TABLA DE MULTIPLICACIÓN Y ESCALADO (Hasta el límite elegido en pasos de 0.5)
        if (recipe.ingredients.isNotEmpty()) {
            // Build multipliers list: 0.5, 1.0, 1.5, 2.0, ... up to multiplierLimit
            val multipliers = mutableListOf<Float>()
            var m = 0.5f
            val maxLimit = multiplierLimit.coerceIn(1.0f, 16.0f)
            while (m <= maxLimit + 0.01f) {
                multipliers.add(m)
                m += 0.5f
            }

            // Split into sub-tables if too many columns for horizontal page width
            val maxColsPerTable = 7 // 1 column for Ingredient name + up to 6 multiplier columns
            val chunks = multipliers.chunked(6)

            for ((chunkIndex, chunkMultipliers) in chunks.withIndex()) {
                val tableHeaderTitle = if (chunks.size == 1) {
                    "3. TABLA DE MULTIPLICACIÓN DE INGREDIENTES (x0.5 a x${RecipeIngredient.formatAmount(maxLimit.toDouble())})"
                } else {
                    "3. TABLA DE MULTIPLICACIÓN (Parte ${chunkIndex + 1}: x${RecipeIngredient.formatAmount(chunkMultipliers.first().toDouble())} a x${RecipeIngredient.formatAmount(chunkMultipliers.last().toDouble())})"
                }

                checkPageBreak(40f + (recipe.ingredients.size * 16f))
                canvas.drawText(tableHeaderTitle, MARGIN_LEFT, currentY, sectionHeadingPaint)
                currentY += 12f

                // Draw Table
                val ingColWidth = 175f
                val colCount = chunkMultipliers.size
                val numColWidth = (CONTENT_WIDTH - ingColWidth) / colCount
                val rowHeight = 15f
                val headerHeight = 18f

                // Table Header background
                val tableHeaderRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + headerHeight)
                val thBgPaint = Paint().apply {
                    color = Color.rgb(184, 74, 40)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(tableHeaderRect, 2f, 2f, thBgPaint)

                // Header Texts
                canvas.drawText("Ingrediente (Unidad)", MARGIN_LEFT + 6f, currentY + 12f, tableHeaderPaint)
                for ((idx, mult) in chunkMultipliers.withIndex()) {
                    val multLabel = "x${RecipeIngredient.formatAmount(mult.toDouble())}"
                    val xPos = MARGIN_LEFT + ingColWidth + (idx * numColWidth) + 4f
                    canvas.drawText(multLabel, xPos, currentY + 12f, tableHeaderPaint)
                }
                currentY += headerHeight

                // Table Rows
                val altRowPaint = Paint().apply {
                    color = Color.rgb(248, 244, 238)
                    style = Paint.Style.FILL
                }
                val borderPaint = Paint().apply {
                    color = Color.rgb(220, 210, 200)
                    style = Paint.Style.STROKE
                    strokeWidth = 0.5f
                }

                for ((rIdx, ing) in recipe.ingredients.withIndex()) {
                    checkPageBreak(rowHeight + 5f)
                    val rowRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + rowHeight)
                    if (rIdx % 2 == 1) {
                        canvas.drawRect(rowRect, altRowPaint)
                    }
                    canvas.drawRect(rowRect, borderPaint)

                    // Ingredient name + unit
                    val ingNameShort = truncateText("${ing.name} (${ing.unit})", tableCellPaint, ingColWidth - 10f)
                    canvas.drawText(ingNameShort, MARGIN_LEFT + 6f, currentY + 11f, tableCellPaint)

                    // Multiplied amounts
                    for ((idx, mult) in chunkMultipliers.withIndex()) {
                        val scaledVal = ing.amount * mult
                        val scaledText = RecipeIngredient.formatAmount(scaledVal)
                        val xPos = MARGIN_LEFT + ingColWidth + (idx * numColWidth) + 4f
                        canvas.drawText(scaledText, xPos, currentY + 11f, tableCellPaint)
                    }
                    currentY += rowHeight
                }
                currentY += 12f
            }
        }

        // Draw footer on last page
        drawFooter(canvas, currentPageNumber)
        pdfDocument.finishPage(currentPage)

        // Save PDF to cache or documents directory
        val fileName = "Receta_${sanitizeFileName(recipe.title)}_${System.currentTimeMillis()}.pdf"
        val outputDir = File(context.cacheDir, "recetas_pdf")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val outputFile = File(outputDir, fileName)

        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return outputFile
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return if (lines.isEmpty()) listOf(text) else lines
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.length > 3 && paint.measureText("$truncated...") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated..."
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(30)
    }
}
