package com.example.memoloop

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import android.app.Activity
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.activity.result.contract.ActivityResultContracts
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
    private var selectedImageUri: Uri? = null
    private var capturedImageUri: Uri? = null

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(LanguageManager.updateBaseContextLocale(newBase!!))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contentFrame = findViewById<FrameLayout>(R.id.content_frame)
        LayoutInflater.from(this).inflate(R.layout.activity_add_reminders, contentFrame, true)

        val toolbar: Toolbar = findViewById(R.id.toolbar_main)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.add_reminder_toolbar_title)

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
            supportActionBar?.title = getString(R.string.edit_reminder_toolbar_title)
            loadReminderData()
        } else {
            supportActionBar?.title = getString(R.string.add_reminder_toolbar_title)
        }
    }

    private fun setupSaveButton(isEditMode: Boolean) {
        val saveButton = findViewById<Button>(R.id.btn_add_reminder)
        saveButton.text = if (isEditMode) getString(R.string.confirm_edit_button) else getString(R.string.add_reminder_button)
        saveButton.setOnClickListener { addReminder() }
    }

    private fun setupSpinnersWithSelection(selectedCategory: String?, selectedType: String?) {
        val categories = resources.getStringArray(R.array.reminder_categories)
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter

        val types = resources.getStringArray(R.array.reminder_frequencies)
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFrequency.adapter = typeAdapter
        selectedCategory?.let { categoryKey ->
            val categoryDisplayName = ReminderConstants.getCategoryDisplayName(this, categoryKey)
            val categoryPosition = categories.indexOf(categoryDisplayName)
            if (categoryPosition >= 0) {
                spinnerCategory.setSelection(categoryPosition)
            } else {
                Log.w("AddReminder", "Categoría no encontrada en el array: $categoryDisplayName (key: $categoryKey)")
            }
        }
        selectedType?.let { typeKey ->
            val typeDisplayName = ReminderConstants.getTypeDisplayName(this, typeKey)
            val typePosition = types.indexOf(typeDisplayName)
            if (typePosition >= 0) {
                spinnerFrequency.setSelection(typePosition)
            } else {
                Log.w("AddReminder", "Tipo no encontrado en el array: $typeDisplayName (key: $typeKey)")
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

        val category = intent.getStringExtra("CATEGORY")
        val type = intent.getStringExtra("TYPE")
        setupSpinnersWithSelection(category, type)

        selectedLatitude = intent.getDoubleExtra("LATITUDE", Double.NaN).takeIf { !it.isNaN() }
        selectedLongitude = intent.getDoubleExtra("LONGITUDE", Double.NaN).takeIf { !it.isNaN() }

        sharedWithUserIds = intent.getStringArrayListExtra("SHARED_WITH")?.toMutableList() ?: mutableListOf()

        val imageUrl = intent.getStringExtra("IMAGE_URL")
        if (!imageUrl.isNullOrEmpty()) {
            Log.d("AddReminderActivity", "Reminder has existing image URL: $imageUrl")
            btnSelectImage.text = getString(R.string.change_image_button)
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
        spinnerFrequency = findViewById(R.id.spinner_type)
        btnAddReminder = findViewById(R.id.btn_add_reminder)
        progressBar = findViewById(R.id.progress_bar)
        btnShareReminder = findViewById(R.id.btn_share_reminder)
        btnSelectLocation = findViewById(R.id.btn_select_location)
        btnSelectImage = findViewById(R.id.btn_select_image)

        btnSelectLocation.setOnClickListener {
            val intent = Intent(this, MapPickerActivity::class.java)
            locationPicker.launch(intent)
        }

        btnSelectImage.setOnClickListener {
            showImageSourceDialog()
        }
    }

    private fun setupSpinners() {
        setupSpinnersWithSelection(null, null)
    }

    private fun setupClickListeners() {
        btnSelectDate.setOnClickListener { showDatePicker() }
        btnSelectTime.setOnClickListener { showTimePicker() }
        btnShareReminder.setOnClickListener { showShareReminderDialog() }
    }

    private fun loadCurrentUserName() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    currentUserName = document.getString("name") ?: getString(R.string.unknown_user)
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
                        Toast.makeText(this, getString(R.string.toast_location_saved), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, getString(R.string.toast_error_receiving_location), Toast.LENGTH_SHORT).show()
                        selectedLatitude = null
                        selectedLongitude = null
                    }
                } else {
                    Toast.makeText(this, getString(R.string.toast_no_coordinates_received), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.toast_no_map_data_received), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, getString(R.string.toast_operation_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            Toast.makeText(this, getString(R.string.image_selected), Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && capturedImageUri != null) {
            selectedImageUri = capturedImageUri
            Toast.makeText(this, getString(R.string.image_taken), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.image_capture_failed), Toast.LENGTH_SHORT).show()
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
        } ?: run {
            Toast.makeText(this, getString(R.string.error_creating_image_file), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf(getString(R.string.take_photo_option), getString(R.string.choose_from_gallery_option))

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_image_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> imagePickerLauncher.launch("image/*")
                }
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }


    private fun updateDateDisplay() {
        selectedDate?.let { date ->
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            tvSelectedDate.text = "${getString(R.string.selected_date_placeholder).split(":")[0]}: ${dateFormat.format(date.time)}"
        }
    }

    private fun updateTimeDisplay() {
        selectedTime?.let { time ->
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            tvSelectedTime.text = "${getString(R.string.selected_time_placeholder).split(":")[0]}: ${timeFormat.format(time.time)}"
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
            .setTitle(getString(R.string.dialog_share_reminder_title))
            .setCancelable(false)
            .create()

        btnConfirmShare.setOnClickListener {
            val emailsText = etShareEmails.text.toString().trim()
            if (emailsText.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_empty_emails), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val emails = emailsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (emails.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_invalid_emails), Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(this, getString(R.string.toast_cannot_share_self), Toast.LENGTH_SHORT).show()
                            } else {
                                foundUserIds.add(uid)
                                successfulLookups++
                                Log.d("AddReminderActivity", "Found UID for email $email: $uid")
                            }
                        } else {
                            Log.w("AddReminderActivity", "User with email $email not found.")
                            Toast.makeText(this, getString(R.string.toast_user_not_found, email), Toast.LENGTH_SHORT).show()
                        }

                        if (emailsToProcess == 0) {
                            progressBarShare.isVisible = false
                            btnConfirmShare.isEnabled = true
                            btnCancelShare.isEnabled = true

                            if (successfulLookups > 0) {
                                sharedWithUserIds.clear()
                                sharedWithUserIds.addAll(foundUserIds)
                                Toast.makeText(this, getString(R.string.toast_shared_with_users, successfulLookups), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            } else {
                                Toast.makeText(this, getString(R.string.toast_no_valid_users_found), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        emailsToProcess--
                        Log.e("AddReminderActivity", "Error looking up user by email: $email, Error: ${e.message}", e)
                        Toast.makeText(this, getString(R.string.toast_error_fetching_users, e.message), Toast.LENGTH_SHORT).show()

                        if (emailsToProcess == 0) {
                            progressBarShare.isVisible = false
                            btnConfirmShare.isEnabled = true
                            btnCancelShare.isEnabled = true
                            Toast.makeText(this, getString(R.string.toast_error_all_users_not_found), Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        btnCancelShare.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }


    private fun addReminder() {
        val title = etReminderTitle.text.toString().trim()
        val selectedCategoryDisplayName = spinnerCategory.selectedItem.toString()
        val selectedFrequencyDisplayName = spinnerFrequency.selectedItem.toString()
        val userId = auth.currentUser?.uid
        val isEditMode = intent.getBooleanExtra("EDIT_MODE", false)
        val reminderId = if (isEditMode) intent.getStringExtra("REMINDER_ID") else null

        val selectedCategoryKey = ReminderConstants.getCategoryKeyFromDisplayName(this, selectedCategoryDisplayName)
        val selectedTypeKey = ReminderConstants.getTypeKeyFromDisplayName(this, selectedFrequencyDisplayName)

        if (title.isEmpty() || selectedDate == null || selectedTime == null) {
            Toast.makeText(this, getString(R.string.error_completing_fields), Toast.LENGTH_SHORT).show()
            return
        }

        if (userId == null) {
            Toast.makeText(this, getString(R.string.error_user_not_authenticated), Toast.LENGTH_SHORT).show()
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
                type = selectedTypeKey,
                category = selectedCategoryKey,
                sharedWith = sharedWithUserIds,
                isShared = sharedWithUserIds.isNotEmpty(),
                originalCreatorId = userId,
                sharedFromUserName = currentUserName,
                latitude = selectedLatitude,
                longitude = selectedLongitude,
                imageUrl = imageUrl ?: intent.getStringExtra("IMAGE_URL")
            )

            if (isEditMode && reminderId != null) {
                firestore.collection("users").document(userId).collection("reminders")
                    .document(reminderId)
                    .set(reminder.copy(id = reminderId))
                    .addOnSuccessListener {
                        cancelExistingNotification(reminderId)
                        scheduleReminderNotification(reminder.copy(id = reminderId), reminderId)

                        if (sharedWithUserIds.isNotEmpty()) {
                            updateSharedReminders(reminderId, reminder)
                        }

                        setLoadingState(false)
                        Toast.makeText(this, getString(R.string.toast_reminder_updated_successfully), Toast.LENGTH_SHORT).show()
                        firebaseAnalytics.logEvent("edit_reminder") {
                            param("user_id", userId)
                            param("reminder_id", reminderId)
                            param("reminder_title", reminder.title)
                            param("has_image", (!imageUrl.isNullOrEmpty()).toString())
                        }
                        finish()
                    }
                    .addOnFailureListener { e ->
                        setLoadingState(false)
                        Toast.makeText(this, getString(R.string.toast_error_updating_reminder, e.message), Toast.LENGTH_LONG).show()
                    }
            } else {
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
                                Toast.makeText(this, getString(R.string.toast_reminder_added_successfully), Toast.LENGTH_SHORT).show()
                                clearForm()
                                firebaseAnalytics.logEvent("add_reminder") {
                                    param("user_id", userId)
                                    param("reminder_title", reminder.title)
                                    param("reminder_timestamp", reminder.timestamp.toString())
                                    param("reminder_type", reminder.type)
                                    param("reminder_category", reminder.category)
                                    param("shared_with_count", reminder.sharedWith.size.toLong())
                                    param("has_location", (reminder.latitude != null && reminder.longitude != null).toString())
                                    param("has_image", (!imageUrl.isNullOrEmpty()).toString())
                                }
                                finish()
                            }
                            .addOnFailureListener { e ->
                                setLoadingState(false)
                                Toast.makeText(this, getString(R.string.toast_error_saving_reminder, e.message), Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        setLoadingState(false)
                        Toast.makeText(this, getString(R.string.toast_error_adding_reminder, e.message), Toast.LENGTH_LONG).show()
                    }
            }
        }

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
                        Toast.makeText(this@AddReminderActivity, getString(R.string.error_uploading_image), Toast.LENGTH_SHORT).show()
                        Log.e("AddReminderActivity", "ImgBB upload error: ${response.errorBody()?.string()}")
                    }
                }

                override fun onFailure(call: Call<ImgbbResponse>, t: Throwable) {
                    setLoadingState(false)
                    Toast.makeText(this@AddReminderActivity, getString(R.string.image_upload_failed), Toast.LENGTH_SHORT).show()
                    Log.e("AddReminderActivity", "ImgBB upload failure: ${t.message}", t)
                }
            })
        } else {
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

        val translatedCategory = ReminderConstants.getCategoryDisplayName(this, reminder.category)

        if (notificationTime <= System.currentTimeMillis()) {
            Log.d("AddReminderActivity", "Notification time for ${reminder.title} is in the past or too soon, showing immediately.")
            notificationHelper.showNotification(
                getString(R.string.notification_title_reminder, reminder.title),
                getString(R.string.notification_message_past_due, translatedCategory),
                notificationId
            )
            return
        }

        val intent = Intent(this, NotificationPublisher::class.java).apply {
            putExtra(NotificationPublisher.REMINDER_TITLE_EXTRA, reminder.title)
            putExtra(NotificationPublisher.REMINDER_MESSAGE_EXTRA_KEY, reminder.category)
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
            Toast.makeText(this, getString(R.string.error_scheduling_alarm), Toast.LENGTH_LONG).show()
        }
    }

    private fun uriToBase64(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bytes = inputStream.readBytes()
            Base64.encodeToString(bytes, Base64.DEFAULT)
        } ?: ""
    }

    private fun clearForm() {
        etReminderTitle.text.clear()
        selectedDate = null
        selectedTime = null
        tvSelectedDate.text = "${getString(R.string.selected_date_placeholder).split(":")[0]}: ${getString(R.string.select_text)}"
        tvSelectedTime.text = "${getString(R.string.selected_time_placeholder).split(":")[0]}: ${getString(R.string.select_text)}"
        spinnerCategory.setSelection(0)
        spinnerFrequency.setSelection(0)
        sharedWithUserIds.clear()
        selectedLatitude = null
        selectedLongitude = null
        selectedImageUri = null
        btnSelectImage.text = getString(R.string.select_image_button)
    }

    private fun setLoadingState(isLoading: Boolean) {
        btnAddReminder.isEnabled = !isLoading
        btnShareReminder.isEnabled = !isLoading
        btnSelectLocation.isEnabled = !isLoading
        btnSelectImage.isEnabled = !isLoading
        progressBar.isVisible = isLoading
        if (isLoading) {
            btnAddReminder.text = ""
        } else {
            btnAddReminder.text = getString(R.string.add_reminder_button)
        }
    }
}