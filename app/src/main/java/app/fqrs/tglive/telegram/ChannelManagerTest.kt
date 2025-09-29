package app.fqrs.tglive.telegram

import app.fqrs.tglive.models.ChannelError
import kotlinx.coroutines.runBlocking

/**
 * Manual integration test for ChannelManager
 * This is for manual testing with real Telegram data only
 * Requirements: Production-only testing approach as specified in design document
 */
class ChannelManagerTest {
    
    /**
     * Manual test method to verify ChannelManager functionality
     * This should be called manually with a real TelegramClient instance
     * 
     * Usage example:
     * val client = TelegramClient(context)
     * client.initialize()
     * val channelManager = ChannelManager(client)
     * val test = ChannelManagerTest()
     * test.testChannelOperations(channelManager)
     */
    fun testChannelOperations(channelManager: ChannelManager) {
        runBlocking {
            println("=== ChannelManager Manual Test ===")
            
            // Test 1: Valid public channel
            try {
                println("Test 1: Getting channel info for @telegram")
                val channelInfo = channelManager.getChannelByUsername("@telegram")
                println("✓ Success: ${channelInfo.title} (@${channelInfo.username})")
                println("  Members: ${channelInfo.memberCount}")
                println("  Description: ${channelInfo.description.take(100)}...")
                println("  Has video chat: ${channelInfo.hasActiveVideoChat}")
            } catch (e: ChannelError) {
                println("✗ Error: ${e.message}")
            }
            
            // Test 2: Invalid username format
            try {
                println("\nTest 2: Testing invalid username")
                channelManager.getChannelByUsername("invalid")
                println("✗ Should have thrown error")
            } catch (e: ChannelError.InvalidUsername) {
                println("✓ Correctly caught invalid username: ${e.message}")
            } catch (e: ChannelError) {
                println("✗ Wrong error type: ${e.message}")
            }
            
            // Test 3: Non-existent channel
            try {
                println("\nTest 3: Testing non-existent channel")
                channelManager.getChannelByUsername("@nonexistentchannel12345")
                println("✗ Should have thrown error")
            } catch (e: ChannelError.ChannelNotFound) {
                println("✓ Correctly caught channel not found: ${e.message}")
            } catch (e: ChannelError) {
                println("✗ Wrong error type: ${e.message}")
            }
            
            // Test 4: Empty username
            try {
                println("\nTest 4: Testing empty username")
                channelManager.getChannelByUsername("")
                println("✗ Should have thrown error")
            } catch (e: ChannelError.InvalidUsername) {
                println("✓ Correctly caught empty username: ${e.message}")
            } catch (e: ChannelError) {
                println("✗ Wrong error type: ${e.message}")
            }
            
            // Test 5: Username without @ prefix
            try {
                println("\nTest 5: Testing username without @ prefix")
                val channelInfo = channelManager.getChannelByUsername("telegram")
                println("✓ Success without @ prefix: ${channelInfo.title}")
            } catch (e: ChannelError) {
                println("✗ Error: ${e.message}")
            }
            
            println("\n=== Test Complete ===")
        }
    }
    
    /**
     * Test channel full info functionality
     */
    fun testChannelFullInfo(channelManager: ChannelManager, channelId: Long) {
        runBlocking {
            println("=== Testing getChannelFullInfo ===")
            
            try {
                val channelInfo = channelManager.getChannelFullInfo(channelId)
                println("✓ Full info retrieved: ${channelInfo.title}")
                println("  Username: @${channelInfo.username}")
                println("  Members: ${channelInfo.memberCount}")
                println("  Description length: ${channelInfo.description.length}")
                println("  Has video chat: ${channelInfo.hasActiveVideoChat}")
                if (channelInfo.hasActiveVideoChat) {
                    println("  Video chat ID: ${channelInfo.activeVideoChatId}")
                }
            } catch (e: ChannelError) {
                println("✗ Error getting full info: ${e.message}")
            }
        }
    }
}