package app.fqrs.tglive.models

/**
 * Data class representing group call information
 * Requirements: 3.1, 3.2, 3.3
 */
data class GroupCallInfo(
    val id: Int,
    val title: String,
    val participantCount: Int,
    val isActive: Boolean,
    val canBeManaged: Boolean,
    val isJoined: Boolean,
    val inviteLink: String = "", // Add invite link to GroupCallInfo
)