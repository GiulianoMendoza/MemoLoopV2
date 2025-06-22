package com.example.memoloop

import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*

// Modificado para heredar de BaseActivity
class RemindersActivity : BaseActivity(), RemindersAdapter.OnReminderOptionsClickListener {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var notificationHelper: NotificationHelper

    private lateinit var rvReminders: RecyclerView
    private lateinit var tvNoReminders: TextView
    private lateinit var fabGoToAddReminder: FloatingActionButton

    private lateinit var spinnerFilterCategory: Spinner
    private lateinit var spinnerFilterFrequency: Spinner
    private lateinit var btnApplyFilter: Button
    private lateinit var btnViewInvitations: Button

    private val reminders = mutableListOf<Reminder>()
    private lateinit var remindersAdapter: RemindersAdapter

    // Inicializar con cadenas de recurso
    private var currentFilterCategory: String = ""
    private var currentFilterFrequency: String = ""
    private var currentUserName: String = "" // Variable para almacenar el nombre del usuario actual

    private val REQUEST_NOTIFICATION_PERMISSIONS_REMINDERS = 101

    // Sobrescribir attachBaseContext para aplicar el idioma antes de crear la actividad
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(LanguageManager.updateBaseContextLocale(newBase!!))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflar el layout específico de RemindersActivity en el FrameLayout del layout base
        val contentFrame = findViewById<FrameLayout>(R.id.content_frame) // Obtener el FrameLayout del layout base
        LayoutInflater.from(this).inflate(R.layout.activity_reminders_list, contentFrame, true) // Inflar el contenido

        val toolbar: Toolbar = findViewById(R.id.toolbar_main)
        setSupportActionBar(toolbar)
        // Usar cadena de recurso para el título del toolbar
        supportActionBar?.title = getString(R.string.my_reminders_toolbar_title)

        // Inicializar filtros con los valores por defecto del string-array
        currentFilterCategory = getString(R.string.all_categories_filter)
        currentFilterFrequency = getString(R.string.all_frequencies_filter)


        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        notificationHelper = NotificationHelper(this)

        loadCurrentUserName() // Cargar el nombre del usuario actual

