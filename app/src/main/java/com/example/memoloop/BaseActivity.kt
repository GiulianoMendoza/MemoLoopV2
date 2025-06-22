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
        setContentView(R.layout.activity_base)
        setupBottomNavigationBar()
    }
    protected fun setChildContentView(layoutResId: Int) {
        val contentFrame = findViewById<FrameLayout>(R.id.content_frame)
        if (contentFrame != null) {
            LayoutInflater.from(this).inflate(layoutResId, contentFrame, true)
        } else {
            throw IllegalStateException("FrameLayout con ID 'content_frame' no encontrado en activity_base.xml. Por favor, verifica tu archivo XML.")
        }
    }

    protected fun setupBottomNavigationBar() {
        val btnHome: ImageButton = findViewById(R.id.btn_home)
        val btnHistory: ImageButton = findViewById(R.id.btn_history)
        val btnLanguageSwitcher: Button = findViewById(R.id.btn_language_switcher)
        val btnConfig: ImageButton = findViewById(R.id.btn_config)
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
            startActivity(Intent(this, HistoryActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            })
            overridePendingTransition(0, 0)
        }


        btnLanguageSwitcher.setOnClickListener {
            toggleLanguage()
        }

        btnConfig.setOnClickListener {
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
