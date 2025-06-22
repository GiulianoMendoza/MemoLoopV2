package com.example.memoloop

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class WelcomeActivity : BaseActivity() {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var tvWelcomeMessage: TextView
    private lateinit var tvNoRemindersWelcome: TextView
    private lateinit var fabAddReminder: FloatingActionButton
    private lateinit var progressBarWelcome: ProgressBar
    private lateinit var toolbarWelcome: Toolbar
    private lateinit var btnViewInvitationsWelcome: Button

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(LanguageManager.updateBaseContextLocale(newBase!!))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setChildContentView(R.layout.activity_welcome)

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        initViews()

        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, "WelcomeActivity")
            param(FirebaseAnalytics.Param.SCREEN_CLASS, "WelcomeActivity")
            param("message", getString(R.string.welcome_screen_initialized_log))
        }
    }

    override fun onResume() {
        super.onResume()
        checkUserRemindersAndSetupUI()
    }

    private fun initViews() {
        toolbarWelcome = findViewById(R.id.toolbar_welcome_dynamic)
        setSupportActionBar(toolbarWelcome)
        supportActionBar?.title = getString(R.string.app_name)

        tvWelcomeMessage = findViewById(R.id.tv_welcome_message_dynamic)
        tvNoRemindersWelcome = findViewById(R.id.tv_no_reminders_info_dynamic)
        fabAddReminder = findViewById(R.id.fab_add_reminder_dynamic)
        progressBarWelcome = findViewById(R.id.progress_bar_welcome_dynamic)
        btnViewInvitationsWelcome = findViewById(R.id.btn_view_invitations_welcome)
        fabAddReminder.setOnClickListener {
            Toast.makeText(this, getString(R.string.toast_redirecting_add_reminder), Toast.LENGTH_SHORT).show()
            firebaseAnalytics.logEvent("add_reminder_from_welcome_fab") {
                param("user_id", auth.currentUser?.uid ?: "unknown")
            }
            startActivity(Intent(this, AddReminderActivity::class.java))
        }

        btnViewInvitationsWelcome.setOnClickListener {
            startActivity(Intent(this, InvitationsActivity::class.java))
        }
    }

    private fun checkUserRemindersAndSetupUI() {
        val userId = auth.currentUser?.uid
        Log.d("WelcomeActivityDebug", "Current user UID at start: $userId")

        if (userId == null) {
            Toast.makeText(this, getString(R.string.toast_session_not_started_login_prompt), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setLoadingState(true)

        scope.launch {
            try {
                val reminderSnapshot = firestore.collection("users")
                    .document(userId)
                    .collection("reminders")
                    .limit(1)
                    .get()
                    .await()
                val hasReminders = !reminderSnapshot.isEmpty

                val invitationSnapshot = firestore.collection("users")
                    .document(userId)
                    .collection("userInvitations")
                    .limit(1)
                    .get()
                    .await()
                val hasPendingInvitations = !invitationSnapshot.isEmpty

                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    if (hasReminders) {
                        Log.d("WelcomeActivityDebug", "Personal reminders found. Redirecting to RemindersActivity.")
                        Toast.makeText(this@WelcomeActivity, getString(R.string.toast_loading_reminders), Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@WelcomeActivity, RemindersActivity::class.java))
                        finish()
                    } else {
                        Log.d("WelcomeActivityDebug", "No personal reminders found.")
                        setupNoRemindersWelcomeScreenContent(userId, hasPendingInvitations)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    Log.e("WelcomeActivity", getString(R.string.error_checking_personal_reminders_log, e.message), e)
                    Toast.makeText(this@WelcomeActivity, getString(R.string.error_loading_reminders, e.message), Toast.LENGTH_LONG).show()
                    setupNoRemindersWelcomeScreenContent(userId, false)
                }
            }
        }
    }

    private fun setupNoRemindersWelcomeScreenContent(userId: String, hasInvitations: Boolean) {
        loadUserName(userId)

        if (hasInvitations) {
            tvNoRemindersWelcome.text = getString(R.string.tv_no_reminders_info_has_invitations)
            btnViewInvitationsWelcome.isVisible = true
            Log.d("WelcomeActivityDebug", "btnViewInvitationsWelcome set to VISIBLE")
        } else {
            tvNoRemindersWelcome.text = getString(R.string.no_reminders_message)
            btnViewInvitationsWelcome.isVisible = false
            Log.d("WelcomeActivityDebug", "btnViewInvitationsWelcome set to GONE")
        }
        fabAddReminder.isVisible = true
        progressBarWelcome.isVisible = false
    }

    private fun loadUserName(userId: String) {
        setLoadingState(true)
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                setLoadingState(false)
                if (document.exists()) {
                    val userName = document.getString("name")
                    tvWelcomeMessage.text = getString(R.string.welcome_message_placeholder, userName?.trim() ?: "")
                    firebaseAnalytics.logEvent("welcome_message_displayed") {
                        param("user_id", userId)
                        param("user_name", userName ?: "N/A")
                    }
                } else {
                    tvWelcomeMessage.text = getString(R.string.welcome_message_fallback)
                    Log.d("WelcomeActivity", "Documento de usuario no encontrado en Firestore para UID: $userId")
                    Toast.makeText(this, getString(R.string.toast_error_loading_username), Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                setLoadingState(false)
                tvWelcomeMessage.text = getString(R.string.welcome_message_fallback)
                Log.e("WelcomeActivity", getString(R.string.error_loading_user_data_firestore_log, e.message), e)
                Toast.makeText(this, getString(R.string.toast_error_loading_name, e.message), Toast.LENGTH_LONG).show()
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
        Toast.makeText(this, getString(R.string.toast_session_closed), Toast.LENGTH_SHORT).show()

        firebaseAnalytics.logEvent("logout_from_welcome") {
            param("user_email", auth.currentUser?.email ?: "unknown")
        }

        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
