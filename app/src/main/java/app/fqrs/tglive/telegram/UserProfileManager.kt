package app.fqrs.tglive.telegram

import kotlinx.coroutines.*
import org.drinkless.tdlib.TdApi

class UserProfileManager(private val client: TelegramClient) {
    
    suspend fun getCurrentUser(): UserProfile? {
        return withContext(Dispatchers.IO) {
            try {
                println("DEBUG: Getting current user from Telegram...")
                
                // Get current user info from Telegram
                val result = client.send(TdApi.GetMe())
                
                if (result is TdApi.User) {
                    println("DEBUG: Successfully got user data from Telegram")
                    println("DEBUG: User ID: ${result.id}")
                    println("DEBUG: First Name: ${result.firstName}")
                    println("DEBUG: Last Name: ${result.lastName}")
                    val username = result.usernames?.editableUsername ?: ""
                    println("DEBUG: Username: $username")
                    println("DEBUG: Phone: ${result.phoneNumber}")
                    
                    // Get user bio
                    val bio = getUserBio(result.id)
                    
                    // Get profile photo URL
                    val profilePhotoUrl = getProfilePhotoUrl(result.profilePhoto)
                    
                    UserProfile(
                        id = result.id,
                        firstName = result.firstName ?: "",
                        lastName = result.lastName ?: "",
                        username = username,
                        phoneNumber = result.phoneNumber ?: "",
                        bio = bio ?: "",
                        profilePhotoUrl = profilePhotoUrl
                    )
                } else {
                    println("DEBUG: Failed to get user data: ${result.javaClass.simpleName}")
                    if (result is TdApi.Error) {
                        println("DEBUG: Error: ${result.code} - ${result.message}")
                    }
                    null
                }
            } catch (e: Exception) {
                println("DEBUG: Exception getting current user: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }
    
    private suspend fun getUserBio(userId: Long): String? {
        return try {
            println("DEBUG: Getting user bio for user ID: $userId")
            val result = client.send(TdApi.GetUserFullInfo(userId))
            
            if (result is TdApi.UserFullInfo) {
                val bio = result.bio?.text ?: ""
                println("DEBUG: User bio: $bio")
                bio
            } else {
                println("DEBUG: Failed to get user bio: ${result.javaClass.simpleName}")
                null
            }
        } catch (e: Exception) {
            println("DEBUG: Exception getting user bio: ${e.message}")
            null
        }
    }
    
    private suspend fun getProfilePhotoUrl(profilePhoto: TdApi.ProfilePhoto?): String? {
        return try {
            if (profilePhoto?.small != null) {
                println("DEBUG: Getting profile photo with file ID: ${profilePhoto.small.id}")
                val result = client.send(TdApi.GetFile(profilePhoto.small.id))
                
                if (result is TdApi.File) {
                    println("DEBUG: File info - ID: ${result.id}, Size: ${result.size}, Downloaded: ${result.local.isDownloadingCompleted}")
                    if (result.local.isDownloadingCompleted) {
                        println("DEBUG: Profile photo already downloaded: ${result.local.path}")
                        result.local.path
                    } else {
                        println("DEBUG: Profile photo not downloaded, initiating download...")
                        // Download the file with higher priority
                        val downloadResult = client.send(TdApi.DownloadFile(result.id, 32, 0, 0, true))
                        if (downloadResult is TdApi.File) {
                            println("DEBUG: Download initiated, checking status...")
                            if (downloadResult.local.isDownloadingCompleted) {
                                println("DEBUG: Profile photo downloaded successfully: ${downloadResult.local.path}")
                                downloadResult.local.path
                            } else {
                                println("DEBUG: Download in progress, path will be: ${downloadResult.local.path}")
                                // Return the path even if download is in progress
                                downloadResult.local.path.takeIf { it.isNotEmpty() }
                            }
                        } else {
                            println("DEBUG: Download failed: ${downloadResult.javaClass.simpleName}")
                            null
                        }
                    }
                } else {
                    println("DEBUG: Failed to get profile photo file: ${result.javaClass.simpleName}")
                    if (result is TdApi.Error) {
                        println("DEBUG: Error details: ${result.code} - ${result.message}")
                    }
                    null
                }
            } else {
                println("DEBUG: No profile photo available")
                null
            }
        } catch (e: Exception) {
            println("DEBUG: Exception getting profile photo: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    suspend fun getCompleteUserProfile(): UserProfile? {
        return getCurrentUser()
    }
}

data class UserProfile(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val username: String,
    val phoneNumber: String,
    val bio: String,
    val profilePhotoUrl: String?
) {
    val displayName: String
        get() = if (firstName.isNotEmpty() || lastName.isNotEmpty()) {
            "$firstName $lastName".trim()
        } else username.ifEmpty { "User $id" }
    
    val handle: String
        get() = if (username.isNotEmpty()) "@$username" else ""
}