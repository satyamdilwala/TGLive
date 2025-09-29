package app.fqrs.tglive.telegram

import app.fqrs.tglive.models.ChannelError
import app.fqrs.tglive.models.ChannelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

/**
 * Manager class for channel operations
 * 
 * This class provides functionality to:
 * - Resolve channel usernames to channel information
 * - Validate that targets are public channels (not private channels, groups, or users)
 * - Retrieve detailed channel information including member counts and descriptions
 * - Handle video chat status detection
 * 
 * Usage:
 * ```kotlin
 * val channelManager = ChannelManager(telegramClient)
 * try {
 *     val channelInfo = channelManager.getChannelByUsername("@telegram")
 *     println("Channel: ${channelInfo.title} with ${channelInfo.memberCount} members")
 * } catch (e: ChannelError) {
 *     println("Error: ${e.message}")
 * }
 * ```
 * 
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 7.1, 7.2, 7.3, 7.4
 */
class ChannelManager(private val client: TelegramClient) {
    
    /**
     * Get channel information by username
     * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 7.1, 7.2, 7.3, 7.4
     */
    suspend fun getChannelByUsername(username: String): ChannelInfo {
        return withContext(Dispatchers.IO) {
            // Validate username format
            val cleanUsername = validateAndCleanUsername(username)
            
            try {
                println("TGLIVE: Searching for channel: $cleanUsername")
                
                // Search for the public chat using TdApi.SearchPublicChat
                val result = client.send(TdApi.SearchPublicChat(cleanUsername))
                
                when (result.constructor) {
                    TdApi.Chat.CONSTRUCTOR -> {
                        val chat = result as TdApi.Chat
                        println("TGLIVE: Found chat: ${chat.title} (ID: ${chat.id})")
                        
                        // Validate that this is a public channel
                        validateIsPublicChannel(chat)
                        
                        // Get fresh full information including description and member count
                        getChannelFullInfo(chat.id)
                    }
                    TdApi.Error.CONSTRUCTOR -> {
                        val error = result as TdApi.Error
                        println("TGLIVE: Search error: ${error.code} - ${error.message}")
                        handleTdLibError(error)
                    }
                    else -> {
                        println("TGLIVE: Unexpected result type: ${result.javaClass.simpleName}")
                        throw ChannelError.ChannelNotFound
                    }
                }
            } catch (e: ChannelError) {
                throw e
            } catch (e: Exception) {
                println("TGLIVE: Exception in getChannelByUsername: ${e.message}")
                e.printStackTrace()
                throw ChannelError.NetworkError
            }
        }
    }
    
    /**
     * Get detailed channel information
     * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5
     */
    suspend fun getChannelFullInfo(channelId: Long): ChannelInfo {
        return withContext(Dispatchers.IO) {
            try {
                println("TGLIVE: Getting full info for channel ID: $channelId")
                
                // Get the chat first - this gets fresh data from server
                val chatResult = client.send(TdApi.GetChat(channelId))
                
                when (chatResult.constructor) {
                    TdApi.Chat.CONSTRUCTOR -> {
                        val chat = chatResult as TdApi.Chat
                        println("TGLIVE: Got chat info: ${chat.title}")
                        
                        // Validate that this is a public channel
                        validateIsPublicChannel(chat)
                        
                        // Get supergroup full info for additional details like description and member count
                        val supergroupId = (chat.type as TdApi.ChatTypeSupergroup).supergroupId
                        println("TGLIVE: Getting supergroup full info for ID: $supergroupId")
                        
                        val fullInfoResult = client.send(TdApi.GetSupergroupFullInfo(supergroupId))
                        
                        when (fullInfoResult.constructor) {
                            TdApi.SupergroupFullInfo.CONSTRUCTOR -> {
                                val fullInfo = fullInfoResult as TdApi.SupergroupFullInfo
                                println("TGLIVE: Got full info - Description: '${fullInfo.description}', Members: ${fullInfo.memberCount}")
                                convertChatToChannelInfo(chat, fullInfo)
                            }
                            TdApi.Error.CONSTRUCTOR -> {
                                val error = fullInfoResult as TdApi.Error
                                println("TGLIVE: Full info error: ${error.code} - ${error.message}")
                                // Don't fail completely, use basic info
                                convertChatToChannelInfo(chat)
                            }
                            else -> {
                                println("TGLIVE: Unexpected full info result: ${fullInfoResult.javaClass.simpleName}")
                                // Fallback to basic info if full info fails
                                convertChatToChannelInfo(chat)
                            }
                        }
                    }
                    TdApi.Error.CONSTRUCTOR -> {
                        val error = chatResult as TdApi.Error
                        println("TGLIVE: Chat error: ${error.code} - ${error.message}")
                        handleTdLibError(error)
                    }
                    else -> {
                        println("TGLIVE: Unexpected chat result: ${chatResult.javaClass.simpleName}")
                        throw ChannelError.ChannelNotFound
                    }
                }
            } catch (e: ChannelError) {
                throw e
            } catch (e: Exception) {
                println("TGLIVE: Exception in getChannelFullInfo: ${e.message}")
                e.printStackTrace()
                throw ChannelError.NetworkError
            }
        }
    }
    
