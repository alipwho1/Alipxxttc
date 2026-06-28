package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val chatId: String,
    val name: String,
    val avatarUrl: String?,
    val bio: String,
    val isGroup: Boolean,
    val groupDescription: String?,
    val groupAdmins: String, // comma-separated contact IDs
    val lastMessage: String?,
    val lastMessageTime: Long,
    val isOnline: Boolean,
    val isTyping: Boolean,
    val unreadCount: Int
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val status: String, // "SENT", "DELIVERED", "READ"
    val type: String, // "TEXT", "IMAGE", "VIDEO", "VOICE", "DOCUMENT"
    val mediaUrl: String?,
    val localPath: String?,
    val duration: Long, // in seconds for voice/video
    val fileSize: Long, // in bytes
    val isEdited: Boolean,
    val replyToMessageId: String?,
    val replyToText: String?,
    val reactions: String, // comma separated reactions or emojis, e.g. "❤️,👍"
    val isDeletedForEveryone: Boolean
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val contactId: String,
    val name: String,
    val phoneNumber: String,
    val bio: String,
    val avatarUrl: String?,
    val isBlocked: Boolean,
    val isOnline: Boolean,
    val lastSeenTime: Long
)

@Entity(tableName = "statuses")
data class StatusEntity(
    @PrimaryKey val statusId: String,
    val userId: String,
    val userName: String,
    val userAvatar: String?,
    val mediaUrl: String,
    val caption: String,
    val timestamp: Long,
    val isVideo: Boolean
)

@Entity(tableName = "calls")
data class CallEntity(
    @PrimaryKey val callId: String,
    val callerId: String,
    val callerName: String,
    val callerAvatar: String?,
    val receiverId: String,
    val receiverName: String,
    val receiverAvatar: String?,
    val isVideo: Boolean,
    val timestamp: Long,
    val duration: Long, // in seconds
    val status: String // "MISSED", "COMPLETED", "REJECTED", "ONGOING"
)
