package com.example.memoloop

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import android.app.Activity
import android.net.Uri
import android.content.Context
import androidx.core.content.FileProvider
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

import com.example.memoloop.ImgbbResponse
import com.example.memoloop.network.ImgbbService

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AddReminderActivity : BaseActivity() {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var notificationHelper: NotificationHelper

    private lateinit var etReminderTitle: EditText
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvSelectedTime: TextView
    private lateinit var btnSelectDate: Button
    private lateinit var btnSelectTime: Button
    private lateinit var btnSelectImage: Button
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerType: Spinner
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
    private var selectedImageUri: Uri? = null
    private var capturedImageUri: Uri? = null

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



        initViews()
        setupSpinners()
        setupClickListeners()
        loadCurrentUserName()

        val isEditMode = intent.getBooleanExtra("EDIT_MODE", false)
        setupSaveButton(isEditMode)
        if (isEditMode) {
            supportActionBar?.title = "Editar Recordatorio"
            loadReminderData()
        } else {
            supportActionBar?.title = "Agregar Recordatorio"
        }
    }

    private fun setupSaveButton(isEditMode: Boolean) {
        val saveButton = findViewById<Button>(R.id.btn_add_reminder)
        saveButton.text = if (isEditMode) "Confirmar Edición" else "Agregar Recordatorio"
        saveButton.setOnClickListener { addReminder() }
    }

    private fun setupSpinnersWithSelection(selectedCategory: String?, selectedType: String?) {
        // Configurar spinner de categoría
        val categories = resources.getStringArray(R.array.reminder_categories)
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter

        // Configurar spinner de tipo/frecuencia
        val types = resources.getStringArray(R.array.reminder_frequencies)
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = typeAdapter

        // Establecer selección en el spinner de categoría si existe
        selectedCategory?.let {
            val categoryPosition = categories.indexOf(it)
            if (categoryPosition >= 0) {
                spinnerCategory.setSelection(categoryPosition)
            } else {
                Log.w("AddReminder", "Categoría no encontrada en el array: $it")
            }
        }

        // Establecer selección en el spinner de tipo si existe
        selectedType?.let {
            val typePosition = types.indexOf(it)
            if (typePosition >= 0) {
                spinnerType.setSelection(typePosition)
            } else {
                Log.w("AddReminder", "Tipo no encontrado en el array: $it")
            }
        }
    }

    private fun loadReminderData() {
        etReminderTitle.setText(intent.getStringExtra("TITLE"))

        val timestamp = intent.getLongExtra("TIMESTAMP", 0)
        if (timestamp > 0) {
            val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
            selectedDate = calendar
            selectedTime = calendar
            updateDateDisplay()
            updateTimeDisplay()
        }

        // Establecer categoría y tipo
        val category = intent.getStringExtra("CATEGORY")
        val type = intent.getStringExtra("TYPE")

        setupSpinnersWithSelection(category, type)


        selectedLatitude = intent.getDoubleExtra("LATITUDE", Double.NaN).takeIf { !it.isNaN() }
        selectedLongitude = intent.getDoubleExtra("LONGITUDE", Double.NaN).takeIf { !it.isNaN() }

        // Usuarios compartidos
        sharedWithUserIds = intent.getStringArrayListExtra("SHARED_WITH")?.toMutableList() ?: mutableListOf()

        // Imagen (necesitarías cargar la imagen desde la URL si existe)
        val imageUrl = intent.getStringExtra("IMAGE_URL")
        if (!imageUrl.isNullOrEmpty()) {
            // Implementa la carga de la imagen aquí
        }
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
        spinnerType = findViewById(R.id.spinner_type) // Inicialización de spinnerType
        btnAddReminder = findViewById(R.id.btn_add_reminder)
        progressBar = findViewById(R.id.progress_bar)
        btnShareReminder = findViewById(R.id.btn_share_reminder)
        btnSelectLocation = findViewById(R.id.btn_select_location)
        btnSelectImage = findViewById(R.id.btn_select_image)

        val btnSelectLocation: Button = findViewById(R.id.btn_select_location)
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
        btnSelectImage.setOnClickListener {
            //imagePickerLauncher.launch("image/*")
            showImageSourceDialog()
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

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
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
        val selectedType = spinnerType.selectedItem.toString()
        val userId = auth.currentUser?.uid
        val isEditMode = intent.getBooleanExtra("EDIT_MODE", false)
        val reminderId = if (isEditMode) intent.getStringExtra("REMINDER_ID") else null

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

        fun saveReminderToFirestore(imageUrl: String?) {
            val reminder = Reminder(
                userId = userId,
                title = title,
                timestamp = reminderCalendar.timeInMillis,
                type = selectedType,
                category = selectedCategory,
                sharedWith = sharedWithUserIds,
                isShared = sharedWithUserIds.isNotEmpty(),
                originalCreatorId = userId,
                sharedFromUserName = currentUserName,
                latitude = selectedLatitude,
                longitude = selectedLongitude,
                imageUrl = imageUrl ?: intent.getStringExtra("IMAGE_URL") // Mantener la imagen existente si no se sube una nueva
            )

            if (isEditMode && reminderId != null) {
                // Modo edición - actualizar el recordatorio existente
                firestore.collection("users").document(userId).collection("reminders")
                    .document(reminderId)
                    .set(reminder.copy(id = reminderId))
                    .addOnSuccessListener {
                        // Cancelar notificación antigua y programar nueva
                        cancelExistingNotification(reminderId)
                        scheduleReminderNotification(reminder.copy(id = reminderId), reminderId)

                        // Actualizar invitaciones si es un recordatorio compartido
                        if (sharedWithUserIds.isNotEmpty()) {
                            updateSharedReminders(reminderId, reminder)
                        }

                        setLoadingState(false)
                        Toast.makeText(this, "Recordatorio actualizado exitosamente", Toast.LENGTH_SHORT).show()
                        firebaseAnalytics.logEvent("edit_reminder") {
                            param("user_id", userId)
                            param("reminder_id", reminderId)
                            param("reminder_title", reminder.title)
                        }
                        finish()
                    }
                    .addOnFailureListener { e ->
                        setLoadingState(false)
                        Toast.makeText(this, "Error al actualizar recordatorio: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                // Modo creación - agregar nuevo recordatorio
                firestore.collection("users").document(userId).collection("reminders")
                    .add(reminder)
                    .addOnSuccessListener { documentReference ->
                        val newReminderId = documentReference.id
                        firestore.collection("users").document(userId).collection("reminders")
                            .document(newReminderId)
                            .set(reminder.copy(id = newReminderId), SetOptions.merge())
                            .addOnSuccessListener {
                                scheduleReminderNotification(reminder.copy(id = newReminderId), newReminderId)

                                if (sharedWithUserIds.isNotEmpty()) {
                                    sharedWithUserIds.forEach { targetUserId ->
                                        val invitation = Invitation(
                                            reminderId = newReminderId,
                                            creatorId = userId,
                                            reminderTitle = reminder.title,
                                            reminderTimestamp = reminder.timestamp,
                                            reminderType = reminder.type,
                                            reminderCategory = reminder.category,
                                            sharedFromUserName = currentUserName
                                        )
                                        firestore.collection("users").document(targetUserId)
                                            .collection("userInvitations")
                                            .add(invitation)
                                    }
                                }

                                setLoadingState(false)
                                Toast.makeText(this, "Recordatorio agregado exitosamente", Toast.LENGTH_SHORT).show()
                                clearForm()
                                firebaseAnalytics.logEvent("add_reminder") {
                                    param("user_id", userId)
                                    param("reminder_title", reminder.title)
                                    param("reminder_timestamp", reminder.timestamp.toString())
                                    param("reminder_type", reminder.type)
                                    param("reminder_category", reminder.category)
                                    param("shared_with_count", reminder.sharedWith.size.toLong())
                                    param("has_location", (reminder.latitude != null && reminder.longitude != null).toString())
                                }
                                finish()
                            }
                            .addOnFailureListener { e ->
                                setLoadingState(false)
                                Toast.makeText(this, "Error al guardar recordatorio: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        setLoadingState(false)
                        Toast.makeText(this, "Error al agregar recordatorio: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }

        // Subir imagen a imgbb si existe una nueva imagen seleccionada
        if (selectedImageUri != null) {
            val base64Image = uriToBase64(this, selectedImageUri!!)

            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.imgbb.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(ImgbbService::class.java)
            val requestBody = RequestBody.create(MultipartBody.FORM, base64Image)
            val call = service.uploadImage("a22a878e6ccb5e3bcfd0a26cd4f5ac6c", requestBody)

            call.enqueue(object : Callback<ImgbbResponse> {
                override fun onResponse(call: Call<ImgbbResponse>, response: Response<ImgbbResponse>) {
                    if (response.isSuccessful) {
                        val imageUrl = response.body()?.data?.url
                        saveReminderToFirestore(imageUrl)
                    } else {
                        setLoadingState(false)
                        Toast.makeText(this@AddReminderActivity, "Error al subir imagen", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ImgbbResponse>, t: Throwable) {
                    setLoadingState(false)
                    Toast.makeText(this@AddReminderActivity, "Fallo al subir imagen", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            // No hay nueva imagen, guardar con la imagen existente (en modo edición) o sin imagen
            saveReminderToFirestore(null)
        }
    }

    private fun updateSharedReminders(reminderId: String, updatedReminder: Reminder) {
        sharedWithUserIds.forEach { userId ->
            firestore.collection("users").document(userId).collection("reminders")
                .document(reminderId)
                .set(updatedReminder)
                .addOnFailureListener { e ->
                    Log.e("AddReminderActivity", "Error al actualizar recordatorio compartido para usuario $userId: ${e.message}")
                }
        }
    }

    private fun cancelExistingNotification(reminderId: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notificationId = reminderId.hashCode()
        val intent = Intent(this, NotificationPublisher::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
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

    private fun uriToBase64(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && capturedImageUri != null) {
            selectedImageUri = capturedImageUri
        }
    }

    private fun openCamera() {
        val imageFile = File.createTempFile("camera_photo", ".jpg", cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }

        capturedImageUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            imageFile
        )

        capturedImageUri?.let {
            cameraLauncher.launch(it)
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Sacar foto", "Elegir desde la galería")

        AlertDialog.Builder(this)
            .setTitle("Seleccionar imagen")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> imagePickerLauncher.launch("image/*")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun clearForm() {
        etReminderTitle.text.clear()
        selectedDate = null
        selectedTime = null
        tvSelectedDate.text = "Fecha: Seleccionar"
        tvSelectedTime.text = "Hora: Seleccionar"
        spinnerCategory.setSelection(0)
        spinnerType.setSelection(0)
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
