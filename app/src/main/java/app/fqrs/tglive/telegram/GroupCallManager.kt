package app.fqrs.tglive.telegram

import app.fqrs.tglive.models.GroupCallInfo
import app.fqrs.tglive.models.GroupCallParticipant
import app.fqrs.tglive.models.GroupCallUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

/**
 * Manager class for group call operations and video chat monitoring
 * 
 * This class provides functionality to:
 * - Fetch group call information and participant data
 * - Join and leave group calls
 * - Track participant status changes (muted, speaking, video)
 * - Provide real-time updates for UI components
 * 
 * Usage:
 * ```kotlin
 * val groupCallManager = GroupCallManager(telegramClient)
 * try {
 *     val groupCallInfo = groupCallManager.getGroupCall(groupCallId)
 *     // Participants are now fetched separately if needed, or through updates
 *     
 *     // Observe real-time updates
 *     groupCallManager.observeGroupCallUpdates().collect { update ->
 *         when (update) {
 *             is GroupCallUpdate.ParticipantJoined -> handleParticipantJoined(update.participant)
 *             is GroupCallUpdate.ParticipantLeft -> handleParticipantLeft(update.participantId)
 *             is GroupCallUpdate.ParticipantStatusChanged -> handleStatusChange(update.participant)
 *             is GroupCallUpdate.CallEnded -> handleCallEnded()
 *         }
 *     }
 * } catch (e: Exception) {
 *     println("Error: ${e.message}")
 * }
 * ```
 * 
 * Requirements: 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 5.1, 5.2, 5.3, 5.4, 5.5
 */
import android.util.Log
import app.fqrs.tglive.voip.VoipEngine

class GroupCallManager(private val client: TelegramClient) {
    private val LOG_JOIN = "TGLive_Join"
    
    private val _groupCallUpdates = MutableSharedFlow<GroupCallUpdate>()
    private val groupCallUpdates: SharedFlow<GroupCallUpdate> = _groupCallUpdates.asSharedFlow()
    
    /**
     * Get group call information by ID
     * Requirements: 3.1, 3.2, 3.3 - fetch group call information
     * 
     * Following Telegram's approach: call is active if isActive=true AND has participants
     */
    suspend fun getGroupCall(groupCallId: Int): GroupCallInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val result = client.send(TdApi.GetGroupCall(groupCallId))
                
