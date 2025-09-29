# Design Document

## Overview

This design extends the existing Android Telegram client to add channel information display and real-time live video chat monitoring capabilities. The solution leverages the existing TDLib integration and follows the current architecture patterns using Kotlin coroutines and Android's MVVM-like structure.

## Architecture

### High-Level Architecture

The feature follows the existing layered architecture:

```
UI Layer (Activities/Fragments)
    ↓
Manager Layer (Business Logic)
    ↓
TelegramClient Layer (TDLib Integration)
    ↓
TDLib (Native Library)
```

### Key Components

1. **ChannelInfoActivity** - New activity for displaying channel details
2. **ChannelManager** - Business logic for channel operations
3. **GroupCallManager** - Business logic for video chat monitoring
4. **UpdatesHandler** - Central handler for real-time TDLib updates
5. **UI Components** - Custom views for participant grid and status displays

## Components and Interfaces

### 1. ChannelManager

```kotlin
class ChannelManager(private val client: TelegramClient) {
    suspend fun getChannelByUsername(username: String): ChannelInfo?
    suspend fun getChannelFullInfo(channelId: Long): ChannelFullInfo?
    suspend fun joinChannel(channelId: Long): Boolean
    suspend fun leaveChannel(channelId: Long): Boolean
}
```

**Responsibilities:**
- Validate and resolve channel usernames
- Fetch channel basic and detailed information
- Handle channel join/leave operations
- Validate that target is a public channel (not group/user)

### 2. GroupCallManager

```kotlin
class GroupCallManager(private val client: TelegramClient) {
    suspend fun getGroupCall(groupCallId: Int): GroupCallInfo?
    suspend fun joinGroupCall(groupCallId: Int): Boolean
    suspend fun leaveGroupCall(groupCallId: Int): Boolean
    suspend fun getGroupCallParticipants(groupCallId: Int): List<GroupCallParticipant>
    fun observeGroupCallUpdates(): Flow<GroupCallUpdate>
}
```

**Responsibilities:**
- Monitor active group calls in channels
- Handle group call join/leave operations
- Track participant status changes
- Provide real-time updates for UI

### 3. UpdatesHandler

```kotlin
class UpdatesHandler(private val client: TelegramClient) {
    private val updateFlow = MutableSharedFlow<TdApi.Update>()
    
    fun initialize()
    fun observeUpdates(): SharedFlow<TdApi.Update>
    fun observeChannelUpdates(channelId: Long): Flow<ChannelUpdate>
    fun observeGroupCallUpdates(groupCallId: Int): Flow<GroupCallUpdate>
}
```

**Responsibilities:**
- Central hub for all TDLib updates
- Filter and route updates to appropriate managers
- Provide typed update flows for UI components
- Handle update processing without blocking UI

### 4. ChannelInfoActivity

```kotlin
class ChannelInfoActivity : AppCompatActivity() {
    private lateinit var channelManager: ChannelManager
    private lateinit var groupCallManager: GroupCallManager
    private lateinit var updatesHandler: UpdatesHandler
    
    private fun displayChannelInfo(channel: ChannelInfo)
    private fun displayGroupCallStatus(groupCall: GroupCallInfo?)
    private fun displayParticipants(participants: List<GroupCallParticipant>)
    private fun setupRealTimeUpdates()
}
```

**Responsibilities:**
- Display channel information and status
- Handle user interactions (join/leave calls)
- Update UI in real-time based on TDLib updates
- Manage participant grid display

## Data Models

### ChannelInfo
```kotlin
data class ChannelInfo(
    val id: Long,
    val title: String,
    val username: String,
    val description: String,
    val memberCount: Int,
    val photo: TdApi.ChatPhoto?,
    val hasActiveVideoChat: Boolean,
    val activeVideoChatId: Int?
)
```

### GroupCallInfo
```kotlin
data class GroupCallInfo(
    val id: Int,
    val title: String,
    val participantCount: Int,
    val isActive: Boolean,
    val canBeManaged: Boolean,
    val isJoined: Boolean
)
```

### GroupCallParticipant
```kotlin
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
```

### Update Models
```kotlin
sealed class ChannelUpdate {
    data class InfoChanged(val channelInfo: ChannelInfo) : ChannelUpdate()
    data class MemberCountChanged(val newCount: Int) : ChannelUpdate()
    data class VideoChatStarted(val groupCallId: Int) : ChannelUpdate()
    data class VideoChatEnded : ChannelUpdate()
}

sealed class GroupCallUpdate {
    data class ParticipantJoined(val participant: GroupCallParticipant) : GroupCallUpdate()
    data class ParticipantLeft(val participantId: TdApi.MessageSender) : GroupCallUpdate()
    data class ParticipantStatusChanged(val participant: GroupCallParticipant) : GroupCallUpdate()
    data class CallEnded : GroupCallUpdate()
}
```

## Error Handling

### Input Validation
- Validate channel username format (@channelname)
- Check if target is a public channel (not private/group/user)
- Handle network connectivity issues
- Validate TDLib responses

