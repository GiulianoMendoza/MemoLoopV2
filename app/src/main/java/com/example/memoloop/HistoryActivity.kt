package com.example.memoloop

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class HistoryActivity : BaseActivity(), RemindersAdapter.OnReminderOptionsClickListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RemindersAdapter
    private lateinit var noRemindersTextView: TextView
    private val reminders = mutableListOf<Reminder>()

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setChildContentView(R.layout.activity_history)

        val toolbar: Toolbar = findViewById(R.id.toolbar_main)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.my_history_toolbar_title)

        recyclerView = findViewById(R.id.recycler_view_history)
        noRemindersTextView = findViewById(R.id.text_view_no_reminders)

        adapter = RemindersAdapter(reminders, this, firestore)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadHistoricalReminders()
    }

    private fun loadHistoricalReminders() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val todayMidnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        firestore.collection("users")
            .document(userId)
            .collection("reminders")
            .whereLessThan("timestamp", todayMidnight)
            .get()
            .addOnSuccessListener { result ->
                reminders.clear()
                for (doc in result) {
                    val reminder = doc.toObject(Reminder::class.java).copy(id = doc.id)
                    reminders.add(reminder)
                }
                adapter.notifyDataSetChanged()

                // Mostrar u ocultar mensaje según si hay recordatorios o no
                if (reminders.isEmpty()) {
                    noRemindersTextView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    noRemindersTextView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener {
                // Opcional: manejar error
            }
    }

    override fun onReminderOptionsClick(reminder: Reminder, anchorView: View) {
        // No hacemos nada aquí por ahora. Opcional: podrías agregar un menú contextual.
    }
}

