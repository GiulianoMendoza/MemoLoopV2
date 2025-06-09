package com.example.memoloop

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

data class Reminder(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val timestamp: Long = 0L,
    val type: String = "",
    val category: String = ""
)

class RemindersActivity : AppCompatActivity() {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var rvReminders: RecyclerView
    private lateinit var tvNoReminders: TextView
    private lateinit var fabGoToAddReminder: FloatingActionButton

    private val reminders = mutableListOf<Reminder>()
    private lateinit var remindersAdapter: RemindersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminders_list)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar_main)
        setSupportActionBar(toolbar)

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        initViews()
        setupClickListeners()
        setupRecyclerView()

        loadReminders()
    }

    private fun initViews() {
        rvReminders = findViewById(R.id.rv_reminders)
        tvNoReminders = findViewById(R.id.tv_no_reminders)
        fabGoToAddReminder = findViewById(R.id.fab_go_to_add_reminder)
    }

    private fun setupClickListeners() {
        fabGoToAddReminder.setOnClickListener {
            startActivity(Intent(this, AddReminderActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        remindersAdapter = RemindersAdapter(reminders)
        rvReminders.apply {
            layoutManager = LinearLayoutManager(this@RemindersActivity)
            adapter = remindersAdapter
        }
    }

    private fun loadReminders() {
        val userId = auth.currentUser?.uid ?: run {
            Log.e("RemindersActivity", "User not authenticated when loading reminders.")
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        firestore.collection("users").document(userId).collection("reminders")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("RemindersActivity", "Listen failed.", e)
                    Toast.makeText(this, "Error al cargar recordatorios: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val currentReminders = mutableListOf<Reminder>()
                    val remindersToDelete = mutableListOf<String>()
                    val currentTime = System.currentTimeMillis()

                    for (doc in snapshot.documents) {
                        val reminder = doc.toObject(Reminder::class.java)?.copy(id = doc.id)
                        if (reminder != null) {
                            // Lógica para borrar recordatorios "Eventual" pasados
                            if (reminder.type == "Eventual" && reminder.timestamp < currentTime) {
                                remindersToDelete.add(reminder.id)
                            } else {
                                currentReminders.add(reminder)
                            }
                        }
                    }
                    if (remindersToDelete.isNotEmpty()) {
                        val batch = firestore.batch()
                        for (id in remindersToDelete) {
                            val docRef = firestore.collection("users").document(userId).collection("reminders").document(id)
                            batch.delete(docRef)
                        }
                        batch.commit()
                            .addOnSuccessListener {
                                Log.d("RemindersActivity", "Recordatorios eventuales pasados eliminados: ${remindersToDelete.size}")
                            }
                            .addOnFailureListener { deleteError ->
                                Log.e("RemindersActivity", "Error al eliminar recordatorios eventuales pasados", deleteError)
                            }
                    }

                    reminders.clear()
                    reminders.addAll(currentReminders)
                    remindersAdapter.notifyDataSetChanged()
                    updateEmptyState()

                    Log.d("RemindersActivity", "Recordatorios cargados y listos para mostrar: ${reminders.size}")
                    if (reminders.isNotEmpty()) {
                        Log.d("RemindersActivity", "Primer recordatorio cargado: Título='${reminders[0].title}', Fecha=${reminders[0].timestamp}, Categoría=${reminders[0].category}, Tipo=${reminders[0].type}")
                    }

                    firebaseAnalytics.logEvent("reminders_loaded") {
                        param("user_id", userId)
                        param("reminder_count", reminders.size.toLong())
                    }
                } else {
                    Log.d("RemindersActivity", "Current data: null")
                }
            }
    }

    private fun updateEmptyState() {
        if (reminders.isEmpty()) {
            rvReminders.isVisible = false
            tvNoReminders.isVisible = true
        } else {
            rvReminders.isVisible = true
            tvNoReminders.isVisible = false
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
    override fun onDestroy() {
        super.onDestroy()
    }

    private fun logout() {
        auth.signOut()
        Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()

        firebaseAnalytics.logEvent("logout") {
            param("user_email", auth.currentUser?.email ?: "unknown")
        }

        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