### Error Types
```kotlin
sealed class ChannelError {
    object InvalidUsername : ChannelError()
    object ChannelNotFound : ChannelError()
    object NotAChannel : ChannelError()
    object PrivateChannel : ChannelError()
    object NetworkError : ChannelError()
    data class TdLibError(val code: Int, val message: String) : ChannelError()
}
```

### Error Display
- Toast messages for user-facing errors
- Retry mechanisms for network failures
- Graceful degradation when features unavailable
- Loading states during operations

## Testing Strategy

### IMPORTANT: NO MOCKING OR UNIT TESTS
This implementation will **NOT** use:
- ❌ Unit tests
- ❌ Mock objects or mocking frameworks
- ❌ Dummy data or fake responses
- ❌ Test doubles or stubs
- ❌ Isolated component testing
- ❌ Automated test suites

### Production-Only Testing Approach
All testing will be done directly with:
- ✅ **Real Telegram production API**
- ✅ **Actual public channels**
- ✅ **Live video chats with real participants**
- ✅ **Production TDLib integration**
- ✅ **Real-time data from Telegram servers**

### Manual Testing with Live Data
1. **Channel Information Display**
   - Test with real public channels (e.g., @telegram, @durov)
   - Verify actual channel data display
   - Test with non-existent channels for error handling
   - Use real channel profile pictures and member counts

2. **Video Chat Monitoring**
   - Find channels with active live video chats
   - Test real participant count display
   - Join actual video chats to test participant grid
   - Monitor real participants' status changes

3. **Real-Time Updates**
   - Test with live video chats where participants actually join/leave
   - Verify real mute/unmute events from actual users
   - Test real video/screen sharing toggles
   - Monitor actual speaking status from live participants

### Live Environment Validation
- Use production Telegram API credentials
- Test with real user accounts and channels
- Validate against actual Telegram behavior
- Ensure synchronization with official Telegram clients

## Implementation Details

### TDLib API Usage

**Channel Resolution:**
```kotlin
// Resolve username to channel
val result = client.send(TdApi.SearchPublicChat(username))
if (result is TdApi.Chat && result.type is TdApi.ChatTypeSupergroup) {
    val supergroup = result.type as TdApi.ChatTypeSupergroup
    if (!supergroup.isChannel) {
        throw ChannelError.NotAChannel
    }
}
```

**Group Call Information:**
```kotlin
// Get group call info
val result = client.send(TdApi.GetGroupCall(groupCallId))
if (result is TdApi.GroupCall) {
    // Process group call data
}
```

**Real-Time Updates:**
```kotlin
// In UpdatesHandler
client.setUpdatesHandler { update ->
    when (update.constructor) {
        TdApi.UpdateGroupCall.CONSTRUCTOR -> {
            val groupCallUpdate = update as TdApi.UpdateGroupCall
            // Process group call updates
        }
        TdApi.UpdateGroupCallParticipant.CONSTRUCTOR -> {
            val participantUpdate = update as TdApi.UpdateGroupCallParticipant
            // Process participant updates
        }
        TdApi.UpdateChatVideoChat.CONSTRUCTOR -> {
            val videoChatUpdate = update as TdApi.UpdateChatVideoChat
            // Process video chat status changes
        }
    }
}
```

### UI Implementation

**Profile Page Enhancement:**
- Add text input with "@" prefix
- Implement input validation
- Add "Show" button with click handler
- Navigate to ChannelInfoActivity

**Channel Details Page:**
- Display channel information in card layout
- Show video chat status prominently
- Implement participant grid with RecyclerView
- Add join/leave buttons with proper states

**Real-Time UI Updates:**
```kotlin
// Observe updates in Activity
lifecycleScope.launch {
    updatesHandler.observeGroupCallUpdates(groupCallId)
        .collect { update ->
            when (update) {
                is GroupCallUpdate.ParticipantStatusChanged -> {
                    updateParticipantInGrid(update.participant)
                }
                is GroupCallUpdate.ParticipantJoined -> {
                    addParticipantToGrid(update.participant)
                }
                is GroupCallUpdate.ParticipantLeft -> {
                    removeParticipantFromGrid(update.participantId)
                }
            }
        }
}
```

### Performance Considerations

1. **Memory Management**
   - Use ViewHolder pattern for participant grid
   - Implement proper lifecycle management
   - Clean up observers on activity destroy

2. **Network Efficiency**
   - Cache channel information
   - Batch participant updates
   - Use appropriate update frequencies

3. **UI Responsiveness**
   - Process updates on background threads
   - Use coroutines for async operations
   - Implement proper loading states

### Security Considerations

1. **Input Validation**
   - Sanitize channel username input
   - Validate TDLib responses
   - Handle malformed data gracefully

2. **Privacy**
   - Only display public channel information
   - Respect user privacy settings
   - Handle permissions appropriately

3. **Error Information**
   - Don't expose internal error details
   - Log sensitive information securely
   - Provide user-friendly error messages