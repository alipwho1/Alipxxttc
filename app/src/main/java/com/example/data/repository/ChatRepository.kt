package com.example.data.repository

import android.content.Context
import com.example.data.local.*
import com.example.data.model.CallStatus
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.security.SignalEncryption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class ChatRepository(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()
    private val contactDao = db.contactDao()
    private val statusDao = db.statusDao()
    private val callDao = db.callDao()

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    // Reactive Flow Feeds for ViewModels
    val chats: Flow<List<ChatEntity>> = chatDao.getAllChats()
    val contacts: Flow<List<ContactEntity>> = contactDao.getAllContacts()
    val blockedContacts: Flow<List<ContactEntity>> = contactDao.getBlockedContacts()
    val statuses: Flow<List<StatusEntity>> = statusDao.getAllStatuses()
    val calls: Flow<List<CallEntity>> = callDao.getAllCalls()

    init {
        // Initialize with default contacts and conversations for a great onboarding experience
        repositoryScope.launch {
            seedInitialData()
        }
    }

    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForChat(chatId)
    }

    suspend fun getChatById(chatId: String): ChatEntity? {
        return chatDao.getChatById(chatId)
    }

    /**
     * Send a Message: Encrypts the content end-to-end and saves it to local Room DB.
     * Fires Firestore synchronization hooks and emulates real-time double check progression.
     */
    suspend fun sendMessage(
        chatId: String,
        content: String,
        type: MessageType = MessageType.TEXT,
        mediaUrl: String? = null,
        localPath: String? = null,
        fileSize: Long = 0,
        replyToMessageId: String? = null,
        replyToText: String? = null
    ): String {
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // Get key phrase for this chat encryption (usually phone pairing)
        val chat = chatDao.getChatById(chatId)
        val keyPhrase = chat?.name ?: "$chatId-secure-channel"

        // Secure E2EE Encryption
        val encryptedContent = if (type == MessageType.TEXT) {
            SignalEncryption.encrypt(content, keyPhrase)
        } else {
            content // Media captions or descriptions
        }

        val entity = MessageEntity(
            messageId = messageId,
            chatId = chatId,
            senderId = "me",
            senderName = "You",
            content = encryptedContent,
            timestamp = timestamp,
            status = MessageStatus.SENT.name,
            type = type.name,
            mediaUrl = mediaUrl,
            localPath = localPath,
            duration = if (type == MessageType.VOICE) 8 else 0,
            fileSize = fileSize,
            isEdited = false,
            replyToMessageId = replyToMessageId,
            replyToText = replyToText,
            reactions = "",
            isDeletedForEveryone = false
        )

        // Save locally
        messageDao.insertMessage(entity)

        // Update last message in the chat
        val rawDisplayString = when (type) {
            MessageType.TEXT -> content
            MessageType.IMAGE -> "📷 Photo"
            MessageType.VIDEO -> "📹 Video"
            MessageType.VOICE -> "🎵 Voice note"
            MessageType.DOCUMENT -> "📄 Document"
        }
        chatDao.updateLastMessage(chatId, rawDisplayString, timestamp)

        /*
         * ==========================================
         * FIREBASE FIRESTORE SYNC HOOK (Production Blueprint)
         * ==========================================
         * In a connected client, we would push to firestore:
         *
         *  val firebaseDb = FirebaseFirestore.getInstance()
         *  val secureMessagePayload = hashMapOf(
         *      "messageId" to messageId,
         *      "chatId" to chatId,
         *      "senderId" to "me",
         *      "content" to encryptedContent,
         *      "timestamp" to timestamp,
         *      "status" to "SENT"
         *  )
         *  firebaseDb.collection("chats").document(chatId)
         *      .collection("messages").document(messageId)
         *      .set(secureMessagePayload)
         */

        // Local reactive pipeline updates & simulator responses
        repositoryScope.launch {
            // Step 1: Simulate transmission delay to delivered
            delay(1000)
            messageDao.updateMessageStatus(messageId, MessageStatus.DELIVERED.name)

            // Step 2: Simulate read receipt transition
            delay(1200)
            messageDao.updateMessageStatus(messageId, MessageStatus.READ.name)

            // Step 3: Trigger real-time automatic responder replies based on sender keyword
            triggerAutoResponse(chatId, content, type)
        }

        return messageId
    }

    suspend fun editMessage(messageId: String, newContent: String) {
        val msg = messageDao.getMessageById(messageId) ?: return
        val chat = chatDao.getChatById(msg.chatId)
        val keyPhrase = chat?.name ?: "${msg.chatId}-secure-channel"

        // Re-encrypt edited content
        val encryptedContent = SignalEncryption.encrypt(newContent, keyPhrase)
        messageDao.editMessage(messageId, encryptedContent)

        // Update last message in chat preview
        chatDao.updateLastMessage(msg.chatId, "$newContent (Edited)", System.currentTimeMillis())
    }

    suspend fun deleteMessageForEveryone(messageId: String) {
        val msg = messageDao.getMessageById(messageId) ?: return
        messageDao.softDeleteMessage(messageId)

        // Update chat summary preview
        chatDao.updateLastMessage(msg.chatId, "🚫 This message was deleted.", System.currentTimeMillis())
    }

    suspend fun addReaction(messageId: String, emoji: String) {
        val msg = messageDao.getMessageById(messageId) ?: return
        val currentReactions = if (msg.reactions.isEmpty()) emptyList() else msg.reactions.split(",")
        val updatedReactions = if (currentReactions.contains(emoji)) {
            currentReactions.filter { it != emoji }
        } else {
            currentReactions + emoji
        }
        messageDao.updateMessageReactions(messageId, updatedReactions.joinToString(","))
    }

    suspend fun createGroup(name: String, description: String, avatarUrl: String?, adminIds: List<String>) {
        val groupId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val groupEntity = ChatEntity(
            chatId = groupId,
            name = name,
            avatarUrl = avatarUrl,
            bio = description,
            isGroup = true,
            groupDescription = description,
            groupAdmins = adminIds.joinToString(","),
            lastMessage = "Group created",
            lastMessageTime = timestamp,
            isOnline = false,
            isTyping = false,
            unreadCount = 0
        )
        chatDao.insertChat(groupEntity)
    }

    suspend fun registerStatus(caption: String, imageUrl: String, isVideo: Boolean) {
        val statusId = UUID.randomUUID().toString()
        val status = StatusEntity(
            statusId = statusId,
            userId = "me",
            userName = "You",
            userAvatar = null,
            mediaUrl = imageUrl,
            caption = caption,
            timestamp = System.currentTimeMillis(),
            isVideo = isVideo
        )
        statusDao.insertStatus(status)
    }

    suspend fun registerCall(callerId: String, callerName: String, callerAvatar: String?, receiverId: String, receiverName: String, receiverAvatar: String?, isVideo: Boolean, status: CallStatus) {
        val callId = UUID.randomUUID().toString()
        val call = CallEntity(
            callId = callId,
            callerId = callerId,
            callerName = callerName,
            callerAvatar = callerAvatar,
            receiverId = receiverId,
            receiverName = receiverName,
            receiverAvatar = receiverAvatar,
            isVideo = isVideo,
            timestamp = System.currentTimeMillis(),
            duration = if (status == CallStatus.COMPLETED) (30..600).random().toLong() else 0L,
            status = status.name
        )
        callDao.insertCall(call)
    }

    suspend fun clearCallHistory() {
        callDao.clearCallHistory()
    }

    suspend fun deleteCall(callId: String) {
        callDao.deleteCallById(callId)
    }

    suspend fun toggleBlockContact(contactId: String) {
        val contact = contactDao.getContactById(contactId) ?: return
        contactDao.updateBlockStatus(contactId, !contact.isBlocked)
    }

    private suspend fun triggerAutoResponse(chatId: String, userMessage: String, userMessageType: MessageType) {
        val chat = chatDao.getChatById(chatId) ?: return
        if (chat.isGroup) return // Skip complex replies in group for simple layout

        // Start typing states
        delay(1500)
        chatDao.updateTypingStatus(chatId, true)

        delay(2000) // Simulating user reading and writing
        chatDao.updateTypingStatus(chatId, false)

        val senderName = chat.name
        val replyId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // Decrypted response content formulation
        val rawReplyContent = when {
            userMessageType != MessageType.TEXT -> "That's awesome! I love when you send files. Let's securely verify our encryption keys next time."
            userMessage.lowercase().contains("hello") || userMessage.lowercase().contains("hi") -> "Hello! Yes, our connection here is end-to-end encrypted under AES-256 protocols. How are you?"
            userMessage.lowercase().contains("call") -> "Sure! Tap the call button above to start a voice or video call right now."
            userMessage.lowercase().contains("group") -> "Let's create a Chat Group using the Floating Action Button, and add members!"
            userMessage.lowercase().contains("status") || userMessage.lowercase().contains("story") -> "I saw your status update! It looks gorgeous."
            else -> "I received your encrypted message. Tap the header info to inspect our secure cryptographic fingerprint keys!"
        }

        // Encrypt reply
        val encryptedReply = SignalEncryption.encrypt(rawReplyContent, chat.name)

        val responseEntity = MessageEntity(
            messageId = replyId,
            chatId = chatId,
            senderId = chatId, // peer ID is the chatId
            senderName = senderName,
            content = encryptedReply,
            timestamp = timestamp,
            status = MessageStatus.READ.name,
            type = MessageType.TEXT.name,
            mediaUrl = null,
            localPath = null,
            duration = 0,
            fileSize = 0,
            isEdited = false,
            replyToMessageId = null,
            replyToText = null,
            reactions = "",
            isDeletedForEveryone = false
        )

        // Save reply in database
        messageDao.insertMessage(responseEntity)
        chatDao.updateLastMessage(chatId, rawReplyContent, timestamp)
        chatDao.updateUnreadCount(chatId, chat.unreadCount + 1)
    }

    /**
     * Seeds Room local database with premium sample chats, contacts, calls, and initial encryption logs.
     */
    private suspend fun seedInitialData() {
        if (chatDao.getAllChats().first().isNotEmpty()) return

        // 1. Seed Contacts
        val c1 = ContactEntity("alice", "Alice Vance", "+1 555-0101", "Let's catch up soon! ☕", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=150", false, true, System.currentTimeMillis())
        val c2 = ContactEntity("bob", "Bob Miller", "+1 555-0102", "Busy coding in Kotlin 📱", "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?q=80&w=150", false, false, System.currentTimeMillis() - 7200000)
        val c3 = ContactEntity("charlie", "Charlie Green", "+1 555-0103", "Nature is healing 🌿", "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?q=80&w=150", false, true, System.currentTimeMillis())
        val c4 = ContactEntity("david", "David K.", "+1 555-0104", "Blocked contacts demo profile", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?q=80&w=150", true, false, System.currentTimeMillis() - 86400000)

        contactDao.insertContact(c1)
        contactDao.insertContact(c2)
        contactDao.insertContact(c3)
        contactDao.insertContact(c4)

        // 2. Seed Chats
        val chat1 = ChatEntity("alice", "Alice Vance", c1.avatarUrl, c1.bio, false, null, "", "Hey there! I'm using Emerald Chat.", System.currentTimeMillis() - 500000, true, false, 0)
        val chat2 = ChatEntity("bob", "Bob Miller", c2.avatarUrl, c2.bio, false, null, "", "Did you see my status?", System.currentTimeMillis() - 2500000, false, false, 1)
        val chat3 = ChatEntity("charlie", "Charlie Green", c3.avatarUrl, c3.bio, false, null, "", "Let's organize a call later.", System.currentTimeMillis() - 10800000, true, false, 0)
        val chatGroup = ChatEntity("group_devs", "Kotlin Dev Group 🚀", "https://images.unsplash.com/photo-1522071820081-009f0129c71c?q=80&w=150", "The official Kotlin assembly", true, "Where mobile masterminds coordinate E2EE communication and UI components", "alice,bob", "Kotlin Compose feels fantastic!", System.currentTimeMillis() - 60000, false, false, 0)

        chatDao.insertChat(chat1)
        chatDao.insertChat(chat2)
        chatDao.insertChat(chat3)
        chatDao.insertChat(chatGroup)

        // 3. Seed initial messages with encryption
        val keyAlice = chat1.name
        val m1 = MessageEntity("m1", "alice", "alice", "Alice Vance", SignalEncryption.encrypt("Are we still meeting today?", keyAlice), System.currentTimeMillis() - 3600000, MessageStatus.READ.name, MessageType.TEXT.name, null, null, 0, 0, false, null, null, "", false)
        val m2 = MessageEntity("m2", "alice", "me", "You", SignalEncryption.encrypt("Absolutely! Looking forward to it.", keyAlice), System.currentTimeMillis() - 1800000, MessageStatus.READ.name, MessageType.TEXT.name, null, null, 0, 0, false, null, null, "❤️", false)
        val m3 = MessageEntity("m3", "alice", "alice", "Alice Vance", SignalEncryption.encrypt("Awesome, sent you the location document.", keyAlice), System.currentTimeMillis() - 500000, MessageStatus.READ.name, MessageType.TEXT.name, null, null, 0, 0, false, null, null, "", false)

        val keyBob = chat2.name
        val m4 = MessageEntity("m4", "bob", "bob", "Bob Miller", SignalEncryption.encrypt("Have you sent the project report yet?", keyBob), System.currentTimeMillis() - 3600000 * 2, MessageStatus.READ.name, MessageType.TEXT.name, null, null, 0, 0, false, null, null, "", false)
        val m5 = MessageEntity("m5", "bob", "me", "You", SignalEncryption.encrypt("Yes! Let me share the PDF here.", keyBob), System.currentTimeMillis() - 3600000, MessageStatus.READ.name, MessageType.TEXT.name, null, null, 0, 0, false, null, null, "", false)
        val m6 = MessageEntity("m6", "bob", "me", "You", "Share report pdf", System.currentTimeMillis() - 3000000, MessageStatus.READ.name, MessageType.DOCUMENT.name, "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf", null, 0, 10240, false, null, null, "", false)

        val m7 = MessageEntity("m7", "group_devs", "alice", "Alice Vance", "Hey gang, check this mock UI screenshot!", System.currentTimeMillis() - 600000, MessageStatus.READ.name, MessageType.IMAGE.name, "https://images.unsplash.com/photo-1531403009284-440f080d1e12?q=80&w=400", null, 0, 204800, false, null, null, "🔥", false)
        val m8 = MessageEntity("m8", "group_devs", "bob", "Bob Miller", "Kotlin Compose feels fantastic!", System.currentTimeMillis() - 60000, MessageStatus.READ.name, MessageType.TEXT.name, null, null, 0, 0, false, null, null, "", false)

        messageDao.insertMessage(m1)
        messageDao.insertMessage(m2)
        messageDao.insertMessage(m3)
        messageDao.insertMessage(m4)
        messageDao.insertMessage(m5)
        messageDao.insertMessage(m6)
        messageDao.insertMessage(m7)
        messageDao.insertMessage(m8)

        // 4. Seed Status Updates (Stories)
        val s1 = StatusEntity("s1", "alice", "Alice Vance", c1.avatarUrl, "https://images.unsplash.com/photo-1501854140801-50d01698950b?q=80&w=400", "Morning hike! Sunrise was breath-taking 🌅", System.currentTimeMillis() - 12000000, false)
        val s2 = StatusEntity("s2", "charlie", "Charlie Green", c3.avatarUrl, "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?q=80&w=400", "Deep forest serenity 🌲💚", System.currentTimeMillis() - 20000000, false)
        statusDao.insertStatus(s1)
        statusDao.insertStatus(s2)

        // 5. Seed Call Logs
        val call1 = CallEntity("cl1", "alice", "Alice Vance", c1.avatarUrl, "me", "You", null, true, System.currentTimeMillis() - 17200000, 128, CallStatus.COMPLETED.name)
        val call2 = CallEntity("cl2", "me", "You", null, "bob", "Bob Miller", c2.avatarUrl, false, System.currentTimeMillis() - 72000000, 0, CallStatus.MISSED.name)
        val call3 = CallEntity("cl3", "charlie", "Charlie Green", c3.avatarUrl, "me", "You", null, false, System.currentTimeMillis() - 100000000, 312, CallStatus.COMPLETED.name)

        callDao.insertCall(call1)
        callDao.insertCall(call2)
        callDao.insertCall(call3)
    }
}
