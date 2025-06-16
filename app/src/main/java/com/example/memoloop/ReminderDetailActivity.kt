package com.example.memoloop

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ReminderDetailActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var tvTitle: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvFrequency: TextView
    private lateinit var tvType: TextView
    private lateinit var cardView: CardView

    private lateinit var btnEdit: Button
    private lateinit var btnDelete: Button

    private var reminderId: String? = null
    private var reminderData: Reminder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder_detail)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        initViews()
        loadReminder()
        setupListeners()
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tv_detail_title)
        tvDate = findViewById(R.id.tv_detail_date)
        tvTime = findViewById(R.id.tv_detail_time)
        tvFrequency = findViewById(R.id.tv_detail_frequency)
        tvType = findViewById(R.id.tv_detail_type)
        btnEdit = findViewById(R.id.btn_edit)
        btnDelete = findViewById(R.id.btn_delete)
        cardView = findViewById(R.id.card_reminder_detail)
    }
    private fun getColorByType(context: Context, type: String): Int {
        val typeLower = type.lowercase()
        val colorRes = when (typeLower) {
            "salud" -> R.color.reminder_category_salud
            "deporte" -> R.color.reminder_category_deporte
            "ocio" -> R.color.reminder_category_ocio
            "estudio" -> R.color.reminder_category_estudio
            "general" -> R.color.reminder_category_general
            else -> R.color.reminder_category_general
        }
        return context.getColor(colorRes) // si estás en API 23+
        // return ContextCompat.getColor(context, colorRes) // si necesitas compatibilidad
    }


    private fun loadReminder() {
        reminderId = intent.getStringExtra("REMINDER_ID")
        val userId = auth.currentUser?.uid

        if (userId == null || reminderId == null) {
            Toast.makeText(this, "Error al cargar el recordatorio", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        firestore.collection("users")
            .document(userId)
            .collection("reminders")
            .document(reminderId!!)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    reminderData = doc.toObject(Reminder::class.java)
                    reminderData?.let { displayReminder(it) }
                } else {
                    Toast.makeText(this, "Recordatorio no encontrado", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al obtener el recordatorio", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun displayReminder(reminder: Reminder) {
        tvTitle.text = reminder.title

        val date = Date(reminder.timestamp)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        tvDate.text = dateFormat.format(date)
        tvTime.text = timeFormat.format(date)
        tvFrequency.text = reminder.type
        tvType.text = reminder.category
        cardView.setCardBackgroundColor(getColorByType(this, reminder.category))




    }

    private fun setupListeners() {
        btnEdit.setOnClickListener {
            val intent = Intent(this, AddReminderActivity::class.java).apply {
                putExtra("EDIT_REMINDER_ID", reminderId)
            }
            startActivity(intent)
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Eliminar recordatorio")
                .setMessage("¿Estás seguro de que deseas eliminar este recordatorio?")
                .setPositiveButton("Eliminar") { _, _ -> deleteReminder() }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun deleteReminder() {
        val userId = auth.currentUser?.uid ?: return
        val id = reminderId ?: return

        firestore.collection("users")
            .document(userId)
            .collection("reminders")
            .document(id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Recordatorio eliminado", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al eliminar el recordatorio", Toast.LENGTH_SHORT).show()
            }
    }
}
