package app.fqrs.tglive.models

import org.drinkless.tdlib.TdApi

/**
 * Sealed class for group call update events
 * Requirements: 5.2, 5.3, 5.4, 5.5, 6.5 - real-time participant updates
 */
sealed class GroupCallUpdate {
    data class ParticipantJoined(val participant: GroupCallParticipant) : GroupCallUpdate()
    data class ParticipantLeft(val participantId: TdApi.MessageSender) : GroupCallUpdate()
    data class ParticipantStatusChanged(val participant: GroupCallParticipant) : GroupCallUpdate()
    data class StatusChanged(val groupCallInfo: GroupCallInfo) : GroupCallUpdate()
    object CallEnded : GroupCallUpdate()
}