package com.example.memoloop

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*

class AddReminderActivity : AppCompatActivity() {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var notificationHelper: NotificationHelper

    private lateinit var etReminderTitle: EditText
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvSelectedTime: TextView
    private lateinit var btnSelectDate: Button
    private lateinit var btnSelectTime: Button
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerFrequency: Spinner
    private lateinit var btnAddReminder: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var btnShareReminder: Button
    private lateinit var btnSelectLocation: Button

    private var selectedDate: Calendar? = null
    private var selectedTime: Calendar? = null
    private var sharedWithUserIds: MutableList<String> = mutableListOf()
    private var currentUserName: String = ""
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null

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
        notificationHelper = NotificationHelper(this)

        loadCurrentUserName()

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
        btnShareReminder = findViewById(R.id.btn_share_reminder)
        btnSelectLocation = findViewById(R.id.btn_select_location)
        btnSelectLocation.setOnClickListener {
            val intent = Intent(this, MapPickerActivity::class.java)
            locationPicker.launch(intent)
        }
    }

    private fun setupSpinners() {
    }

    private fun setupClickListeners() {
        btnSelectDate.setOnClickListener { showDatePicker() }
        btnSelectTime.setOnClickListener { showTimePicker() }
        btnAddReminder.setOnClickListener { addReminder() }
        btnShareReminder.setOnClickListener { showShareReminderDialog() }
    }

    private fun loadCurrentUserName() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    currentUserName = document.getString("name") ?: "Usuario Desconocido"
                    Log.d("AddReminderActivity", "Current user name loaded: $currentUserName")
                }
                .addOnFailureListener { e ->
                    Log.e("AddReminderActivity", "Error loading current user name: ${e.message}", e)
                }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
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
        ).show()
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
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
        ).show()
    }

    private val locationPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                Log.d("AddReminderActivity", "Datos recibidos del mapa: $data")
                if (data.hasExtra("latitude") && data.hasExtra("longitude")) {
                    selectedLatitude = data.getDoubleExtra("latitude", Double.NaN)
                    selectedLongitude = data.getDoubleExtra("longitude", Double.NaN)

                    if (!selectedLatitude!!.isNaN() && !selectedLongitude!!.isNaN()) {
                        Toast.makeText(this, "Ubicación guardada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error al recibir la ubicación", Toast.LENGTH_SHORT).show()
                        selectedLatitude = null
                        selectedLongitude = null
                    }
                } else {
                    Toast.makeText(this, "No se recibieron coordenadas", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "No se recibieron datos del mapa", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Operación cancelada", Toast.LENGTH_SHORT).show()
        }
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

    private fun showShareReminderDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_share_reminder, null)
        val etShareEmails: EditText = dialogView.findViewById(R.id.et_share_emails)
        val btnConfirmShare: Button = dialogView.findViewById(R.id.btn_confirm_share)
        val btnCancelShare: Button = dialogView.findViewById(R.id.btn_cancel_share)
        val progressBarShare: ProgressBar = dialogView.findViewById(R.id.progress_bar_share)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnConfirmShare.setOnClickListener {
            val emailsText = etShareEmails.text.toString().trim()
            if (emailsText.isEmpty()) {
                Toast.makeText(this, "Por favor, ingresa al menos un correo electrónico.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val emails = emailsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (emails.isEmpty()) {
                Toast.makeText(this, "Correos electrónicos no válidos.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBarShare.isVisible = true
            btnConfirmShare.isEnabled = false
            btnCancelShare.isEnabled = false

            val usersCollection = firestore.collection("users")
            val foundUserIds = mutableListOf<String>()
            var emailsToProcess = emails.size
            var successfulLookups = 0

            emails.forEach { email ->
                usersCollection.whereEqualTo("email", email).get()
                    .addOnSuccessListener { querySnapshot ->
                        emailsToProcess--
                        if (!querySnapshot.isEmpty) {
                            val uid = querySnapshot.documents[0].id
                            if (uid == auth.currentUser?.uid) {
                                Toast.makeText(this, "No puedes compartir un recordatorio contigo mismo.", Toast.LENGTH_SHORT).show()
                            } else {
                                foundUserIds.add(uid)
                                successfulLookups++
                                Log.d("AddReminderActivity", "Found UID for email $email: $uid")
                            }
                        } else {
                            Log.w("AddReminderActivity", "User with email $email not found.")
                            Toast.makeText(this, "Usuario con correo '$email' no encontrado.", Toast.LENGTH_SHORT).show()
                        }

                        if (emailsToProcess == 0) {
                            progressBarShare.isVisible = false
                            btnConfirmShare.isEnabled = true
                            btnCancelShare.isEnabled = true

                            if (successfulLookups > 0) {
                                sharedWithUserIds.clear()
                                sharedWithUserIds.addAll(foundUserIds)
                                Toast.makeText(this, "Se compartirán con ${successfulLookups} usuario(s).", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            } else {
                                Toast.makeText(this, "No se encontraron usuarios válidos para compartir.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        emailsToProcess--
                        Log.e("AddReminderActivity", "Error looking up user by email: $email, Error: ${e.message}", e)
                        Toast.makeText(this, "Error al buscar usuario: ${e.message}", Toast.LENGTH_SHORT).show()

                        if (emailsToProcess == 0) {
                            progressBarShare.isVisible = false
                            btnConfirmShare.isEnabled = true
                            btnCancelShare.isEnabled = true
                            Toast.makeText(this, "Error: No se pudieron buscar todos los usuarios.", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        btnCancelShare.setOnClickListener { dialog.dismiss() }
        dialog.show()
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
            category = selectedCategory,
            sharedWith = sharedWithUserIds,
            isShared = sharedWithUserIds.isNotEmpty(),
            originalCreatorId = userId,
            sharedFromUserName = currentUserName,
            latitude = selectedLatitude,
            longitude = selectedLongitude
        )

        firestore.collection("users").document(userId).collection("reminders")
            .add(newReminder)
            .addOnSuccessListener { documentReference ->
                val reminderId = documentReference.id
                firestore.collection("users").document(userId).collection("reminders")
                    .document(reminderId)
                    .set(newReminder.copy(id = reminderId), SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("AddReminderActivity", "Recordatorio con ID actualizado en Firestore: $reminderId")
                        scheduleReminderNotification(newReminder.copy(id = reminderId), reminderId)

                        if (sharedWithUserIds.isNotEmpty()) {
                            Log.d("AddReminderActivity", "Attempting to send invitations to ${sharedWithUserIds.size} users.")
                            sharedWithUserIds.forEach { targetUserId ->
                                val invitation = Invitation(
                                    reminderId = reminderId,
                                    creatorId = userId,
                                    reminderTitle = newReminder.title,
                                    reminderTimestamp = newReminder.timestamp,
                                    reminderType = newReminder.type,
                                    reminderCategory = newReminder.category,
                                    sharedFromUserName = currentUserName
                                )
                                firestore.collection("users").document(targetUserId).collection("userInvitations")
                                    .add(invitation)
                                    .addOnSuccessListener { Log.d("AddReminderActivity", "Invitation sent to $targetUserId successfully!") }
                                    .addOnFailureListener { e -> Log.e("AddReminderActivity", "Failed to send invitation to $targetUserId: ${e.message}", e) }
                            }
                        }

                        setLoadingState(false)
                        Toast.makeText(this, "Recordatorio agregado y compartido exitosamente", Toast.LENGTH_SHORT).show()
                        clearForm()
                        firebaseAnalytics.logEvent("add_reminder") {
                            param("user_id", userId)
                            param("reminder_title", newReminder.title)
                            param("reminder_timestamp", newReminder.timestamp.toString())
                            param("reminder_type", newReminder.type)
                            param("reminder_category", newReminder.category)
                            param("shared_with_count", newReminder.sharedWith.size.toLong())
                            param("has_location", (newReminder.latitude != null && newReminder.longitude != null).toString())
                        }
                        finish()
                    }
                    .addOnFailureListener { e ->
                        setLoadingState(false)
                        Toast.makeText(this, "Error al actualizar ID del recordatorio: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("AddReminderActivity", "Error updating reminder with ID: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                setLoadingState(false)
                Toast.makeText(this, "Error al agregar recordatorio: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("AddReminderActivity", "Error adding reminder: ${e.message}", e)
            }
    }

    private fun scheduleReminderNotification(reminder: Reminder, reminderId: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notificationId = reminderId.hashCode()

        val notificationTime = Calendar.getInstance().apply {
            timeInMillis = reminder.timestamp
            add(Calendar.MINUTE, -5)
        }.timeInMillis
        if (notificationTime <= System.currentTimeMillis()) {
            Log.d("AddReminderActivity", "Notification time for ${reminder.title} is in the past or too soon, showing immediately.")
            notificationHelper.showNotification(
                "Recordatorio: ${reminder.title}",
                "Tu recordatorio de ${reminder.category} ya pasó o está a punto de vencer.",
                notificationId
            )
            return
        }

        val intent = Intent(this, NotificationPublisher::class.java).apply {
            putExtra(NotificationPublisher.REMINDER_TITLE_EXTRA, "Recordatorio: ${reminder.title}")
            putExtra(NotificationPublisher.REMINDER_MESSAGE_EXTRA, "Tu recordatorio de ${reminder.category} está a punto de vencer.")
            putExtra(NotificationPublisher.NOTIFICATION_ID_EXTRA, notificationId)
            putExtra(NotificationPublisher.REMINDER_ID_EXTRA, reminderId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                notificationTime,
                pendingIntent
            )
            Log.d("AddReminderActivity", "Scheduled notification for ${reminder.title} at ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(notificationTime))}")
        } catch (e: SecurityException) {
            Log.e("AddReminderActivity", "SecurityException al programar alarma: ${e.message}", e)
            Toast.makeText(this, "No se pudo programar el recordatorio. Por favor, revisa los permisos de alarma de la aplicación.", Toast.LENGTH_LONG).show()
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
        sharedWithUserIds.clear()
        selectedLatitude = null
        selectedLongitude = null
    }

    private fun setLoadingState(isLoading: Boolean) {
        btnAddReminder.isEnabled = !isLoading
        btnShareReminder.isEnabled = !isLoading
        btnSelectLocation.isEnabled = !isLoading
        progressBar.isVisible = isLoading
        if (isLoading) {
            btnAddReminder.text = ""
        } else {
            btnAddReminder.text = "Agregar Recordatorio"
        }
    }
}
