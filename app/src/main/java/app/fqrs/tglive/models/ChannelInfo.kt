package app.fqrs.tglive.models

import org.drinkless.tdlib.TdApi

/**
 * Data class representing channel information
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5
 */
data class ChannelInfo(
    val id: Long,
    val title: String,
    val username: String,
    val description: String,
    val memberCount: Int,
    val photo: TdApi.ChatPhotoInfo?,
    val hasActiveVideoChat: Boolean,
    val activeVideoChatId: Int?
)