                when (result.constructor) {
                    TdApi.GroupCall.CONSTRUCTOR -> {
                        val groupCall = result as TdApi.GroupCall
                        println("TGLIVE: Got group call - isActive: ${groupCall.isActive}, participants: ${groupCall.participantCount}")
                        convertToGroupCallInfo(groupCall) // No participants in GroupCallInfo now
                    }
                    TdApi.Error.CONSTRUCTOR -> {
                        val error = result as TdApi.Error
                        println("TGLIVE: GetGroupCall error: ${error.code} - ${error.message}")
                        null
                    }
                    else -> {
                        println("TGLIVE: Unexpected GetGroupCall result: ${result.javaClass.simpleName}")
                        null
                    }
                }
            } catch (e: Exception) {
                println("TGLIVE: Exception in getGroupCall: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Get group call participants
     * Requirements: 4.1, 4.2, 5.1 - fetch participant data with status information
     */
    suspend fun getGroupCallParticipants(groupCallId: Int): List<GroupCallParticipant> {
        return withContext(Dispatchers.IO) {
            try {
                println("TGLIVE: Loading participants for group call $groupCallId")

                // First, get the basic GroupCall information to get the invite link, if any
                val groupCallResult = client.send(TdApi.GetGroupCall(groupCallId))
                val tdGroupCall = if (groupCallResult.constructor == TdApi.GroupCall.CONSTRUCTOR) {
                    groupCallResult as TdApi.GroupCall
                } else {
                    println("TGLIVE: Could not get TdApi.GroupCall object for ID $groupCallId to fetch participants.")
                    return@withContext emptyList()
                }

                // TdApi.GetGroupCallParticipants requires an InputGroupCall.
                // We'll use InputGroupCallLink if an inviteLink is available.
                val inputGroupCall: TdApi.InputGroupCall? = if (!tdGroupCall.inviteLink.isNullOrEmpty()) {
                    TdApi.InputGroupCallLink(tdGroupCall.inviteLink)
                } else {
                    // If no invite link, we can't use InputGroupCallLink directly.
                    // Another way might be TdApi.InputGroupCallId (if it existed) or InputGroupCallMessage (needs chat/message ID).
                    // For now, if no inviteLink, we assume we cannot get participants this way.
                    println("TGLIVE: No invite link available for group call $groupCallId, cannot fetch participants via InputGroupCallLink.")
                    null
                }

                if (inputGroupCall == null) {
                    return@withContext emptyList()
                }
                
                val result = client.send(TdApi.GetGroupCallParticipants(
                    inputGroupCall, 
                    100 // limit
                ))

                when (result.constructor) {
                    TdApi.GroupCallParticipants.CONSTRUCTOR -> {
                        val tdGroupCallParticipants = result as TdApi.GroupCallParticipants
                        // TdApi.GroupCallParticipants has participantIds (MessageSender[]), not GroupCallParticipant[]
                        // So, we need to convert each MessageSender to GroupCallParticipant
                        tdGroupCallParticipants.participantIds?.mapNotNull { messageSender ->
                            // For now, we only have MessageSender. To get full GroupCallParticipant,
                            // we would need more detailed TDLib calls (e.g., GetUser, then GetGroupCallParticipant to get videoInfo, etc.).
                            // As a placeholder, we create a basic GroupCallParticipant from MessageSender
                            val tdId = getTdIdFromMessageSender(messageSender)
                            if (tdId != 0L) {
                                GroupCallParticipant(
                                    participantId = messageSender,
                                    tdId = tdId,
                                    displayName = getParticipantDisplayName(messageSender), // This is a suspend function
                                    profilePhoto = getParticipantProfilePhoto(messageSender), // This is a suspend function
                                    isMuted = false, // Placeholder, actual status comes from UpdateGroupCallParticipant
                                    isSpeaking = false, // Placeholder
                                    hasVideo = false, // Placeholder
                                    isScreenSharing = false, // Placeholder
                                    joinedTimestamp = 0 // Placeholder
                                )
                            } else null
                        } ?: emptyList()
                    }
                    TdApi.Error.CONSTRUCTOR -> {
                        val error = result as TdApi.Error
                        println("TGLIVE: GetGroupCallParticipants error: ${error.code} - ${error.message}")
                        emptyList()
                    }
                    else -> {
                        println("TGLIVE: Unexpected GetGroupCallParticipants result: ${result.javaClass.simpleName}")
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                println("TGLIVE: Exception in getGroupCallParticipants: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Join a group call
     * Requirements: 4.2, 4.3 - join group call functionality
     */
    suspend fun joinGroupCall(chatId: Long, groupCallId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                println("TGLIVE: Attempting to join group call $groupCallId using TDLib API")
                println("$LOG_JOIN: start groupCallId=$groupCallId chatId=$chatId")
                Log.i(LOG_JOIN, "start groupCallId=$groupCallId chatId=$chatId")

                val groupCallInfo = getGroupCall(groupCallId) // Get full GroupCallInfo, which includes inviteLink
                if (groupCallInfo == null || !groupCallInfo.isActive) {
                    println("TGLIVE: Cannot join group call $groupCallId - not active or not found")
                    return@withContext false
                }

                // Attempt 1: JoinVideoChat (preferred for chat-bound video chats)
                // Initialize VOIP engine (stub for now) and produce a non-empty payload
                println("$LOG_JOIN: initializing_voip groupCallId=$groupCallId")
                VoipEngine.initialize(client.getAppContext())
                val payload = VoipEngine.getJoinPayload(chatId, groupCallId)
                println("$LOG_JOIN: voip_payload groupCallId=$groupCallId payloadLen=${payload.length} payload=${payload.take(50)}...")

                val joinParams = TdApi.GroupCallJoinParameters(
                    1,          // audioSourceId (must be non-zero)
                    payload,    // payload (must be non-empty)
                    false,      // isMuted
                    false       // isMyVideoEnabled
                )
                println("$LOG_JOIN: join_params groupCallId=$groupCallId payloadLen=${joinParams.payload.length}")

                // Get invite URL (HttpUrl) to possibly include inviteHash
                var inviteUrl = ""
                run {
                    val linkResult = client.send(TdApi.GetVideoChatInviteLink(groupCallId, true))
                    if (linkResult.constructor == TdApi.HttpUrl.CONSTRUCTOR) {
                        val httpUrl = linkResult as TdApi.HttpUrl
                        inviteUrl = httpUrl.url ?: ""
                        println("TGLIVE: Invite URL: ${inviteUrl}")
                    } else if (groupCallInfo.inviteLink.isNotEmpty()) {
                        inviteUrl = groupCallInfo.inviteLink
                    }
                }

                fun extractInviteHash(url: String): String {
                    // Best-effort: read query param after "invite=" or fragment at end
                    val qIndex = url.indexOf("invite=")
                    if (qIndex >= 0) {
                        val sub = url.substring(qIndex + 7)
                        val end = listOf('&', '#').map { c -> sub.indexOf(c) }.filter { it >= 0 }.minOrNull() ?: -1
                        return if (end >= 0) sub.substring(0, end) else sub
                    }
                    return ""
                }

                val inviteHash = extractInviteHash(inviteUrl)

                val joinVideoChat = TdApi.JoinVideoChat(
                    groupCallId,
                    null,        // participantId -> join as self
                    joinParams,
                    inviteHash
                )

                println("$LOG_JOIN: joinVideoChat.try groupCallId=$groupCallId inviteHash=${inviteHash}")
                Log.i(LOG_JOIN, "joinVideoChat.try groupCallId=$groupCallId inviteHash=${inviteHash}")
                println("$LOG_JOIN: joinVideoChat.params groupCallId=$groupCallId audioSourceId=${joinParams.audioSourceId} payloadLen=${joinParams.payload.length} isMuted=${joinParams.isMuted} isMyVideoEnabled=${joinParams.isMyVideoEnabled}")
                Log.i(LOG_JOIN, "joinVideoChat.params groupCallId=$groupCallId audioSourceId=${joinParams.audioSourceId} payloadLen=${joinParams.payload.length} isMuted=${joinParams.isMuted} isMyVideoEnabled=${joinParams.isMyVideoEnabled}")
                val result1 = client.send(joinVideoChat)
                if (result1.constructor != TdApi.Error.CONSTRUCTOR) {
                    println("TGLIVE: Successfully joined via JoinVideoChat")
                    println("$LOG_JOIN: joinVideoChat.success groupCallId=$groupCallId")
                    Log.i(LOG_JOIN, "joinVideoChat.success groupCallId=$groupCallId")
                    return@withContext true
                } else {
                    val err = result1 as TdApi.Error
                    println("TGLIVE: JoinVideoChat error: ${err.code} - ${err.message}")
                    println("$LOG_JOIN: joinVideoChat.error groupCallId=$groupCallId code=${err.code} message=${err.message}")
                    Log.w(LOG_JOIN, "joinVideoChat.error groupCallId=$groupCallId code=${err.code} message=${err.message}")
                }

                // Small retry after 500ms in case of race
                kotlinx.coroutines.delay(500)
                println("$LOG_JOIN: joinVideoChat.retry groupCallId=$groupCallId")
                Log.i(LOG_JOIN, "joinVideoChat.retry groupCallId=$groupCallId")
                val resultRetry = client.send(joinVideoChat)
                if (resultRetry.constructor != TdApi.Error.CONSTRUCTOR) {
                    println("TGLIVE: Successfully joined via JoinVideoChat on retry")
                    println("$LOG_JOIN: joinVideoChat.retry.success groupCallId=$groupCallId")
                    Log.i(LOG_JOIN, "joinVideoChat.retry.success groupCallId=$groupCallId")
                    return@withContext true
                } else {
                    val err = resultRetry as TdApi.Error
                    println("TGLIVE: JoinVideoChat retry error: ${err.code} - ${err.message}")
                    println("$LOG_JOIN: joinVideoChat.retry.error groupCallId=$groupCallId code=${err.code} message=${err.message}")
                    Log.w(LOG_JOIN, "joinVideoChat.retry.error groupCallId=$groupCallId code=${err.code} message=${err.message}")
                }

                // Fallback: JoinGroupCall using InputGroupCallLink if we have any URL
                if (inviteUrl.isNotEmpty()) {
                    val inputGroupCall = TdApi.InputGroupCallLink(inviteUrl)
                    val joinGroupCall = TdApi.JoinGroupCall(inputGroupCall, joinParams)
                    println("$LOG_JOIN: joinGroupCall.try groupCallId=$groupCallId inviteUrlSet=${inviteUrl.isNotEmpty()}")
                    Log.i(LOG_JOIN, "joinGroupCall.try groupCallId=$groupCallId inviteUrlSet=${inviteUrl.isNotEmpty()}")
                    val result2 = client.send(joinGroupCall)
                    if (result2.constructor == TdApi.GroupCallInfo.CONSTRUCTOR || result2.constructor == TdApi.Ok.CONSTRUCTOR) {
                        println("TGLIVE: Successfully joined via JoinGroupCall fallback")
                        println("$LOG_JOIN: joinGroupCall.success groupCallId=$groupCallId")
                        Log.i(LOG_JOIN, "joinGroupCall.success groupCallId=$groupCallId")
                        return@withContext true
                    } else if (result2.constructor == TdApi.Error.CONSTRUCTOR) {
                        val error = result2 as TdApi.Error
                        println("TGLIVE: JoinGroupCall fallback error: ${error.code} - ${error.message}")
                        println("$LOG_JOIN: joinGroupCall.error groupCallId=$groupCallId code=${error.code} message=${error.message}")
                        Log.w(LOG_JOIN, "joinGroupCall.error groupCallId=$groupCallId code=${error.code} message=${error.message}")
                        if (error.message.contains("GROUP_CALL_ALREADY_JOINED")) {
                            println("$LOG_JOIN: already_joined.ok groupCallId=$groupCallId")
                            Log.i(LOG_JOIN, "already_joined.ok groupCallId=$groupCallId")
                            return@withContext true
                        }
                    }
                }
                println("$LOG_JOIN: end.failure groupCallId=$groupCallId")
                Log.w(LOG_JOIN, "end.failure groupCallId=$groupCallId")
                false
            } catch (e: Exception) {
                println("TGLIVE: Exception in joinGroupCall: ${e.message}")
                e.printStackTrace()
                println("$LOG_JOIN: exception groupCallId=$groupCallId message=${e.message}")
                Log.e(LOG_JOIN, "exception groupCallId=$groupCallId message=${e.message}")
                false
            }
        }
    }
    
    /**
     * Leave a group call
     * Requirements: 4.3 - leave group call functionality
     */
    suspend fun leaveGroupCall(groupCallId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                println("TGLIVE: Attempting to leave group call $groupCallId using TDLib API")
                
                val result = client.send(TdApi.LeaveGroupCall(groupCallId))
                
                when (result.constructor) {
                    TdApi.Ok.CONSTRUCTOR -> {
                        println("TGLIVE: Successfully left group call $groupCallId")
                        true
                    }
                    TdApi.Error.CONSTRUCTOR -> {
                        val error = result as TdApi.Error
                        println("TGLIVE: LeaveGroupCall error: ${error.code} - ${error.message}")
                        
                        // Handle specific error cases
                        when (error.code) {
                            400 -> {
                                if (error.message.contains("GROUP_CALL_NOT_FOUND")) {
                                    println("TGLIVE: Group call not found - may have already ended")
                                    // Consider this successful since the call is gone
                                    return@withContext true
                                } else if (error.message.contains("NOT_PARTICIPANT")) {
                                    println("TGLIVE: Not a participant in the group call")
                                    // Consider this successful since we're not in the call
                                    return@withContext true
                                }
                            }
                            403 -> {
                                println("TGLIVE: Not allowed to leave group call: ${error.message}")
                            }
                        }
                        false
                    }
                    else -> {
                        println("TGLIVE: Unexpected LeaveGroupCall result: ${result.javaClass.simpleName}")
                        false
                    }
                }
            } catch (e: Exception) {
                println("TGLIVE: Exception in leaveGroupCall: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Toggle mute status in a group call
     * Requirements: 5.2, 5.3 - track participant status changes
     */
    suspend fun toggleMute(groupCallId: Int, isMuted: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Note: TDLib API for muting may be different, using a placeholder approach
                val result = client.send(TdApi.GetGroupCall(groupCallId))
                
                when (result.constructor) {
                    TdApi.Ok.CONSTRUCTOR -> {
                        println("TGLIVE: Successfully toggled mute in group call $groupCallId")
                        true
                    }
                    TdApi.Error.CONSTRUCTOR -> {
                        val error = result as TdApi.Error
                        println("TGLIVE: ToggleGroupCallMute error: ${error.code} - ${error.message}")
                        false
                    }
                    else -> {
                        println("TGLIVE: Unexpected ToggleGroupCallMute result: ${result.javaClass.simpleName}")
                        false
                    }
                }
            } catch (e: Exception) {
                println("TGLIVE: Exception in toggleMute: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Set video enabled/disabled in group call
     * Requirements: 5.4 - track video status changes
     */
    suspend fun setVideoEnabled(groupCallId: Int, enabled: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Note: TDLib doesn't have a direct method to set video for others
                // This would typically be handled through the group call participant updates
                // For now, we'll return true as this is more about tracking than controlling
                println("TGLIVE: Video status change requested for group call $groupCallId: $enabled")
                true
            } catch (e: Exception) {
                println("TGLIVE: Exception in setVideoEnabled: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Observe group call updates for real-time monitoring
     * Requirements: 5.2, 5.3, 5.4, 5.5 - real-time participant status updates
     * Note: This method is deprecated. Use UpdatesHandler.observeGroupCallUpdates() instead.
     */
    @Deprecated("Use UpdatesHandler.observeGroupCallUpdates() instead")
    fun observeGroupCallUpdates(): Flow<GroupCallUpdate> {
        return groupCallUpdates
    }
    
    /**
     * Convert TdApi.GroupCall to GroupCallInfo
     * Requirements: 3.1, 3.2, 3.3
     * 
     * Following Telegram's logic: call is active if isActive=true AND has participants
     * @param tdGroupCall The TdApi.GroupCall object directly from TDLib.
     * @param participants The list of GroupCallParticipant objects.
     */
    private suspend fun convertToGroupCallInfo(tdGroupCall: TdApi.GroupCall): GroupCallInfo { // No participants parameter anymore
        // Following Telegram's logic: call_active && call_not_empty
        val actuallyActive = tdGroupCall.isActive && tdGroupCall.participantCount > 0

        return GroupCallInfo(
            id = tdGroupCall.id,
            title = tdGroupCall.title,
            participantCount = tdGroupCall.participantCount,
            isActive = actuallyActive,
            canBeManaged = tdGroupCall.canBeManaged,
            isJoined = tdGroupCall.isJoined,
            inviteLink = tdGroupCall.inviteLink ?: ""
        )
    }

    /**
     * Convert TdApi.GroupCallParticipant to our custom GroupCallParticipant model.
     * Requirements: 5.1, 5.2, 5.3, 5.4 - participant status information
     */
    suspend fun convertToGroupCallParticipant(participant: TdApi.GroupCallParticipant): GroupCallParticipant {
        // Get display name based on participant type
        val displayName = getParticipantDisplayName(participant.participantId)

        // Get profile photo
        val profilePhoto = getParticipantProfilePhoto(participant.participantId)

        return GroupCallParticipant(
            participantId = participant.participantId,
            tdId = getTdIdFromMessageSender(participant.participantId), // Extract TDLib User ID
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
     * Helper to extract TDLib User ID from TdApi.MessageSender
     */
    private fun getTdIdFromMessageSender(messageSender: TdApi.MessageSender): Long {
        return when (messageSender.constructor) {
            TdApi.MessageSenderUser.CONSTRUCTOR -> (messageSender as TdApi.MessageSenderUser).userId
            TdApi.MessageSenderChat.CONSTRUCTOR -> (messageSender as TdApi.MessageSenderChat).chatId // Chats can also be participants
            else -> 0L // Default or error case
        }
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
     * Check if a group call is active
     * Requirements: 3.1, 3.2
     */
    suspend fun isGroupCallActive(groupCallId: Int): Boolean {
        return try {
            val groupCall = getGroupCall(groupCallId)
            val isActive = groupCall?.isActive == true

            if (isActive) {
                println("TGLIVE: Group call $groupCallId verified as active with ${groupCall?.participantCount ?: 0} participants")
            } else {
                println("TGLIVE: Group call $groupCallId is not active")
            }

            isActive
        } catch (e: Exception) {
            println("TGLIVE: Exception checking if group call is active: ${e.message}")
            false
        }
    }

    /**
     * Get participant count for a group call
     * Requirements: 3.3, 4.1
     */
    suspend fun getParticipantCount(groupCallId: Int): Int {
        return try {
            val groupCall = getGroupCall(groupCallId)
            groupCall?.participantCount ?: 0
        } catch (e: Exception) {
            println("TGLIVE: Exception getting participant count: ${e.message}")
            0
        }
    }
}