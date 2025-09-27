package app.fqrs.tglive.telegram

import kotlinx.coroutines.*
import org.drinkless.tdlib.TdApi

class AuthenticationManager(private val client: TelegramClient) {
    
    suspend fun authenticate(): AuthResult = withContext(Dispatchers.IO) {
        try {
            client.initialize()
            
            // Wait for the current authorization state
            val authState = client.waitForAuthorizationState()
            
            when (authState.constructor) {
                TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                    AuthResult.WaitingForPhoneNumber
                }
                
                TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> {
                    AuthResult.WaitingForCode
                }
                
                TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                    AuthResult.WaitingForPassword
                }
                
                TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                    AuthResult.Success
                }
                
                else -> {
                    AuthResult.Error("Unexpected authorization state: ${authState.javaClass.simpleName}")
                }
            }
        } catch (e: Exception) {
            AuthResult.Error("Authentication failed: ${e.message}")
        }
    }
    
    suspend fun setPhoneNumber(phoneNumber: String): Boolean {
        return try {
            println("DEBUG: AuthenticationManager.setPhoneNumber: $phoneNumber")
            client.setPhoneNumber(phoneNumber)
        } catch (e: Exception) {
            println("DEBUG: Exception in setPhoneNumber: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    suspend fun checkCode(code: String): AuthResult {
        return try {
            println("DEBUG: AuthenticationManager.checkCode: $code")
            client.checkCode(code)
        } catch (e: Exception) {
            println("DEBUG: Exception in checkCode: ${e.message}")
            e.printStackTrace()
            AuthResult.Error("Network error: ${e.message}")
        }
    }
    
    suspend fun checkPassword(password: String): AuthResult {
        return try {
            println("DEBUG: AuthenticationManager.checkPassword")
            client.checkPassword(password)
        } catch (e: Exception) {
            println("DEBUG: Exception in checkPassword: ${e.message}")
            e.printStackTrace()
            AuthResult.Error("Network error: ${e.message}")
        }
    }
}