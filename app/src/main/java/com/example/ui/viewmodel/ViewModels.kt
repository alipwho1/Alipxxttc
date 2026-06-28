package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.model.CallStatus
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.data.model.UserProfile
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

sealed class AuthState {
    object UNAUTHENTICATED : AuthState()
    object OTP_VERIFICATION : AuthState()
    object PROFILE_SETUP : AuthState()
    object AUTHENTICATED : AuthState()
}

data class DeveloperCode(
    val code: String,
    val limit: Int,
    var currentUses: Int = 0
)

/**
 * Authentication ViewModel: Controls login, OTP verification, and profile configuration.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.AUTHENTICATED) // Default directly authenticated for smooth onboarding
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()

    private val _otpCode = MutableStateFlow("")
    val otpCode: StateFlow<String> = _otpCode.asStateFlow()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _profileGradientIndex = MutableStateFlow(0)
    val profileGradientIndex: StateFlow<Int> = _profileGradientIndex.asStateFlow()

    private val _isOtpVerifying = MutableStateFlow(false)
    val isOtpVerifying: StateFlow<Boolean> = _isOtpVerifying.asStateFlow()

    // Passcode list created by developers. Seeds can be used by standard users.
    private val _developerCodes = MutableStateFlow<List<DeveloperCode>>(
        listOf(
            DeveloperCode("EMERALD777", 5, 0),
            DeveloperCode("HANDSOME100", 3, 0)
        )
    )
    val developerCodes: StateFlow<List<DeveloperCode>> = _developerCodes.asStateFlow()

    fun loginWithDeveloperPassword(password: String): Boolean {
        if (password == "010612") {
            viewModelScope.launch {
                _userProfile.value = UserProfile(
                    name = "Dev Admin",
                    phoneNumber = "+1 555-0106",
                    bio = "Emerald System Architect ✦",
                    avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150",
                    isDeveloper = true,
                    isPremium = true,
                    customTitle = "SYSTEM DEV",
                    titleBgColorIndex = 1 // Electric Amethyst
                )
                _profileGradientIndex.value = 1 // Neon Purple
                _authState.value = AuthState.AUTHENTICATED
            }
            return true
        }
        return false
    }

    fun createDeveloperCode(code: String, limit: Int): Boolean {
        if (code.isBlank() || limit <= 0) return false
        val trimmed = code.trim().uppercase()
        val currentList = _developerCodes.value.toMutableList()
        // If code already exists, overwrite limit or return false. We will return false to preserve original rules.
        if (currentList.any { it.code == trimmed }) return false
        currentList.add(DeveloperCode(trimmed, limit, 0))
        _developerCodes.value = currentList
        return true
    }

    fun attemptUnlockPremium(codeToCheck: String): Boolean {
        val trimmed = codeToCheck.trim().uppercase()
        val currentList = _developerCodes.value.map { it.copy() }.toMutableList()
        val index = currentList.indexOfFirst { it.code == trimmed }
        if (index != -1) {
            val devCode = currentList[index]
            if (devCode.currentUses < devCode.limit) {
                devCode.currentUses += 1
                _developerCodes.value = currentList
                
                // Set premium to true
                _userProfile.value = _userProfile.value.copy(
                    isPremium = true,
                    customTitle = "Elite Premium" // Default unlocked title
                )
                return true
            }
        }
        return false
    }

    fun updatePremiumTitle(title: String, titleBgColorIdx: Int) {
        _userProfile.value = _userProfile.value.copy(
            customTitle = title.ifBlank { null },
            titleBgColorIndex = titleBgColorIdx
        )
    }

    fun updateChatBackground(bgIdx: Int, customUrl: String?) {
        _userProfile.value = _userProfile.value.copy(
            chatBgIndex = bgIdx,
            customChatBgUrl = customUrl?.ifBlank { null }
        )
    }

    fun sendOtp(phone: String) {
        viewModelScope.launch {
            _phoneNumber.value = phone
            // Let a standard user easily input the developer passcode as the phone number to trigger instant dev auth login!
            if (phone == "010612") {
                loginWithDeveloperPassword("010612")
            } else {
                _authState.value = AuthState.OTP_VERIFICATION
            }
        }
    }

    fun verifyOtp(otp: String) {
        viewModelScope.launch {
            _isOtpVerifying.value = true
            delay(1500) // Realistic networks duration
            _otpCode.value = otp
            _isOtpVerifying.value = false
            if (otp == "010612") {
                loginWithDeveloperPassword("010612")
            } else {
                _authState.value = AuthState.PROFILE_SETUP
            }
        }
    }

    fun saveProfile(name: String, bio: String, avatarUrl: String?) {
        viewModelScope.launch {
            _userProfile.value = UserProfile(name = name, bio = bio, avatarUrl = avatarUrl)
            _authState.value = AuthState.AUTHENTICATED
        }
    }

    fun updateProfile(name: String, bio: String, avatarUrl: String?, gradientIndex: Int) {
        viewModelScope.launch {
            _userProfile.value = _userProfile.value.copy(name = name, bio = bio, avatarUrl = avatarUrl)
            _profileGradientIndex.value = gradientIndex
        }
    }

    fun logout() {
        _authState.value = AuthState.UNAUTHENTICATED
        _phoneNumber.value = ""
        _otpCode.value = ""
    }
}

/**
 * ChatViewModel: Real-time core mechanics, thread list loading, typing, sending, editing, reactions.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    val repository = ChatRepository(application)

    val chats: StateFlow<List<ChatEntity>> = repository.chats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts: StateFlow<List<ContactEntity>> = repository.contacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val messagesForActiveChat: StateFlow<List<MessageEntity>> = _activeChatId
        .filterNotNull()
        .flatMapLatest { chatId -> repository.getMessagesForChat(chatId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _replyingMessage = MutableStateFlow<MessageEntity?>(null)
    val replyingMessage: StateFlow<MessageEntity?> = _replyingMessage.asStateFlow()

    private val _attachmentPreview = MutableStateFlow<String?>(null)
    val attachmentPreview: StateFlow<String?> = _attachmentPreview.asStateFlow()

    private val _mediaDownloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val mediaDownloadProgress: StateFlow<Map<String, Int>> = _mediaDownloadProgress.asStateFlow()

    fun setActiveChat(chatId: String?) {
        _activeChatId.value = chatId
        _replyingMessage.value = null
        _attachmentPreview.value = null
        if (chatId != null) {
            viewModelScope.launch {
                // Clear unread counts
                val db = AppDatabase.getDatabase(getApplication())
                db.chatDao().updateUnreadCount(chatId, 0)
            }
        }
    }

    fun sendTextMessage(content: String) {
        val chatId = _activeChatId.value ?: return
        if (content.trim().isEmpty()) return

        viewModelScope.launch {
            val replyMsg = _replyingMessage.value
            _replyingMessage.value = null
            repository.sendMessage(
                chatId = chatId,
                content = content,
                type = MessageType.TEXT,
                replyToMessageId = replyMsg?.messageId,
                replyToText = replyMsg?.content
            )
        }
    }

    fun sendMediaMessage(caption: String, type: MessageType, mediaUrl: String, fileSize: Long = 1048576) {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            _attachmentPreview.value = null
            val messageId = repository.sendMessage(
                chatId = chatId,
                content = caption,
                type = type,
                mediaUrl = mediaUrl,
                fileSize = fileSize
            )

            // Simulate file download/upload visual progress spinner
            simulateProgress(messageId)
        }
    }

    private fun simulateProgress(messageId: String) {
        viewModelScope.launch {
            for (p in 0..100 step 10) {
                _mediaDownloadProgress.value = _mediaDownloadProgress.value + (messageId to p)
                delay(300)
            }
            _mediaDownloadProgress.value = _mediaDownloadProgress.value - messageId
        }
    }

    fun setReplyingMessage(message: MessageEntity?) {
        _replyingMessage.value = message
    }

    fun setAttachmentPreview(url: String?) {
        _attachmentPreview.value = url
    }

    fun addReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            repository.addReaction(messageId, emoji)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessageForEveryone(messageId)
        }
    }

    fun editMessage(messageId: String, newContent: String) {
        viewModelScope.launch {
            repository.editMessage(messageId, newContent)
        }
    }

    fun createGroup(name: String, description: String, members: List<String>) {
        viewModelScope.launch {
            repository.createGroup(name, description, null, members)
        }
    }
}

/**
 * CallViewModel: Manages phone dialings, incoming calling interfaces, audio ringers, call logs.
 */
class CallViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application)

    val callLogs: StateFlow<List<CallEntity>> = repository.calls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeCall = MutableStateFlow<CallEntity?>(null)
    val activeCall: StateFlow<CallEntity?> = _activeCall.asStateFlow()

    private val _incomingCall = MutableStateFlow<CallEntity?>(null)
    val incomingCall: StateFlow<CallEntity?> = _incomingCall.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0)
    val callDurationSeconds: StateFlow<Int> = _callDurationSeconds.asStateFlow()

    fun initiateCall(peerId: String, peerName: String, peerAvatar: String?, isVideo: Boolean) {
        viewModelScope.launch {
            val callId = UUID.randomUUID().toString()
            val call = CallEntity(
                callId = callId,
                callerId = "me",
                callerName = "You",
                callerAvatar = null,
                receiverId = peerId,
                receiverName = peerName,
                receiverAvatar = peerAvatar,
                isVideo = isVideo,
                timestamp = System.currentTimeMillis(),
                duration = 0,
                status = CallStatus.ONGOING.name
            )
            _activeCall.value = call

            // Start call duration stopwatch ticker
            _callDurationSeconds.value = 0
            tickerJob()
        }
    }

    fun triggerSimulatedIncomingCall(callerId: String, callerName: String, callerAvatar: String?, isVideo: Boolean) {
        viewModelScope.launch {
            val callId = UUID.randomUUID().toString()
            val call = CallEntity(
                callId = callId,
                callerId = callerId,
                callerName = callerName,
                callerAvatar = callerAvatar,
                receiverId = "me",
                receiverName = "You",
                receiverAvatar = null,
                isVideo = isVideo,
                timestamp = System.currentTimeMillis(),
                duration = 0,
                status = CallStatus.ONGOING.name
            )
            _incomingCall.value = call
        }
    }

    fun acceptIncomingCall() {
        val incoming = _incomingCall.value ?: return
        _activeCall.value = incoming
        _incomingCall.value = null
        _callDurationSeconds.value = 0
        tickerJob()
    }

    fun rejectIncomingCall() {
        val incoming = _incomingCall.value ?: return
        viewModelScope.launch {
            repository.registerCall(
                incoming.callerId, incoming.callerName, incoming.callerAvatar,
                incoming.receiverId, incoming.receiverName, incoming.receiverAvatar,
                incoming.isVideo, CallStatus.REJECTED
            )
            _incomingCall.value = null
        }
    }

    fun endActiveCall() {
        val active = _activeCall.value ?: return
        viewModelScope.launch {
            repository.registerCall(
                active.callerId, active.callerName, active.callerAvatar,
                active.receiverId, active.receiverName, active.receiverAvatar,
                active.isVideo, CallStatus.COMPLETED
            )
            _activeCall.value = null
        }
    }

    fun deleteCall(callId: String) {
        viewModelScope.launch {
            repository.deleteCall(callId)
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            repository.clearCallHistory()
        }
    }

    private fun tickerJob() {
        viewModelScope.launch {
            while (_activeCall.value != null) {
                delay(1000)
                _callDurationSeconds.value += 1
            }
        }
    }
}