    /**
     * Validate and clean username format
     * Requirements: 7.4 - handle invalid usernames
     */
    private fun validateAndCleanUsername(username: String): String {
        if (username.isBlank()) {
            throw ChannelError.InvalidUsername
        }
        
        // Remove @ prefix if present
        val cleanUsername = username.removePrefix("@").trim()
        
        // Validate username format
        if (cleanUsername.isEmpty()) {
            throw ChannelError.InvalidUsername
        }
        
        // Telegram username requirements:
        // - Must be at least 5 characters long
        // - Can contain a-z, 0-9 and underscores
        // - Must start with a letter
        // - Must end with a letter or number
        // - Cannot have two consecutive underscores
        if (cleanUsername.length < 5 || cleanUsername.length > 32) {
            throw ChannelError.InvalidUsername
        }
        
        // Must start with a letter
        if (!cleanUsername.first().isLetter()) {
            throw ChannelError.InvalidUsername
        }
        
        // Must end with a letter or number
        if (!cleanUsername.last().isLetterOrDigit()) {
            throw ChannelError.InvalidUsername
        }
        
        // Check for valid characters only
        if (!cleanUsername.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*[a-zA-Z0-9]$"))) {
            throw ChannelError.InvalidUsername
        }
        
        // Check for consecutive underscores
        if (cleanUsername.contains("__")) {
            throw ChannelError.InvalidUsername
        }
        
        return cleanUsername
    }
    
    /**
     * Validate that the chat is a public channel
     * Requirements: 7.1, 7.2, 7.3 - ensure only public channels are processed
     */
    private fun validateIsPublicChannel(chat: TdApi.Chat) {
        when (chat.type.constructor) {
            TdApi.ChatTypeSupergroup.CONSTRUCTOR -> {
                val supergroup = chat.type as TdApi.ChatTypeSupergroup
                if (!supergroup.isChannel) {
                    throw ChannelError.NotAChannel
                }
                // Note: We can't easily check if it's public from the Chat object alone
                // The fact that SearchPublicChat found it indicates it's public
            }
            TdApi.ChatTypePrivate.CONSTRUCTOR -> {
                throw ChannelError.NotAChannel
            }
            TdApi.ChatTypeBasicGroup.CONSTRUCTOR -> {
                throw ChannelError.NotAChannel
            }
            TdApi.ChatTypeSecret.CONSTRUCTOR -> {
                throw ChannelError.NotAChannel
            }
            else -> {
                throw ChannelError.NotAChannel
            }
        }
    }
    
