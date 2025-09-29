package app.fqrs.tglive.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.fqrs.tglive.R
import app.fqrs.tglive.models.GroupCallParticipant
import org.drinkless.tdlib.TdApi

/**
 * RecyclerView adapter for displaying group call participants
 * 
 * Features:
 * - Displays participant information in a grid format
 * - Shows real-time status indicators (Camera/Screen, Speaking, Muted)
 * - Handles participant join/leave animations
 * - Updates participant status in real-time
 * 
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5
 */
class ParticipantAdapter(
    private val context: Context
) : RecyclerView.Adapter<ParticipantAdapter.ParticipantViewHolder>() {

    private var participants = mutableListOf<GroupCallParticipant>()

    /**
     * ViewHolder for participant items
     * Requirements: 5.1 - participant grid with status columns
     */
    class ParticipantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profilePicture: ImageView = itemView.findViewById(R.id.ivParticipantPhoto)
        val username: TextView = itemView.findViewById(R.id.tvParticipantName)
        val cameraStatus: TextView = itemView.findViewById(R.id.tvCameraStatus)
        val speakingStatus: TextView = itemView.findViewById(R.id.tvSpeakingStatus)
        val mutedStatus: TextView = itemView.findViewById(R.id.tvMutedStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_participant, parent, false)
        return ParticipantViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParticipantViewHolder, position: Int) {
        val participant = participants[position]
        bindParticipant(holder, participant)
    }

    override fun getItemCount(): Int = participants.size

    /**
     * Bind participant data to ViewHolder
     * Requirements: 5.1, 5.2, 5.3, 5.4 - display participant status information
     */
    private fun bindParticipant(holder: ParticipantViewHolder, participant: GroupCallParticipant) {
        // Set username
        holder.username.text = participant.displayName

        // Set profile picture (placeholder for now)
        holder.profilePicture.setImageResource(R.drawable.ic_launcher_foreground)
        
        // Set camera/screen sharing status
        updateCameraStatus(holder.cameraStatus, participant.hasVideo, participant.isScreenSharing)
        
        // Set speaking status
        updateSpeakingStatus(holder.speakingStatus, participant.isSpeaking)
        
        // Set muted status
        updateMutedStatus(holder.mutedStatus, participant.isMuted)
    }

    /**
     * Update camera/screen sharing status indicator
     * Requirements: 5.4 - track video status changes
     */
    private fun updateCameraStatus(statusView: TextView, hasVideo: Boolean, isScreenSharing: Boolean) {
        when {
            isScreenSharing -> {
                statusView.text = "SCREEN"
                statusView.setTextColor(ContextCompat.getColor(context, R.color.status_active))
            }
            hasVideo -> {
                statusView.text = "CAM"
                statusView.setTextColor(ContextCompat.getColor(context, R.color.status_active))
            }
            else -> {
                statusView.text = "NO"
                statusView.setTextColor(ContextCompat.getColor(context, R.color.status_inactive))
            }
        }
    }

    /**
     * Update speaking status indicator
     * Requirements: 5.3 - track speaking status changes
     */
    private fun updateSpeakingStatus(statusView: TextView, isSpeaking: Boolean) {
        if (isSpeaking) {
            statusView.text = "YES"
            statusView.setTextColor(ContextCompat.getColor(context, R.color.status_active))
            // Add subtle animation for speaking indicator
            animateStatusChange(statusView)
        } else {
            statusView.text = "NO"
            statusView.setTextColor(ContextCompat.getColor(context, R.color.status_inactive))
        }
    }

    /**
     * Update muted status indicator
     * Requirements: 5.2 - track muted status changes
     */
    private fun updateMutedStatus(statusView: TextView, isMuted: Boolean) {
        if (isMuted) {
            statusView.text = "YES"
            statusView.setTextColor(ContextCompat.getColor(context, R.color.error))
        } else {
            statusView.text = "NO"
            statusView.setTextColor(ContextCompat.getColor(context, R.color.status_inactive))
        }
    }

    /**
     * Add subtle animation for status changes
     * Requirements: 5.5 - real-time status updates with visual feedback
     */
    private fun animateStatusChange(view: TextView) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 1.1f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 1.1f, 1.0f)
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY)
        animatorSet.duration = 200
        animatorSet.start()
    }

    /**
     * Update the entire participant list
     * Requirements: 5.5 - real-time participant updates
     */
    fun updateParticipants(newParticipants: List<GroupCallParticipant>) {
        val diffCallback = ParticipantDiffCallback(participants, newParticipants)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        participants.clear()
        participants.addAll(newParticipants)
        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * Add a new participant with animation
     * Requirements: 5.5 - handle participant join animations
     */
    fun addParticipant(participant: GroupCallParticipant) {
        val position = participants.size
        participants.add(participant)
        notifyItemInserted(position)
        
        // Animate the new item
        animateItemInsertion(position)
    }

    /**
     * Remove a participant with animation
     * Requirements: 5.5 - handle participant leave animations
     */
    fun removeParticipant(participantId: TdApi.MessageSender) {
        val position = participants.indexOfFirst { 
            areMessageSendersEqual(it.participantId, participantId) 
        }
        
        if (position != -1) {
            participants.removeAt(position)
            notifyItemRemoved(position)
            
            // Animate the removal
            animateItemRemoval(position)
        }
    }

    /**
     * Update a specific participant's status
     * Requirements: 5.2, 5.3, 5.4 - real-time status updates
     */
    fun updateParticipantStatus(updatedParticipant: GroupCallParticipant) {
        val position = participants.indexOfFirst { 
            areMessageSendersEqual(it.participantId, updatedParticipant.participantId) 
        }
        
        if (position != -1) {
            participants[position] = updatedParticipant
            notifyItemChanged(position)
        }
    }

    /**
     * Animate item insertion
     * Requirements: 5.5 - participant join animations
     */
    private fun animateItemInsertion(position: Int) {
        // Animation will be handled by RecyclerView's default item animator
        // Additional custom animations can be added here if needed
    }

    /**
     * Animate item removal
     * Requirements: 5.5 - participant leave animations
     */
    private fun animateItemRemoval(position: Int) {
        // Animation will be handled by RecyclerView's default item animator
        // Additional custom animations can be added here if needed
    }

    /**
     * Compare MessageSender objects for equality
     * Helper method to identify participants
     */
    private fun areMessageSendersEqual(sender1: TdApi.MessageSender, sender2: TdApi.MessageSender): Boolean {
        return when {
            sender1.constructor != sender2.constructor -> false
            sender1.constructor == TdApi.MessageSenderUser.CONSTRUCTOR -> {
                val user1 = sender1 as TdApi.MessageSenderUser
                val user2 = sender2 as TdApi.MessageSenderUser
                user1.userId == user2.userId
            }
            sender1.constructor == TdApi.MessageSenderChat.CONSTRUCTOR -> {
                val chat1 = sender1 as TdApi.MessageSenderChat
                val chat2 = sender2 as TdApi.MessageSenderChat
                chat1.chatId == chat2.chatId
            }
            else -> false
        }
    }

    /**
     * Get participant by ID
     * Helper method for external access
     */
    fun getParticipantById(participantId: TdApi.MessageSender): GroupCallParticipant? {
        return participants.find { areMessageSendersEqual(it.participantId, participantId) }
    }

    /**
     * Get all participants
     * Helper method for external access
     */
    fun getAllParticipants(): List<GroupCallParticipant> {
        return participants.toList()
    }

    /**
     * Clear all participants
     * Helper method for cleanup
     */
    fun clearParticipants() {
        val size = participants.size
        participants.clear()
        notifyItemRangeRemoved(0, size)
    }

    /**
     * DiffUtil callback for efficient list updates
     * Requirements: 5.5 - efficient real-time updates
     */
    private class ParticipantDiffCallback(
        private val oldList: List<GroupCallParticipant>,
        private val newList: List<GroupCallParticipant>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldParticipant = oldList[oldItemPosition]
            val newParticipant = newList[newItemPosition]
            
            return areMessageSendersEqual(oldParticipant.participantId, newParticipant.participantId)
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldParticipant = oldList[oldItemPosition]
            val newParticipant = newList[newItemPosition]
            
            return oldParticipant == newParticipant
        }

        /**
         * Compare MessageSender objects for equality
         */
        private fun areMessageSendersEqual(sender1: TdApi.MessageSender, sender2: TdApi.MessageSender): Boolean {
            return when {
                sender1.constructor != sender2.constructor -> false
                sender1.constructor == TdApi.MessageSenderUser.CONSTRUCTOR -> {
                    val user1 = sender1 as TdApi.MessageSenderUser
                    val user2 = sender2 as TdApi.MessageSenderUser
                    user1.userId == user2.userId
                }
                sender1.constructor == TdApi.MessageSenderChat.CONSTRUCTOR -> {
                    val chat1 = sender1 as TdApi.MessageSenderChat
                    val chat2 = sender2 as TdApi.MessageSenderChat
                    chat1.chatId == chat2.chatId
                }
                else -> false
            }
        }
    }
}