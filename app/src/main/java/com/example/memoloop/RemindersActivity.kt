package com.example.memoloop

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
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
    val frequency:String = "",
    val category: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val imageUrl: String? = null,
    )

class RemindersActivity : AppCompatActivity(), RemindersAdapter.OnReminderOptionsClickListener {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var rvReminders: RecyclerView
    private lateinit var tvNoReminders: TextView
    private lateinit var fabGoToAddReminder: FloatingActionButton
    private lateinit var spinnerFilterCategory: Spinner
    private lateinit var spinnerFilterFrequency: Spinner
    private lateinit var btnApplyFilter: Button
    private val reminders = mutableListOf<Reminder>()
    private lateinit var remindersAdapter: RemindersAdapter
    private var currentFilterCategory: String = "Todas las Categorías"
    private var currentFilterFrequency: String = "Todas las Frecuencias"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminders_list)

        val toolbar: Toolbar = findViewById(R.id.toolbar_main)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Mis Recordatorios"

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        initViews()
        setupClickListeners()
        setupRecyclerView()
    }
    override fun onResume() {
        super.onResume()
        loadReminders()
    }
    private fun initViews() {
        rvReminders = findViewById(R.id.rv_reminders)
        tvNoReminders = findViewById(R.id.tv_no_reminders)
        fabGoToAddReminder = findViewById(R.id.fab_go_to_add_reminder)
        spinnerFilterCategory = findViewById(R.id.spinner_filter_category)
        spinnerFilterFrequency = findViewById(R.id.spinner_filter_frequency)
        btnApplyFilter = findViewById(R.id.btn_apply_filter)
    }
    private fun setupClickListeners() {
        fabGoToAddReminder.setOnClickListener {
            startActivity(Intent(this, AddReminderActivity::class.java))
        }

        btnApplyFilter.setOnClickListener {
            currentFilterCategory = spinnerFilterCategory.selectedItem.toString()
            currentFilterFrequency = spinnerFilterFrequency.selectedItem.toString()
            loadReminders()
            Toast.makeText(this, "Filtros aplicados", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        remindersAdapter = RemindersAdapter(reminders, this)
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
        var query: Query = firestore.collection("users").document(userId).collection("reminders")
        if (currentFilterCategory != "Todas las Categorías") {
            query = query.whereEqualTo("category", currentFilterCategory)
        }
        if (currentFilterFrequency != "Todas las Frecuencias") {
            query = query.whereEqualTo("type", currentFilterFrequency)
        }
        query.orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("RemindersActivity", "Listen failed.", e)
                    Toast.makeText(this, "Error al cargar recordatorios: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val allRemindersFromDb = mutableListOf<Reminder>()
                    val remindersToDelete = mutableListOf<String>()
                    val currentTime = System.currentTimeMillis()

                    for (doc in snapshot.documents) {
                        val reminder = doc.toObject(Reminder::class.java)?.copy(id = doc.id)
                        if (reminder != null) {
                            allRemindersFromDb.add(reminder)
                            if (reminder.type == "Eventual" && reminder.timestamp < currentTime) {
                                remindersToDelete.add(reminder.id)
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
                    val filteredAndValidReminders = allRemindersFromDb.filter {
                        !remindersToDelete.contains(it.id) && !(it.type == "Eventual" && it.timestamp < currentTime)
                    }.filter {
                        (currentFilterCategory == "Todas las Categorías" || it.category == currentFilterCategory) &&
                                (currentFilterFrequency == "Todas las Frecuencias" || it.type == currentFilterFrequency)
                    }.sortedBy { it.timestamp }

                    reminders.clear()
                    reminders.addAll(filteredAndValidReminders)
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
            if (currentFilterCategory == "Todas las Categorías" && currentFilterFrequency == "Todas las Frecuencias") {
                Log.d("RemindersActivity", "La lista de recordatorios está completamente vacía. Redirigiendo a WelcomeActivity.")
                startActivity(Intent(this, WelcomeActivity::class.java))
                finish()
            } else {
                rvReminders.isVisible = false
                tvNoReminders.isVisible = true
                tvNoReminders.text = "No hay recordatorios que coincidan con los filtros."
            }
        } else {
            rvReminders.isVisible = true
            tvNoReminders.isVisible = false
            tvNoReminders.text = "No hay recordatorios. ¡Haz clic en el '+' para agregar uno!"
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

        firebaseAnalytics.logEvent("logout") {
            param("user_email", auth.currentUser?.email ?: "unknown")
        }
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
    override fun onReminderOptionsClick(reminder: Reminder, anchorView: View) {
        val popupMenu = PopupMenu(this, anchorView)
        popupMenu.menuInflater.inflate(R.menu.menu_card_options, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    showEditReminderDialog(reminder)
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

        spinnerEditCategory.setSelection(categories.indexOf(reminder.category))
        spinnerEditFrequency.setSelection(frequencies.indexOf(reminder.type))

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Editar Recordatorio")
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
            val newCategory = spinnerEditCategory.selectedItem.toString()
            val newFrequency = spinnerEditFrequency.selectedItem.toString()

            if (newTitle.isEmpty()) {
                Toast.makeText(this, "El título no puede estar vacío", Toast.LENGTH_SHORT).show()
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
                "type" to newFrequency,
                "category" to newCategory
            )
            progressBarEdit.visibility = View.VISIBLE
            btnSaveEdit.isEnabled = false
            firestore.collection("users").document(auth.currentUser?.uid ?: "").collection("reminders")
                .document(reminder.id)
                .update(updatedReminderMap as Map<String, Any>)
                .addOnSuccessListener {
                    Toast.makeText(this, "Recordatorio actualizado", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()

                    firebaseAnalytics.logEvent("edit_reminder") {
                        param("user_id", auth.currentUser?.uid ?: "unknown")
                        param("reminder_id", reminder.id)
                        param("new_title", newTitle)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al actualizar: ${e.message}", Toast.LENGTH_LONG).show()
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
        textView.text = "Fecha: ${dateFormat.format(calendar.time)}"
    }

    private fun updateTimeDisplay(textView: TextView, calendar: Calendar) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        textView.text = "Hora: ${timeFormat.format(calendar.time)}"
    }
    private fun showDeleteConfirmationDialog(reminder: Reminder) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Recordatorio")
            .setMessage("¿Estás seguro de que quieres eliminar '${reminder.title}'?")
            .setPositiveButton("Sí, Eliminar") { dialog, _ ->
                deleteReminder(reminder.id)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    private fun deleteReminder(reminderId: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }
        firestore.collection("users").document(userId).collection("reminders")
            .document(reminderId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Recordatorio eliminado exitosamente", Toast.LENGTH_SHORT).show()

                firebaseAnalytics.logEvent("delete_reminder") {
                    param("user_id", userId)
                    param("reminder_id", reminderId)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al eliminar recordatorio: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("RemindersActivity", "Error deleting reminder", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