    /**
     * Convert TdApi.Chat to ChannelInfo
     * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5
     */
    private suspend fun convertChatToChannelInfo(
        chat: TdApi.Chat, 
        fullInfo: TdApi.SupergroupFullInfo? = null
    ): ChannelInfo {
        // Extract username from supergroup info
        val username = extractUsername(chat)
        
        // Get description from full info - handle empty/null descriptions properly
        val description = fullInfo?.description?.takeIf { it.isNotBlank() } ?: ""
        
        // Check for active video chat following Telegram's approach
        // In TDLib, videoChat exists if there's a group call, but we need to check if it's actually active
        val hasActiveVideoChat = chat.videoChat != null && chat.videoChat.groupCallId != 0
        val activeVideoChatId = if (hasActiveVideoChat) chat.videoChat.groupCallId else null
        
        println("TGLIVE: Converting chat to ChannelInfo:")
        println("  Title: ${chat.title}")
        println("  Username: $username")
        println("  Description: '$description' (length: ${description.length})")
        println("  Member count: ${fullInfo?.memberCount ?: 0}")
        println("  Has video chat: $hasActiveVideoChat")
        println("  Video chat ID: $activeVideoChatId")
        println("  Photo: ${chat.photo != null}")
        
        return ChannelInfo(
            id = chat.id,
            title = chat.title,
            username = username,
            description = description,
            memberCount = fullInfo?.memberCount ?: 0,
            photo = chat.photo,
            hasActiveVideoChat = hasActiveVideoChat,
            activeVideoChatId = activeVideoChatId
        )
    }
    
    /**
     * Extract username from chat object by getting supergroup info
     */
    private suspend fun extractUsername(chat: TdApi.Chat): String {
        return when (chat.type.constructor) {
            TdApi.ChatTypeSupergroup.CONSTRUCTOR -> {
                try {
                    val supergroup = chat.type as TdApi.ChatTypeSupergroup
                    val supergroupResult = client.send(TdApi.GetSupergroup(supergroup.supergroupId))
                    
                    when (supergroupResult.constructor) {
                        TdApi.Supergroup.CONSTRUCTOR -> {
                            val supergroupInfo = supergroupResult as TdApi.Supergroup
                            supergroupInfo.usernames?.activeUsernames?.firstOrNull() ?: ""
                        }
                        else -> ""
                    }
                } catch (e: Exception) {
                    // If we can't get the username, return empty string
                    ""
                }
            }
            else -> ""
        }
    }
    
    /**
     * Handle TDLib errors and convert to ChannelError
     * Requirements: 7.1, 7.2, 7.3, 7.4
     */
    private fun handleTdLibError(error: TdApi.Error): Nothing {
        when (error.code) {
            400 -> {
                when {
                    error.message.contains("USERNAME_NOT_OCCUPIED") -> throw ChannelError.ChannelNotFound
                    error.message.contains("USERNAME_INVALID") -> throw ChannelError.InvalidUsername
                    error.message.contains("CHAT_NOT_FOUND") -> throw ChannelError.ChannelNotFound
                    error.message.contains("SUPERGROUP_NOT_FOUND") -> throw ChannelError.ChannelNotFound
                    error.message.contains("CHAT_ADMIN_REQUIRED") -> throw ChannelError.PrivateChannel
                    else -> throw ChannelError.TdLibError(error.code, error.message)
                }
            }
            401 -> throw ChannelError.PrivateChannel // Unauthorized access
            403 -> throw ChannelError.PrivateChannel // Forbidden access
            404 -> throw ChannelError.ChannelNotFound // Not found
            429 -> throw ChannelError.NetworkError // Rate limited
            500, 502, 503, 504 -> throw ChannelError.NetworkError // Server errors
            else -> throw ChannelError.TdLibError(error.code, error.message)
        }
    }
    
    /**
     * Check if a channel has an active video chat
     * Requirements: 3.1, 3.2 - video chat status detection
     */
    suspend fun hasActiveVideoChat(channelId: Long): Boolean {
        return try {
            val channelInfo = getChannelFullInfo(channelId)
            channelInfo.hasActiveVideoChat
        } catch (e: ChannelError) {
            false
        }
    }
    
    /**
     * Get the active video chat ID for a channel
     * Requirements: 3.1, 3.2 - video chat information
     */
    suspend fun getActiveVideoChatId(channelId: Long): Int? {
        return try {
            val channelInfo = getChannelFullInfo(channelId)
            channelInfo.activeVideoChatId
        } catch (e: ChannelError) {
            null
        }
    }
}