package app.fqrs.tglive.telegram

import android.content.Context

/**
 * Singleton wrapper for TelegramClient to ensure only one instance exists
 * 
 * This solves the issue where multiple activities try to create TelegramClient instances,
 * which causes TDLib initialization conflicts since TDLib only allows one client per app.
 */
object TelegramClientSingleton {
    
    @Volatile
    private var instance: TelegramClient? = null
    
    @Volatile
    private var updatesHandler: UpdatesHandler? = null
    
    /**
     * Get the singleton TelegramClient instance
     * Creates a new instance if none exists, otherwise returns the existing one
     */
    fun getInstance(context: Context): TelegramClient {
        return instance ?: synchronized(this) {
            instance ?: TelegramClient(context.applicationContext).also { client ->
                instance = client
                
                // CRITICAL: Set up UpdatesHandler immediately when client is created
                // This ensures real-time updates work from the start
                updatesHandler = UpdatesHandler(client).also { handler ->
                    client.setUpdatesHandler(handler)
                    handler.initialize()
                    println("TGLIVE: UpdatesHandler initialized with new TelegramClient instance")
                }
                
                println("TGLIVE: Created new TelegramClient singleton instance with UpdatesHandler")
            }
        }
    }
    
    /**
     * Get the UpdatesHandler instance
     */
    fun getUpdatesHandler(): UpdatesHandler? {
        return updatesHandler
    }
    
    /**
     * Check if an instance already exists
     */
    fun hasInstance(): Boolean {
        return instance != null
    }
    
    /**
     * Destroy the singleton instance (for logout)
     */
    fun destroyInstance() {
        synchronized(this) {
            updatesHandler?.cleanup()
            updatesHandler = null
            instance?.destroy()
            instance = null
            println("TGLIVE: Destroyed TelegramClient singleton instance and UpdatesHandler")
        }
    }
    
    /**
     * Close the singleton instance (for disconnect without logout)
     */
    fun closeInstance() {
        synchronized(this) {
            instance?.close()
            // Don't set instance to null - keep it for reuse
            // Don't destroy updatesHandler - keep it for reuse
            println("TGLIVE: Closed TelegramClient singleton instance")
        }
    }
}