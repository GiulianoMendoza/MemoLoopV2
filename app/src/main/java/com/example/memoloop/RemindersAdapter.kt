package com.example.memoloop

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class RemindersAdapter(
    private val reminders: List<Reminder>,
    private val clickListener: OnReminderOptionsClickListener
) : RecyclerView.Adapter<RemindersAdapter.ReminderViewHolder>() {

    interface OnReminderOptionsClickListener {
        fun onReminderOptionsClick(reminder: Reminder, anchorView: View)
    }

    class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tv_reminder_title)
        val tvDateTime: TextView = itemView.findViewById(R.id.tv_reminder_datetime)
        val tvDetails: TextView = itemView.findViewById(R.id.tv_reminder_details)
        val btnOptions: ImageButton = itemView.findViewById(R.id.btn_options)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminder = reminders[position]

        holder.tvTitle.text = reminder.title
        val date = Calendar.getInstance().apply { timeInMillis = reminder.timestamp }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.tvDateTime.text = "Fecha: ${dateFormat.format(date.time)}"
        holder.tvDetails.text = "Tipo: ${reminder.type} - Categoría: ${reminder.category}"

        holder.btnOptions.setOnClickListener {
            clickListener.onReminderOptionsClick(reminder, it)
        }
    }

    override fun getItemCount(): Int = reminders.size
}
