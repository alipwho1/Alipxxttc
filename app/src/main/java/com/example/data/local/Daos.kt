package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE chatId = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Query("UPDATE chats SET isTyping = :isTyping WHERE chatId = :chatId")
    suspend fun updateTypingStatus(chatId: String, isTyping: Boolean)

    @Query("UPDATE chats SET isOnline = :isOnline WHERE chatId = :chatId")
    suspend fun updateOnlineStatus(chatId: String, isOnline: Boolean)

    @Query("UPDATE chats SET unreadCount = :unreadCount WHERE chatId = :chatId")
    suspend fun updateUnreadCount(chatId: String, unreadCount: Int)

    @Query("UPDATE chats SET lastMessage = :lastMessage, lastMessageTime = :lastMessageTime WHERE chatId = :chatId")
    suspend fun updateLastMessage(chatId: String, lastMessage: String?, lastMessageTime: Long)

    @Query("DELETE FROM chats WHERE chatId = :chatId")
    suspend fun deleteChatById(chatId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageId = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("UPDATE messages SET reactions = :reactions WHERE messageId = :messageId")
    suspend fun updateMessageReactions(messageId: String, reactions: String)

    @Query("UPDATE messages SET isDeletedForEveryone = 1 WHERE messageId = :messageId")
    suspend fun softDeleteMessage(messageId: String)

    @Query("UPDATE messages SET content = :newContent, isEdited = 1 WHERE messageId = :messageId")
    suspend fun editMessage(messageId: String, newContent: String)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE contactId = :contactId")
    suspend fun getContactById(contactId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE isBlocked = 1 ORDER BY name ASC")
    fun getBlockedContacts(): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Query("UPDATE contacts SET isBlocked = :isBlocked WHERE contactId = :contactId")
    suspend fun updateBlockStatus(contactId: String, isBlocked: Boolean)

    @Query("UPDATE contacts SET isOnline = :isOnline, lastSeenTime = :lastSeenTime WHERE contactId = :contactId")
    suspend fun updateOnlineStatus(contactId: String, isOnline: Boolean, lastSeenTime: Long)
}

@Dao
interface StatusDao {
    @Query("SELECT * FROM statuses ORDER BY timestamp DESC")
    fun getAllStatuses(): Flow<List<StatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatus(status: StatusEntity)

    @Query("DELETE FROM statuses WHERE timestamp < :cutoffTime")
    suspend fun deleteExpiredStatuses(cutoffTime: Long)
}

@Dao
interface CallDao {
    @Query("SELECT * FROM calls ORDER BY timestamp DESC")
    fun getAllCalls(): Flow<List<CallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallEntity)

    @Query("DELETE FROM calls WHERE callId = :callId")
    suspend fun deleteCallById(callId: String)

    @Query("DELETE FROM calls")
    suspend fun clearCallHistory()
}
