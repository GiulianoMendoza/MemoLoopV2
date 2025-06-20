package com.example.memoloop

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast // Importa Toast para mensajes
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

open class BaseActivity : AppCompatActivity() {

    // Se llama antes de onCreate, perfecto para configurar el locale
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(LanguageManager.updateBaseContextLocale(newBase!!))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.setContentView(R.layout.activity_base) // Asegúrate de que este es el layout de tu base

        setupBottomToolbar()
    }

    override fun setContentView(view: View?) {
        val contentFrame = findViewById<FrameLayout>(R.id.content_frame)
        contentFrame.removeAllViews()
        contentFrame.addView(view)
    }

    override fun setContentView(layoutResID: Int) {
        val view = LayoutInflater.from(this).inflate(layoutResID, null)
        setContentView(view)
    }

    private fun setupBottomToolbar() {
        findViewById<ImageButton>(R.id.btn_home)?.setOnClickListener {
            startActivity(Intent(this, WelcomeActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btn_history)?.setOnClickListener {
            startActivity(Intent(this, RemindersActivity::class.java))
        }

        // Listener para el botón de cambio de idioma
        findViewById<ImageButton>(R.id.btn_language_switcher)?.setOnClickListener {
            val currentLanguage = LanguageManager.getSavedLanguage(this)
            val newLanguage = if (currentLanguage == "es") "en" else "es" // Alterna entre es y en

            LanguageManager.saveLanguage(this, newLanguage) // Guarda la preferencia
            LanguageManager.setLocale(this, newLanguage)    // Aplica el nuevo idioma al contexto
            LanguageManager.restartActivity(this)           // Reinicia la actividad para que los cambios se reflejen
            Toast.makeText(this, "Idioma cambiado a ${newLanguage.uppercase()}", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btn_config)?.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
