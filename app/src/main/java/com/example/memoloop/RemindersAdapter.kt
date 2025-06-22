package com.example.memoloop

import android.content.Intent
import android.util.Log // Se mantiene el import de Log para depuración
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton // Se importa ImageButton para el botón de opciones
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RemindersAdapter(
    private val reminders: List<Reminder>,
    private val clickListener: OnReminderOptionsClickListener,
    private val firestore: FirebaseFirestore
) : RecyclerView.Adapter<RemindersAdapter.ReminderViewHolder>() {

    interface OnReminderOptionsClickListener {
        fun onReminderOptionsClick(reminder: Reminder, anchorView: View)
    }

    class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.card_reminder)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_reminder_title)

        val ivCategoryIcon: ImageView = itemView.findViewById(R.id.iv_category_icon)
        val ivTypeIcon: ImageView = itemView.findViewById(R.id.iv_type_icon)
        val tvDate: TextView = itemView.findViewById(R.id.tv_reminder_date)
        val tvTime: TextView = itemView.findViewById(R.id.tv_reminder_time)
        val tvTypeLabel: TextView = itemView.findViewById(R.id.tv_type_label)
        val tvType: TextView = itemView.findViewById(R.id.tv_type)
        val tvCategoryLabel: TextView = itemView.findViewById(R.id.tv_category_label)
        val tvCategory: TextView = itemView.findViewById(R.id.tv_category)
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
        val context = holder.itemView.context
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        if (reminder.isShared && reminder.userId != currentUserId) {
            holder.tvTitle.text = context.getString(R.string.event_with_user, reminder.sharedFromUserName)
        } else {
            holder.tvTitle.text = reminder.title
        }
        val calendar = Calendar.getInstance().apply { timeInMillis = reminder.timestamp }
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        holder.tvDate.text = dateFormat.format(calendar.time)
        holder.tvTime.text = timeFormat.format(calendar.time)
        val categoryLower = reminder.category.lowercase(Locale.getDefault())

        val colorRes = when (categoryLower) {
            context.getString(R.string.reminder_category_salud_raw).lowercase(Locale.getDefault()) -> R.color.reminder_category_salud
            context.getString(R.string.reminder_category_deporte_raw).lowercase(Locale.getDefault()) -> R.color.reminder_category_deporte
            context.getString(R.string.reminder_category_ocio_raw).lowercase(Locale.getDefault()) -> R.color.reminder_category_ocio
            context.getString(R.string.reminder_category_estudio_raw).lowercase(Locale.getDefault()) -> R.color.reminder_category_estudio
            context.getString(R.string.reminder_category_general_raw).lowercase(Locale.getDefault()) -> R.color.reminder_category_general
            else -> R.color.reminder_category_general
        }
        holder.cardView.setCardBackgroundColor(context.getColor(colorRes))

        val iconCategoryRes = when (categoryLower) {
            context.getString(R.string.reminder_category_salud_raw).lowercase(Locale.getDefault()) -> R.drawable.ic_category_salud
            context.getString(R.string.reminder_category_deporte_raw).lowercase(Locale.getDefault()) -> R.drawable.ic_category_deporte
            context.getString(R.string.reminder_category_ocio_raw).lowercase(Locale.getDefault()) -> R.drawable.ic_category_ocio
            context.getString(R.string.reminder_category_estudio_raw).lowercase(Locale.getDefault()) -> R.drawable.ic_category_estudio
            context.getString(R.string.reminder_category_general_raw).lowercase(Locale.getDefault()) -> R.drawable.ic_category_general
            else -> R.drawable.ic_category_general
        }
        holder.ivCategoryIcon.setImageResource(iconCategoryRes)

        val iconTypeRes = when (reminder.type) {
            context.getString(R.string.reminder_type_occasional) -> R.drawable.ic_access_time
            context.getString(R.string.reminder_type_fixed),
            context.getString(R.string.reminder_type_daily),
            context.getString(R.string.reminder_type_weekly),
            context.getString(R.string.reminder_type_monthly),
            context.getString(R.string.reminder_type_annual) -> R.drawable.ic_date_range
            else -> R.drawable.ic_date_range
        }
        holder.ivTypeIcon.setImageResource(iconTypeRes)

        holder.tvTypeLabel.text = context.getString(R.string.frequency_label_short)
        holder.tvType.text = reminder.type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        holder.tvCategoryLabel.text = context.getString(R.string.category_label_short)
        holder.tvCategory.text = reminder.category.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

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
        holder.cardView.setOnClickListener {
            val intent = Intent(context, ReminderDetailActivity::class.java).apply {
                putExtra("REMINDER_ID", reminder.id)
                putExtra("title", reminder.title)
                putExtra("category", reminder.category)
                putExtra("type", reminder.type)
                putExtra("timestamp", reminder.timestamp)
                putExtra("latitude", reminder.latitude ?: Double.NaN)
                putExtra("longitude", reminder.longitude ?: Double.NaN)
                putExtra("imageUrl", reminder.imageUrl)
                putExtra("isShared", reminder.isShared)
                putExtra("sharedFromUserName", reminder.sharedFromUserName)
            }
            context.startActivity(intent)
        }
    }
    private fun fetchSharedUserNames(sharedUserIds: List<String>, textView: TextView) {
        if (sharedUserIds.isEmpty()) {
            textView.visibility = View.GONE
            return
        }

        val names = mutableListOf<String>()
        var count = 0
        val context = textView.context

        textView.text = context.getString(R.string.shared_with_loading)
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
                            textView.text = context.getString(R.string.shared_with_users, names.joinToString(", "))
                        } else {
                            textView.text = context.getString(R.string.shared_with_users_not_found)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    count++
                    Log.e("RemindersAdapter", "Error fetching user name for ID $userId: ${e.message}", e)
                    if (count == sharedUserIds.size) {
                        if (names.isNotEmpty()) {
                            textView.text = context.getString(R.string.shared_with_some_errors, names.joinToString(", "))
                        } else {
                            textView.text = context.getString(R.string.shared_with_error_loading)
                        }
                    }
                }
        }
    }
    override fun getItemCount(): Int = reminders.size
}
