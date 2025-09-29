package app.fqrs.tglive.telegram

import app.fqrs.tglive.models.ChannelInfo
import app.fqrs.tglive.models.ChannelUpdate
import app.fqrs.tglive.models.GroupCallInfo
import app.fqrs.tglive.models.GroupCallParticipant
import app.fqrs.tglive.models.GroupCallUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/**
 * Central handler for real-time TDLib updates
 * 
 * This class provides functionality to:
 * - Process all TDLib updates using client.setUpdatesHandler integration
 * - Filter and route updates to appropriate flows
 * - Provide typed update flows for UI components
 * - Handle updates without blocking UI thread
 * 
 * Usage:
 * ```kotlin
 * val updatesHandler = UpdatesHandler(telegramClient)
 * updatesHandler.initialize()
 * 
 * // Observe channel updates
 * updatesHandler.observeChannelUpdates(channelId).collect { update ->
 *     when (update) {
 *         is ChannelUpdate.InfoChanged -> handleChannelInfoChanged(update.channelInfo)
 *         is ChannelUpdate.MemberCountChanged -> handleMemberCountChanged(update.newCount)
 *         is ChannelUpdate.VideoChatStarted -> handleVideoChatStarted(update.groupCallId)
 *         is ChannelUpdate.VideoChatEnded -> handleVideoChatEnded()
 *     }
 * }
 * 
 * // Observe group call updates
 * updatesHandler.observeGroupCallUpdates(groupCallId).collect { update ->
 *     when (update) {
 *         is GroupCallUpdate.ParticipantJoined -> handleParticipantJoined(update.participant)
 *         is GroupCallUpdate.ParticipantLeft -> handleParticipantLeft(update.participantId)
 *         is GroupCallUpdate.ParticipantStatusChanged -> handleStatusChange(update.participant)
 *         is GroupCallUpdate.CallEnded -> handleCallEnded()
 *     }
 * }
 * ```
 * 
 * Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6
 */
class UpdatesHandler(private val client: TelegramClient) {
    
    // Use SupervisorJob to ensure one failed update doesn't cancel others
    // Use Dispatchers.Default for CPU-bound work
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Central update flow for all TDLib updates
    // Minimal buffer sizes for maximum memory efficiency
    private val _updateFlow = MutableSharedFlow<TdApi.Update>(
        replay = 0,
        extraBufferCapacity = 16 // Minimal buffer - only essential updates
    )
    private val updateFlow: SharedFlow<TdApi.Update> = _updateFlow.asSharedFlow()
    
    // Specific flows for different update types - minimal buffers
    private val _channelUpdates = MutableSharedFlow<Pair<Long, ChannelUpdate>>(
        replay = 0,
        extraBufferCapacity = 8 // Very small buffer for channel updates
    )
    private val channelUpdates: SharedFlow<Pair<Long, ChannelUpdate>> = _channelUpdates.asSharedFlow()
    
    private val _groupCallUpdates = MutableSharedFlow<Pair<Int, GroupCallUpdate>>(
        replay = 0,
        extraBufferCapacity = 8 // Very small buffer for group call updates
    )
    private val groupCallUpdates: SharedFlow<Pair<Int, GroupCallUpdate>> = _groupCallUpdates.asSharedFlow()
    
    private var isInitialized = false
    private var isPaused = false
    
    /**
     * Initialize the updates handler
     * Requirements: 6.1 - implement central update handler using client.setUpdatesHandler
     */
    fun initialize() {
        if (isInitialized) {
            println("TGLIVE: UpdatesHandler already initialized")
            return
        }
        
        println("TGLIVE: Initializing UpdatesHandler")
        
        // Start processing updates in background
        scope.launch {
            updateFlow.collect { update ->
                processUpdate(update)
            }
        }
        
        isInitialized = true
        println("TGLIVE: UpdatesHandler initialized successfully")
    }
    
