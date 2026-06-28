package com.example.data.model

enum class MessageStatus {
    SENT, DELIVERED, READ
}

enum class MessageType {
    TEXT, IMAGE, VIDEO, VOICE, DOCUMENT
}

enum class CallStatus {
    MISSED, COMPLETED, REJECTED, ONGOING
}

data class UserProfile(
    val id: String = "me",
    val name: String = "You",
    val phoneNumber: String = "+1 555-0100",
    val bio: String = "Hey there! I am using Emerald Chat.",
    val avatarUrl: String? = null,
    val isDeveloper: Boolean = false,
    val isPremium: Boolean = false,
    val customTitle: String? = null,
    val titleBgColorIndex: Int = 0,
    val chatBgIndex: Int = 0,
    val customChatBgUrl: String? = null
)

data class ChatItem(
    val chatId: String,
    val name: String,
    val avatarUrl: String?,
    val bio: String,
    val isGroup: Boolean,
    val groupDescription: String?,
    val groupAdmins: List<String>,
    val lastMessage: String?,
    val lastMessageTime: Long,
    val isOnline: Boolean,
    val isTyping: Boolean,
    val unreadCount: Int
)

data class MessageItem(
    val messageId: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val status: MessageStatus,
    val type: MessageType,
    val mediaUrl: String? = null,
    val localPath: String? = null,
    val duration: Long = 0, // for voice/video duration in seconds
    val fileSize: Long = 0, // file size in bytes
    val isEdited: Boolean = false,
    val replyToMessageId: String? = null,
    val replyToText: String? = null,
    val reactions: List<String> = emptyList(),
    val isDeletedForEveryone: Boolean = false
)

data class StatusItem(
    val statusId: String,
    val userId: String,
    val userName: String,
    val userAvatar: String?,
    val mediaUrl: String,
    val caption: String,
    val timestamp: Long,
    val isVideo: Boolean
)

data class CallLogItem(
    val callId: String,
    val callerId: String,
    val callerName: String,
    val callerAvatar: String?,
    val receiverId: String,
    val receiverName: String,
    val receiverAvatar: String?,
    val isVideo: Boolean,
    val timestamp: Long,
    val duration: Long,
    val status: CallStatus
)
