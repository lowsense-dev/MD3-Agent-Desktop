package dev.huecoder.md3agent.e2e

import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.data.settings.AgentProfile
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.ModelSelection
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.data.settings.ShellMode
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.system.shell.AccessStatus
import dev.huecoder.md3agent.testing.testContainer
import dev.huecoder.md3agent.tools.ToolContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The real app container (temp folders, no desktop bus) driven end to end: every local tool through the same registry
 * the agent loop uses, and, when MD3_TEST_PROVIDER_URL / MD3_TEST_API_KEY / MD3_TEST_MODEL are set, one real reply.
 */
class HeadlessAgentRun {
    private val container = testContainer()
    private var callId = 0

    private suspend fun run(tool: String, arguments: String, shell: ShellMode = ShellMode.SYSTEM): String {
        val settings = container.settings.current().tools.let { it.copy(access = it.access.copy(shell = shell)) }
        val outcome = container.tools.run(ToolCall("c${++callId}", tool, arguments), settings, ToolContext("chat"))
        assertFalse(outcome.isError, "$tool failed: ${outcome.content}")
        return outcome.content
    }

    @Test
    fun localToolsWorkEndToEnd() = runBlocking {
        assertContains(run("run_command", """{"command":"echo hi && uname -s"}"""), "Linux")
        if (container.shellAccess.status(ShellMode.SANDBOX) == AccessStatus.READY) {
            val sandboxed = run("run_command", """{"command":"touch made-in-sandbox && ls ~ && ls /home 2>&1 | head -3"}""", ShellMode.SANDBOX)
            assertContains(sandboxed, "made-in-sandbox")
        }
        assertEquals("4", run("run_python", """{"code":"print(2+2)"}""").trim().lines().first())
        run("write_file", """{"path":"notes/a.txt","content":"привет"}""")
        assertContains(run("read_file", """{"path":"notes/a.txt"}"""), "привет")
        assertContains(run("list_files", """{"path":"notes"}"""), "a.txt")
        run("memory_add", """{"title":"Name","content":"The user is Alex","tier":"instant"}""")
        assertContains(run("memory_list", "{}"), "Alex")

        val at = LocalDateTime.now().plusSeconds(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).substringBefore('.')
        assertContains(run("set_reminder", """{"text":"stretch","at":"$at"}""").lowercase(), "stretch")
        withTimeout(10_000) { while (container.reminders.upcoming().isNotEmpty()) delay(200) }
        assertTrue(container.reminders.upcoming().isEmpty(), "the reminder fired and was removed")
        run("set_timer", """{"seconds":1,"label":"tea"}""")
        withTimeout(10_000) { while (container.reminderAlarms.active.value.isEmpty()) delay(100) }
        assertEquals("tea", container.reminderAlarms.active.value.single().reminder.text)
    }

    @Test
    fun oneRealReplyWithATool() = runBlocking {
        val url = System.getenv("MD3_TEST_PROVIDER_URL")
        val key = System.getenv("MD3_TEST_API_KEY")
        val model = System.getenv("MD3_TEST_MODEL")
        assumeTrue("set MD3_TEST_PROVIDER_URL, MD3_TEST_API_KEY and MD3_TEST_MODEL for a real model run", url != null && key != null && model != null)
        container.settings.upsertProvider(ProviderProfile(id = "test", name = "Test", baseUrl = url!!, apiKey = key!!, format = ApiFormat.OPENAI))
        container.settings.setActiveSelection(ModelSelection("test", model!!))
        container.settings.updateTools { tools -> tools.withPermission("run_python", ToolPermission.AUTO) }
        val chat = container.chats.createChat("e2e", ModelSelection("test", model), AgentProfile.MAIN_AGENT_ID)
        container.conversations.send(chat.id, "Use run_python to compute 17*23 and answer with just the number.", emptyList())
        val reply = withTimeout(120_000) {
            var last: dev.huecoder.md3agent.data.db.MessageEntity? = null
            while (true) {
                last = container.chats.history(chat.id).lastOrNull { it.role == MessageRole.ASSISTANT && it.status == MessageStatus.COMPLETE && it.toolCallsJson == null }
                if (last != null) break
                delay(500)
            }
            last!!
        }
        assertNull(reply.errorText)
        assertContains(reply.content, "391")
    }
}
