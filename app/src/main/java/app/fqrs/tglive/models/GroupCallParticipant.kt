package app.fqrs.tglive.models

import org.drinkless.tdlib.TdApi

/**
 * Data class representing a group call participant
 * Requirements: 5.1 - participant grid with status information
 */
data class GroupCallParticipant(
    val participantId: TdApi.MessageSender,
    val displayName: String,
    val profilePhoto: TdApi.ProfilePhoto?,
    val isMuted: Boolean,
    val isSpeaking: Boolean,
    val hasVideo: Boolean,
    val isScreenSharing: Boolean,
    val joinedTimestamp: Int
)