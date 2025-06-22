package com.example.memoloop

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity // Ya no se usa directamente, ahora se hereda de BaseActivity
import androidx.core.view.isVisible
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : BaseActivity() {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvRegister: TextView

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(LanguageManager.updateBaseContextLocale(newBase!!))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        progressBar = findViewById(R.id.progress_bar)
        tvRegister = findViewById(R.id.tv_register)
    }

    private fun setupClickListeners() {
        btnLogin.setOnClickListener {
            performLogin()
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun performLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_completing_fields_login), Toast.LENGTH_SHORT).show()
            return
        }

        setLoadingState(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                setLoadingState(false)
                if (task.isSuccessful) {
                    Toast.makeText(this, getString(R.string.toast_login_success), Toast.LENGTH_SHORT).show()

                    firebaseAnalytics.logEvent("login_success") {
                        param("user_email", email)
                    }

                    startActivity(Intent(this, WelcomeActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, getString(R.string.error_login_failed, task.exception?.message), Toast.LENGTH_LONG).show()
                    Log.e("LoginActivity", "Error de inicio de sesión", task.exception)
                }
            }
    }

    private fun setLoadingState(isLoading: Boolean) {
        btnLogin.isEnabled = !isLoading
        progressBar.isVisible = isLoading
        if (isLoading) {
            btnLogin.text = ""
        } else {
            btnLogin.text = getString(R.string.login_button_text)
        }
    }
}
