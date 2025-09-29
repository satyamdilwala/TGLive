package app.fqrs.tglive.models

/**
 * Sealed class for channel-related errors
 * Requirements: 7.1, 7.2, 7.3, 7.4 - error handling for invalid channels
 */
sealed class ChannelError : Exception() {
    object InvalidUsername : ChannelError() {
        override val message: String = "Invalid channel handle"
    }
    
    object ChannelNotFound : ChannelError() {
        override val message: String = "Channel not found"
    }
    
    object NotAChannel : ChannelError() {
        override val message: String = "Only public channels are supported"
    }
    
    object PrivateChannel : ChannelError() {
        override val message: String = "Only public channels are supported"
    }
    
    object NetworkError : ChannelError() {
        override val message: String = "Network error occurred"
    }
    
    data class TdLibError(val code: Int, val errorMessage: String) : ChannelError() {
        override val message: String = "TDLib error: $errorMessage (code: $code)"
    }
}