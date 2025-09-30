package app.fqrs.tglive

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.fqrs.tglive.databinding.ActivityChannelInfoBinding
import app.fqrs.tglive.models.ChannelError
import app.fqrs.tglive.models.ChannelInfo
import app.fqrs.tglive.models.ChannelUpdate
import app.fqrs.tglive.models.GroupCallInfo
import app.fqrs.tglive.models.GroupCallParticipant
import app.fqrs.tglive.models.GroupCallUpdate
import app.fqrs.tglive.telegram.ChannelManager
import app.fqrs.tglive.telegram.GroupCallManager
import app.fqrs.tglive.telegram.TelegramClient
import app.fqrs.tglive.telegram.TelegramClientSingleton
import app.fqrs.tglive.telegram.UpdatesHandler
import app.fqrs.tglive.ui.ParticipantAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/**
 * Activity for displaying channel information and live video chat monitoring
 */
class ChannelInfoActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_CHANNEL_HANDLE = "channel_handle"
    }
    
    private lateinit var binding: ActivityChannelInfoBinding
    private lateinit var telegramClient: TelegramClient
    private lateinit var channelManager: ChannelManager
    private lateinit var groupCallManager: GroupCallManager
    private lateinit var updatesHandler: UpdatesHandler
    
    private var currentChannelInfo: ChannelInfo? = null
    private var currentGroupCallInfo: GroupCallInfo? = null
    private var channelHandle: String? = null
    private var isJoinedToCall: Boolean = false
    
    // Modal state
    private var isModalVisible = false
    
    // Keyboard state
    private var isKeyboardVisible = false
    private var keyboardLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    
    // Participant adapter for RecyclerView
    private lateinit var participantAdapter: ParticipantAdapter
    
    // Coroutine scope for real-time updates with proper lifecycle management
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Jobs for tracking active update subscriptions
    private var channelUpdateJob: Job? = null
    private var groupCallUpdateJob: Job? = null
    private var periodicRefreshJob: Job? = null

    // Video player properties
    private val videoSurfaceViews = mutableListOf<SurfaceView>()
    private val videoSurfaceHolders = mutableListOf<SurfaceHolder?>()
    private var isVideoPlayerInitialized = false
    private val activeVideoStreams = mutableMapOf<String, Boolean>() // Map of participant_id to active video status
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide action bar
        supportActionBar?.hide()
        
        // Initialize view binding
        binding = ActivityChannelInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get channel handle from intent
        channelHandle = intent.getStringExtra(EXTRA_CHANNEL_HANDLE)
        
        // Validate channel handle
        if (channelHandle.isNullOrEmpty()) {
            showError("No channel handle provided")
            return
        }
        
        // Initialize Telegram components
        initializeTelegramComponents()

        // Initialize participant adapter (for potential future use)
        setupParticipantRecyclerView()

        // Setup TikTok-style button listeners
        setupTikTokButtonListeners()

        // Setup modal functionality
        setupModalFunctionality()

        // Initialize video players
        initializeVideoPlayers()

        // Set default camera off state
        displayCameraOffState()

        // Set default channel title
        binding.tvChannelTitle.text = "Loading channel..."

        // Load fresh channel data
        loadChannelData()

        // Setup comment input functionality
        setupCommentInput()
        
        // Setup keyboard visibility listener
        setupKeyboardListener()
        
        // Setup touch outside listener
        setupTouchOutsideListener()
    }
    
    /**
     * Initialize ChannelManager, GroupCallManager, and UpdatesHandler
     */
    private fun initializeTelegramComponents() {
        try {
            println("TGLIVE: Initializing Telegram components for real-time updates")
            
            // Use the singleton TelegramClient to avoid conflicts
            telegramClient = TelegramClientSingleton.getInstance(this)
            channelManager = ChannelManager(telegramClient)
            groupCallManager = GroupCallManager(telegramClient)
            
            // CRITICAL: Use the singleton UpdatesHandler that was initialized with the client
            // This ensures we get real-time updates from the start
            val existingHandler = TelegramClientSingleton.getUpdatesHandler()
            if (existingHandler != null) {
                println("TGLIVE: Using singleton UpdatesHandler - real-time updates should work")
                updatesHandler = existingHandler
            } else {
                // This should not happen if singleton is working correctly
                println("TGLIVE: WARNING: No singleton UpdatesHandler found, creating new one")
                updatesHandler = UpdatesHandler(telegramClient)
                telegramClient.setUpdatesHandler(updatesHandler)
                updatesHandler.initialize()
            }
            
            println("TGLIVE: Telegram components initialized successfully with real-time update support")
        } catch (e: Exception) {
            println("TGLIVE: Failed to initialize Telegram components: ${e.message}")
            e.printStackTrace()
            showError("Failed to initialize Telegram client")
        }
    }
    
    /**
     * Initialize participant adapter (for potential future use)
     * Note: TikTok-style UI doesn't show participant list
     */
    private fun setupParticipantRecyclerView() {
        // Participant list not used in TikTok-style UI
        // Keeping adapter for potential future features
        participantAdapter = ParticipantAdapter(this)
    }
    
    /**
     * Load channel data and set up real-time updates
     */
    private fun loadChannelData() {
        val handle = channelHandle ?: return
        
        lifecycleScope.launch {
            showLoading(true)
            
            try {
                println("TGLIVE: Loading channel data for handle: $handle")
                
                // Initialize the Telegram client first
                val initSuccess = telegramClient.initialize()
                if (!initSuccess) {
                    showError("Failed to initialize Telegram client")
                    return@launch
                }
                
                // Get channel information
                val channelInfo = channelManager.getChannelByUsername(handle)
                currentChannelInfo = channelInfo
                
                // Display channel information
                displayChannelInfo(channelInfo)
                
                // Check for active video chat and display status
                checkAndDisplayVideoChat(channelInfo)
                
                // CRITICAL: Set up INSTANT real-time updates for this channel
                setupInstantRealTimeUpdates(channelInfo.id)
                
                // Test if updates are working
                val updatesWorking = updatesHandler.testUpdatesFlow()
                val clientStatus = telegramClient.testUpdatesReception()
                println("TGLIVE: Channel data loaded successfully - Real-time updates active: $updatesWorking")
                println("TGLIVE: Client status: $clientStatus")
                println("TGLIVE: ${updatesHandler.getUpdateStats()}")
                
                // Real-time status updates work without visual indicator in TikTok UI
                println("TGLIVE: Real-time updates active: $updatesWorking")
                
                // Also set up a general update listener to see ALL updates
                setupGeneralUpdateListener()
                
                // CRITICAL: Test if we're receiving ANY updates at all
                testRealTimeUpdates()
                
            } catch (e: ChannelError) {
                println("TGLIVE: ChannelError: $e")
                showError(getErrorMessage(e))
            } catch (e: Exception) {
                println("TGLIVE: Exception loading channel data: ${e.message}")
                e.printStackTrace()
                showError("Failed to load channel information: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    /**
     * Display channel information in UI (TikTok style bottom bar)
     */
    private fun displayChannelInfo(channelInfo: ChannelInfo) {
        try {
            // Display channel title in bottom bar
            binding.tvChannelTitle.text = channelInfo.title

            // Load and display profile picture
            loadChannelProfilePicture(channelInfo.photo)

            println("TGLIVE: Channel info displayed - Title: ${channelInfo.title}, Members: ${channelInfo.memberCount}")

        } catch (e: Exception) {
            println("TGLIVE: Exception displaying channel info: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Check for active video chat and display status
     */
    private suspend fun checkAndDisplayVideoChat(channelInfo: ChannelInfo) {
        try {
            println("TGLIVE: Checking video chat status - hasActiveVideoChat: ${channelInfo.hasActiveVideoChat}, activeVideoChatId: ${channelInfo.activeVideoChatId}")
            
            if (channelInfo.hasActiveVideoChat && channelInfo.activeVideoChatId != null) {
                // Get group call information
                val groupCallInfo = groupCallManager.getGroupCall(channelInfo.activeVideoChatId)
                currentGroupCallInfo = groupCallInfo
                
                println("TGLIVE: Group call info - isActive: ${groupCallInfo?.isActive}, participantCount: ${groupCallInfo?.participantCount}")
                
                if (groupCallInfo != null && groupCallInfo.isActive) {
                    currentGroupCallInfo = groupCallInfo
                    joinGroupCall(groupCallInfo.id) // Automatically join the group call
                    displayActiveVideoChat(groupCallInfo)
                } else {
                    displayNoVideoChat()
                }
            } else {
                displayNoVideoChat()
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception checking video chat: ${e.message}")
            displayNoVideoChat()
        }
    }
    
    /**
     * Force refresh channel data to get latest status
     */
    private fun refreshChannelData() {
        val handle = channelHandle ?: return
        
        lifecycleScope.launch {
            try {
                println("TGLIVE: 🔄 FORCE REFRESHING channel data for handle: $handle")
                
                // Get fresh channel information
                val channelInfo = channelManager.getChannelByUsername(handle)
                currentChannelInfo = channelInfo
                
                // Display updated channel information
                displayChannelInfo(channelInfo)
                
                // Check for active video chat and display status
                checkAndDisplayVideoChat(channelInfo)
                
                // Also force refresh group call status if we have an active call
                currentGroupCallInfo?.let { groupCall ->
                    try {
                        println("TGLIVE: 🔄 Force refreshing group call status during manual refresh")
                        updatesHandler.refreshGroupCallStatus(groupCall.id)
                    } catch (e: Exception) {
                        println("TGLIVE: Exception refreshing group call during manual refresh: ${e.message}")
                    }
                }
                
                println("TGLIVE: 🔄 Channel data refreshed successfully")
                showUpdateNotification("✅ Refreshed successfully")
                
            } catch (e: Exception) {
                println("TGLIVE: Exception refreshing channel data: ${e.message}")
                e.printStackTrace()
                showUpdateNotification("❌ Refresh failed")
            }
        }
    }
    
    /**
     * Display active video chat status (TikTok style)
     */
    private fun displayActiveVideoChat(groupCallInfo: GroupCallInfo) {
        println("TGLIVE: 🎨 DISPLAYING ACTIVE VIDEO CHAT")
        println("TGLIVE: Participants: ${groupCallInfo.participantCount}")

        // Hide stream ended message
        binding.tvStreamEnded.visibility = View.GONE
        
        // Show viewer count in top right
        binding.llViewerCount.visibility = View.VISIBLE

        // Update participant count
        binding.tvViewerCount.text = groupCallInfo.participantCount.toString()

        // Refresh all video streams. This will dynamically manage the layout and rendering.
        refreshVideoStreams()

        println("TGLIVE: ✅ Viewer count set to: ${groupCallInfo.participantCount}")

        // Set up button click listeners
        setupTikTokButtonListeners()

        // CRITICAL: Subscribe to group call updates for real-time participant changes.
        // This is handled by setupGroupCallUpdates which is called after joining the call.

        println("TGLIVE: Active video chat displayed - ${groupCallInfo.participantCount} participants")
    }
    
    /**
     * Display no video chat status (TikTok style)
     */
    private fun displayNoVideoChat() {
        println("TGLIVE: 🎨 DISPLAYING NO VIDEO CHAT")

        // Stop all video renderings
        lifecycleScope.launch { stopAllVideoRenderings() }

        // Show camera off state in video player
        displayCameraOffState()

        // Show stream ended message in center
        binding.tvStreamEnded.visibility = View.VISIBLE

        // Hide viewer count
        binding.llViewerCount.visibility = View.GONE

        // Set up button listeners for no-stream state
        setupTikTokButtonListeners()

        println("TGLIVE: ✅ Stream ended message displayed")
    }
    
    /**
     * Set up INSTANT real-time updates for channel and group call changes
     * This is the CRITICAL method that enables INSTANT real-time updates
     */
    private fun setupInstantRealTimeUpdates(channelId: Long) {
        // Cancel any existing update subscriptions
        cancelUpdateSubscriptions()
        
        println("TGLIVE: 🚀 Setting up INSTANT real-time updates for channel ID: $channelId")
        
        // Subscribe to chat updates for instant video chat start/end notifications
        lifecycleScope.launch {
            try {
                val chatSubscribed = telegramClient.subscribeToChatUpdates(channelId)
                if (chatSubscribed) {
                    println("TGLIVE: ✅ Successfully subscribed to INSTANT chat updates")
                } else {
                    println("TGLIVE: ⚠️ Failed to subscribe to chat updates")
                }
            } catch (e: Exception) {
                println("TGLIVE: ❌ Exception subscribing to chat updates: ${e.message}")
            }
        }
        
        // Set up channel updates with proper error handling and lifecycle management
        channelUpdateJob = updateScope.launch {
            try {
                println("TGLIVE: Starting channel updates flow for channel ID: $channelId")
                
                updatesHandler.observeChannelUpdates(channelId)
                    .catch { exception ->
                        println("TGLIVE: Error in channel updates flow: ${exception.message}")
                        exception.printStackTrace()
                    }
                    .collect { update ->
                        println("TGLIVE: ✅ RECEIVED CHANNEL UPDATE: ${update.javaClass.simpleName}")
                        handleChannelUpdate(update)
                    }
            } catch (e: Exception) {
                println("TGLIVE: Exception setting up channel updates: ${e.message}")
                e.printStackTrace()
            }
        }
        
        // Set up group call updates if there's an active video chat.
        // This is now called after joining the group call.
        
        // Start smart refresh (minimal backup only)
        startSmartRefresh()
        
        println("TGLIVE: ✅ INSTANT real-time updates initialized for channel $channelId")
    }
    
    /**
     * Set up group call updates with proper lifecycle management
     */
    private fun setupGroupCallUpdates(groupCallId: Int) {
        // Cancel existing group call updates
        groupCallUpdateJob?.cancel()
        
        groupCallUpdateJob = updateScope.launch {
            try {
                println("TGLIVE: Setting up group call updates for call ID: $groupCallId")
                
                updatesHandler.observeGroupCallUpdates(groupCallId)
                    .catch { exception ->
                        println("TGLIVE: Error in group call updates flow: ${exception.message}")
                        exception.printStackTrace()
                    }
                    .collect { update ->
                        println("TGLIVE: ✅ RECEIVED GROUP CALL UPDATE: ${update.javaClass.simpleName}")
                        handleGroupCallUpdate(update)
                    }
            } catch (e: Exception) {
                println("TGLIVE: Exception setting up group call updates: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Handle channel updates in real-time
     */
    private suspend fun handleChannelUpdate(update: ChannelUpdate) {
        try {
            when (update) {
                is ChannelUpdate.VideoChatStarted -> {
                    try {
                        val groupCallInfo = groupCallManager.getGroupCall(update.groupCallId)
                        if (groupCallInfo != null) {
                            currentGroupCallInfo = groupCallInfo

                            // Update UI on main thread IMMEDIATELY
                            runOnUiThread {
                                displayActiveVideoChat(groupCallInfo)
                                showUpdateNotification("🔴 Live stream started!")
                            }

                            // Set up group call updates for the new call and join it
                            joinGroupCall(update.groupCallId)
                            setupGroupCallUpdates(update.groupCallId)
                        }
                    } catch (e: Exception) {
                        println("TGLIVE: Error getting group call: ${e.message}")
                    }
                }

                is ChannelUpdate.VideoChatEnded -> {
                    currentGroupCallInfo = null

                    // Cancel group call updates to save resources
                    groupCallUpdateJob?.cancel()
                    groupCallUpdateJob = null

                    // Update UI on main thread
                    runOnUiThread {
                        displayNoVideoChat()
                        showUpdateNotification("⚫ Live stream ended!")
                    }
                }

                is ChannelUpdate.InfoChanged -> {
                    println("TGLIVE: 📝 CHANNEL INFO CHANGED")
                    currentChannelInfo = update.channelInfo

                    runOnUiThread {
                        displayChannelInfo(update.channelInfo)
                        showUpdateNotification("Channel information updated")
                    }
                }

                is ChannelUpdate.MemberCountChanged -> {
                    println("TGLIVE: 👥 MEMBER COUNT CHANGED: ${update.newCount}")

                    runOnUiThread {
                        showUpdateNotification("Member count updated: ${formatMemberCount(update.newCount)}")
                    }

                    // Update current channel info if available
                    currentChannelInfo?.let { channelInfo ->
                        currentChannelInfo = channelInfo.copy(memberCount = update.newCount)
                    }
                }
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception handling channel update: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Handle group call updates in real-time
     */
    private suspend fun handleGroupCallUpdate(update: GroupCallUpdate) {
        try {
            when (update) {
                is GroupCallUpdate.ParticipantJoined -> {
                    println("TGLIVE: 👤 PARTICIPANT JOINED: ${update.participant.displayName}")

                    runOnUiThread {
                        currentGroupCallInfo?.let { groupCall ->
                            val newCount = groupCall.participantCount + 1
                            // Update viewer count in TikTok-style UI
                            binding.tvViewerCount.text = newCount.toString()
                            currentGroupCallInfo = groupCall.copy(participantCount = newCount)
                        }

                        showUpdateNotification("${update.participant.displayName} joined")
                    }
                }

                is GroupCallUpdate.ParticipantLeft -> {
                    println("TGLIVE: 👤 PARTICIPANT LEFT")

                    runOnUiThread {
                        currentGroupCallInfo?.let { groupCall ->
                            val newCount = maxOf(0, groupCall.participantCount - 1)
                            // Update viewer count in TikTok-style UI
                            binding.tvViewerCount.text = newCount.toString()
                            currentGroupCallInfo = groupCall.copy(participantCount = newCount)
                        }

                        showUpdateNotification("Participant left")
                    }
                }

                is GroupCallUpdate.ParticipantStatusChanged -> {
                    println("TGLIVE: 🔄 PARTICIPANT STATUS CHANGED: ${update.participant.displayName}")
                    // Update the active video streams map and refresh UI
                    val participantId = update.participant.tdId.toString()
                    val hasVideo = update.participant.hasVideo // Assuming GroupCallParticipant has hasVideo property
                    activeVideoStreams[participantId] = hasVideo
                    
                    runOnUiThread {
                        showUpdateNotification("${update.participant.displayName} status updated")
                        refreshVideoStreams()
                    }
                }

                is GroupCallUpdate.StatusChanged -> {
                    currentGroupCallInfo = update.groupCallInfo

                    runOnUiThread {
                        if (update.groupCallInfo.isActive) {
                            displayActiveVideoChat(update.groupCallInfo)
                            showUpdateNotification("🔴 ${update.groupCallInfo.participantCount} viewers")
                        } else {
                            displayNoVideoChat()
                            showUpdateNotification("⚫ Stream ended")
                        }
                    }
                }

                is GroupCallUpdate.CallEnded -> {
                    println("TGLIVE: 🚨 UI RECEIVED CALL ENDED 🚨")

                    // Leave the group call if we were joined
                    leaveGroupCall()

                    currentGroupCallInfo = null
                    isJoinedToCall = false

                    // Cancel group call updates
                    groupCallUpdateJob?.cancel()
                    groupCallUpdateJob = null

                    println("TGLIVE: 🎯 UPDATING UI FOR CALL ENDED")
                    runOnUiThread {
                        println("TGLIVE: 🔴 DISPLAYING NO VIDEO CHAT")
                        displayNoVideoChat()
                        showUpdateNotification("⚫ Live stream ended")

                        println("TGLIVE: ✅ CALL ENDED UI UPDATE COMPLETED")
                    }
                }
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception handling group call update: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Set up a general update listener to debug what updates we're actually receiving.
     * This is crucial for understanding TDLib's real-time update mechanism.
     */
    private fun setupGeneralUpdateListener() {
        updateScope.launch {
            try {
                println("TGLIVE: Setting up general update listener for debugging")
                updatesHandler.observeUpdates()
                    .collect { update ->
                        println("TGLIVE: 🔍 RAW UPDATE RECEIVED: ${update.javaClass.simpleName}")

                        // Log specific updates we care about for group calls and participants
                        when (update.constructor) {
                            TdApi.UpdateChatVideoChat.CONSTRUCTOR -> {
                                val videoChatUpdate = update as TdApi.UpdateChatVideoChat
                                println("TGLIVE: 📹 UpdateChatVideoChat - Chat: ${videoChatUpdate.chatId}, VideoChat: ${videoChatUpdate.videoChat}")
                                videoChatUpdate.videoChat?.let {
                                    println("TGLIVE: 📹 VideoChat GroupCallId: ${it.groupCallId}")
                                } ?: run {
                                    println("TGLIVE: 📹 VideoChat is NULL (chat ended)")
                                }
                            }
                            TdApi.UpdateGroupCall.CONSTRUCTOR -> {
                                val groupCallUpdate = update as TdApi.UpdateGroupCall
                                println("TGLIVE: 🔴 UpdateGroupCall - Call: ${groupCallUpdate.groupCall.id}, Participants: ${groupCallUpdate.groupCall.participantCount}, isActive: ${groupCallUpdate.groupCall.isActive}")
                            }
                            TdApi.UpdateGroupCallParticipant.CONSTRUCTOR -> {
                                val participantUpdate = update as TdApi.UpdateGroupCallParticipant
                                val participant = participantUpdate.participant
                                val participantUserId = (participant.participantId as? TdApi.MessageSenderUser)?.userId ?: 0L
                                println("TGLIVE: 👤 UpdateGroupCallParticipant - Call: ${participantUpdate.groupCallId}, User: $participantUserId, isMuted: ${participant.isMutedForAllUsers || participant.isMutedForCurrentUser}, hasVideo: ${participant.videoInfo != null}, volume: ${participant.volumeLevel}")
                                // Directly trigger video stream refresh on participant updates
                                refreshVideoStreams()
                            }
                        }
                    }
            } catch (e: Exception) {
                println("TGLIVE: Exception in general update listener: ${e.message}")
            }
        }
    }

    /**
     * Start smart refresh - only when needed, not on a timer.
     * This is much more efficient and provides instant updates.
     */
    private fun startSmartRefresh() {
        periodicRefreshJob?.cancel()

        periodicRefreshJob = updateScope.launch {
            try {
                // Only do a backup refresh every 5 minutes as a safety net
                // Real updates should come through TDLib instantly
                while (true) {
                    kotlinx.coroutines.delay(300000) // Every 5 minutes as backup only

                    println("TGLIVE: Safety backup refresh (should rarely be needed)")

                    // Only refresh if we have an active group call
                    currentGroupCallInfo?.let { groupCall ->
                        try {
                            updatesHandler.refreshGroupCallStatus(groupCall.id)
                            // Also refresh video streams during backup refresh to catch any missed states
                            refreshVideoStreams()
                        } catch (e: Exception) {
                            println("TGLIVE: Exception in backup refresh: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    println("TGLIVE: Exception in backup refresh loop: ${e.message}")
                }
            }
        }

        println("TGLIVE: Smart refresh started (5min backup only)")
    }

    /**
     * Test if we're actually receiving real-time updates.
     */
    private fun testRealTimeUpdates() {
        lifecycleScope.launch {
            try {
                println("TGLIVE: 🧪 TESTING REAL-TIME UPDATES")

                // Test 1: Check if we can get current user (tests basic TDLib connection)
                val getMeResult = telegramClient.send(TdApi.GetMe())
                when (getMeResult.constructor) {
                    TdApi.User.CONSTRUCTOR -> {
                        val user = getMeResult as TdApi.User
                        println("TGLIVE: ✅ TDLib connection working - User: ${user.firstName}")
                    }
                    else -> {
                        println("TGLIVE: ❌ TDLib connection issue")
                    }
                }

                // Test 2: Force a simple update by getting chats
                val getChatsResult = telegramClient.send(TdApi.GetChats(null, 10))
                println("TGLIVE: GetChats result: ${getChatsResult.javaClass.simpleName}")

                // Test 3: Check UpdatesHandler status
                val handlerWorking = updatesHandler.testUpdatesFlow()
                println("TGLIVE: UpdatesHandler working: $handlerWorking")

                println("TGLIVE: 🧪 Real-time update test completed")

            } catch (e: Exception) {
                println("TGLIVE: ❌ Exception testing real-time updates: ${e.message}")
            }
        }
    }
    
    /**
     * Join the active group call as a viewer.
     */
    private suspend fun joinGroupCall(groupCallId: Int) {
        if (isJoinedToCall) {
            println("TGLIVE: Already joined to group call $groupCallId")
            return
        }
        try {
            println("TGLIVE: Attempting to join group call $groupCallId")
            val joinResult = groupCallManager.joinGroupCall(groupCallId)
            if (joinResult) {
                isJoinedToCall = true
                println("TGLIVE: Successfully joined group call $groupCallId")
                showUpdateNotification("Joined live stream!")
                // Once joined, set up group call updates
                setupGroupCallUpdates(groupCallId)
            } else {
                println("TGLIVE: Failed to join group call $groupCallId")
                showError("Failed to join live stream")
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception joining group call $groupCallId: ${e.message}")
            showError("Error joining live stream: ${e.message}")
        }
    }

    /**
     * Leave the active group call.
     */
    private suspend fun leaveGroupCall() {
        if (!isJoinedToCall || currentGroupCallInfo == null) {
            println("TGLIVE: Not currently in a group call or already left.")
            return
        }
        try {
            println("TGLIVE: Attempting to leave group call ${currentGroupCallInfo!!.id}")
            val leaveResult = groupCallManager.leaveGroupCall(currentGroupCallInfo!!.id)
            if (leaveResult) {
                isJoinedToCall = false
                println("TGLIVE: Successfully left group call ${currentGroupCallInfo!!.id}")
                showUpdateNotification("Left live stream.")
            } else {
                println("TGLIVE: Failed to leave group call ${currentGroupCallInfo!!.id}")
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception leaving group call: ${e.message}")
        }
    }

    /**
     * Cancel active update subscriptions
     */
    private fun cancelUpdateSubscriptions() {
        channelUpdateJob?.cancel()
        groupCallUpdateJob?.cancel()
        periodicRefreshJob?.cancel()
        channelUpdateJob = null
        groupCallUpdateJob = null
        periodicRefreshJob = null
        println("TGLIVE: Update subscriptions cancelled")
    }
    
    override fun onPause() {
        super.onPause()
        
        // Pause updates processing and backup refresh to save battery
        println("TGLIVE: Activity paused - optimizing for battery")
        updatesHandler.pauseUpdates()
        
        // Cancel backup refresh when app is in background
        periodicRefreshJob?.cancel()
        periodicRefreshJob = null
    }
    
    override fun onResume() {
        super.onResume()
        
        // Resume updates processing for real-time updates
        println("TGLIVE: Activity resumed - resuming real-time updates")
        updatesHandler.resumeUpdates()
        
        // Restart backup refresh only when app is active
        startSmartRefresh()
        
        // Force refresh channel data when returning to the activity
        refreshChannelData()
        
        // Also force refresh group call status if we have an active call
        currentGroupCallInfo?.let { groupCall ->
            lifecycleScope.launch {
                try {
                    updatesHandler.refreshGroupCallStatus(groupCall.id)
                } catch (e: Exception) {
                    println("TGLIVE: Error refreshing group call: ${e.message}")
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()

        println("TGLIVE: Activity destroyed - cleaning up all resources")

        // Stop all video renderings
        try {
            lifecycleScope.launch { stopAllVideoRenderings() }
        } catch (e: Exception) {
            println("TGLIVE: Exception stopping video on destroy: ${e.message}")
        }

        // Clean up update subscriptions and coroutine scope
        try {
            cancelUpdateSubscriptions()
            updateScope.cancel()

            // Clean up keyboard listener
            keyboardLayoutListener?.let {
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
            }

            // Unsubscribe from chat updates and leave call when activity is destroyed
            currentChannelInfo?.id?.let { channelId ->
                lifecycleScope.launch {
                    leaveGroupCall() // Ensure we leave the call on activity destruction
                    telegramClient.unsubscribeFromChatUpdates(channelId)
                }
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception during destroy: ${e.message}")
        }
    }
    
    /**
     * Setup modal functionality for channel details
     */
    private fun setupModalFunctionality() {
        // Modal overlay click to hide modal
        binding.flModalOverlay.setOnClickListener {
            hideChannelModal()
        }

        // Prevent modal content clicks from closing modal
        binding.cvChannelModal.setOnClickListener {
            // Do nothing - prevents click from propagating to overlay
        }
    }
    
    /**
     * Show channel details modal with slide-up animation
     */
    private fun showChannelModal() {
        if (isModalVisible) return

        // Update modal content with current channel info
        updateModalContent()
        
        // Show overlay
        binding.flModalOverlay.visibility = View.VISIBLE
        binding.flModalOverlay.alpha = 0f
        
        // Position modal below screen
        binding.cvChannelModal.translationY = binding.cvChannelModal.height.toFloat()
        
        // Animate modal slide up
        binding.flModalOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
            
        binding.cvChannelModal.animate()
            .translationY(0f)
            .setDuration(300)
            .start()
            
        isModalVisible = true
    }
    
    /**
     * Hide channel details modal with slide-down animation
     */
    private fun hideChannelModal() {
        if (!isModalVisible) return
        
        // Animate modal slide down
        binding.flModalOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .start()
            
        binding.cvChannelModal.animate()
            .translationY(binding.cvChannelModal.height.toFloat())
            .setDuration(300)
            .withEndAction {
                binding.flModalOverlay.visibility = View.GONE
            }
            .start()
            
        isModalVisible = false
    }
    
    /**
     * Update modal content with current channel information
     */
    private fun updateModalContent() {
        currentChannelInfo?.let { channelInfo ->
            // Load and set profile pictures
            loadModalProfilePicture(channelInfo.photo)

            // Set channel title
            binding.tvModalChannelTitle.text = channelInfo.title

            // Set channel handle
            val handle = if (channelInfo.username.isNotEmpty()) "@${channelInfo.username}" else ""
            binding.tvModalChannelHandle.text = handle
            binding.tvModalChannelHandle.visibility = if (handle.isNotEmpty()) View.VISIBLE else View.GONE

            // Set member count
            binding.tvModalMemberCount.text = formatMemberCountNumber(channelInfo.memberCount)

            // Set description
            binding.tvModalChannelDescription.text = channelInfo.description
            binding.tvModalChannelDescription.visibility = if (channelInfo.description.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }
    
    /**
     * Format member count as number only (for modal stats)
     */
    private fun formatMemberCountNumber(count: Int): String {
        return when {
            count >= 1_000_000 -> "${count / 1_000_000}M"
            count >= 1_000 -> "${count / 1_000}K"
            else -> count.toString()
        }
    }
    
    /**
     * Handle back button press to close modal or comment input if open
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            isModalVisible -> {
                hideChannelModal()
            }
            binding.llCommentInputContainer.visibility == View.VISIBLE -> {
                toggleCommentInputVisibility(false)
            }
            else -> {
                super.onBackPressed()
            }
        }
    }
    
    /**
     * Set up TikTok-style button click listeners
     */
    private fun setupTikTokButtonListeners() {
        // Back button
        binding.ivBack.setOnClickListener {
            finish()
        }

        // Share button
        binding.ivShare.setOnClickListener {
            showUpdateNotification("Share functionality not implemented yet")
        }

        // Comment button
        binding.ivCommentButton.setOnClickListener {
            toggleCommentInputVisibility(true)
        }

        // Send button click listener (now part of llCommentInputContainer)
        binding.ivSendButton.setOnClickListener {
            val message = binding.etCommentInput.text.toString().trim()
            if (message.isNotEmpty()) {
                showUpdateNotification("Sending message: $message")
                // TODO: Implement sending message via TDLib
                binding.etCommentInput.text.clear()
                toggleCommentInputVisibility(false) // Hide input and keyboard after sending
            }
        }

        // Channel info section click listener to show modal
        binding.llChannelInfo.setOnClickListener {
            showChannelModal()
        }

        // Profile picture click listener to show modal
        binding.ivChannelProfileCenter.setOnClickListener {
            showChannelModal()
        }

        // Channel title click listener to show modal
        binding.tvChannelTitle.setOnClickListener {
            showChannelModal()
        }
    }

    /**
     * Load channel profile picture from Telegram photo data
     */
    private fun loadChannelProfilePicture(photoInfo: org.drinkless.tdlib.TdApi.ChatPhotoInfo?) {
        if (photoInfo?.small?.local?.path?.isNotEmpty() == true) {
            try {
                // Load small profile picture from local path
                val bitmap = android.graphics.BitmapFactory.decodeFile(photoInfo.small.local.path)
                if (bitmap != null) {
                    val circularBitmap = createCircularBitmap(bitmap)
                    binding.ivChannelProfileCenter.setImageBitmap(circularBitmap)
                    println("TGLIVE: Channel profile picture loaded successfully")
                } else {
                    // Use default image if bitmap is null
                    binding.ivChannelProfileCenter.setImageResource(R.mipmap.ic_launcher)
                    println("TGLIVE: Failed to decode channel profile picture, using default")
                }
            } catch (e: Exception) {
                // Use default image on error
                binding.ivChannelProfileCenter.setImageResource(R.mipmap.ic_launcher)
                println("TGLIVE: Error loading channel profile picture: ${e.message}")
            }
        } else {
            // Use default image if no photo available
            binding.ivChannelProfileCenter.setImageResource(R.mipmap.ic_launcher)
            println("TGLIVE: No channel profile picture available, using default")
        }
    }

    /**
     * Load modal profile picture from Telegram photo data
     */
    private fun loadModalProfilePicture(photoInfo: org.drinkless.tdlib.TdApi.ChatPhotoInfo?) {
        if (photoInfo?.small?.local?.path?.isNotEmpty() == true) {
            try {
                // Load small profile picture from local path
                val bitmap = android.graphics.BitmapFactory.decodeFile(photoInfo.small.local.path)
                if (bitmap != null) {
                    val circularBitmap = createCircularBitmap(bitmap)
                    binding.ivModalProfilePicture.setImageBitmap(circularBitmap)
                    println("TGLIVE: Modal profile picture loaded successfully")
                } else {
                    // Use default image if bitmap is null
                    binding.ivModalProfilePicture.setImageResource(R.mipmap.ic_launcher)
                    println("TGLIVE: Failed to decode modal profile picture, using default")
                }
            } catch (e: Exception) {
                // Use default image on error
                binding.ivModalProfilePicture.setImageResource(R.mipmap.ic_launcher)
                println("TGLIVE: Error loading modal profile picture: ${e.message}")
            }
        } else {
            // Use default image if no photo available
            binding.ivModalProfilePicture.setImageResource(R.mipmap.ic_launcher)
            println("TGLIVE: No modal profile picture available, using default")
        }
    }

    /**
     * Create a circular bitmap from a square bitmap
     */
    private fun createCircularBitmap(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val output = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)

        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }

        val rect = android.graphics.Rect(0, 0, size, size)
        val rectF = android.graphics.RectF(rect)

        canvas.drawOval(rectF, paint)

        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)

        val sourceRect = android.graphics.Rect(
            (bitmap.width - size) / 2,
            (bitmap.height - size) / 2,
            (bitmap.width + size) / 2,
            (bitmap.height + size) / 2
        )

        canvas.drawBitmap(bitmap, sourceRect, rect, paint)

        return output
    }

    /**
     * Initialize all video SurfaceViews for dynamic rendering.
     */
    private fun initializeVideoPlayers() {
        println("TGLIVE: Initializing multiple video SurfaceViews")

        videoSurfaceViews.add(binding.svVideoPlayer1)
        videoSurfaceViews.add(binding.svVideoPlayer2)
        videoSurfaceViews.add(binding.svVideoPlayer3)
        videoSurfaceViews.add(binding.svVideoPlayer4)

        videoSurfaceViews.forEachIndexed { index, surfaceView ->
            val holder = surfaceView.holder
            holder.setFormat(android.graphics.PixelFormat.RGBA_8888)
            videoSurfaceHolders.add(holder)

            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(currentHolder: SurfaceHolder) {
                    println("TGLIVE: Video surface $index created - ready for rendering")
                    isVideoPlayerInitialized = true
                    videoSurfaceHolders[index] = currentHolder
                    // Refresh streams to assign surfaces
                    refreshVideoStreams()
                }

                override fun surfaceChanged(currentHolder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    println("TGLIVE: Video surface $index changed: $width x $height, format: $format")
                    videoSurfaceHolders[index] = currentHolder
                }

                override fun surfaceDestroyed(currentHolder: SurfaceHolder) {
                    println("TGLIVE: Video surface $index destroyed")
                    // No need to set isVideoPlayerInitialized to false for all if one is destroyed
                    // We only care if at least one is initialized for rendering to start.
                    // This will be handled by stopAllVideoRenderings if the activity is destroyed.
                }
            })
        }
        println("TGLIVE: All video SurfaceViews initialized successfully")
    }

    /**
     * Dynamically refreshes video streams based on active participants and their video status.
     * Manages UI layout for 1-4 speakers.
     */
    private fun refreshVideoStreams() {
        if (!isVideoPlayerInitialized || videoSurfaceHolders.any { it == null }) {
            println("TGLIVE: Video players not ready for refreshing streams.")
            return
        }

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                println("TGLIVE: 🔄 Refreshing video streams...")
                val currentGroupCallId = currentGroupCallInfo?.id ?: return@launch
                val allParticipants = groupCallManager.getGroupCallParticipants(currentGroupCallId)
                val participantsWithVideo = allParticipants.filter { it.hasVideo }
                val activeSpeakersCount = participantsWithVideo.size

                // Hide all surfaces and rows initially
                videoSurfaceViews.forEach { it.visibility = View.GONE }
                binding.llVideoRow1.visibility = View.GONE
                binding.llVideoRow2.visibility = View.GONE
                stopAllVideoRenderings() // Clear all renderers before re-assigning

                when (activeSpeakersCount) {
                    0 -> {
                        displayCameraOffState()
                    }
                    else -> {
                        // Display up to 4 active speakers
                        for (i in 0 until minOf(activeSpeakersCount, 4)) {
                            val participant = participantsWithVideo[i]
                            val surfaceView = videoSurfaceViews[i]
                            val surfaceHolder = videoSurfaceHolders[i]

                            surfaceView.visibility = View.VISIBLE
                            if (i < 2) binding.llVideoRow1.visibility = View.VISIBLE
                            else binding.llVideoRow2.visibility = View.VISIBLE

                            surfaceHolder?.let {
                                telegramClient.setVideoRenderer(currentGroupCallInfo!!.id, participant.tdId, it.surface)
                                println("TGLIVE: Rendering video for participant ${participant.tdId} on surface ${i+1}")
                            }
                        }
                        displayVideoPlayingState()
                    }
                }
            } catch (e: Exception) {
                println("TGLIVE: Exception refreshing video streams: ${e.message}")
                e.printStackTrace()
                displayCameraOffState()
            }
        }
    }

    /**
     * Stop all video renderers.
     */
    private suspend fun stopAllVideoRenderings() {
        try {
            println("TGLIVE: Stopping all video renderings")
            currentGroupCallInfo?.let { groupCall ->
                // Clear renderers for all actively streaming participants
                activeVideoStreams.keys.forEach { participantUserIdString ->
                    val participantUserId = participantUserIdString.toLong()
                    telegramClient.clearVideoRenderer(groupCall.id, participantUserId)
                }
                activeVideoStreams.clear() // Clear the map after stopping renderers
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception stopping all video renderings: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Display camera off state.
     */
    private fun displayCameraOffState() {
        binding.llVideoGrid.visibility = View.GONE // Hide the video grid
        binding.tvCameraOff.visibility = View.VISIBLE // Show "Camera Off" text
        binding.flVideoPlayerContainer.setBackgroundColor(android.graphics.Color.BLACK) // Set black background
        // Clear any active video flags
        activeVideoStreams.clear()
        println("TGLIVE: Camera off state displayed")
    }

    /**
     * Display video playing state.
     */
    private fun displayVideoPlayingState() {
        binding.llVideoGrid.visibility = View.VISIBLE // Show the video grid
        binding.tvCameraOff.visibility = View.GONE // Hide "Camera Off" text
        binding.flVideoPlayerContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT) // Transparent background for video
        println("TGLIVE: Video playing state displayed")
    }

    // Helper methods
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // Show error message in center
        binding.tvErrorMessage.text = message
        binding.tvErrorMessage.visibility = View.VISIBLE
        binding.tvStreamEnded.visibility = View.GONE
        binding.llViewerCount.visibility = View.GONE
        binding.flVideoPlayerContainer.visibility = View.GONE
        binding.llCommentsSection.visibility = View.GONE // Hide comments on error
        binding.llCommentInputContainer.visibility = View.GONE // Hide comment input container on error

        // Set channel title to error
        binding.tvChannelTitle.text = "Error"
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            // Hide other messages when loading
            binding.tvStreamEnded.visibility = View.GONE
            binding.tvErrorMessage.visibility = View.GONE
            binding.llViewerCount.visibility = View.GONE
            binding.flVideoPlayerContainer.visibility = View.GONE
            binding.llCommentsSection.visibility = View.GONE // Hide comments on loading
            binding.llCommentInputContainer.visibility = View.GONE // Hide comment input container on loading

            binding.tvChannelTitle.text = "Loading..."
        } else {
            // Show video player container and comments when not loading (if no error)
            binding.flVideoPlayerContainer.visibility = View.VISIBLE
            binding.llCommentsSection.visibility = View.VISIBLE
            // Note: Comment input container visibility is managed by toggleCommentInputVisibility
        }
    }

    private fun formatMemberCount(count: Int): String {
        return when {
            count >= 1_000_000 -> "${count / 1_000_000}M members"
            count >= 1_000 -> "${count / 1_000}K members"
            else -> "$count members"
        }
    }

    private fun showUpdateNotification(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        println("TGLIVE: Update notification: $message")
    }

    private fun getErrorMessage(error: ChannelError): String {
        return when (error) {
            is ChannelError.ChannelNotFound -> "Channel not found"
            is ChannelError.InvalidUsername -> "Invalid channel username"
            is ChannelError.NotAChannel -> "This is not a public channel"
            is ChannelError.PrivateChannel -> "This channel is private"
            is ChannelError.NetworkError -> "Network error. Please check your connection"
            is ChannelError.TdLibError -> "Telegram error: ${error.message}"
        }
    }

    private fun toggleCommentInputVisibility(show: Boolean) {
        if (show) {
            binding.llCommentInputContainer.visibility = View.VISIBLE
            binding.etCommentInput.requestFocus()
            showKeyboard(binding.etCommentInput)
        } else {
            binding.llCommentInputContainer.visibility = View.GONE
            hideKeyboard()
            
            // Reset bottom margin when hiding
            val layoutParams = binding.llCommentInputContainer.layoutParams as android.widget.RelativeLayout.LayoutParams
            layoutParams.bottomMargin = 0
            binding.llCommentInputContainer.layoutParams = layoutParams
        }
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etCommentInput.windowToken, 0)
    }

    private fun setupCommentInput() {
        binding.etCommentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.ivSendButton.isEnabled = !s.isNullOrEmpty()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /**
     * Setup keyboard visibility listener to hide comment input when keyboard is dismissed
     */
    private fun setupKeyboardListener() {
        keyboardLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            binding.root.getWindowVisibleDisplayFrame(rect)
            val screenHeight = binding.root.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            val wasKeyboardVisible = isKeyboardVisible
            isKeyboardVisible = keypadHeight > screenHeight * 0.15 // If more than 15% of screen is covered

            // Adjust comment input position based on keyboard visibility
            val layoutParams = binding.llCommentInputContainer.layoutParams as android.widget.RelativeLayout.LayoutParams
            
            if (isKeyboardVisible && !wasKeyboardVisible) {
                // Keyboard just appeared - move comment input above keyboard
                layoutParams.bottomMargin = keypadHeight
                binding.llCommentInputContainer.layoutParams = layoutParams
            } else if (!isKeyboardVisible && wasKeyboardVisible) {
                // Keyboard just disappeared - reset position and hide comment input
                layoutParams.bottomMargin = 0
                binding.llCommentInputContainer.layoutParams = layoutParams
                
                if (binding.llCommentInputContainer.visibility == View.VISIBLE) {
                    toggleCommentInputVisibility(false)
                }
            }
        }
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(keyboardLayoutListener)
    }

    /**
     * Setup touch outside listener to hide comment input and keyboard when clicking outside
     */
    private fun setupTouchOutsideListener() {
        binding.root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (binding.llCommentInputContainer.visibility == View.VISIBLE) {
                    val outRect = Rect()
                    binding.llCommentInputContainer.getGlobalVisibleRect(outRect)
                    if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                        toggleCommentInputVisibility(false)
                        return@setOnTouchListener true // Consume the touch event
                    }
                }
            }
            false // Let other touch events be handled
        }
    }
}