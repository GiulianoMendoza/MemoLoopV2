package com.example.memoloop

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
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
    private lateinit var btnViewInvitationsWelcome: Button

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
        Log.d("WelcomeActivityDebug", "Current user UID at start: $userId")

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
                    Log.d("WelcomeActivityDebug", "No personal reminders found.")
                    checkForPendingInvitationsAndSetupWelcomeScreen(userId)
                } else {
                    Log.d("WelcomeActivityDebug", "Personal reminders found. Redirecting to RemindersActivity.")
                    Toast.makeText(this, "Cargando tus recordatorios...", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, RemindersActivity::class.java))
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e("WelcomeActivity", "Error al verificar recordatorios propios: ${e.message}", e)
                Toast.makeText(this, "Error al cargar recordatorios: ${e.message}", Toast.LENGTH_LONG).show()
                checkForPendingInvitationsAndSetupWelcomeScreen(userId)
            }
    }

    private fun checkForPendingInvitationsAndSetupWelcomeScreen(userId: String) {
        firestore.collection("users").document(userId).collection("userInvitations")
            .limit(1)
            .get()
            .addOnSuccessListener { invitationSnapshot ->
                Log.d("WelcomeActivityDebug", "Invitations snapshot empty: ${invitationSnapshot.isEmpty}")
                if (!invitationSnapshot.isEmpty) {
                    Log.d("WelcomeActivityDebug", "Invitations found: ${invitationSnapshot.documents.map { it.id }}")
                }


                if (invitationSnapshot.isEmpty) {
                    Log.d("WelcomeActivity", "No hay recordatorios ni invitaciones. Mostrando pantalla de bienvenida sin recordatorios.")
                    setupNoRemindersWelcomeScreen(userId, hasInvitations = false)
                } else {
                    Log.d("WelcomeActivity", "Tienes invitaciones pendientes. Mostrando pantalla de bienvenida con indicador.")
                    setupNoRemindersWelcomeScreen(userId, hasInvitations = true)
                }
            }
            .addOnFailureListener { e ->
                Log.e("WelcomeActivity", "Error al verificar invitaciones: ${e.message}", e)
                Toast.makeText(this, "Error al verificar invitaciones: ${e.message}", Toast.LENGTH_LONG).show()
                setupNoRemindersWelcomeScreen(userId, hasInvitations = false)
            }
    }

    private fun setupNoRemindersWelcomeScreen(userId: String, hasInvitations: Boolean) {
        setContentView(R.layout.activity_welcome_no_reminders)

        toolbarWelcome = findViewById(R.id.toolbar_welcome_dynamic)
        setSupportActionBar(toolbarWelcome)

        tvWelcomeMessage = findViewById(R.id.tv_welcome_message_dynamic)
        tvNoRemindersWelcome = findViewById(R.id.tv_no_reminders_info_dynamic)
        fabAddReminder = findViewById(R.id.fab_add_reminder_dynamic)
        progressBarWelcome = findViewById(R.id.progress_bar_welcome_dynamic)
        btnViewInvitationsWelcome = findViewById(R.id.btn_view_invitations_welcome)

        loadUserName(userId)

        if (hasInvitations) {
            tvNoRemindersWelcome.text = "¡Tienes invitaciones pendientes! Haz clic para verlas."
            btnViewInvitationsWelcome.isVisible = true
            Log.d("WelcomeActivityDebug", "btnViewInvitationsWelcome set to VISIBLE")
        } else {
            tvNoRemindersWelcome.text = "No tienes recordatorios. ¡Haz clic en el '+' para agregar uno!"
            btnViewInvitationsWelcome.isVisible = false
            Log.d("WelcomeActivityDebug", "btnViewInvitationsWelcome set to GONE")
        }

        fabAddReminder.setOnClickListener {
            Toast.makeText(this, "Redirigiendo para agregar recordatorios...", Toast.LENGTH_SHORT).show()
            firebaseAnalytics.logEvent("add_reminder_from_welcome_fab") {
                param("user_id", auth.currentUser?.uid ?: "unknown")
            }
            startActivity(Intent(this, AddReminderActivity::class.java))
        }

        btnViewInvitationsWelcome.setOnClickListener {
            startActivity(Intent(this, InvitationsActivity::class.java))
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
                setLoadingState(false)
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
        if (::btnViewInvitationsWelcome.isInitialized) {
            btnViewInvitationsWelcome.isEnabled = !isLoading
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
