package com.example.memoloop

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
        val ivTypeIcon: ImageView = itemView.findViewById(R.id.iv_type_icon)
        val ivFrequencyIcon: ImageView = itemView.findViewById(R.id.iv_frequency_icon)
        val tvDate: TextView = itemView.findViewById(R.id.tv_reminder_date)
        val tvTime: TextView = itemView.findViewById(R.id.tv_reminder_time)
        val tvFrequencyLabel: TextView = itemView.findViewById(R.id.tv_frequency_label)
        val tvFrequency: TextView = itemView.findViewById(R.id.tv_frequency)
        val tvTypeLabel: TextView = itemView.findViewById(R.id.tv_type_label)
        val tvType: TextView = itemView.findViewById(R.id.tv_type)
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

        val typeLower = reminder.type.lowercase(Locale.getDefault())

        val colorRes = when (typeLower) {
            "salud" -> R.color.reminder_type_salud
            "deporte" -> R.color.reminder_type_deporte
            "ocio" -> R.color.reminder_type_ocio
            "estudio" -> R.color.reminder_type_estudio
            "general" -> R.color.reminder_type_general
            else -> R.color.reminder_type_general
        }

        val iconRes = when (typeLower) {
            "salud" -> R.drawable.ic_type_salud
            "deporte" -> R.drawable.ic_type_deporte
            "ocio" -> R.drawable.ic_type_ocio
            "estudio" -> R.drawable.ic_type_estudio
            "general" -> R.drawable.ic_type_general
            else -> R.drawable.ic_type_general
        }

        val iconFrequency = when (reminder.frequency) {
            "Eventual" -> R.drawable.ic_access_time
            else -> R.drawable.ic_date_range
        }



        holder.cardView.setCardBackgroundColor(context.getColor(colorRes))
        holder.ivTypeIcon.setImageResource(iconRes)
        holder.ivFrequencyIcon.setImageResource(iconFrequency)

        holder.tvDate.text = "${dateFormat.format(calendar.time)}"
        holder.tvTime.text = "${timeFormat.format(calendar.time)}"

        holder.tvFrequencyLabel.text = "FRECUENCIA"
        holder.tvFrequency.text = reminder.frequency.replaceFirstChar { it.uppercaseChar() }

        holder.tvTypeLabel.text = "TIPO"
        holder.tvType.text = reminder.type.replaceFirstChar { it.uppercaseChar() }

    }


    override fun getItemCount(): Int = reminders.size
}