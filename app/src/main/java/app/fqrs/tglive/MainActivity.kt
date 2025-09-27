package app.fqrs.tglive

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.fqrs.tglive.databinding.ActivityMainBinding
import app.fqrs.tglive.telegram.*
import app.fqrs.tglive.ui.CountryPickerDialog
import app.fqrs.tglive.utils.Country
import app.fqrs.tglive.utils.CountryHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var telegramClient: TelegramClient
    private lateinit var authManager: AuthenticationManager
    private lateinit var profileManager: UserProfileManager
    
    private var currentAuthState = AuthState.PHONE_NUMBER
    private var selectedCountry: Country = CountryHelper.getDefaultCountry()
    private var currentPhoneNumber: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Explicitly hide action bar
        supportActionBar?.hide()
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initially hide both sections and show loading
        initializeUI()
        
        initializeTelegram()
        setupClickListeners()
        setupCountrySelector()
        
        // Check if user is already authenticated
        checkExistingAuthState()
    }
    
    private fun initializeUI() {
        // Initially hide both sections and show loading
        binding.loginSection.visibility = View.GONE
        binding.profileSection.visibility = View.GONE
        binding.loadingOverlay.visibility = View.VISIBLE
    }
    
    private fun initializeTelegram() {
        telegramClient = TelegramClient(this)
        authManager = AuthenticationManager(telegramClient)
        profileManager = UserProfileManager(telegramClient)
    }
    
    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            when (currentAuthState) {
                AuthState.PHONE_NUMBER -> sendPhoneNumber()
                AuthState.CODE -> sendCode()
                AuthState.PASSWORD -> sendPassword()
            }
        }
        
        binding.logoutButton.setOnClickListener {
            logout()
        }
        
        binding.countrySelector.setOnClickListener {
            showCountryPicker()
        }
        
        binding.backButton.setOnClickListener {
            goBackToPhoneInput()
        }
    }
    
    private fun setupCountrySelector() {
        updateCountryDisplay()
    }
    
    private fun checkExistingAuthState() {
        lifecycleScope.launch {
            try {
                // Initialize the client first (this is fast if already initialized)
                val initSuccess = telegramClient.initialize()
                
                if (!initSuccess) {
                    // If initialization fails, show login section
                    showLoginSection()
                    binding.statusText.text = "Ready to login"
                    return@launch
                }
                
                // Check current authentication state
                val authResult = authManager.authenticate()
                when (authResult) {
                    is AuthResult.Success -> {
                        // User is already logged in, show profile directly
                        showProfile()
                    }
                    is AuthResult.WaitingForPhoneNumber -> {
                        // Need to enter phone number - show login section
                        showLoginSection()
                        currentAuthState = AuthState.PHONE_NUMBER
                        binding.statusText.text = "Ready to login"
                    }
                    is AuthResult.WaitingForCode -> {
                        // Need to enter verification code - show OTP screen
                        showLoginSection()
                        currentAuthState = AuthState.CODE
                        // Show OTP screen with a generic phone number message
                        showOtpScreen("your phone number")
                        binding.statusText.text = "Enter the verification code"
                    }
                    is AuthResult.WaitingForPassword -> {
                        // Need to enter 2FA password - show password screen
                        showLoginSection()
                        currentAuthState = AuthState.PASSWORD
                        showPasswordScreen()
                    }
                    is AuthResult.Error -> {
                        // Error occurred - show login section
                        showLoginSection()
                        binding.statusText.text = "Ready to login"
                        println("DEBUG: Auth check error: ${authResult.message}")
                    }
                }
            } catch (e: Exception) {
                // Exception occurred - show login section
                showLoginSection()
                binding.statusText.text = "Ready to login"
                println("DEBUG: Exception checking auth state: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private fun updateCountryDisplay() {
        binding.countryFlag.text = selectedCountry.flag
        binding.countryCode.text = selectedCountry.dialCode
        binding.countryName.text = selectedCountry.name
    }
    
    private fun showCountryPicker() {
        val dialog = CountryPickerDialog(this) { country ->
            selectedCountry = country
            updateCountryDisplay()
        }
        dialog.show()
    }
    
    private fun sendPhoneNumber() {
        val phoneNumber = binding.phoneNumberInput.text.toString().trim()
        if (phoneNumber.isEmpty()) {
            Toast.makeText(this, "Please enter phone number", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Combine country code with phone number
        val fullPhoneNumber = if (phoneNumber.startsWith(selectedCountry.dialCode)) {
            phoneNumber
        } else {
            selectedCountry.dialCode + phoneNumber
        }
        
        lifecycleScope.launch {
            showLoading(true)
            binding.statusText.text = "Sending verification code..."
            binding.loginButton.isEnabled = false
            
            try {
                val success = authManager.setPhoneNumber(fullPhoneNumber)
                if (success) {
                    currentAuthState = AuthState.CODE
                    currentPhoneNumber = fullPhoneNumber // Store for later use
                    showOtpScreen(fullPhoneNumber)
                } else {
                    binding.statusText.text = "Failed to send code. Please try again."
                }
            } catch (e: Exception) {
                binding.statusText.text = "Error: ${e.message}"
                e.printStackTrace()
            }
            
            showLoading(false)
            binding.loginButton.isEnabled = true
        }
    }
    
    private fun sendCode() {
        val code = binding.codeInput.text.toString().trim()
        println("DEBUG: Attempting to send code: $code")
        
        if (code.isEmpty()) {
            Toast.makeText(this, "Please enter verification code", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            showLoading(true)
            binding.statusText.text = "Verifying code..."
            binding.loginButton.isEnabled = false
            
            try {
                println("DEBUG: Calling authManager.checkCode")
                val result = authManager.checkCode(code)
                println("DEBUG: checkCode result: $result")
                
                when (result) {
                    is AuthResult.Success -> {
                        println("DEBUG: Code verification successful, handling auth result")
                        // Check if we need password or if we're logged in
                        handleAuthenticationResult()
                    }
                    is AuthResult.Error -> {
                        println("DEBUG: Code verification error: ${result.message}")
                        binding.statusText.text = result.message
                    }
                    else -> {
                        println("DEBUG: Unexpected result type: $result")
                        binding.statusText.text = "Invalid code. Please try again."
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: Exception in sendCode: ${e.message}")
                binding.statusText.text = "Error: ${e.message}"
                e.printStackTrace()
            }
            
            showLoading(false)
            binding.loginButton.isEnabled = true
        }
    }
    
    private fun sendPassword() {
        val password = binding.passwordInput.text.toString().trim()
        if (password.isEmpty()) {
            Toast.makeText(this, "Please enter password", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            showLoading(true)
            binding.statusText.text = "Verifying password..."
            binding.loginButton.isEnabled = false
            
            try {
                val result = authManager.checkPassword(password)
                when (result) {
                    is AuthResult.Success -> {
                        handleAuthenticationResult()
                    }
                    is AuthResult.Error -> {
                        binding.statusText.text = result.message
                    }
                    else -> {
                        binding.statusText.text = "Invalid password. Please try again."
                    }
                }
            } catch (e: Exception) {
                binding.statusText.text = "Error: ${e.message}"
                e.printStackTrace()
            }
            
            showLoading(false)
            binding.loginButton.isEnabled = true
        }
    }
    
    private fun showPasswordScreen() {
        // Hide phone input and OTP elements
        binding.welcomeTitle.visibility = View.GONE
        binding.welcomeSubtitle.visibility = View.GONE
        binding.phoneInputSection.visibility = View.GONE
        binding.otpHeader.visibility = View.VISIBLE
        binding.otpInfoText.visibility = View.VISIBLE
        binding.phoneNumberDisplay.visibility = View.VISIBLE
        binding.codeInput.visibility = View.GONE
        
        // Show password input
        binding.passwordInput.visibility = View.VISIBLE
        binding.loginButton.text = "Login"
        
        // Update texts for 2FA screen
        binding.otpHeaderTitle.text = "Two-Factor Authentication"
        if (currentPhoneNumber.isNotEmpty()) {
            binding.otpInfoText.text = "Please enter your 2FA password for"
            binding.phoneNumberDisplay.text = formatPhoneNumber(currentPhoneNumber)
        } else {
            binding.otpInfoText.text = "Please enter your 2FA password"
            binding.phoneNumberDisplay.text = "your account"
        }
        binding.statusText.text = "Enter your 2FA password to complete login"
        
        // Focus on password input
        binding.passwordInput.requestFocus()
    }
    
    private suspend fun handleAuthenticationResult() {
        val authResult = authManager.authenticate()
        when (authResult) {
            is AuthResult.Success -> {
                showProfile()
            }
            is AuthResult.WaitingForPassword -> {
                currentAuthState = AuthState.PASSWORD
                showPasswordScreen()
            }
            is AuthResult.Error -> {
                binding.statusText.text = "Login failed: ${authResult.message}"
            }
            else -> {
                binding.statusText.text = "Unexpected authentication state"
            }
        }
    }
    
    private fun showProfile() {
        lifecycleScope.launch {
            showLoading(true)
            
            try {
                println("DEBUG: Starting profile load...")
                val profile = profileManager.getCompleteUserProfile()
                if (profile != null) {
                    println("DEBUG: Profile loaded successfully: ${profile.displayName}")
                    displayProfile(profile)
                } else {
                    println("DEBUG: Failed to load profile - null returned")
                    showLoginSection()
                    binding.statusText.text = "Failed to load profile. Please try logging in again."
                }
            } catch (e: Exception) {
                println("DEBUG: Exception loading profile: ${e.message}")
                e.printStackTrace()
                showLoginSection()
                binding.statusText.text = "Error loading profile: ${e.message}"
            }
            
            showLoading(false)
        }
    }
    
    private fun displayProfile(profile: UserProfile) {
        showProfileSection()
        
        binding.displayNameText.text = "Display Name: ${profile.displayName}"
        binding.handleText.text = "Handle: ${profile.handle}"
        binding.phoneText.text = "Phone: ${profile.phoneNumber}"
        binding.bioText.text = "Bio: ${profile.bio.ifEmpty { "No bio" }}"
        binding.userIdText.text = "User ID: ${profile.id}"
        
        // Load profile picture
        loadProfilePicture(profile.profilePhotoUrl)
        
        // Show Telegram session information
        lifecycleScope.launch {
            val sessionParams = telegramClient.getTelegramSessionParameters()
            
            // Show complete session string (what appears in Telegram)
            binding.telegramFullSessionText.text = sessionParams["fullSessionString"] ?: "Session info not available"
        }
    }
    
    private fun logout() {
        lifecycleScope.launch {
            // Actually log out and destroy the session
            telegramClient.destroy()
            
            // Reset UI to login section
            showLoginSection()
            binding.codeInput.visibility = View.GONE
            binding.passwordInput.visibility = View.GONE
            binding.loginButton.text = "Send Code"
            binding.statusText.text = "Ready to login"
            
            // Clear inputs
            binding.phoneNumberInput.text?.clear()
            binding.codeInput.text?.clear()
            binding.passwordInput.text?.clear()
            
            currentAuthState = AuthState.PHONE_NUMBER
            currentPhoneNumber = "" // Clear stored phone number
            
            // Reinitialize client
            initializeTelegram()
        }
    }
    
    private fun showLoginSection() {
        binding.loadingOverlay.visibility = View.GONE
        binding.profileSection.visibility = View.GONE
        binding.loginSection.visibility = View.VISIBLE
        binding.loginSection.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_in))
    }
    
    private fun showProfileSection() {
        binding.loadingOverlay.visibility = View.GONE
        binding.loginSection.visibility = View.GONE
        binding.profileSection.visibility = View.VISIBLE
        binding.profileSection.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_in))
    }
    
    private fun showOtpScreen(phoneNumber: String) {
        // Hide phone input elements
        binding.welcomeTitle.visibility = View.GONE
        binding.welcomeSubtitle.visibility = View.GONE
        binding.phoneInputSection.visibility = View.GONE
        
        // Show OTP elements
        binding.otpHeader.visibility = View.VISIBLE
        binding.otpInfoText.visibility = View.VISIBLE
        binding.phoneNumberDisplay.visibility = View.VISIBLE
        binding.codeInput.visibility = View.VISIBLE
        
        // Update texts
        binding.otpHeaderTitle.text = "Verification Code"
        binding.phoneNumberDisplay.text = formatPhoneNumber(phoneNumber)
        binding.loginButton.text = "Verify Code"
        binding.statusText.text = "Enter the verification code sent to ${formatPhoneNumber(phoneNumber)}"
        
        // Clear and focus on OTP input
        binding.codeInput.text?.clear()
        binding.codeInput.requestFocus()
        
        // Show keyboard
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.codeInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }
    
    private fun goBackToPhoneInput() {
        // Show phone input elements
        binding.welcomeTitle.visibility = View.VISIBLE
        binding.welcomeSubtitle.visibility = View.VISIBLE
        binding.phoneInputSection.visibility = View.VISIBLE
        
        // Hide OTP elements
        binding.otpHeader.visibility = View.GONE
        binding.otpInfoText.visibility = View.GONE
        binding.phoneNumberDisplay.visibility = View.GONE
        binding.codeInput.visibility = View.GONE
        binding.passwordInput.visibility = View.GONE
        
        // Reset state
        currentAuthState = AuthState.PHONE_NUMBER
        binding.loginButton.text = "Send Code"
        binding.statusText.text = "Ready to login"
        
        // Clear OTP input and focus on phone input
        binding.codeInput.text?.clear()
        binding.phoneNumberInput.requestFocus()
    }
    
    private fun showLoading(show: Boolean) {
        if (show) {
            // Don't hide sections when showing loading during operations
            binding.loadingOverlay.visibility = View.VISIBLE
        } else {
            binding.loadingOverlay.visibility = View.GONE
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Just disconnect, don't destroy the session
        telegramClient.close()
    }
    
    private fun loadProfilePicture(photoUrl: String?) {
        if (photoUrl != null && photoUrl.isNotEmpty()) {
            try {
                // Load image from file path
                val bitmap = android.graphics.BitmapFactory.decodeFile(photoUrl)
                if (bitmap != null) {
                    val circularBitmap = createCircularBitmap(bitmap)
                    binding.profileImageView.setImageBitmap(circularBitmap)
                    println("DEBUG: Profile picture loaded successfully")
                } else {
                    // Use default image if bitmap is null
                    binding.profileImageView.setImageResource(R.mipmap.ic_launcher)
                    println("DEBUG: Failed to decode profile picture, using default")
                }
            } catch (e: Exception) {
                // Use default image on error
                binding.profileImageView.setImageResource(R.mipmap.ic_launcher)
                println("DEBUG: Error loading profile picture: ${e.message}")
            }
        } else {
            // Use default image if no photo URL
            binding.profileImageView.setImageResource(R.mipmap.ic_launcher)
            println("DEBUG: No profile picture URL, using default")
        }
    }
    
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
    
    private fun formatPhoneNumber(phoneNumber: String): String {
        // Format phone number like +91 9022893397
        return if (phoneNumber.startsWith("+")) {
            val countryCode = phoneNumber.substring(0, 3) // +91
            val number = phoneNumber.substring(3) // 9022893397
            "$countryCode $number"
        } else {
            phoneNumber
        }
    }
    
    private enum class AuthState {
        PHONE_NUMBER, CODE, PASSWORD
    }
}