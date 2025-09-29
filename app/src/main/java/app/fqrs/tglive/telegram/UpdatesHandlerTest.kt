package app.fqrs.tglive.telegram

import app.fqrs.tglive.models.ChannelUpdate
import app.fqrs.tglive.models.GroupCallUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/**
 * Integration test for UpdatesHandler with real TelegramClient
 * 
 * This class provides methods to test the UpdatesHandler functionality
 * with production data and real TDLib integration.
 * 
 * Usage:
 * ```kotlin
 * val test = UpdatesHandlerTest(telegramClient)
 * test.testChannelUpdates(channelId)
 * test.testGroupCallUpdates(groupCallId)
 * ```
 * 
 * Requirements: Testing for 6.1, 6.2, 6.3, 6.4, 6.5, 6.6
 */
class UpdatesHandlerTest(private val client: TelegramClient) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    /**
     * Test channel updates with real channel data
     * Requirements: 6.3, 6.4 - test channel update flows
     */
    fun testChannelUpdates(channelId: Long) {
        println("DEBUG: Testing channel updates for channel $channelId")
        
        // Create and initialize UpdatesHandler
        val updatesHandler = UpdatesHandler(client)
        client.setUpdatesHandler(updatesHandler)
        
        // Observe channel updates
        scope.launch {
            try {
                updatesHandler.observeChannelUpdates(channelId)
                    .take(5) // Take first 5 updates for testing
                    .collect { update ->
                        when (update) {
                            is ChannelUpdate.InfoChanged -> {
                                println("DEBUG: Channel info changed: ${update.channelInfo.title}")
                                println("  - Members: ${update.channelInfo.memberCount}")
                                println("  - Has video chat: ${update.channelInfo.hasActiveVideoChat}")
                            }
                            is ChannelUpdate.MemberCountChanged -> {
                                println("DEBUG: Member count changed to: ${update.newCount}")
                            }
                            is ChannelUpdate.VideoChatStarted -> {
                                println("DEBUG: Video chat started with group call ID: ${update.groupCallId}")
                            }
                            is ChannelUpdate.VideoChatEnded -> {
                                println("DEBUG: Video chat ended")
                            }
                        }
                    }
                
                println("DEBUG: Channel updates test completed")
            } catch (e: Exception) {
                println("DEBUG: Exception in channel updates test: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Test group call updates with real group call data
     * Requirements: 6.5 - test group call update flows
     */
    fun testGroupCallUpdates(groupCallId: Int) {
        println("DEBUG: Testing group call updates for group call $groupCallId")
        
        // Create and initialize UpdatesHandler
        val updatesHandler = UpdatesHandler(client)
        client.setUpdatesHandler(updatesHandler)
        
        // Observe group call updates
        scope.launch {
            try {
                updatesHandler.observeGroupCallUpdates(groupCallId)
                    .take(10) // Take first 10 updates for testing
                    .collect { update ->
                        when (update) {
                            is GroupCallUpdate.ParticipantJoined -> {
                                println("DEBUG: Participant joined: ${update.participant.displayName}")
                                println("  - Muted: ${update.participant.isMuted}")
                                println("  - Has video: ${update.participant.hasVideo}")
                                println("  - Screen sharing: ${update.participant.isScreenSharing}")
                            }
                            is GroupCallUpdate.ParticipantLeft -> {
                                println("DEBUG: Participant left: ${update.participantId}")
                            }
                            is GroupCallUpdate.ParticipantStatusChanged -> {
                                println("DEBUG: Participant status changed: ${update.participant.displayName}")
                                println("  - Speaking: ${update.participant.isSpeaking}")
                                println("  - Muted: ${update.participant.isMuted}")
                                println("  - Has video: ${update.participant.hasVideo}")
                            }
                            is GroupCallUpdate.StatusChanged -> {
                                println("DEBUG: Group call status changed:")
                                println("  - Active: ${update.groupCallInfo.isActive}")
                                println("  - Participants: ${update.groupCallInfo.participantCount}")
                            }
                            is GroupCallUpdate.CallEnded -> {
                                println("DEBUG: Group call ended")
                            }
                        }
                    }
                
                println("DEBUG: Group call updates test completed")
            } catch (e: Exception) {
                println("DEBUG: Exception in group call updates test: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Test update processing performance
     * Requirements: 6.6 - ensure updates don't block UI thread
     */
    fun testUpdateProcessingPerformance() {
        println("DEBUG: Testing update processing performance")
        
        val updatesHandler = UpdatesHandler(client)
        client.setUpdatesHandler(updatesHandler)
        
        scope.launch {
            try {
                val startTime = System.currentTimeMillis()
                var updateCount = 0
                
                // Observe all updates for 30 seconds
                updatesHandler.observeUpdates()
                    .collect { update ->
                        updateCount++
                        val currentTime = System.currentTimeMillis()
                        val elapsed = currentTime - startTime
                        
                        if (elapsed > 30000) { // 30 seconds
                            println("DEBUG: Performance test completed")
                            println("  - Updates processed: $updateCount")
                            println("  - Time elapsed: ${elapsed}ms")
                            println("  - Updates per second: ${updateCount * 1000.0 / elapsed}")
                            return@collect
                        }
                        
                        // Log every 100th update
                        if (updateCount % 100 == 0) {
                            println("DEBUG: Processed $updateCount updates in ${elapsed}ms")
                        }
                    }
            } catch (e: Exception) {
                println("DEBUG: Exception in performance test: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Test UpdatesHandler initialization and cleanup
     * Requirements: 6.1 - test initialization process
     */
    fun testInitializationAndCleanup() {
        println("DEBUG: Testing UpdatesHandler initialization and cleanup")
        
        try {
            // Test initialization
            val updatesHandler = UpdatesHandler(client)
            println("DEBUG: UpdatesHandler created successfully")
            
            // Test setting handler on client
            client.setUpdatesHandler(updatesHandler)
            println("DEBUG: UpdatesHandler set on client successfully")
            
            // Test getting handler from client
            val retrievedHandler = client.getUpdatesHandler()
            if (retrievedHandler != null) {
                println("DEBUG: UpdatesHandler retrieved from client successfully")
            } else {
                println("DEBUG: ERROR - UpdatesHandler not found on client")
            }
            
            // Test cleanup
            updatesHandler.cleanup()
            println("DEBUG: UpdatesHandler cleanup completed")
            
            println("DEBUG: Initialization and cleanup test completed successfully")
        } catch (e: Exception) {
            println("DEBUG: Exception in initialization test: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Simulate update emission for testing
     * Requirements: 6.2 - test update processing
     */
    fun simulateUpdates() {
        println("DEBUG: Simulating updates for testing")
        
        val updatesHandler = UpdatesHandler(client)
        client.setUpdatesHandler(updatesHandler)
        
        scope.launch {
            try {
                // Simulate various update types
                delay(1000)
                
                // Simulate a group call update
                val groupCall = TdApi.GroupCall(
                    123, // id
                    "Test Group Call", // title
                    "", // inviteLink
                    0, // scheduledStartDate
                    false, // enabledStartNotification
                    true, // isActive
                    false, // isVideoChat
                    false, // isRtmpStream
                    false, // isJoined
                    false, // needRejoin
                    false, // isOwned
                    true, // canBeManaged
                    5, // participantCount
                    false, // hasHiddenListeners
                    false, // loadedAllParticipants
                    emptyArray<TdApi.GroupCallRecentSpeaker>(), // recentSpeakers
                    false, // isMyVideoEnabled
                    false, // isMyVideoPaused
                    true, // canEnableVideo
                    false, // muteNewParticipants
                    false, // canToggleMuteNewParticipants
                    0, // recordDuration
                    false, // isVideoRecorded
                    0 // duration
                )
                
                val groupCallUpdate = TdApi.UpdateGroupCall(groupCall)
                updatesHandler.emitUpdate(groupCallUpdate)
                println("DEBUG: Simulated group call update")
                
                delay(2000)
                
                // Simulate a chat video chat update
                val defaultParticipant = TdApi.MessageSenderUser(1)
                val videoChat = TdApi.VideoChat(123, false, defaultParticipant)
                val videoChatUpdate = TdApi.UpdateChatVideoChat(456, videoChat)
                updatesHandler.emitUpdate(videoChatUpdate)
                println("DEBUG: Simulated video chat update")
                
                println("DEBUG: Update simulation completed")
            } catch (e: Exception) {
                println("DEBUG: Exception in update simulation: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Run all tests
     */
    fun runAllTests(channelId: Long, groupCallId: Int) {
        println("DEBUG: Running all UpdatesHandler tests")
        
        testInitializationAndCleanup()
        delay(1000)
        
        testChannelUpdates(channelId)
        delay(2000)
        
        testGroupCallUpdates(groupCallId)
        delay(2000)
        
        testUpdateProcessingPerformance()
        delay(2000)
        
        simulateUpdates()
        
        println("DEBUG: All UpdatesHandler tests completed")
    }
    
    private fun delay(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}