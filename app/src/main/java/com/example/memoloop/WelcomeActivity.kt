package com.example.memoloop

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query


class WelcomeActivity : AppCompatActivity() {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var tvWelcomeMessage: TextView
    private lateinit var tvNoRemindersWelcome: TextView
    private lateinit var fabAddReminder: FloatingActionButton
    private lateinit var progressBarWelcome: ProgressBar
    private lateinit var toolbarWelcome: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, "WelcomeActivity")
            param(FirebaseAnalytics.Param.SCREEN_CLASS, "WelcomeActivity")
            param("message", "Pantalla de bienvenida inicializada")
        }
    }
    override fun onResume() {
        super.onResume()
        checkUserRemindersAndSetupUI()
    }

    private fun checkUserRemindersAndSetupUI() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Sesión no iniciada. Por favor, inicie sesión.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        firestore.collection("users").document(userId).collection("reminders")
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Log.d("WelcomeActivity", "No hay recordatorios de ningún tipo. Mostrando pantalla de bienvenida sin recordatorios.")
                    setupNoRemindersWelcomeScreen(userId)
                } else {
                    Log.d("WelcomeActivity", "Se encontraron recordatorios. Redirigiendo a la lista de recordatorios.")
                    Toast.makeText(this, "Cargando tus recordatorios...", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, RemindersActivity::class.java))
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e("WelcomeActivity", "Error al verificar recordatorios: ${e.message}", e)
                Toast.makeText(this, "Error al cargar recordatorios: ${e.message}", Toast.LENGTH_LONG).show()
                setupNoRemindersWelcomeScreen(userId)
            }
    }
    private fun setupNoRemindersWelcomeScreen(userId: String) {
        setContentView(R.layout.activity_welcome_no_reminders)
        toolbarWelcome = findViewById(R.id.toolbar_welcome_dynamic)
        setSupportActionBar(toolbarWelcome)
        tvWelcomeMessage = findViewById(R.id.tv_welcome_message_dynamic)
        tvNoRemindersWelcome = findViewById(R.id.tv_no_reminders_info_dynamic)
        fabAddReminder = findViewById(R.id.fab_add_reminder_dynamic)
        progressBarWelcome = findViewById(R.id.progress_bar_welcome_dynamic)

        loadUserName(userId)
        fabAddReminder.setOnClickListener {
            Toast.makeText(this, "Redirigiendo para agregar recordatorios...", Toast.LENGTH_SHORT).show()
            firebaseAnalytics.logEvent("add_reminder_from_welcome_fab") {
                param("user_id", auth.currentUser?.uid ?: "unknown")
            }
            startActivity(Intent(this, AddReminderActivity::class.java))
        }
    }
    private fun loadUserName(userId: String) {
        setLoadingState(true)
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                setLoadingState(false)
                if (document.exists()) {
                    val userName = document.getString("name")
                    tvWelcomeMessage.text = "Bienvenido ${userName?.trim() ?: ""} a MemoLoop, tu app de recordatorios"
                    firebaseAnalytics.logEvent("welcome_message_displayed") {
                        param("user_id", userId)
                        param("user_name", userName ?: "N/A")
                    }
                } else {
                    tvWelcomeMessage.text = "Bienvenido a MemoLoop, tu app de recordatorios"
                    Log.d("WelcomeActivity", "Documento de usuario no encontrado en Firestore para UID: $userId")
                    Toast.makeText(this, "No se pudo cargar el nombre del usuario", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                setLoadingState(false) // Oculta la ProgressBar
                tvWelcomeMessage.text = "Bienvenido a MemoLoop, tu app de recordatorios"
                Log.e("WelcomeActivity", "Error al cargar datos del usuario desde Firestore", e)
                Toast.makeText(this, "Error al cargar el nombre: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    private fun setLoadingState(isLoading: Boolean) {
        progressBarWelcome.isVisible = isLoading
        if (::fabAddReminder.isInitialized) {
            fabAddReminder.isEnabled = !isLoading
        }
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_reminders, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    private fun logout() {
        auth.signOut()
        Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()
        firebaseAnalytics.logEvent("logout_from_welcome") {
            param("user_email", auth.currentUser?.email ?: "unknown")
        }
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
