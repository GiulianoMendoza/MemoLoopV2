package com.example.memoloop

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object LanguageManager {

    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    /**
     * Guarda el idioma seleccionado en SharedPreferences.
     */
    fun saveLanguage(context: Context, languageCode: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    /**
     * Obtiene el idioma guardado en SharedPreferences.
     * Si no hay idioma guardado, devuelve "es" (español) como predeterminado.
     */
    fun getSavedLanguage(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "es") ?: "es" // Predeterminado a español
    }

    /**
     * Establece el idioma de la aplicación.
     * Esto afecta al Configuration de la aplicación.
     */
    fun setLocale(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val resources: Resources = context.resources
        val configuration: Configuration = resources.configuration
        configuration.setLocale(locale)
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }

    /**
     * Reinicia la actividad actual para aplicar los cambios de idioma.
     */
    fun restartActivity(activity: Activity) {
        activity.recreate() // recreate() es una forma eficiente de reiniciar la actividad.
        // Si necesitas reiniciar la pila de actividades, podrías usar:
        // val intent = Intent(activity, activity.javaClass)
        // intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        // activity.startActivity(intent)
        // activity.finish()
    }

    /**
     * Aplica el idioma guardado al contexto de la actividad.
     * Debe llamarse en attachBaseContext de cada actividad que necesite el soporte de localización.
     */
    fun updateBaseContextLocale(context: Context): Context {
        val language = getSavedLanguage(context)
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
