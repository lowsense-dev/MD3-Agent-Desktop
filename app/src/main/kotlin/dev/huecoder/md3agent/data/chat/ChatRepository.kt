package dev.huecoder.md3agent.data.chat

import androidx.sqlite.SQLiteException
import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.attachments.AttachmentStore
import dev.huecoder.md3agent.data.db.ChatDao
import dev.huecoder.md3agent.data.db.ChatEntity
import dev.huecoder.md3agent.data.db.ChatSummary
import dev.huecoder.md3agent.data.db.FolderDao
import dev.huecoder.md3agent.data.db.FolderEntity
import dev.huecoder.md3agent.data.db.MessageDao
import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageHit
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.data.db.TitleSource
import dev.huecoder.md3agent.data.settings.ModelSelection
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import java.util.UUID

class ChatRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val folderDao: FolderDao,
    private val attachments: AttachmentStore,
    private val drafts: ChatDrafts,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeSummaries(): Flow<List<ChatSummary>> = chatDao.observeSummaries()

    fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeAll()

    suspend fun searchChatIds(query: String): Set<String> {
        val needle = query.trim()
        if (foldsInSql(needle)) return chatDao.search(likePattern(needle)).toSet()
        val titled = chatDao.observeSummaries().first().filter { it.title.contains(needle, ignoreCase = true) }.map { it.id }
        return titled.toSet() + scanMatches(needle, Int.MAX_VALUE, chatDao::searchableMessages).map { it.chatId }
    }

    suspend fun searchMessages(query: String, limit: Int): List<MessageHit> {
        val needle = query.trim()
        if (foldsInSql(needle)) return chatDao.searchMessages(likePattern(needle), limit)
        return scanMatches(needle, limit, chatDao::searchableMessages)
    }

    val trashEvents: SharedFlow<Set<String>> get() = _trashEvents

    private val _trashEvents = MutableSharedFlow<Set<String>>(extraBufferCapacity = 1)

    fun observeChat(chatId: String): Flow<ChatEntity?> = chatDao.observe(chatId)

    fun observeMessages(chatId: String): Flow<List<MessageEntity>> = messageDao.observeForChat(chatId)

    suspend fun chat(chatId: String): ChatEntity? = chatDao.get(chatId)

    suspend fun history(chatId: String): List<MessageEntity> = messageDao.listForChat(chatId)

    /** Every chat that is not in the trash, for export. */
    suspend fun allChats(): List<ChatEntity> = chatDao.listAll()

    /** Puts back a chat exported elsewhere, keeping its ids. */
    suspend fun restoreChat(chat: ChatEntity, messages: List<MessageEntity>) = chatDao.insertWithMessages(chat, messages)

    suspend fun createChat(title: String, selection: ModelSelection, agentId: String): ChatEntity {
        val now = clock()
        val chat = ChatEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
            providerId = selection.providerId,
            modelId = selection.modelId,
            reasoningEffort = selection.reasoningEffort?.wireName,
            agentId = agentId,
        )
        chatDao.insert(chat)
        return chat
    }

    suspend fun appendUserMessage(chatId: String, text: String, attached: List<Attachment>, contextNote: String? = null): MessageEntity =
        newMessage(chatId, MessageRole.USER, text, MessageStatus.COMPLETE, modelId = null)
            .copy(attachmentsJson = Attachment.encode(attached), contextNote = contextNote)
            .also { save(it) }

    suspend fun appendToolResult(chatId: String, toolCallId: String, content: String, status: MessageStatus, attached: List<Attachment>): MessageEntity =
        newMessage(chatId, MessageRole.TOOL, content, status, modelId = null)
            .copy(toolCallId = toolCallId, attachmentsJson = Attachment.encode(attached))
            .also { save(it) }

    /**
     * Stores a compaction summary as a SYSTEM row. A prompt still waiting for its reply ([pending]) is moved after it,
     * so the model answers that prompt with the summary as its context.
     */
    suspend fun appendCompaction(chatId: String, summary: String, modelId: String, pending: MessageEntity? = null): MessageEntity {
        val row = newMessage(chatId, MessageRole.SYSTEM, summary, MessageStatus.COMPLETE, modelId).also { save(it) }
        pending?.let { save(it.copy(createdAt = monotonicNow(floor = row.createdAt + 1))) }
        return row
    }

    suspend fun startAssistantMessage(chatId: String, modelId: String): MessageEntity =
        newMessage(chatId, MessageRole.ASSISTANT, content = "", MessageStatus.STREAMING, modelId)
            .also { save(it) }

    suspend fun save(message: MessageEntity) {
        try {
            messageDao.upsert(message)
        } catch (e: SQLiteException) {
            // The chat was deleted while this reply was still being written; anything else is a real failure.
            if (e.message?.contains("constraint", ignoreCase = true) != true || chatDao.get(message.chatId) != null) throw e
            return
        }
        chatDao.touch(message.chatId, clock())
    }

    suspend fun truncateAfter(message: MessageEntity) = deleteFrom(message.chatId, message.createdAt + 1)

    suspend fun truncateFrom(message: MessageEntity, keep: List<Attachment>) = deleteFrom(message.chatId, message.createdAt, keep.mapTo(HashSet()) { it.id })

    private suspend fun deleteFrom(chatId: String, from: Long, keepIds: Set<String> = emptySet()) {
        val dropped = messageDao.listForChat(chatId).filter { it.createdAt >= from }.flatMap { Attachment.decode(it.attachmentsJson) }.filterNot { it.id in keepIds }
        messageDao.deleteFrom(chatId, from)
        attachments.delete(dropped)
    }

    suspend fun fork(chatId: String, messageId: String, title: String): String? {
        val source = chatDao.get(chatId) ?: return null
        val kept = forkCut(messageDao.listForChat(chatId), messageId).ifEmpty { return null }
        val now = clock()
        val fork = source.copy(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
            folderId = null,
            titleSource = TitleSource.USER,
            archivedAt = null,
            trashedAt = null,
        )
        val copied = mutableListOf<Attachment>()
        try {
            val messages = kept.map { message ->
                val files = Attachment.decode(message.attachmentsJson).map { attachments.duplicate(it).also(copied::add) }
                message.copy(id = UUID.randomUUID().toString(), chatId = fork.id, attachmentsJson = Attachment.encode(files))
            }
            chatDao.insertWithMessages(fork, messages)
        } catch (e: Exception) {
            withContext(NonCancellable) { attachments.delete(copied) }
            throw e
        }
        return fork.id
    }

    suspend fun rename(chatId: String, title: String) = chatDao.rename(chatId, title)

    suspend fun renameByUser(chatId: String, title: String) = chatDao.renameByUser(chatId, title.trim())

    suspend fun applyGeneratedLabel(chatId: String, title: String, look: ChatLook) =
        chatDao.applyGeneratedLabel(chatId, title.trim(), look.glyph.key, look.tint.key, look.shape.key)

    suspend fun setLook(chatId: String, look: ChatLook) = chatDao.setLook(chatId, look.glyph.key, look.tint.key, look.shape.key)

    suspend fun moveToFolder(chatIds: Collection<String>, folderId: String?) = chatDao.moveToFolder(chatIds.toList(), folderId)

    suspend fun createFolder(name: String): FolderEntity {
        val folder = FolderEntity(id = UUID.randomUUID().toString(), name = name.trim(), createdAt = clock())
        folderDao.insert(folder)
        return folder
    }

    suspend fun renameFolder(folderId: String, name: String) = folderDao.rename(folderId, name.trim())

    suspend fun deleteFolder(folderId: String) = folderDao.delete(folderId)

    suspend fun setModel(chatId: String, selection: ModelSelection) =
        chatDao.setModel(chatId, selection.providerId, selection.modelId, selection.reasoningEffort?.wireName)

    suspend fun setAgent(chatId: String, agentId: String) = chatDao.setAgent(chatId, agentId)

    suspend fun trash(chatIds: Collection<String>) {
        chatDao.setTrashed(chatIds.toList(), clock())
        _trashEvents.tryEmit(chatIds.toSet())
    }

    suspend fun restore(chatIds: Collection<String>) = chatDao.setTrashed(chatIds.toList(), null)

    suspend fun archive(chatIds: Collection<String>) = chatDao.setArchived(chatIds.toList(), clock())

    suspend fun unarchive(chatIds: Collection<String>) = chatDao.setArchived(chatIds.toList(), null)

    suspend fun deleteForever(chatIds: Collection<String>) = chatIds.forEach { delete(it) }

    suspend fun emptyTrash() = deleteForever(chatDao.trashed())

    suspend fun purgeExpiredTrash() = deleteForever(chatDao.trashedBefore(clock() - TRASH_RETENTION_MS))

    private suspend fun delete(chatId: String) {
        val attached = messageDao.listForChat(chatId).flatMap { Attachment.decode(it.attachmentsJson) }
        chatDao.delete(chatId)
        drafts.forget(chatId)
        attachments.delete(attached)
    }

    fun draft(chatId: String?): String = drafts.read(chatId)

    fun saveDraft(chatId: String?, text: String) = drafts.write(chatId, text)

    suspend fun recoverInterruptedStreams() = messageDao.markInterruptedStreams()

    private suspend fun newMessage(
        chatId: String,
        role: MessageRole,
        content: String,
        status: MessageStatus,
        modelId: String?,
    ) = MessageEntity(
        id = UUID.randomUUID().toString(),
        chatId = chatId,
        role = role,
        content = content,
        reasoning = null,
        status = status,
        errorText = null,
        createdAt = monotonicNow(floor = (messageDao.latestCreatedAt(chatId) ?: 0L) + 1),
        modelId = modelId,
        toolCallsJson = null,
        toolCallId = null,
    )

    private var lastTimestamp = 0L

    private fun likePattern(query: String) = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun foldsInSql(query: String) = query.all { it.code < 128 }

    companion object {
        const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        private const val SCAN_PAGE = 500

        // A reply spans every round and tool result up to the next prompt, so forking at it keeps the whole turn.
        internal fun forkCut(history: List<MessageEntity>, messageId: String): List<MessageEntity> {
            val at = history.indexOfFirst { it.id == messageId }
            if (at < 0) return emptyList()
            if (history[at].role == MessageRole.USER) return history.take(at + 1)
            val nextPrompt = (at + 1 until history.size).firstOrNull { history[it].role == MessageRole.USER } ?: history.size
            return history.take(nextPrompt)
        }

        // shortcut: SQLite LIKE folds case only for ASCII, so other queries scan every message here; move to FTS if this gets slow.
        internal suspend fun scanMatches(needle: String, limit: Int, page: suspend (limit: Int, offset: Int) -> List<MessageHit>): List<MessageHit> {
            val hits = mutableListOf<MessageHit>()
            var offset = 0
            while (hits.size < limit) {
                val batch = page(SCAN_PAGE, offset)
                batch.filterTo(hits) { it.content.contains(needle, ignoreCase = true) }
                if (batch.size < SCAN_PAGE) break
                offset += SCAN_PAGE
            }
            return hits.take(limit)
        }
    }

    @Synchronized
    private fun monotonicNow(floor: Long): Long {
        lastTimestamp = maxOf(clock(), lastTimestamp + 1, floor)
        return lastTimestamp
    }
}