        initViews()
        setupClickListeners()
        setupRecyclerView()
        setupFilterSpinners()
        // setupBottomNavigationBar() -- ¡YA NO SE LLAMA AQUÍ! Lo llama BaseActivity.onCreate()
    }

    override fun onResume() {
        super.onResume()
        loadReminders()
        checkPendingInvitations()
    }

    private fun initViews() {
        // Asegúrate de que estos IDs existan en activity_reminders_list.xml
        rvReminders = findViewById(R.id.rv_reminders)
        tvNoReminders = findViewById(R.id.tv_no_reminders)
        fabGoToAddReminder = findViewById(R.id.fab_go_to_add_reminder)

        spinnerFilterCategory = findViewById(R.id.spinner_filter_category)
        spinnerFilterFrequency = findViewById(R.id.spinner_filter_frequency)
        btnApplyFilter = findViewById(R.id.btn_apply_filter)
        btnViewInvitations = findViewById(R.id.btn_view_invitations)
    }

    private fun setupClickListeners() {
        fabGoToAddReminder.setOnClickListener {
            startActivity(Intent(this, AddReminderActivity::class.java))
        }

        btnApplyFilter.setOnClickListener {
            currentFilterCategory = spinnerFilterCategory.selectedItem.toString()
            currentFilterFrequency = spinnerFilterFrequency.selectedItem.toString()
            loadReminders()
            // Usar cadena de recurso
            Toast.makeText(this, getString(R.string.filter_applied_toast), Toast.LENGTH_SHORT).show()
        }

        btnViewInvitations.setOnClickListener {
            startActivity(Intent(this, InvitationsActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        remindersAdapter = RemindersAdapter(reminders, this, firestore)
        rvReminders.apply {
            layoutManager = LinearLayoutManager(this@RemindersActivity)
            adapter = remindersAdapter
        }
    }

    private fun setupFilterSpinners() {
        // Configurar el spinner de categoría
        val categories = resources.getStringArray(R.array.reminder_categories) // Usar el array de categorías traducido
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilterCategory.adapter = categoryAdapter
        // Seleccionar la categoría actual basándose en la traducción
        spinnerFilterCategory.setSelection(categories.indexOf(currentFilterCategory))


        // Configurar el spinner de frecuencia
        val frequencies = resources.getStringArray(R.array.reminder_frequencies) // Usar el array de frecuencias traducido
        val frequencyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, frequencies)
        frequencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilterFrequency.adapter = frequencyAdapter
        // Seleccionar la frecuencia actual basándose en la traducción
        spinnerFilterFrequency.setSelection(frequencies.indexOf(currentFilterFrequency))
    }

    // Carga el nombre del usuario actual desde Firestore
    private fun loadCurrentUserName() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    currentUserName = document.getString("name") ?: getString(R.string.unknown_user)
                    Log.d("RemindersActivity", "Current user name loaded: $currentUserName")
                }
                .addOnFailureListener { e ->
                    Log.e("RemindersActivity", "Error loading current user name: ${e.message}", e)
                }
        }
    }

    private fun loadReminders() {
        val userId = auth.currentUser?.uid ?: run {
            Log.e("RemindersActivity", "User not authenticated when loading reminders.")
            // Usar cadena de recurso
            Toast.makeText(this, getString(R.string.error_user_not_authenticated), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val allFetchedReminders = mutableListOf<Reminder>()
        val remindersToDelete = mutableListOf<String>()
        val currentTime = System.currentTimeMillis()

        firestore.collection("users").document(userId).collection("reminders")
            .get()
            .addOnSuccessListener { userSnapshot ->
                for (doc in userSnapshot.documents) {
                    // Mapear a la nueva clase Reminder con typeKey y categoryKey
                    val reminder = doc.toObject(Reminder::class.java)?.copy(id = doc.id)
                    if (reminder != null) {
                        allFetchedReminders.add(reminder)
                        // Lógica para eliminar recordatorios eventuales pasados
                        if (reminder.type == ReminderConstants.TYPE_OCCASIONAL_KEY && reminder.timestamp < currentTime) {
                            remindersToDelete.add(reminder.id)
                        }
                    }
                }
                processAndDisplayReminders(allFetchedReminders, userId, remindersToDelete)
            }
            .addOnFailureListener { e ->
                Log.e("RemindersActivity", "Error al cargar recordatorios propios/aceptados: ${e.message}", e)
                // Usar cadena de recurso
                Toast.makeText(this, getString(R.string.error_loading_reminders, e.message), Toast.LENGTH_SHORT).show()
                updateEmptyState()
            }
    }

    private fun processAndDisplayReminders(fetchedReminders: List<Reminder>, userId: String, remindersToDelete: MutableList<String>) {
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

        val filteredReminders = fetchedReminders
            .filter { reminder -> !remindersToDelete.contains(reminder.id) }
            .filter { reminder ->
                // Usar las cadenas de recurso traducidas para las comparaciones de filtro
                (currentFilterCategory == getString(R.string.all_categories_filter) ||
                        ReminderConstants.getCategoryDisplayName(this, reminder.category) == currentFilterCategory) &&
                        (currentFilterFrequency == getString(R.string.all_frequencies_filter) ||
                                ReminderConstants.getTypeDisplayName(this, reminder.type) == currentFilterFrequency)
            }
            .sortedBy { it.timestamp }

        reminders.clear()
        reminders.addAll(filteredReminders)
        remindersAdapter.notifyDataSetChanged()
        updateEmptyState()

        Log.d("RemindersActivity", "Recordatorios cargados y listos para mostrar: ${reminders.size}")
        firebaseAnalytics.logEvent("reminders_loaded") {
            param("user_id", userId)
            param("reminder_count", reminders.size.toLong())
            param("filter_category", currentFilterCategory)
            param("filter_frequency", currentFilterFrequency)
        }
    }


    private fun updateEmptyState() {
        // Usar cadenas de recurso para las comparaciones de filtro
        if (reminders.isEmpty()) {
            if (currentFilterCategory == getString(R.string.all_categories_filter) &&
                currentFilterFrequency == getString(R.string.all_frequencies_filter)) {
                Log.d("RemindersActivity", "La lista de recordatorios está completamente vacía. Redirigiendo a WelcomeActivity.")
                startActivity(Intent(this, WelcomeActivity::class.java))
                finish()
            } else {
                rvReminders.isVisible = false
                tvNoReminders.isVisible = true
                // Usar cadena de recurso
                tvNoReminders.text = getString(R.string.no_reminders_filter_match)
            }
        } else {
            rvReminders.isVisible = true
            tvNoReminders.isVisible = false
            // Usar cadena de recurso
            tvNoReminders.text = getString(R.string.no_reminders_message)
        }
    }

    private fun checkPendingInvitations() {
        val userId = auth.currentUser?.uid ?: run {
            Log.e("RemindersActivity", "checkPendingInvitations: User not authenticated.")
            return
        }
        Log.d("RemindersActivityDebug", "Checking pending invitations for user: $userId")

        firestore.collection("users").document(userId).collection("userInvitations")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d("RemindersActivityDebug", "Invitation snapshot empty: ${snapshot.isEmpty}")
                if (!snapshot.isEmpty) {
                    Log.d("RemindersActivityDebug", "Found invitation document IDs: ${snapshot.documents.map { it.id }}")
                }

                if (snapshot.isEmpty) {
                    btnViewInvitations.isVisible = false
                    // Usar cadena de recurso
                    Log.d("RemindersActivity", getString(R.string.no_pending_invitations))
                } else {
                    btnViewInvitations.isVisible = true
                    // Usar cadena de recurso
                    Log.d("RemindersActivity", getString(R.string.pending_invitations_found))
                }
            }
            .addOnFailureListener { e ->
                Log.e("RemindersActivity", "Error checking invitations: ${e.message}", e)
                btnViewInvitations.isVisible = false
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
        // Usar cadena de recurso
        Toast.makeText(this, getString(R.string.toast_session_closed), Toast.LENGTH_SHORT).show()

        firebaseAnalytics.logEvent("logout") {
            param("user_email", auth.currentUser?.email ?: "unknown")
        }

        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onReminderOptionsClick(reminder: Reminder, anchorView: View) {
        val popupMenu = PopupMenu(this, anchorView)
        popupMenu.menuInflater.inflate(R.menu.menu_card_options, popupMenu.menu)

        val currentUserId = auth.currentUser?.uid
        // Solo permite editar o eliminar si el recordatorio fue creado por el usuario actual
        // Compartir solo si es el creador original o si no se ha establecido un creador original (para recordatorios más antiguos)
        val canShare = reminder.originalCreatorId.isEmpty() || reminder.originalCreatorId == currentUserId

        if (reminder.userId != currentUserId) {
            popupMenu.menu.findItem(R.id.action_edit)?.isVisible = false
            popupMenu.menu.findItem(R.id.action_delete)?.isVisible = false
        }
        popupMenu.menu.findItem(R.id.action_share)?.isVisible = canShare // Ocultar compartir si no es el creador

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    showEditReminderDialog(reminder)
                    true
                }
                R.id.action_share -> {
                    showShareReminderDialog(reminder) // Llamar a la nueva función de compartir
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirmationDialog(reminder)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun showEditReminderDialog(reminder: Reminder) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_reminder, null)
        val etEditTitle: EditText = dialogView.findViewById(R.id.et_edit_reminder_title)
        val tvEditDate: TextView = dialogView.findViewById(R.id.tv_edit_selected_date)
        val tvEditTime: TextView = dialogView.findViewById(R.id.tv_edit_selected_time)
        val btnEditDate: Button = dialogView.findViewById(R.id.btn_edit_select_date)
        val btnEditTime: Button = dialogView.findViewById(R.id.btn_edit_select_time)
        val spinnerEditCategory: Spinner = dialogView.findViewById(R.id.spinner_edit_category)
        val spinnerEditFrequency: Spinner = dialogView.findViewById(R.id.spinner_edit_frequency)
        val btnSaveEdit: Button = dialogView.findViewById(R.id.btn_save_edit_reminder)
        val progressBarEdit: ProgressBar = dialogView.findViewById(R.id.progress_bar_edit)


        etEditTitle.setText(reminder.title)
        var editedDate: Calendar = Calendar.getInstance().apply { timeInMillis = reminder.timestamp }
        var editedTime: Calendar = Calendar.getInstance().apply { timeInMillis = reminder.timestamp }
        updateDateDisplay(tvEditDate, editedDate)
        updateTimeDisplay(tvEditTime, editedTime)

        val categories = resources.getStringArray(R.array.reminder_categories).toList()
        val frequencies = resources.getStringArray(R.array.reminder_frequencies).toList()

        // Configurar adaptadores para los spinners en el diálogo de edición
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEditCategory.adapter = categoryAdapter

        val frequencyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, frequencies)
        frequencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEditFrequency.adapter = frequencyAdapter

        // Establecer la selección inicial basándose en la clave y el idioma actual
        spinnerEditCategory.setSelection(categories.indexOf(ReminderConstants.getCategoryDisplayName(this, reminder.category)))
        spinnerEditFrequency.setSelection(frequencies.indexOf(ReminderConstants.getTypeDisplayName(this, reminder.type)))

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            // Usar cadena de recurso
            .setTitle(getString(R.string.dialog_edit_reminder_title))
            .setCancelable(false)
            .create()

        btnEditDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    editedDate = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    }
                    updateDateDisplay(tvEditDate, editedDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnEditTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    editedTime = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    updateTimeDisplay(tvEditTime, editedTime)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }

        btnSaveEdit.setOnClickListener {
            val newTitle = etEditTitle.text.toString().trim()
            val newCategoryDisplayName = spinnerEditCategory.selectedItem.toString()
            val newFrequencyDisplayName = spinnerEditFrequency.selectedItem.toString()

            // Convertir nombres de visualización a claves antes de guardar
            val newCategoryKey = ReminderConstants.getCategoryKeyFromDisplayName(this, newCategoryDisplayName)
            val newTypeKey = ReminderConstants.getTypeKeyFromDisplayName(this, newFrequencyDisplayName)


            if (newTitle.isEmpty()) {
                // Usar cadena de recurso
                Toast.makeText(this, getString(R.string.error_title_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updatedTimestampCalendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, editedDate.get(Calendar.YEAR))
                set(Calendar.MONTH, editedDate.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, editedDate.get(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, editedTime.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, editedTime.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val updatedReminderMap = hashMapOf(
                "title" to newTitle,
                "timestamp" to updatedTimestampCalendar.timeInMillis,
                "typeKey" to newTypeKey, // Guardar la clave
                "categoryKey" to newCategoryKey // Guardar la clave
            )

            progressBarEdit.visibility = View.VISIBLE
            btnSaveEdit.isEnabled = false

            firestore.collection("users").document(auth.currentUser?.uid ?: "").collection("reminders")
                .document(reminder.id)
                .update(updatedReminderMap as Map<String, Any>)
                .addOnSuccessListener {
                    // Usar cadena de recurso
                    Toast.makeText(this, getString(R.string.toast_update_reminder_success), Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    cancelReminderNotification(reminder.id)
                    val tempUpdatedReminder = Reminder(
                        id = reminder.id,
                        userId = reminder.userId,
                        title = newTitle,
                        timestamp = updatedTimestampCalendar.timeInMillis,
                        type = newTypeKey,
                        category = newCategoryKey,
                        sharedWith = reminder.sharedWith,
                        isShared = reminder.isShared,
                        originalCreatorId = reminder.originalCreatorId,
                        sharedFromUserName = reminder.sharedFromUserName,
                        latitude = reminder.latitude,
                        longitude = reminder.longitude,
                        imageUrl = reminder.imageUrl
                    )
                    scheduleReminderNotification(tempUpdatedReminder, reminder.id)

                    firebaseAnalytics.logEvent("edit_reminder") {
                        param("user_id", auth.currentUser?.uid ?: "unknown")
                        param("reminder_id", reminder.id)
                        param("new_title", newTitle)
                    }
                    loadReminders()
                }
                .addOnFailureListener { e ->
                    // Usar cadena de recurso
                    Toast.makeText(this, getString(R.string.toast_update_reminder_error, e.message), Toast.LENGTH_LONG).show()
                    Log.e("RemindersActivity", "Error updating reminder", e)
                    progressBarEdit.visibility = View.GONE
                    btnSaveEdit.isEnabled = true
                }
        }

        dialogView.findViewById<Button>(R.id.btn_cancel_edit_reminder).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateDateDisplay(textView: TextView, calendar: Calendar) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        // Usar cadena de recurso y dividirla para obtener la etiqueta "Fecha"
        textView.text = "${getString(R.string.selected_date_placeholder).split(":")[0]}: ${dateFormat.format(calendar.time)}"
    }

    private fun updateTimeDisplay(textView: TextView, calendar: Calendar) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        // Usar cadena de recurso y dividirla para obtener la etiqueta "Hora"
        textView.text = "${getString(R.string.selected_time_placeholder).split(":")[0]}: ${timeFormat.format(calendar.time)}"
    }

    // Muestra un diálogo para compartir el recordatorio con otros usuarios por correo electrónico
    private fun showShareReminderDialog(reminder: Reminder) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_share_reminder, null)
        val etShareEmails: EditText = dialogView.findViewById(R.id.et_share_emails)
        val btnConfirmShare: Button = dialogView.findViewById(R.id.btn_confirm_share)
        val btnCancelShare: Button = dialogView.findViewById(R.id.btn_cancel_share)
        val progressBarShare: ProgressBar = dialogView.findViewById(R.id.progress_bar_share)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle(getString(R.string.dialog_share_reminder_title)) // Usar cadena de recurso
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
                                Log.d("RemindersActivity", "Found UID for email $email: $uid")
                            }
                        } else {
                            Log.w("RemindersActivity", "User with email $email not found.")
                            Toast.makeText(this, getString(R.string.toast_user_not_found, email), Toast.LENGTH_SHORT).show()
                        }

                        if (emailsToProcess == 0) {
                            progressBarShare.isVisible = false
                            btnConfirmShare.isEnabled = true
                            btnCancelShare.isEnabled = true

                            if (successfulLookups > 0) {
                                // Aquí se envía el recordatorio a los usuarios encontrados
                                val updatedSharedWith = reminder.sharedWith.toMutableList()
                                foundUserIds.forEach { targetUid ->
                                    if (!updatedSharedWith.contains(targetUid)) {
                                        updatedSharedWith.add(targetUid)
                                    }
                                }

                                // Actualizar el recordatorio original para reflejar que se ha compartido
                                val updatedReminderMap = hashMapOf(
                                    "sharedWith" to updatedSharedWith,
                                    "isShared" to true
                                ) as Map<String, Any>

                                firestore.collection("users").document(reminder.userId).collection("reminders")
                                    .document(reminder.id)
                                    .update(updatedReminderMap)
                                    .addOnSuccessListener {
                                        Log.d("RemindersActivity", "Recordatorio actualizado en Firestore con nuevos usuarios compartidos.")
                                        Toast.makeText(this, getString(R.string.toast_shared_with_users, successfulLookups), Toast.LENGTH_SHORT).show()
                                        // Enviar invitaciones a cada usuario encontrado
                                        foundUserIds.forEach { targetUserId ->
                                            val invitation = Invitation(
                                                reminderId = reminder.id,
                                                creatorId = auth.currentUser?.uid ?: "",
                                                reminderTitle = reminder.title,
                                                reminderTimestamp = reminder.timestamp,
                                                reminderType = reminder.type, // Usar typeKey para la invitación
                                                reminderCategory = reminder.category, // Usar categoryKey para la invitación
                                                sharedFromUserName = currentUserName // Usar el nombre cargado del usuario actual
                                            )
                                            firestore.collection("users").document(targetUserId).collection("userInvitations")
                                                .add(invitation)
                                                .addOnSuccessListener { Log.d("RemindersActivity", "Invitation sent to $targetUserId successfully!") }
                                                .addOnFailureListener { e -> Log.e("RemindersActivity", "Failed to send invitation to $targetUserId: ${e.message}", e) }
                                        }
                                        loadReminders() // Recargar para actualizar la UI si es necesario (ej. el indicador de compartido)
                                        dialog.dismiss()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, getString(R.string.toast_update_reminder_error, e.message), Toast.LENGTH_LONG).show()
                                        Log.e("RemindersActivity", "Error updating reminder with shared users: ${e.message}", e)
                                        dialog.dismiss()
                                    }
                            } else {
                                Toast.makeText(this, getString(R.string.toast_no_valid_users_found), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        emailsToProcess--
                        Log.e("RemindersActivity", "Error looking up user by email: $email, Error: ${e.message}", e)
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


    private fun showDeleteConfirmationDialog(reminder: Reminder) {
        AlertDialog.Builder(this)
            // Usar cadenas de recurso
            .setTitle(getString(R.string.delete_reminder_title))
            .setMessage(getString(R.string.delete_reminder_confirmation, reminder.title))
            .setPositiveButton(getString(R.string.yes_delete_button)) { dialog, _ ->
                deleteReminder(reminder.id)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.no_cancel_button)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteReminder(reminderId: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            // Usar cadena de recurso
            Toast.makeText(this, getString(R.string.error_user_not_authenticated), Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("users").document(userId).collection("reminders")
            .document(reminderId)
            .delete()
            .addOnSuccessListener {
                // Usar cadena de recurso
                Toast.makeText(this, getString(R.string.reminder_deleted_success), Toast.LENGTH_SHORT).show()
                cancelReminderNotification(reminderId)
                firebaseAnalytics.logEvent("delete_reminder") {
                    param("user_id", userId)
                    param("reminder_id", reminderId)
                }
                loadReminders()
            }
            .addOnFailureListener { e ->
                // Usar cadena de recurso
                Toast.makeText(this, getString(R.string.error_deleting_reminder, e.message), Toast.LENGTH_LONG).show()
                Log.e("RemindersActivity", "Error deleting reminder", e)
            }
    }

    private fun scheduleReminderNotification(reminder: Reminder, reminderId: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notificationId = reminderId.hashCode()

        val notificationTime = Calendar.getInstance().apply {
            timeInMillis = reminder.timestamp
            add(Calendar.MINUTE, -5)
        }.timeInMillis

        // Obtener la categoría traducida para la notificación
        val translatedCategory = ReminderConstants.getCategoryDisplayName(this, reminder.category)

        if (notificationTime <= System.currentTimeMillis()) {
            Log.d("RemindersActivity", "Notification time for ${reminder.title} is in the past or too soon, showing immediately.")
            notificationHelper.showNotification(
                // Usar cadenas de recurso
                getString(R.string.notification_title_reminder, reminder.title),
                getString(R.string.notification_message_past_due, translatedCategory),
                notificationId
            )
            return
        }

        val intent = Intent(this, NotificationPublisher::class.java).apply {
            // Pasar la clave de la categoría y el tipo para que el NotificationPublisher pueda traducirlos
            putExtra(NotificationPublisher.REMINDER_TITLE_EXTRA, reminder.title)
            putExtra(NotificationPublisher.REMINDER_MESSAGE_EXTRA_KEY, reminder.category) // Pasa la clave
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
            Log.d("RemindersActivity", "Scheduled notification for ${reminder.title} at ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(notificationTime))}")
        } catch (e: SecurityException) {
            Log.e("RemindersActivity", "SecurityException al programar alarma: ${e.message}", e)
            // Usar cadena de recurso
            Toast.makeText(this, getString(R.string.error_scheduling_alarm), Toast.LENGTH_LONG).show()
        }
    }

    private fun cancelReminderNotification(reminderId: String) {
        val notificationId = reminderId.hashCode()
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NotificationPublisher::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            notificationHelper.cancelNotification(notificationId)
            Log.d("RemindersActivity", "Cancelled notification for reminder ID: $reminderId")
        } else {
            Log.d("RemindersActivity", "No pending notification found for reminder ID: $reminderId")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