/**
 * StatusViewModel: Ephemeral state handling, 24-hr status limits, creating custom text/media stories.
 */
class StatusViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application)

    // Ephemeral filtering: Only fetch statuses under 24 hours old
    val activeStatuses: StateFlow<List<StatusEntity>> = repository.statuses
        .map { list ->
            val cutoff = System.currentTimeMillis() - 86400000 // 24 hours
            list.filter { it.timestamp >= cutoff }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    fun uploadStatus(caption: String, mediaUrl: String, isVideo: Boolean = false) {
        viewModelScope.launch {
            _isUploading.value = true
            delay(2000) // Simulates image upload network progress
            repository.registerStatus(caption, mediaUrl, isVideo)
            _isUploading.value = false
        }
    }
}

/**
 * SettingsViewModel: Handles system wide toggles, blocking list, custom receipts, dynamic M3 colors.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application)

    private val _isDarkMode = MutableStateFlow(true) // Modern AMOLED theme is the default for premium look
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _privacyReadReceipts = MutableStateFlow(true)
    val privacyReadReceipts: StateFlow<Boolean> = _privacyReadReceipts.asStateFlow()

    private val _notificationEnabled = MutableStateFlow(true)
    val notificationEnabled: StateFlow<Boolean> = _notificationEnabled.asStateFlow()

    val blockedContacts: StateFlow<List<ContactEntity>> = repository.blockedContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // In-app alert system for incoming message simulations
    private val _inAppNotification = MutableStateFlow<String?>(null)
    val inAppNotification: StateFlow<String?> = _inAppNotification.asStateFlow()

    fun toggleTheme() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun toggleReadReceipts() {
        _privacyReadReceipts.value = !_privacyReadReceipts.value
    }

    fun toggleNotifications() {
        _notificationEnabled.value = !_notificationEnabled.value
    }

    fun toggleBlock(contactId: String) {
        viewModelScope.launch {
            repository.toggleBlockContact(contactId)
        }
    }

    fun triggerInAppToast(text: String) {
        viewModelScope.launch {
            _inAppNotification.value = text
            delay(3500)
            _inAppNotification.value = null
        }
    }
}
