package com.example.memoloop

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RemindersAdapter(
    private val reminders: List<Reminder>,
    private val clickListener: OnReminderOptionsClickListener
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
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminder = reminders[position]
        val context = holder.itemView.context

        holder.tvTitle.text = reminder.title

        val calendar = Calendar.getInstance().apply { timeInMillis = reminder.timestamp }
        val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val categoryLower = reminder.category.lowercase(Locale.getDefault())

        val colorRes = when (categoryLower) {
            "salud" -> R.color.reminder_category_salud
            "deporte" -> R.color.reminder_category_deporte
            "ocio" -> R.color.reminder_category_ocio
            "estudio" -> R.color.reminder_category_estudio
            "general" -> R.color.reminder_category_general
            else -> R.color.reminder_category_general
        }

        val iconRes = when (categoryLower) {
            "salud" -> R.drawable.ic_category_salud
            "deporte" -> R.drawable.ic_category_deporte
            "ocio" -> R.drawable.ic_category_ocio
            "estudio" -> R.drawable.ic_category_estudio
            "general" -> R.drawable.ic_category_general
            else -> R.drawable.ic_category_general
        }

        val iconFrequency = when (reminder.frequency) {
            "Eventual" -> R.drawable.ic_access_time
            else -> R.drawable.ic_date_range
        }



        holder.cardView.setCardBackgroundColor(context.getColor(colorRes))
        holder.ivCategoryIcon.setImageResource(iconRes)
        holder.ivTypeIcon.setImageResource(iconFrequency)

        holder.tvDate.text = "${dateFormat.format(calendar.time)}"
        holder.tvTime.text = "${timeFormat.format(calendar.time)}"

        holder.tvTypeLabel.text = "FRECUENCIA"
        holder.tvType.text = reminder.frequency.replaceFirstChar { it.uppercaseChar() }

        holder.tvCategoryLabel.text = "TIPO"
        holder.tvCategory.text = reminder.category.replaceFirstChar { it.uppercaseChar() }

        holder.cardView.setOnClickListener {
            val intent = Intent(context, ReminderDetailActivity::class.java).apply {
                putExtra("title", reminder.title)
                putExtra("category", reminder.category)
                putExtra("frequency", reminder.frequency)
                putExtra("timestamp", reminder.timestamp)
                putExtra("REMINDER_ID", reminder.id)
            }
            context.startActivity(intent)
        }


    }


    override fun getItemCount(): Int = reminders.size
}