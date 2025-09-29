# Implementation Plan

- [x] 1. Set up data models and core interfaces
  - Create ChannelInfo, GroupCallInfo, and GroupCallParticipant data classes
  - Define ChannelError sealed class for error handling
  - Create update model classes (ChannelUpdate, GroupCallUpdate)
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 5.1_

- [x] 2. Implement ChannelManager for channel operations
  - Create ChannelManager class with TelegramClient integration
  - Implement getChannelByUsername method using TdApi.SearchPublicChat
  - Add channel validation to ensure only public channels are processed
  - Implement getChannelFullInfo method for detailed channel data
  - Add error handling for invalid usernames and non-existent channels
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 7.1, 7.2, 7.3, 7.4_

- [x] 3. Create GroupCallManager for video chat monitoring
  - Implement GroupCallManager class with TelegramClient integration
  - Add getGroupCall method to fetch group call information
  - Implement getGroupCallParticipants method for participant data
  - Create joinGroupCall and leaveGroupCall methods
  - Add methods to track participant status (muted, speaking, video)
  - _Requirements: 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 4. Implement central UpdatesHandler for real-time updates
  - Create UpdatesHandler class with client.setUpdatesHandler integration
  - Implement update processing using when statement for different update types
  - Add observeChannelUpdates method returning Flow<ChannelUpdate>
  - Implement observeGroupCallUpdates method returning Flow<GroupCallUpdate>
  - Handle UpdateGroupCall, UpdateGroupCallParticipant, and UpdateChatVideoChat
  - Ensure updates are processed without blocking UI thread
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [x] 5. Enhance MainActivity profile page with channel input
  - Add EditText with "@" prefix that cannot be edited
  - Implement input validation for channel handles
  - Create "Show" button with click handler
  - Add navigation to ChannelInfoActivity with channel handle parameter
  - Implement error handling for empty input
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 8.1_

- [x] 6. Create ChannelInfoActivity layout and UI components
  - Design activity_channel_info.xml layout
  - Add channel information display sections (title, description, handle, member count)
  - Create profile picture ImageView with circular transformation
  - Add video chat status section with participant count display
  - Design participant grid using RecyclerView
  - Add "View Participants (Join Required)" button
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 4.1, 4.2_

- [x] 7. Implement ChannelInfoActivity business logic
  - Create ChannelInfoActivity class extending AppCompatActivity
  - Initialize ChannelManager, GroupCallManager, and UpdatesHandler
  - Implement channel data loading and display methods
  - Add video chat status detection and display
  - Handle navigation from MainActivity with channel handle parameter
  - Implement back navigation to profile page
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 8.1, 8.2, 8.3, 8.4_

- [x] 8. Create participant grid RecyclerView adapter
  - Implement ParticipantAdapter extending RecyclerView.Adapter
  - Create participant_item.xml layout with username, status columns
  - Add ViewHolder with participant data binding
  - Implement status indicators for Camera/Screen, Speaking, Muted
  - Add real-time update methods for participant status changes
  - Handle participant join/leave animations
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 9. Implement real-time UI updates in ChannelInfoActivity
  - Set up coroutine scopes for observing update flows
  - Implement channel information real-time updates
  - Add video chat status real-time monitoring
  - Create participant grid real-time update handlers
  - Ensure updates are processed within 1 second requirement
  - Handle activity lifecycle for update subscriptions
  - _Requirements: 5.2, 5.3, 5.4, 5.5, 6.3, 6.4, 6.5, 6.6_

- [x] 10. Add group call join/leave functionality
  - Implement join button click handler in ChannelInfoActivity
  - Add group call join logic using GroupCallManager
  - Show detailed participant information after joining
  - Implement leave functionality with UI state updates
  - Handle join errors and display appropriate messages
  - Update participant grid visibility based on join status
  - _Requirements: 4.2, 4.3, 4.4_

- [x] 11. Integrate all components and test with production data
  - Wire up all managers in ChannelInfoActivity
  - Test channel resolution with real public channels
  - Verify video chat detection with live channels
  - Test participant monitoring with actual video chats
  - Validate real-time updates with live data
  - Test error handling with invalid inputs
  - Ensure smooth navigation between activities
  - _Requirements: All requirements integration testing_