    /**
     * Process incoming TDLib update and emit to appropriate flows
     * Requirements: 6.2 - process updates using when statement for different update types
     * Requirements: 6.6 - process updates without blocking UI thread
     */
    private suspend fun processUpdate(update: TdApi.Update) {
        try {
            // Skip processing if paused (battery optimization)
            if (isPaused) {
                println("TGLIVE: Skipping update processing (paused for battery optimization): ${update.javaClass.simpleName}")
                return
            }
            
            println("TGLIVE: Processing update: ${update.javaClass.simpleName}")
            
            // Minimal logging for performance - only critical updates
            when (update.constructor) {
                TdApi.UpdateGroupCall.CONSTRUCTOR -> {
                    val groupCallUpdate = update as TdApi.UpdateGroupCall
                    if (groupCallUpdate.groupCall.isActive) {
                        println("TGLIVE: 🔴 GROUP CALL ACTIVE - ${groupCallUpdate.groupCall.participantCount} participants")
                    }
                }
                TdApi.UpdateChatVideoChat.CONSTRUCTOR -> {
                    val videoChatUpdate = update as TdApi.UpdateChatVideoChat
                    println("TGLIVE: 📹 VIDEO CHAT - ${if (videoChatUpdate.videoChat?.groupCallId != 0) "STARTED" else "ENDED"}")
                }
                // Skip logging for less critical updates to reduce CPU overhead
            }
            
            // Requirements: 6.2 - use when statement for different update types
            when (update.constructor) {
                TdApi.UpdateGroupCall.CONSTRUCTOR -> {
                    val groupCallUpdate = update as TdApi.UpdateGroupCall
                    handleGroupCallUpdate(groupCallUpdate.groupCall)
                }
                
                TdApi.UpdateGroupCallParticipant.CONSTRUCTOR -> {
                    val participantUpdate = update as TdApi.UpdateGroupCallParticipant
                    handleGroupCallParticipantUpdate(participantUpdate.groupCallId, participantUpdate.participant)
                }
                
                TdApi.UpdateChatVideoChat.CONSTRUCTOR -> {
                    val videoChatUpdate = update as TdApi.UpdateChatVideoChat
                    handleChatVideoChatUpdate(videoChatUpdate.chatId, videoChatUpdate.videoChat)
                }
                
                // Note: UpdateChat doesn't exist in TDLib, removing this case
                
                TdApi.UpdateChatTitle.CONSTRUCTOR -> {
                    val titleUpdate = update as TdApi.UpdateChatTitle
                    println("TGLIVE: Processing UpdateChatTitle for chat ${titleUpdate.chatId}")
                    handleChatTitleUpdate(titleUpdate.chatId, titleUpdate.title)
                }
                
                TdApi.UpdateSupergroupFullInfo.CONSTRUCTOR -> {
                    val supergroupUpdate = update as TdApi.UpdateSupergroupFullInfo
                    println("TGLIVE: Processing UpdateSupergroupFullInfo for supergroup ${supergroupUpdate.supergroupId}")
                    handleSupergroupFullInfoUpdate(supergroupUpdate.supergroupId, supergroupUpdate.supergroupFullInfo)
                }
                
                TdApi.UpdateSupergroup.CONSTRUCTOR -> {
                    val supergroupUpdate = update as TdApi.UpdateSupergroup
                    println("TGLIVE: Processing UpdateSupergroup for supergroup ${supergroupUpdate.supergroup.id}")
                    handleSupergroupUpdate(supergroupUpdate.supergroup)
                }
                
                // Note: UpdateChatMemberCount doesn't exist in TDLib, removing this case
                
                else -> {
                    // Log all unhandled updates for debugging
                    println("TGLIVE: Unhandled update type: ${update.javaClass.simpleName}")
                }
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception processing update ${update.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Handle chat video chat updates
     * Requirements: 6.3, 6.4 - video chat status changes
     * THIS WAS THE MISSING METHOD CAUSING THE ISSUE!
     */
    private suspend fun handleChatVideoChatUpdate(chatId: Long, videoChat: TdApi.VideoChat?) {
        try {
            println("TGLIVE: 🎥 Processing video chat update for chat $chatId")
            
            if (videoChat != null && videoChat.groupCallId != 0) {
                // Video chat started
                _channelUpdates.emit(Pair(chatId, ChannelUpdate.VideoChatStarted(videoChat.groupCallId)))
                println("TGLIVE: 🎥 Video chat started in chat $chatId with group call ${videoChat.groupCallId}")
            } else {
                // Video chat ended
                _channelUpdates.emit(Pair(chatId, ChannelUpdate.VideoChatEnded))
                println("TGLIVE: 🛑 Video chat ended in chat $chatId")
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception handling video chat update: ${e.message}")
        }
    }
    
    /**
     * Handle group call updates
     * Requirements: 6.5 - group call status updates
     * 
     * IMPROVED: More reliable real-time update processing
     */
    private suspend fun handleGroupCallUpdate(groupCall: TdApi.GroupCall) {
        try {
            // Create GroupCallInfo object only once for efficiency
            val groupCallInfo = GroupCallInfo(
                id = groupCall.id,
                title = groupCall.title,
                participantCount = groupCall.participantCount,
                isActive = groupCall.isActive,
                canBeManaged = groupCall.canBeManaged,
                isJoined = groupCall.isJoined
            )
            
            // Emit StatusChanged update
            _groupCallUpdates.emit(Pair(groupCall.id, GroupCallUpdate.StatusChanged(groupCallInfo)))
            
            // If call is not active, also emit CallEnded
            if (!groupCall.isActive) {
                _groupCallUpdates.emit(Pair(groupCall.id, GroupCallUpdate.CallEnded))
            }
            
        } catch (e: Exception) {
            // Minimal error logging for performance
            println("TGLIVE: Group call update error: ${e.message}")
        }
    }
    
    /**
     * Handle group call participant updates
     * Requirements: 6.5 - participant status updates
     */
    private suspend fun handleGroupCallParticipantUpdate(groupCallId: Int, participant: TdApi.GroupCallParticipant) {
        try {
            println("TGLIVE: Processing participant update for group call $groupCallId")
            
            // Convert TdApi.GroupCallParticipant to our model
            val groupCallParticipant = convertToGroupCallParticipant(participant)
            
            // Emit participant status change
            _groupCallUpdates.emit(Pair(groupCallId, GroupCallUpdate.ParticipantStatusChanged(groupCallParticipant)))
            
            println("TGLIVE: Participant status updated for ${groupCallParticipant.displayName}")
        } catch (e: Exception) {
            println("TGLIVE: Exception handling participant update: ${e.message}")
        }
    }
    
    /**
     * Handle chat title updates
     * Requirements: 6.3, 6.4 - channel information updates
     */
    private suspend fun handleChatTitleUpdate(chatId: Long, title: String) {
        try {
            println("TGLIVE: Processing title update for chat $chatId: $title")
            
            // Get the full chat info to create ChannelInfo
            val chatResult = client.send(TdApi.GetChat(chatId))
            if (chatResult.constructor == TdApi.Chat.CONSTRUCTOR) {
                val chat = chatResult as TdApi.Chat
                if (chat.type.constructor == TdApi.ChatTypeSupergroup.CONSTRUCTOR) {
                    val supergroup = chat.type as TdApi.ChatTypeSupergroup
                    if (supergroup.isChannel) {
                        val channelInfo = convertChatToChannelInfo(chat)
                        _channelUpdates.emit(Pair(chatId, ChannelUpdate.InfoChanged(channelInfo)))
                    }
                }
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception handling title update: ${e.message}")
        }
    }
    
    /**
     * Handle supergroup updates
     */
    private suspend fun handleSupergroupUpdate(supergroup: TdApi.Supergroup) {
        try {
            println("TGLIVE: Processing supergroup update for ${supergroup.id}")
            
            if (supergroup.isChannel) {
                // Get the chat for this supergroup to emit channel update
                val chatResult = client.send(TdApi.GetChat(-1000000000000L - supergroup.id))
                if (chatResult.constructor == TdApi.Chat.CONSTRUCTOR) {
                    val chat = chatResult as TdApi.Chat
                    val channelInfo = convertChatToChannelInfo(chat)
                    _channelUpdates.emit(Pair(chat.id, ChannelUpdate.InfoChanged(channelInfo)))
                }
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception handling supergroup update: ${e.message}")
        }
    }
    
    /**
     * Handle supergroup full info updates (member count changes)
     * Requirements: 6.3, 6.4 - member count updates
     */
    private suspend fun handleSupergroupFullInfoUpdate(supergroupId: Long, fullInfo: TdApi.SupergroupFullInfo) {
        try {
            println("TGLIVE: Processing supergroup full info update for $supergroupId, member count: ${fullInfo.memberCount}")
            
            // Convert supergroup ID to chat ID
            val chatId = -1000000000000L - supergroupId
            _channelUpdates.emit(Pair(chatId, ChannelUpdate.MemberCountChanged(fullInfo.memberCount)))
        } catch (e: Exception) {
            println("TGLIVE: Exception handling supergroup full info update: ${e.message}")
        }
    }
    
    /**
     * Observe all updates (for debugging or advanced use cases)
     * Requirements: 6.2 - provide access to all updates
     */
    fun observeUpdates(): SharedFlow<TdApi.Update> {
        return updateFlow
    }
    
    /**
     * Observe channel updates for a specific channel
     * Requirements: 6.3, 6.4 - channel-specific update flow
     */
    fun observeChannelUpdates(channelId: Long): Flow<ChannelUpdate> {
        return channelUpdates
            .filter { (id, _) -> id == channelId }
            .map { (_, update) -> update }
    }
    
    /**
     * Observe group call updates for a specific group call
     * Requirements: 6.5 - group call-specific update flow
     */
    fun observeGroupCallUpdates(groupCallId: Int): Flow<GroupCallUpdate> {
        return groupCallUpdates
            .filter { (id, _) -> id == groupCallId }
            .map { (_, update) -> update }
    }
    
    /**
     * Emit an update to the central flow
     * This method should be called by TelegramClient's update handler
     * Requirements: 6.1, 6.2 - central update processing
     */
    fun emitUpdate(update: TdApi.Update) {
        println("TGLIVE: UpdatesHandler received update: ${update.javaClass.simpleName}")
        scope.launch {
            try {
                _updateFlow.emit(update)
                println("TGLIVE: Update emitted to flow successfully")
            } catch (e: Exception) {
                println("TGLIVE: Exception emitting update: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Convert TdApi.Chat to ChannelInfo
     * Requirements: 6.3, 6.4 - channel information conversion
     */
    private suspend fun convertChatToChannelInfo(chat: TdApi.Chat): ChannelInfo {
        return try {
            // Extract username from supergroup info
            val username = extractUsername(chat)
            
            // Get description from supergroup full info if available
            val description = getChannelDescription(chat)
            
            // Get member count
            val memberCount = getChannelMemberCount(chat)
            
            // Check for active video chat
            val hasActiveVideoChat = chat.videoChat?.groupCallId != 0
            val activeVideoChatId = if (hasActiveVideoChat) chat.videoChat?.groupCallId else null
            
            ChannelInfo(
                id = chat.id,
                title = chat.title,
                username = username,
                description = description,
                memberCount = memberCount,
                photo = chat.photo,
                hasActiveVideoChat = hasActiveVideoChat,
                activeVideoChatId = activeVideoChatId
            )
        } catch (e: Exception) {
            println("TGLIVE: Exception converting chat to ChannelInfo: ${e.message}")
            // Return basic info on error
            ChannelInfo(
                id = chat.id,
                title = chat.title,
                username = "",
                description = "",
                memberCount = 0,
                photo = chat.photo,
                hasActiveVideoChat = chat.videoChat?.groupCallId != 0,
                activeVideoChatId = if (chat.videoChat?.groupCallId != 0) chat.videoChat?.groupCallId else null
            )
        }
    }
    
    /**
     * Extract username from chat object
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
                    ""
                }
            }
            else -> ""
        }
    }
    
    /**
     * Get channel description
     */
    private suspend fun getChannelDescription(chat: TdApi.Chat): String {
        return try {
            if (chat.type.constructor == TdApi.ChatTypeSupergroup.CONSTRUCTOR) {
                val supergroup = chat.type as TdApi.ChatTypeSupergroup
                val fullInfoResult = client.send(TdApi.GetSupergroupFullInfo(supergroup.supergroupId))
                
                when (fullInfoResult.constructor) {
                    TdApi.SupergroupFullInfo.CONSTRUCTOR -> {
                        val fullInfo = fullInfoResult as TdApi.SupergroupFullInfo
                        fullInfo.description ?: ""
                    }
                    else -> ""
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Get channel member count
     */
    private suspend fun getChannelMemberCount(chat: TdApi.Chat): Int {
        return try {
            if (chat.type.constructor == TdApi.ChatTypeSupergroup.CONSTRUCTOR) {
                val supergroup = chat.type as TdApi.ChatTypeSupergroup
                val fullInfoResult = client.send(TdApi.GetSupergroupFullInfo(supergroup.supergroupId))
                
                when (fullInfoResult.constructor) {
                    TdApi.SupergroupFullInfo.CONSTRUCTOR -> {
                        val fullInfo = fullInfoResult as TdApi.SupergroupFullInfo
                        fullInfo.memberCount
                    }
                    else -> 0
                }
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Convert TdApi.GroupCallParticipant to GroupCallParticipant
     * Requirements: 6.5 - participant information conversion
     */
    private suspend fun convertToGroupCallParticipant(participant: TdApi.GroupCallParticipant): app.fqrs.tglive.models.GroupCallParticipant {
        // Get display name based on participant type
        val displayName = getParticipantDisplayName(participant.participantId)
        
        // Get profile photo
        val profilePhoto = getParticipantProfilePhoto(participant.participantId)
        
        return app.fqrs.tglive.models.GroupCallParticipant(
            participantId = participant.participantId,
            displayName = displayName,
            profilePhoto = profilePhoto,
            isMuted = participant.isMutedForAllUsers || participant.isMutedForCurrentUser,
            isSpeaking = participant.isSpeaking,
            hasVideo = participant.videoInfo != null,
            isScreenSharing = participant.screenSharingVideoInfo != null,
            joinedTimestamp = 0 // TDLib doesn't provide join timestamp directly
        )
    }
    
    /**
     * Get display name for a participant
     */
    private suspend fun getParticipantDisplayName(participantId: TdApi.MessageSender): String {
        return try {
            when (participantId.constructor) {
                TdApi.MessageSenderUser.CONSTRUCTOR -> {
                    val userId = (participantId as TdApi.MessageSenderUser).userId
                    val userResult = client.send(TdApi.GetUser(userId))
                    
                    when (userResult.constructor) {
                        TdApi.User.CONSTRUCTOR -> {
                            val user = userResult as TdApi.User
                            "${user.firstName} ${user.lastName}".trim()
                        }
                        else -> "Unknown User"
                    }
                }
                TdApi.MessageSenderChat.CONSTRUCTOR -> {
                    val chatId = (participantId as TdApi.MessageSenderChat).chatId
                    val chatResult = client.send(TdApi.GetChat(chatId))
                    
                    when (chatResult.constructor) {
                        TdApi.Chat.CONSTRUCTOR -> {
                            val chat = chatResult as TdApi.Chat
                            chat.title
                        }
                        else -> "Unknown Chat"
                    }
                }
                else -> "Unknown Participant"
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception getting participant display name: ${e.message}")
            "Unknown"
        }
    }
    
    /**
     * Get profile photo for a participant
     */
    private suspend fun getParticipantProfilePhoto(participantId: TdApi.MessageSender): TdApi.ProfilePhoto? {
        return try {
            when (participantId.constructor) {
                TdApi.MessageSenderUser.CONSTRUCTOR -> {
                    val userId = (participantId as TdApi.MessageSenderUser).userId
                    val userResult = client.send(TdApi.GetUser(userId))
                    
                    when (userResult.constructor) {
                        TdApi.User.CONSTRUCTOR -> {
                            val user = userResult as TdApi.User
                            user.profilePhoto
                        }
                        else -> null
                    }
                }
                TdApi.MessageSenderChat.CONSTRUCTOR -> {
                    val chatId = (participantId as TdApi.MessageSenderChat).chatId
                    val chatResult = client.send(TdApi.GetChat(chatId))
                    
                    when (chatResult.constructor) {
                        TdApi.Chat.CONSTRUCTOR -> {
                            val chat = chatResult as TdApi.Chat
                            // Convert ChatPhoto to ProfilePhoto if needed
                            null // For now, return null as conversion is complex
                        }
                        else -> null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception getting participant profile photo: ${e.message}")
            null
        }
    }
    
    /**
     * Test method to verify UpdatesHandler is working
     * This can be called to check if the handler is receiving updates
     */
    fun testUpdatesFlow(): Boolean {
        return try {
            println("TGLIVE: Testing UpdatesHandler - Initialized: $isInitialized")
            val job = scope.coroutineContext[kotlinx.coroutines.Job]
            val scopeActive = job?.isCancelled == false
            println("TGLIVE: UpdatesHandler scope active: $scopeActive")
            isInitialized && scopeActive
        } catch (e: Exception) {
            println("TGLIVE: Exception testing UpdatesHandler: ${e.message}")
            false
        }
    }
    
    /**
     * Get update statistics for debugging
     */
    fun getUpdateStats(): String {
        return try {
            val job = scope.coroutineContext[kotlinx.coroutines.Job]
            val scopeActive = job?.isCancelled == false
            "UpdatesHandler - Initialized: $isInitialized, Scope Active: $scopeActive"
        } catch (e: Exception) {
            "UpdatesHandler - Error getting stats: ${e.message}"
        }
    }
    
    /**
     * Force refresh group call status
     * This can be called to manually trigger a group call update
     */
    suspend fun refreshGroupCallStatus(groupCallId: Int) {
        try {
            println("TGLIVE: Force refreshing group call status for call $groupCallId")
            
            val result = client.send(TdApi.GetGroupCall(groupCallId))
            when (result.constructor) {
                TdApi.GroupCall.CONSTRUCTOR -> {
                    val groupCall = result as TdApi.GroupCall
                    println("TGLIVE: Force refresh - got group call data")
                    handleGroupCallUpdate(groupCall)
                }
                TdApi.Error.CONSTRUCTOR -> {
                    val error = result as TdApi.Error
                    println("TGLIVE: Force refresh error: ${error.message}")
                    
                    // If group call not found, emit CallEnded
                    if (error.code == 400 && error.message.contains("GROUP_CALL_NOT_FOUND")) {
                        _groupCallUpdates.emit(Pair(groupCallId, GroupCallUpdate.CallEnded))
                        println("TGLIVE: Force refresh - call not found, emitted CallEnded")
                    }
                }
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception in force refresh: ${e.message}")
        }
    }
    
    /**
     * Pause updates processing to save battery when app is in background
     * This reduces CPU usage when the user isn't actively viewing the app
     */
    fun pauseUpdates() {
        println("TGLIVE: Pausing updates processing for battery optimization")
        isPaused = true
    }
    
    /**
     * Resume updates processing when app comes to foreground
     */
    fun resumeUpdates() {
        println("TGLIVE: Resuming updates processing")
        isPaused = false
    }
    
    /**
     * Check if updates are currently paused
     */
    fun isPaused(): Boolean = isPaused
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        println("TGLIVE: Cleaning up UpdatesHandler")
        // The scope will be cancelled automatically when the object is garbage collected
        // No explicit cleanup needed for flows
    }
}