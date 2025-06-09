package com.example.memoloop

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*


class AddReminderActivity : AppCompatActivity() {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var etReminderTitle: EditText
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvSelectedTime: TextView
    private lateinit var btnSelectDate: Button
    private lateinit var btnSelectTime: Button
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerFrequency: Spinner
    private lateinit var btnAddReminder: Button
    private lateinit var progressBar: ProgressBar

    private var selectedDate: Calendar? = null
    private var selectedTime: Calendar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_add_reminders)

        val toolbar: Toolbar = findViewById(R.id.toolbar_main)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Agregar Recordatorio"

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        initViews()
        setupSpinners()
        setupClickListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initViews() {
        etReminderTitle = findViewById(R.id.et_reminder_title)
        tvSelectedDate = findViewById(R.id.tv_selected_date)
        tvSelectedTime = findViewById(R.id.tv_selected_time)
        btnSelectDate = findViewById(R.id.btn_select_date)
        btnSelectTime = findViewById(R.id.btn_select_time)
        spinnerCategory = findViewById(R.id.spinner_category)
        spinnerFrequency = findViewById(R.id.spinner_frequency)
        btnAddReminder = findViewById(R.id.btn_add_reminder)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupSpinners() {
    }

    private fun setupClickListeners() {
        btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        btnSelectTime.setOnClickListener {
            showTimePicker()
        }

        btnAddReminder.setOnClickListener {
            addReminder()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                updateDateDisplay()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                selectedTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                updateTimeDisplay()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePickerDialog.show()
    }

    private fun updateDateDisplay() {
        selectedDate?.let { date ->
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            tvSelectedDate.text = "Fecha: ${dateFormat.format(date.time)}"
        }
    }

    private fun updateTimeDisplay() {
        selectedTime?.let { time ->
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            tvSelectedTime.text = "Hora: ${timeFormat.format(time.time)}"
        }
    }

    private fun addReminder() {
        val title = etReminderTitle.text.toString().trim()
        val selectedCategory = spinnerCategory.selectedItem.toString()
        val selectedFrequency = spinnerFrequency.selectedItem.toString()
        val userId = auth.currentUser?.uid

        if (title.isEmpty() || selectedDate == null || selectedTime == null) {
            Toast.makeText(this, "Por favor, complete todos los campos de recordatorio", Toast.LENGTH_SHORT).show()
            return
        }

        if (userId == null) {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        setLoadingState(true)

        val reminderCalendar = Calendar.getInstance().apply {
            selectedDate?.let { date ->
                set(Calendar.YEAR, date.get(Calendar.YEAR))
                set(Calendar.MONTH, date.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, date.get(Calendar.DAY_OF_MONTH))
            }
            selectedTime?.let { time ->
                set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, time.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }

        val newReminder = Reminder(
            userId = userId,
            title = title,
            timestamp = reminderCalendar.timeInMillis,
            type = selectedFrequency,
            category = selectedCategory
        )

        firestore.collection("users").document(userId).collection("reminders")
            .add(newReminder)
            .addOnSuccessListener {
                setLoadingState(false)
                Toast.makeText(this, "Recordatorio agregado exitosamente", Toast.LENGTH_SHORT).show()
                clearForm()

                firebaseAnalytics.logEvent("add_reminder") {
                    param("user_id", userId)
                    param("reminder_title", newReminder.title)
                    param("reminder_timestamp", newReminder.timestamp.toString())
                    param("reminder_type", newReminder.type)
                    param("reminder_category", newReminder.category)
                }
                finish() // Cierra AddReminderActivity y regresa a RemindersActivity (o la que la llamó)
            }
            .addOnFailureListener { e ->
                setLoadingState(false)
                Toast.makeText(this, "Error al agregar recordatorio: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("AddReminderActivity", "Error adding reminder", e)
            }
    }

    private fun clearForm() {
        etReminderTitle.text.clear()
        selectedDate = null
        selectedTime = null
        tvSelectedDate.text = "Fecha: Seleccionar"
        tvSelectedTime.text = "Hora: Seleccionar"
        spinnerCategory.setSelection(0)
        spinnerFrequency.setSelection(0)
    }

    private fun setLoadingState(isLoading: Boolean) {
        btnAddReminder.isEnabled = !isLoading
        progressBar.isVisible = isLoading
        if (isLoading) {
            btnAddReminder.text = ""
        } else {
            btnAddReminder.text = "Agregar Recordatorio"
        }
    }
}