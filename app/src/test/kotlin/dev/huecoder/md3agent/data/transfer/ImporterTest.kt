package dev.huecoder.md3agent.data.transfer

import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.db.MemoryEntity
import dev.huecoder.md3agent.data.db.MemoryTier
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.data.settings.AgentProfile
import dev.huecoder.md3agent.data.settings.McpServer
import dev.huecoder.md3agent.data.settings.ModelSelection
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.data.settings.ShellMode
import dev.huecoder.md3agent.testing.testContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImporterTest {
    private val password = "pw".toCharArray()

    @Test
    fun conflictsNeedOverwriteToReplace() = runBlocking {
        val phone = testContainer()
        phone.settings.upsertProvider(ProviderProfile(id = "shared", name = "Phone OpenAI", baseUrl = "https://api.openai.com/v1", apiKey = "sk-phone"))
        phone.settings.upsertProvider(ProviderProfile(id = "only-phone", name = "DeepSeek", baseUrl = "https://api.deepseek.com", apiKey = "sk-ds"))
        phone.settings.updateTools { it.copy(mcpServers = listOf(McpServer(id = "m1", name = "Notes", url = "https://mcp.example/notes"))) }
        phone.memories.let { }
        val file = Exporter(phone).export(password, includeChats = false, iterations = 1_000)

        val desktop = testContainer()
        desktop.settings.upsertProvider(ProviderProfile(id = "shared", name = "Desktop OpenAI", baseUrl = "https://api.openai.com/v1", apiKey = "sk-desk"))
        desktop.settings.updateTools { it.copy(access = it.access.copy(shell = ShellMode.SYSTEM)) }

        val preview = Importer(desktop).preview(file, password)
        assertEquals(setOf("provider:shared", "settings"), preview.items.filter { it.conflict }.map { it.key }.toSet())
        assertTrue(preview.items.any { it.key == "provider:only-phone" && !it.conflict })

        Importer(desktop).apply(preview, overwrite = emptySet())
        val kept = desktop.settings.current()
        assertEquals("Desktop OpenAI", kept.provider("shared")?.name)
        assertEquals("sk-ds", kept.provider("only-phone")?.apiKey)
        assertEquals(listOf("m1"), kept.tools.mcpServers.map { it.id })
        assertEquals(ShellMode.SYSTEM, kept.tools.access.shell, "access settings are never taken from the phone")

        Importer(desktop).apply(Importer(desktop).preview(file, password), overwrite = setOf("provider:shared"))
        assertEquals("Phone OpenAI", desktop.settings.current().provider("shared")?.name)
    }

    @Test
    fun chatsWithAttachmentsComeAcrossWhenIncluded() = runBlocking {
        val phone = testContainer()
        val chat = phone.chats.createChat("С фото", ModelSelection("p", "m"), AgentProfile.MAIN_AGENT_ID)
        val attachment = phone.attachments.importBytes("note.txt", "привет".toByteArray(), forModel = true)
        phone.chats.appendUserMessage(chat.id, "смотри файл", listOf(attachment))
        phone.memories.let { }
        val file = Exporter(phone).export(password, includeChats = true, iterations = 1_000)

        val desktop = testContainer()
        val preview = Importer(desktop).preview(file, password)
        Importer(desktop).apply(preview, overwrite = emptySet())
        val messages = desktop.chats.history(chat.id)
        assertEquals("смотри файл", messages.single().content)
        val imported = Attachment.decode(messages.single().attachmentsJson).single()
        assertEquals("привет", desktop.attachments.file(imported).readText())
    }

    @Test
    fun overwritingAnExistingChatKeepsItsAttachments() = runBlocking {
        val phone = testContainer()
        val chat = phone.chats.createChat("С файлом", ModelSelection("p", "m"), AgentProfile.MAIN_AGENT_ID)
        val attachment = phone.attachments.importBytes("note.txt", "версия телефона".toByteArray(), forModel = true)
        phone.chats.appendUserMessage(chat.id, "файл", listOf(attachment))
        val file = Exporter(phone).export(password, includeChats = true, iterations = 1_000)

        val desktop = testContainer()
        Importer(desktop).apply(Importer(desktop).preview(file, password), overwrite = emptySet())
        val again = Importer(desktop).preview(file, password)
        Importer(desktop).apply(again, overwrite = setOf("chat:${chat.id}"))

        val imported = Attachment.decode(desktop.chats.history(chat.id).single().attachmentsJson).single()
        assertEquals("версия телефона", desktop.attachments.file(imported).readText())
    }
}
