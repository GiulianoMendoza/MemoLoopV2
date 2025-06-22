package com.example.memoloop

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
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

class RegisterActivity : BaseActivity() {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLogin: TextView

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(LanguageManager.updateBaseContextLocale(newBase!!))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        etName = findViewById(R.id.et_name)
        etEmail = findViewById(R.id.et_email)
        etPhone = findViewById(R.id.et_phone)
        etPassword = findViewById(R.id.et_password)
        btnRegister = findViewById(R.id.btn_register)
        progressBar = findViewById(R.id.progress_bar)
        tvLogin = findViewById(R.id.tv_login)
    }

    private fun setupClickListeners() {
        btnRegister.setOnClickListener {
            performRegister()
        }

        tvLogin.setOnClickListener {
            finish()
        }
    }

    private fun performRegister() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_completing_fields_register), Toast.LENGTH_SHORT).show()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, getString(R.string.error_password_length), Toast.LENGTH_SHORT).show()
            return
        }

        setLoadingState(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        val user = hashMapOf(
                            "name" to name,
                            "phone" to phone,
                            "email" to email
                        )

                        firestore.collection("users").document(userId).set(user)
                            .addOnSuccessListener {
                                setLoadingState(false)
                                Toast.makeText(this, getString(R.string.toast_register_success), Toast.LENGTH_SHORT).show()

                                firebaseAnalytics.logEvent("register_success") {
                                    param("user_email", email)
                                }

                                startActivity(Intent(this, WelcomeActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                setLoadingState(false)
                                Toast.makeText(this, getString(R.string.error_saving_user_data, e.message), Toast.LENGTH_LONG).show()
                                Log.e("RegisterActivity", "Error guardando datos de usuario", e)
                            }
                    }
                } else {
                    setLoadingState(false)
                    Toast.makeText(this, getString(R.string.error_register_failed, task.exception?.message), Toast.LENGTH_LONG).show()
                    Log.e("RegisterActivity", "Error de registro", task.exception)
                }
            }
    }

    private fun setLoadingState(isLoading: Boolean) {
        btnRegister.isEnabled = !isLoading
        progressBar.isVisible = isLoading
        if (isLoading) {
            btnRegister.text = ""
        } else {
            btnRegister.text = getString(R.string.register_button_text)
        }
    }
}
