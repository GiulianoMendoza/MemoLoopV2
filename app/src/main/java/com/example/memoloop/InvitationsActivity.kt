package com.example.memoloop

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*

class InvitationsActivity : AppCompatActivity(), InvitationAdapter.OnInvitationActionListener {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var notificationHelper: NotificationHelper

    private lateinit var rvInvitations: RecyclerView
    private lateinit var tvNoInvitations: TextView

    private val invitations = mutableListOf<Invitation>()
    private lateinit var invitationAdapter: InvitationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invitations)

        val toolbar: Toolbar = findViewById(R.id.toolbar_invitations)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Mis Invitaciones"

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        notificationHelper = NotificationHelper(this)

        initViews()
        setupRecyclerView()
        loadInvitations()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initViews() {
        rvInvitations = findViewById(R.id.rv_invitations)
        tvNoInvitations = findViewById(R.id.tv_no_invitations)
    }

    private fun setupRecyclerView() {
        invitationAdapter = InvitationAdapter(invitations, this)
        rvInvitations.apply {
            layoutManager = LinearLayoutManager(this@InvitationsActivity)
            adapter = invitationAdapter
        }
    }

    private fun loadInvitations() {
        val userId = auth.currentUser?.uid ?: run {
            Log.e("InvitationsActivity", "User not authenticated when loading invitations.")
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        firestore.collection("users").document(userId).collection("userInvitations")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("InvitationsActivity", "Listen failed.", e)
                    Toast.makeText(this, "Error al cargar invitaciones: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    invitations.clear()
                    for (doc in snapshot.documents) {
                        val invitation = Invitation(
                            id = doc.id,
                            reminderId = doc.getString("reminderId") ?: "",
                            creatorId = doc.getString("creatorId") ?: "",
                            reminderTitle = doc.getString("reminderTitle") ?: "",
                            reminderTimestamp = doc.getLong("reminderTimestamp") ?: 0L,
                            reminderType = doc.getString("reminderType") ?: "",
                            reminderCategory = doc.getString("reminderCategory") ?: "",
                            sharedFromUserName = doc.getString("sharedFromUserName") ?: "Usuario Desconocido"
                        )
                        invitations.add(invitation)
                    }
                    invitationAdapter.notifyDataSetChanged()
                    updateEmptyState()

                    firebaseAnalytics.logEvent("invitations_loaded") {
                        param("user_id", userId)
                        param("invitation_count", invitations.size.toLong())
                    }
                    Log.d("InvitationsActivity", "Invitations loaded: ${invitations.size}")
                } else {
                    Log.d("InvitationsActivity", "Current data: null")
                }
            }
    }

    private fun updateEmptyState() {
        if (invitations.isEmpty()) {
            rvInvitations.isVisible = false
            tvNoInvitations.isVisible = true
        } else {
            rvInvitations.isVisible = true
            tvNoInvitations.isVisible = false
        }
    }

    override fun onAcceptClick(invitation: Invitation) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Error: Sesión no iniciada.", Toast.LENGTH_SHORT).show()
            return
        }

        val acceptedReminder = Reminder(
            id = invitation.reminderId,
            userId = userId,
            title = invitation.reminderTitle,
            timestamp = invitation.reminderTimestamp,
            type = invitation.reminderType,
            category = invitation.reminderCategory,
            sharedWith = emptyList(),
            isShared = true,
            originalCreatorId = invitation.creatorId,
            sharedFromUserName = invitation.sharedFromUserName
        )

        firestore.collection("users").document(userId).collection("reminders")
            .document(invitation.reminderId)
            .set(acceptedReminder, SetOptions.merge())
            .addOnSuccessListener {
                firestore.collection("users").document(userId).collection("userInvitations")
                    .document(invitation.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Recordatorio '${invitation.reminderTitle}' aceptado.", Toast.LENGTH_SHORT).show()
                        firebaseAnalytics.logEvent("invitation_accepted") {
                            param("user_id", userId)
                            param("reminder_id", invitation.reminderId)
                            param("creator_id", invitation.creatorId)
                        }
                        scheduleReminderNotification(acceptedReminder, acceptedReminder.id)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error al eliminar invitación: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("InvitationsActivity", "Error deleting invitation: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al aceptar recordatorio: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("InvitationsActivity", "Error accepting reminder: ${e.message}", e)
            }
    }


    override fun onRejectClick(invitation: Invitation) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Error: Sesión no iniciada.", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("users").document(userId).collection("userInvitations")
            .document(invitation.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Invitación rechazada.", Toast.LENGTH_SHORT).show()
                firebaseAnalytics.logEvent("invitation_rejected") {
                    param("user_id", userId)
                    param("reminder_id", invitation.reminderId)
                    param("creator_id", invitation.creatorId)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al rechazar invitación: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("InvitationsActivity", "Error rejecting invitation: ${e.message}", e)
            }
    }

    private fun scheduleReminderNotification(reminder: Reminder, reminderId: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notificationId = reminderId.hashCode()

        // Calcula la hora de la notificación 5 minutos antes del recordatorio
        val notificationTime = Calendar.getInstance().apply {
            timeInMillis = reminder.timestamp
            add(Calendar.MINUTE, -5) // Resta 5 minutos
        }.timeInMillis
        if (notificationTime <= System.currentTimeMillis()) {
            Log.d("InvitationsActivity", "Notification time for ${reminder.title} is in the past or too soon, showing immediately.")
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
            Log.d("InvitationsActivity", "Scheduled notification for ${reminder.title} at ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(notificationTime))}")
        } catch (e: SecurityException) {
            Log.e("InvitationsActivity", "SecurityException al programar alarma: ${e.message}", e)
            Toast.makeText(this, "No se pudo programar el recordatorio. Revisar permisos.", Toast.LENGTH_LONG).show()
        }
    }
}
