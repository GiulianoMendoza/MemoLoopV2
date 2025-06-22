package com.example.memoloop

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater // Importar LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout // Importar FrameLayout
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(LanguageManager.updateBaseContextLocale(newBase!!))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // BaseActivity SIEMPRE establecerá su propio layout principal (activity_base.xml)
        // que contiene la barra inferior y un FrameLayout para el contenido de las actividades hijas.
        setContentView(R.layout.activity_base)

        // Configura la barra de navegación inferior, sus elementos ya están disponibles
        // porque activity_base.xml ha sido establecido.
        setupBottomNavigationBar()
    }

    /**
     * Este método es el que las actividades que heredan de BaseActivity deben llamar
     * en su onCreate() para inflar su layout específico DENTRO del FrameLayout
     * de activity_base.xml.
     * @param layoutResId El ID del recurso de layout XML de la actividad hija (ej. R.layout.activity_welcome).
     */
    protected fun setChildContentView(layoutResId: Int) {
        // Encuentra el FrameLayout en el layout base (activity_base.xml)
        // Este es el contenedor donde se insertará el contenido de la actividad hija.
        val contentFrame = findViewById<FrameLayout>(R.id.content_frame)

        // Verifica que el FrameLayout existe para evitar NullPointerException
        if (contentFrame != null) {
            // Infla el layout de la actividad hija dentro del FrameLayout.
            // El tercer parámetro 'true' significa que el layout inflado debe ser
            // adjuntado al 'contentFrame' inmediatamente.
            LayoutInflater.from(this).inflate(layoutResId, contentFrame, true)
        } else {
            // Si llegamos aquí, significa que activity_base.xml NO tiene un FrameLayout
            // con el ID 'content_frame', lo cual es un error en el layout base.
            throw IllegalStateException("FrameLayout con ID 'content_frame' no encontrado en activity_base.xml. Por favor, verifica tu archivo XML.")
        }
    }

    protected fun setupBottomNavigationBar() {
        // Los IDs de los botones DEBEN existir en R.layout.activity_base
        // Si alguno de estos findViewById devuelve null, el error es en activity_base.xml
        val btnHome: ImageButton = findViewById(R.id.btn_home)
        val btnHistory: ImageButton = findViewById(R.id.btn_history)
        val btnLanguageSwitcher: Button = findViewById(R.id.btn_language_switcher)
        val btnConfig: ImageButton = findViewById(R.id.btn_config)

        // Establecer el texto inicial del botón de idioma
        updateLanguageButtonText(btnLanguageSwitcher)

        btnHome.setOnClickListener {
            if (this !is RemindersActivity) {
                startActivity(Intent(this, RemindersActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                })
                overridePendingTransition(0, 0)
            }
        }

        btnHistory.setOnClickListener {
            // Implementa tu lógica de navegación a la pantalla de historial
            // Toast.makeText(this, "Funcionalidad de historial", Toast.LENGTH_SHORT).show()
        }

        btnLanguageSwitcher.setOnClickListener {
            toggleLanguage()
        }

        btnConfig.setOnClickListener {
            // Implementa tu lógica de navegación a la pantalla de configuración o logout
            // Toast.makeText(this, "Funcionalidad de configuración/cerrar sesión", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLanguageButtonText(button: Button) {
        val currentLanguageCode = LanguageManager.getSavedLanguage(this)
        val displayCode = when (currentLanguageCode) {
            "es" -> "ES"
            "en" -> "EN"
            "pt" -> "PT"
            else -> currentLanguageCode.uppercase(Locale.getDefault())
        }
        button.text = displayCode
    }

    private fun toggleLanguage() {
        val currentLang = LanguageManager.getSavedLanguage(this)
        val newLang = when (currentLang) {
            "es" -> "en"
            "en" -> "es"
            else -> "es"
        }
        LanguageManager.saveLanguage(this, newLang)
        LanguageManager.setLocale(this, newLang)
        LanguageManager.restartActivity(this)
    }
}
