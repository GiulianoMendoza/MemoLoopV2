package com.example.memoloop

import android.content.Context
import java.util.Locale

object ReminderConstants {
    const val TYPE_OCCASIONAL_KEY = "occasional"
    const val TYPE_FIXED_KEY = "fixed"
    const val CATEGORY_HEALTH_KEY = "health"
    const val CATEGORY_SPORT_KEY = "sport"
    const val CATEGORY_LEISURE_KEY = "leisure"
    const val CATEGORY_STUDY_KEY = "study"
    const val CATEGORY_GENERAL_KEY = "general"

    val CATEGORY_STRING_MAP = mapOf(
        CATEGORY_HEALTH_KEY to R.string.reminder_category_salud_raw,
        CATEGORY_SPORT_KEY to R.string.reminder_category_deporte_raw,
        CATEGORY_LEISURE_KEY to R.string.reminder_category_ocio_raw,
        CATEGORY_STUDY_KEY to R.string.reminder_category_estudio_raw,
        CATEGORY_GENERAL_KEY to R.string.reminder_category_general_raw
    )
    val TYPE_STRING_MAP = mapOf(
        TYPE_OCCASIONAL_KEY to R.string.reminder_type_occasional,
        TYPE_FIXED_KEY to R.string.reminder_type_fixed
    )
    fun getCategoryKeyFromDisplayName(context: Context, displayName: String): String {
        return CATEGORY_STRING_MAP.entries.firstOrNull { (_, resId) ->
            context.getString(resId) == displayName
        }?.key ?: CATEGORY_GENERAL_KEY
    }
    fun getTypeKeyFromDisplayName(context: Context, displayName: String): String {
        return TYPE_STRING_MAP.entries.firstOrNull { (_, resId) ->
            context.getString(resId) == displayName
        }?.key ?: TYPE_OCCASIONAL_KEY
    }
    fun getCategoryDisplayName(context: Context, key: String): String {
        return CATEGORY_STRING_MAP[key]?.let { context.getString(it) } ?: key
    }
    fun getTypeDisplayName(context: Context, key: String): String {
        return TYPE_STRING_MAP[key]?.let { context.getString(it) } ?: key
    }
    fun iterateCategoryMapExample(context: Context) {
        println("Iterando CATEGORY_STRING_MAP:")
        for ((key, resId) in CATEGORY_STRING_MAP) {
            val displayName = context.getString(resId)
            println("Clave: $key, Nombre de visualización: $displayName, ID de Recurso: $resId")
        }
    }
    fun iterateTypeMapExample(context: Context) {
        println("Iterando TYPE_STRING_MAP:")
        for ((key, resId) in TYPE_STRING_MAP) {
            val displayName = context.getString(resId)
            println("Clave: $key, Nombre de visualización: $displayName, ID de Recurso: $resId")
        }
    }
    fun getAllCategoryDisplayNames(context: Context): List<String> {
        return CATEGORY_STRING_MAP.values.map { resId -> context.getString(resId) }
    }
    fun getAllTypeDisplayNames(context: Context): List<String> {
        return TYPE_STRING_MAP.values.map { resId -> context.getString(resId) }
    }
}
