package com.example.memoloop

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Invitation(
    val id: String = "",
    val reminderId: String = "",
    val creatorId: String = "",
    val reminderTitle: String = "",
    val reminderTimestamp: Long = 0L,
    val reminderType: String = "",
    val reminderCategory: String = "",
    val sharedFromUserName: String = ""
)

class InvitationAdapter(
    private val invitations: List<Invitation>,
    private val listener: OnInvitationActionListener
) : RecyclerView.Adapter<InvitationAdapter.InvitationViewHolder>() {

    interface OnInvitationActionListener {
        fun onAcceptClick(invitation: Invitation)
        fun onRejectClick(invitation: Invitation)
    }

    class InvitationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tv_invitation_title)
        val tvSender: TextView = itemView.findViewById(R.id.tv_invitation_sender)
        val btnAccept: Button = itemView.findViewById(R.id.btn_accept_invitation)
        val btnReject: Button = itemView.findViewById(R.id.btn_reject_invitation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvitationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invitation, parent, false)
        return InvitationViewHolder(view)
    }

    override fun onBindViewHolder(holder: InvitationViewHolder, position: Int) {
        val invitation = invitations[position]
        holder.tvTitle.text = "Invitación: ${invitation.reminderTitle}"
        holder.tvSender.text = "De: ${invitation.sharedFromUserName}"

        holder.btnAccept.setOnClickListener { listener.onAcceptClick(invitation) }
        holder.btnReject.setOnClickListener { listener.onRejectClick(invitation) }
    }

    override fun getItemCount(): Int = invitations.size
}
