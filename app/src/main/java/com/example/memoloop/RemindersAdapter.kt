package com.example.memoloop

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class RemindersAdapter(
    private val reminders: List<Reminder>,
    private val clickListener: OnReminderOptionsClickListener,
    private val firestore: FirebaseFirestore // Instancia de Firestore
) : RecyclerView.Adapter<RemindersAdapter.ReminderViewHolder>() {

    interface OnReminderOptionsClickListener {
        fun onReminderOptionsClick(reminder: Reminder, anchorView: View)
    }

    class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tv_reminder_title)
        val tvDateTime: TextView = itemView.findViewById(R.id.tv_reminder_datetime)
        val tvDetails: TextView = itemView.findViewById(R.id.tv_reminder_details)
        val btnOptions: ImageButton = itemView.findViewById(R.id.btn_options)
        val tvSharedIndicator: TextView = itemView.findViewById(R.id.tv_shared_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminder = reminders[position]
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        if (reminder.isShared && reminder.userId == currentUserId) {
            holder.tvTitle.text = reminder.title
        } else if (reminder.isShared && reminder.userId != currentUserId) {
            holder.tvTitle.text = "Evento con: ${reminder.sharedFromUserName}"
        } else {
            holder.tvTitle.text = reminder.title
        }


        val date = Calendar.getInstance().apply { timeInMillis = reminder.timestamp }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.tvDateTime.text = "Fecha: ${dateFormat.format(date.time)}"

        holder.tvDetails.text = "Tipo: ${reminder.type} - Categoría: ${reminder.category}"
        if (reminder.isShared && reminder.userId != currentUserId) {
            holder.tvSharedIndicator.visibility = View.GONE
        } else if (!reminder.isShared && reminder.sharedWith.isNotEmpty() && reminder.userId == currentUserId) {
            fetchSharedUserNames(reminder.sharedWith, holder.tvSharedIndicator)
        } else {
            holder.tvSharedIndicator.visibility = View.GONE
        }

        holder.btnOptions.setOnClickListener {
            clickListener.onReminderOptionsClick(reminder, it)
        }
    }

    private fun fetchSharedUserNames(sharedUserIds: List<String>, textView: TextView) {
        if (sharedUserIds.isEmpty()) {
            textView.visibility = View.GONE
            return
        }

        val names = mutableListOf<String>()
        var count = 0

        textView.text = "Compartido con: Cargando..."
        textView.visibility = View.VISIBLE

        sharedUserIds.forEach { userId ->
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    count++
                    val userName = document.getString("name")
                    if (userName != null) {
                        names.add(userName)
                    }

                    if (count == sharedUserIds.size) {
                        if (names.isNotEmpty()) {
                            textView.text = "Compartido con: ${names.joinToString(", ")}"
                        } else {
                            textView.text = "Compartido con: Usuarios no encontrados"
                        }
                    }
                }
                .addOnFailureListener { e ->
                    count++
                    Log.e("RemindersAdapter", "Error fetching user name for ID $userId: ${e.message}", e)
                    if (count == sharedUserIds.size) { // Si hay error en alguna, mostrar lo que se pudo
                        if (names.isNotEmpty()) {
                            textView.text = "Compartido con: ${names.joinToString(", ")}"
                        } else {
                            textView.text = "Compartido con: Error al cargar usuarios"
                        }
                    }
                }
        }
    }

    override fun getItemCount(): Int = reminders.size
}
