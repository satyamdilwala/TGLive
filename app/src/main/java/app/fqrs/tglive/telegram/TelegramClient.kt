package app.fqrs.tglive.telegram

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class TelegramClient(private val context: Context) {
    private var client: Client? = null
    private val requestId = AtomicLong(1)
    private val handlers = ConcurrentHashMap<Long, CompletableDeferred<TdApi.Object>>()
    
    // Configuration from TG_Config.md
    private val apiId = 29614720
    private val apiHash = "9bd4f5fb282140d0a399f312c0a22435"
    
    private val authStateChannel = Channel<TdApi.AuthorizationState>(Channel.UNLIMITED)
    
    // UpdatesHandler for real-time updates
    private var updatesHandler: UpdatesHandler? = null
    
    init {
        try {
            println("TGLIVE: Setting TDLib log verbosity")
            Client.execute(TdApi.SetLogVerbosityLevel(2))
            println("TGLIVE: Creating TDLib client")
            createClient()
            println("TGLIVE: TDLib client created successfully")
        } catch (e: Exception) {
            println("TGLIVE: Failed to initialize TDLib: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun createClient() {
        client = Client.create({ update ->
            println("TGLIVE: 🔥 TDLib Update Received: ${update?.javaClass?.simpleName}")
            
            // Log specific updates we care about
            when (update?.constructor) {
                TdApi.UpdateAuthorizationState.CONSTRUCTOR -> {
                    val authUpdate = update as TdApi.UpdateAuthorizationState
                    println("TGLIVE: Auth state update: ${authUpdate.authorizationState.javaClass.simpleName}")
                    authStateChannel.trySend(authUpdate.authorizationState)
                }
                else -> {
                    // Forward ALL non-auth updates to UpdatesHandler (including UpdateChatVideoChat, UpdateGroupCall, etc.)
                    if (update is TdApi.Update) {
                        println("TGLIVE: Forwarding update to UpdatesHandler: ${update.javaClass.simpleName}")
                        
                        // Minimal logging for performance - only critical updates
                        when (update.constructor) {
                            TdApi.UpdateChatVideoChat.CONSTRUCTOR -> {
                                val videoChatUpdate = update as TdApi.UpdateChatVideoChat
                                println("TGLIVE: VideoChat ${if (videoChatUpdate.videoChat?.groupCallId != 0) "started" else "ended"}")
                            }
                            TdApi.UpdateGroupCall.CONSTRUCTOR -> {
                                val groupCallUpdate = update as TdApi.UpdateGroupCall
                                if (groupCallUpdate.groupCall.isActive) {
                                    println("TGLIVE: GroupCall active - ${groupCallUpdate.groupCall.participantCount} participants")
                                }
                            }
                            // Skip logging for participant updates to reduce CPU overhead
                        }
                        
                        if (updatesHandler != null) {
                            updatesHandler?.emitUpdate(update)
                            println("TGLIVE: Update forwarded successfully")
                        } else {
                            println("TGLIVE: WARNING: No UpdatesHandler set, update lost: ${update.javaClass.simpleName}")
                        }
                    }
                }
            }
        }, { exception ->
            println("TGLIVE: Client exception: $exception")
        }) { error ->
            println("TGLIVE: Client error: $error")
        }
    }
    
    suspend fun send(function: TdApi.Function<*>): TdApi.Object {
        return withContext(Dispatchers.IO) {
            val id = requestId.getAndIncrement()
            val deferred = CompletableDeferred<TdApi.Object>()
            handlers[id] = deferred
            
            println("TGLIVE: Sending function: ${function.javaClass.simpleName} with ID: $id")
            
            client?.send(function, { result ->
                println("TGLIVE: Received result for ID $id: ${result?.javaClass?.simpleName}")
                val handler = handlers.remove(id)
                if (handler != null) {
                    handler.complete(result ?: TdApi.Error(500, "Null result"))
                } else {
                    println("TGLIVE: No handler found for ID $id")
                }
            }) { error ->
                println("TGLIVE: Received error for ID $id: $error")
                val handler = handlers.remove(id)
                handler?.complete(TdApi.Error(500, "Send failed: $error"))
            }
            
            // Add timeout to prevent hanging
            withTimeoutOrNull(30000) {
                deferred.await()
            } ?: TdApi.Error(408, "Request timeout")
        }
    }
    
    private var isInitialized = false
    
    private fun getManufacturer(): String {
        return try {
            Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun getDeviceModel(): String {
        return try {
            Build.MODEL
        } catch (e: Exception) {
            "Android Device"
        }
    }
    
    private fun getDeviceName(): String {
        return try {
            // Try to get a user-friendly device name
            val model = Build.MODEL
            val manufacturer = Build.MANUFACTURER
            
            // Remove manufacturer prefix if it exists in model name
            if (model.lowercase().startsWith(manufacturer.lowercase())) {
                model
            } else {
                // For devices like "Galaxy S21", "Pixel 7", "Mi 11"
                model
            }
        } catch (e: Exception) {
            "Android Device"
        }
    }
    
    private fun getFullDeviceModel(): String {
        return try {
            val manufacturer = getManufacturer()
            val model = getDeviceModel()
            if (model.startsWith(manufacturer)) {
                model
            } else {
                "$manufacturer $model"
            }
        } catch (e: Exception) {
            "Android Device"
        }
    }
    
    private fun getAndroidVersion(): String {
        return try {
            // Get the actual Android version
            val release = Build.VERSION.RELEASE
            println("TGLIVE: Detected Android version: $release")
            release
        } catch (e: Exception) {
            println("TGLIVE: Failed to get Android version: ${e.message}")
            "Unknown"
        }
    }
    
    private fun getApiLevel(): Int {
        return try {
            Build.VERSION.SDK_INT
        } catch (e: Exception) {
            0
        }
    }
    
    private fun getSystemVersion(): String {
        return try {
            "Android ${getAndroidVersion()} (API ${getApiLevel()})"
        } catch (e: Exception) {
            "Android"
        }
    }
    
    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                "${packageInfo.versionName} (${packageInfo.longVersionCode})"
            } else {
                @Suppress("DEPRECATION")
                "${packageInfo.versionName} (${packageInfo.versionCode})"
            }
        } catch (e: Exception) {
            "1.0"
        }
    }
    
    private fun getBrand(): String {
        return try {
            Build.BRAND.replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            "Android"
        }
    }
    
    suspend fun initialize(): Boolean {
        return try {
            // If already initialized, return quickly
            if (isInitialized && client != null) {
                println("TGLIVE: TelegramClient already initialized")
                return true
            }
            
            println("TGLIVE: Initializing TelegramClient with API ID: $apiId")
            
            // If client is null, recreate it
            if (client == null) {
                println("TGLIVE: Client is null, creating new client")
                createClient()
            }
            
            // Check current auth state first
            val currentAuthState = send(TdApi.GetAuthorizationState())
            println("TGLIVE: Current auth state: ${currentAuthState.javaClass.simpleName}")
            
            // If we're already past the parameters stage, we don't need to set them again
            if (currentAuthState.constructor != TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR) {
                println("TGLIVE: TDLib already initialized, current state: ${currentAuthState.javaClass.simpleName}")
                isInitialized = true
                return true
            }
            
            // Set TDLib parameters only if needed
            val databaseDir = context.filesDir.absolutePath + "/tdlib"
            val filesDir = context.cacheDir.absolutePath + "/tdlib"
            
            println("TGLIVE: Database directory: $databaseDir")
            println("TGLIVE: Files directory: $filesDir")
            
            val deviceInfo = getCompleteDeviceInfo()
            
            // Create session string for Telegram with complete device ID
            val sessionString = "${deviceInfo.appName} ${deviceInfo.appVersion}, ${deviceInfo.deviceName}, ${deviceInfo.deviceModel}, ${deviceInfo.manufacturer}, Android ${deviceInfo.androidVersion}, ${deviceInfo.platform}, ${deviceInfo.deviceId}"
            
            println("TGLIVE: Complete Device Info:")
            println("  App: ${deviceInfo.appName} ${deviceInfo.appVersion}")
            println("  Device Name: ${deviceInfo.deviceName}")
            println("  Device Model: ${deviceInfo.deviceModel}")
            println("  Manufacturer: ${deviceInfo.manufacturer}")
            println("  Brand: ${deviceInfo.brand}")
            println("  Android Version: ${deviceInfo.androidVersion}")
            println("  Platform: ${deviceInfo.platform}")
            println("  Complete Device ID: ${deviceInfo.deviceId}")
            println("TGLIVE: Build.VERSION.RELEASE = ${Build.VERSION.RELEASE}")
            println("TGLIVE: Build.VERSION.SDK_INT = ${Build.VERSION.SDK_INT}")
            println("TGLIVE: Complete Session String: $sessionString")
            
            // Prepare TDLib parameters with new format:
            // TG Live [version], [Device Name], [Model], [Manufacturer], Android [version], Android, [Device ID]
            val tdlibDeviceModel = sessionString
            val tdlibSystemVersion = "Android ${deviceInfo.androidVersion}"
            val tdlibApplicationVersion = "${deviceInfo.appName} ${deviceInfo.appVersion}"
            
            val parameters = TdApi.SetTdlibParameters(
                false, // useTestDc
                databaseDir, // databaseDirectory
                filesDir, // filesDirectory
                byteArrayOf(), // databaseEncryptionKey
                true, // useFileDatabase
                true, // useChatInfoDatabase
                true, // useMessageDatabase
                true, // useSecretChats
                this@TelegramClient.apiId, // apiId
                this@TelegramClient.apiHash, // apiHash
                java.util.Locale.getDefault().language, // systemLanguageCode
                tdlibDeviceModel, // deviceModel
                tdlibSystemVersion, // systemVersion
                tdlibApplicationVersion // applicationVersion
            )
            
            println("TGLIVE: Sending SetTdlibParameters")
            val result = send(parameters)
            println("TGLIVE: SetTdlibParameters result: ${result.javaClass.simpleName}")
            
            // Log what will appear in Telegram sessions
            println("TGLIVE: Telegram Session Info (what users will see):")
            println("  Complete Session String: $sessionString")
            println("TGLIVE: Format: TG Live [version], [Device Name], [Model], [Manufacturer], Android [version], Android, [Complete Device ID]")
            
            when (result.constructor) {
                TdApi.Ok.CONSTRUCTOR -> {
                    println("TGLIVE: TDLib parameters set successfully")
                    isInitialized = true
                    
                    // CRITICAL: Enable updates reception immediately
                    enableUpdatesReception()
                    
                    true
                }
                TdApi.Error.CONSTRUCTOR -> {
                    val error = result as TdApi.Error
                    println("TGLIVE: TDLib parameters error: ${error.code} - ${error.message}")
                    false
                }
                else -> {
                    println("TGLIVE: Unexpected result: ${result.javaClass.simpleName}")
                    false
                }
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception in initialize: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    suspend fun setPhoneNumber(phoneNumber: String): Boolean {
        return try {
            // First initialize the client
            initialize()
            
            // Wait for the right state
            val authState = waitForAuthorizationState()
            if (authState.constructor != TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR) {
                println("TGLIVE: Not in phone number waiting state: ${authState.javaClass.simpleName}")
                return false
            }
            
            // Send phone number
            val result = send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null))
            result.constructor == TdApi.Ok.CONSTRUCTOR
        } catch (e: Exception) {
            println("TGLIVE: Exception in setPhoneNumber: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    suspend fun checkCode(code: String): AuthResult {
        return try {
            println("TGLIVE: Checking code: $code")
            
            // First check current auth state
            val currentState = waitForAuthorizationState()
            println("TGLIVE: Current auth state: ${currentState.javaClass.simpleName}")
            
            if (currentState.constructor != TdApi.AuthorizationStateWaitCode.CONSTRUCTOR) {
                return AuthResult.Error("Not in code waiting state. Current state: ${currentState.javaClass.simpleName}")
            }
            
            val result = send(TdApi.CheckAuthenticationCode(code))
            println("TGLIVE: CheckCode result: ${result.javaClass.simpleName}")
            
            if (result.constructor == TdApi.Ok.CONSTRUCTOR) {
                AuthResult.Success
            } else {
                when (result.constructor) {
                    TdApi.Error.CONSTRUCTOR -> {
                        val error = result as TdApi.Error
                        println("TGLIVE: Error code: ${error.code}, message: ${error.message}")
                        AuthResult.Error("Code verification failed (${error.code}): ${error.message}")
                    }
                    else -> {
                        println("TGLIVE: Unexpected result type: ${result.javaClass.simpleName}")
                        AuthResult.Error("Unexpected response: ${result.javaClass.simpleName}")
                    }
                }
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception in checkCode: ${e.message}")
            e.printStackTrace()
            AuthResult.Error("Network error: ${e.message}")
        }
    }
    
    suspend fun checkPassword(password: String): AuthResult {
        return try {
            val result = send(TdApi.CheckAuthenticationPassword(password))
            if (result.constructor == TdApi.Ok.CONSTRUCTOR) {
                AuthResult.Success
            } else {
                when (result.constructor) {
                    TdApi.Error.CONSTRUCTOR -> {
                        val error = result as TdApi.Error
                        AuthResult.Error("Password verification failed: ${error.message}")
                    }
                    else -> {
                        AuthResult.Error("Unexpected response: ${result.javaClass.simpleName}")
                    }
                }
            }
        } catch (e: Exception) {
            AuthResult.Error("Network error: ${e.message}")
        }
    }
    
    suspend fun waitForAuthorizationState(): TdApi.AuthorizationState {
        // First try to get the current state directly (this is fast)
        return try {
            val result = send(TdApi.GetAuthorizationState())
            if (result is TdApi.AuthorizationState) {
                println("TGLIVE: Got current auth state: ${result.javaClass.simpleName}")
                result
            } else {
                println("TGLIVE: GetAuthorizationState returned non-auth state, waiting for update")
                // If that doesn't work, wait for an update with shorter timeout
                withTimeoutOrNull(2000) {
                    authStateChannel.receive()
                } ?: TdApi.AuthorizationStateWaitPhoneNumber()
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception getting auth state: ${e.message}")
            TdApi.AuthorizationStateWaitPhoneNumber()
        }
    }
    
    suspend fun getCurrentAuthState(): String {
        return try {
            val authState = waitForAuthorizationState()
            when (authState.constructor) {
                TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> "waitParameters"
                TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> "waitPhoneNumber"
                TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> "waitCode"
                TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> "waitPassword"
                TdApi.AuthorizationStateReady.CONSTRUCTOR -> "ready"
                else -> "waitPhoneNumber"
            }
        } catch (e: Exception) {
            println("TGLIVE: Error getting auth state: ${e.message}")
            "waitPhoneNumber"
        }
    }
    
    fun close() {
        // Don't send Close() as it destroys the session and authentication state
        // Just set client to null to disconnect
        println("TGLIVE: Disconnecting TelegramClient (preserving auth state)")
        client = null
        // Don't reset isInitialized flag to keep it fast on restart
    }
    
    fun destroy() {
        // Only call this when you want to completely log out
        println("TGLIVE: Destroying TelegramClient and clearing auth state")
        client?.send(TdApi.Close(), null, null)
        client = null
        isInitialized = false // Reset initialization flag on logout
    }
    
    data class DeviceInfo(
        val appName: String,
        val appVersion: String,
        val deviceName: String,
        val deviceModel: String,
        val manufacturer: String,
        val brand: String,
        val fullDeviceModel: String,
        val androidVersion: String,
        val apiLevel: Int,
        val systemVersion: String,
        val deviceId: String,
        val platform: String
    )
    
    fun getCompleteDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            appName = "TG Live",
            appVersion = getAppVersion(),
            deviceName = getDeviceName(),
            deviceModel = getDeviceModel(),
            manufacturer = getManufacturer(),
            brand = getBrand(),
            fullDeviceModel = getFullDeviceModel(),
            androidVersion = getAndroidVersion(),
            apiLevel = getApiLevel(),
            systemVersion = getSystemVersion(),
            deviceId = getDeviceId(),
            platform = "Android"
        )
    }
    
    fun getDeviceInfo(): String {
        val info = getCompleteDeviceInfo()
        return "TG Live - ${info.fullDeviceModel}\n${info.systemVersion} (ID: ${info.deviceId.take(8)})"
    }
    
    fun getSessionInfo(): String {
        val info = getCompleteDeviceInfo()
        return "Device: TG Live - ${info.fullDeviceModel}\nSystem: ${info.systemVersion}\nDevice ID: ${info.deviceId.take(8)}"
    }
    
    fun getTelegramSessionParameters(): Map<String, String> {
        val info = getCompleteDeviceInfo()
        val sessionString = "${info.appName} ${info.appVersion}, ${info.deviceName}, ${info.deviceModel}, ${info.manufacturer}, Android ${info.androidVersion}, ${info.platform}, ${info.deviceId}"
        
        return mapOf(
            "deviceModel" to sessionString,
            "systemVersion" to "Android ${info.androidVersion}",
            "applicationVersion" to "${info.appName} ${info.appVersion}",
            "systemLanguageCode" to java.util.Locale.getDefault().language,
            "fullSessionString" to sessionString
        )
    }
    
    /**
     * Set the UpdatesHandler for real-time updates
     * Requirements: 6.1 - integrate with client.setUpdatesHandler
     */
    fun setUpdatesHandler(handler: UpdatesHandler) {
        println("TGLIVE: Setting UpdatesHandler - Previous handler: ${this.updatesHandler != null}")
        this.updatesHandler = handler
        handler.initialize()
        println("TGLIVE: UpdatesHandler set successfully - Handler active: ${this.updatesHandler != null}")
    }
    
    /**
     * Subscribe to group call updates for a specific group call
     * This ensures we receive INSTANT real-time updates for the group call
     */
    suspend fun subscribeToGroupCallUpdates(groupCallId: Int): Boolean {
        return try {
            println("TGLIVE: 🔔 Subscribing to INSTANT group call updates for call $groupCallId")
            
            // 1. Load group call participants to ensure we get updates
            val loadResult = send(TdApi.LoadGroupCallParticipants(groupCallId, 100))
            
            // 2. Get the current group call to ensure we're subscribed
            val getCallResult = send(TdApi.GetGroupCall(groupCallId))
            
            when (loadResult.constructor) {
                TdApi.Ok.CONSTRUCTOR -> {
                    println("TGLIVE: ✅ Successfully subscribed to INSTANT updates for group call $groupCallId")
                    
                    // Also verify the call exists
                    when (getCallResult.constructor) {
                        TdApi.GroupCall.CONSTRUCTOR -> {
                            val groupCall = getCallResult as TdApi.GroupCall
                            println("TGLIVE: ✅ Group call verified - isActive: ${groupCall.isActive}, participants: ${groupCall.participantCount}")
                        }
                        else -> {
                            println("TGLIVE: ⚠️ Group call verification failed, but subscription may still work")
                        }
                    }
                    
                    true
                }
                TdApi.Error.CONSTRUCTOR -> {
                    val error = loadResult as TdApi.Error
                    println("TGLIVE: ⚠️ Failed to subscribe to group call updates: ${error.message}")
                    
                    // Still try to get the call info - we might get some updates anyway
                    when (getCallResult.constructor) {
                        TdApi.GroupCall.CONSTRUCTOR -> {
                            println("TGLIVE: ✅ Group call exists, may still get some updates")
                            true
                        }
                        else -> {
                            println("TGLIVE: ❌ Group call doesn't exist")
                            false
                        }
                    }
                }
                else -> {
                    println("TGLIVE: ⚠️ Unexpected result subscribing to group call updates")
                    true
                }
            }
        } catch (e: Exception) {
            println("TGLIVE: ❌ Exception subscribing to group call updates: ${e.message}")
            false
        }
    }
    

    
    /**
     * Subscribe to chat updates for a specific chat
     * This ensures we get INSTANT notifications when video chats start/end
     */
    suspend fun subscribeToChatUpdates(chatId: Long): Boolean {
        return try {
            println("TGLIVE: 🔔 Subscribing to INSTANT chat updates for chat $chatId")
            
            // 1. Get the chat to ensure we're subscribed to its updates
            val getChatResult = send(TdApi.GetChat(chatId))
            
            when (getChatResult.constructor) {
                TdApi.Chat.CONSTRUCTOR -> {
                    val chat = getChatResult as TdApi.Chat
                    println("TGLIVE: ✅ Successfully subscribed to INSTANT updates for chat: ${chat.title}")
                    
                    // 2. CRITICAL: Open the chat to ensure we get updates
                    val openChatResult = send(TdApi.OpenChat(chatId))
                    when (openChatResult.constructor) {
                        TdApi.Ok.CONSTRUCTOR -> {
                            println("TGLIVE: ✅ Chat opened successfully - updates should flow")
                        }
                        else -> {
                            println("TGLIVE: ⚠️ Failed to open chat, but may still get updates")
                        }
                    }
                    
                    // 3. Check current video chat status
                    if (chat.videoChat != null && chat.videoChat.groupCallId != 0) {
                        println("TGLIVE: 🎥 Chat has active video chat: ${chat.videoChat.groupCallId}")
                    } else {
                        println("TGLIVE: 📵 Chat has no active video chat")
                    }
                    
                    true
                }
                TdApi.Error.CONSTRUCTOR -> {
                    val error = getChatResult as TdApi.Error
                    println("TGLIVE: ❌ Failed to subscribe to chat updates: ${error.message}")
                    false
                }
                else -> {
                    println("TGLIVE: ⚠️ Unexpected result subscribing to chat updates")
                    false
                }
            }
        } catch (e: Exception) {
            println("TGLIVE: ❌ Exception subscribing to chat updates: ${e.message}")
            false
        }
    }
    
    /**
     * Enable updates reception - this is CRITICAL for real-time updates
     */
    private fun enableUpdatesReception() {
        try {
            println("TGLIVE: 🔥 Enabling updates reception")
            
            // Force TDLib to start sending updates by making a simple request
            client?.send(TdApi.GetMe()) { result ->
                when (result?.constructor) {
                    TdApi.User.CONSTRUCTOR -> {
                        val user = result as TdApi.User
                        println("TGLIVE: ✅ Connected as: ${user.firstName} ${user.lastName}")
                        println("TGLIVE: ✅ Updates should now be flowing")
                    }
                    TdApi.Error.CONSTRUCTOR -> {
                        val error = result as TdApi.Error
                        println("TGLIVE: ⚠️ GetMe error (but updates may still work): ${error.message}")
                    }
                    else -> {
                        println("TGLIVE: ⚠️ Unexpected GetMe result: ${result?.javaClass?.simpleName}")
                    }
                }
            }
        } catch (e: Exception) {
            println("TGLIVE: Exception enabling updates reception: ${e.message}")
        }
    }
    
    /**
     * Test if we're receiving ANY TDLib updates at all
     */
    fun testUpdatesReception(): String {
        return try {
            val clientActive = client != null
            val handlerSet = updatesHandler != null
            "Client Active: $clientActive, Handler Set: $handlerSet"
        } catch (e: Exception) {
            "Error testing updates: ${e.message}"
        }
    }
    
    /**
     * Get the current UpdatesHandler
     */
    fun getUpdatesHandler(): UpdatesHandler? {
        return updatesHandler
    }
}

sealed class AuthResult {
    object Success : AuthResult()
    object WaitingForPhoneNumber : AuthResult()
    object WaitingForCode : AuthResult()
    object WaitingForPassword : AuthResult()
    data class Error(val message: String) : AuthResult()
}