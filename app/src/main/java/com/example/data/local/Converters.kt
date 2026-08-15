package com.example.data.local

import com.example.model.RecipeIngredient
import org.json.JSONArray
import org.json.JSONObject

object Converters {
    fun ingredientsToJson(ingredients: List<RecipeIngredient>): String {
        val jsonArray = JSONArray()
        for (ing in ingredients) {
            val obj = JSONObject()
            obj.put("id", ing.id)
            obj.put("name", ing.name)
            obj.put("amount", ing.amount)
            obj.put("unit", ing.unit)
            obj.put("notes", ing.notes)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    fun jsonToIngredients(json: String): List<RecipeIngredient> {
        if (json.isBlank()) return emptyList()
        val list = mutableListOf<RecipeIngredient>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "")
                val name = obj.optString("name", "")
                val amount = obj.optDouble("amount", 0.0)
                val unit = obj.optString("unit", "")
                val notes = obj.optString("notes", "")
                list.add(
                    RecipeIngredient(
                        id = if (id.isNotEmpty()) id else java.util.UUID.randomUUID().toString(),
                        name = name,
                        amount = amount,
                        unit = unit,
                        notes = notes
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
