package app.fqrs.tglive.models

/**
 * Sealed class for channel update events
 * Requirements: 6.3, 6.4 - real-time channel updates
 */
sealed class ChannelUpdate {
    data class InfoChanged(val channelInfo: ChannelInfo) : ChannelUpdate()
    data class MemberCountChanged(val newCount: Int) : ChannelUpdate()
    data class VideoChatStarted(val groupCallId: Int) : ChannelUpdate()
    object VideoChatEnded : ChannelUpdate()
}