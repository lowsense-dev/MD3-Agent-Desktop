package dev.huecoder.md3agent

import androidx.compose.runtime.staticCompositionLocalOf
import dev.huecoder.md3agent.conversation.ConversationRunner
import dev.huecoder.md3agent.conversation.GeneratedImages
import dev.huecoder.md3agent.conversation.ToolApprovals
import dev.huecoder.md3agent.conversation.UserPrompts
import dev.huecoder.md3agent.data.attachments.AttachmentStore
import dev.huecoder.md3agent.data.chat.ChatDrafts
import dev.huecoder.md3agent.data.chat.ChatRepository
import dev.huecoder.md3agent.data.chat.SearchHistory
import dev.huecoder.md3agent.data.db.AppDatabase
import dev.huecoder.md3agent.data.files.ChatSentFiles
import dev.huecoder.md3agent.data.files.FileLibrary
import dev.huecoder.md3agent.data.memory.MemoryStore
import dev.huecoder.md3agent.data.models.ModelCatalog
import dev.huecoder.md3agent.data.reminders.ReminderRepository
import dev.huecoder.md3agent.data.settings.McpOAuthSession
import dev.huecoder.md3agent.data.settings.SecretCipher
import dev.huecoder.md3agent.data.settings.SettingsRepository
import dev.huecoder.md3agent.data.updates.UpdateChecker
import dev.huecoder.md3agent.llm.ChatCompletionClient
import dev.huecoder.md3agent.llm.EmbeddingClient
import dev.huecoder.md3agent.llm.ModelCatalogClient
import dev.huecoder.md3agent.llm.TranscriptionClient
import dev.huecoder.md3agent.llm.VoiceListClient
import dev.huecoder.md3agent.mcp.McpToolbox
import dev.huecoder.md3agent.mcp.auth.McpOAuth
import dev.huecoder.md3agent.mcp.auth.McpSignIn
import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.platform.Log
import dev.huecoder.md3agent.prompt.DeviceInfoProvider
import dev.huecoder.md3agent.prompt.SystemPromptRenderer
import dev.huecoder.md3agent.system.appearance.SystemAppearance
import dev.huecoder.md3agent.system.clipboard.ClipboardReader
import dev.huecoder.md3agent.system.dbus.SessionBus
import dev.huecoder.md3agent.system.files.DeviceFiles
import dev.huecoder.md3agent.system.files.FilePicker
import dev.huecoder.md3agent.system.notifications.Notifier
import dev.huecoder.md3agent.system.paths.AppPaths
import dev.huecoder.md3agent.system.python.PythonRuntime
import dev.huecoder.md3agent.system.reminders.DesktopClock
import dev.huecoder.md3agent.system.reminders.DesktopReminderAlarms
import dev.huecoder.md3agent.system.reminders.SystemdTimers
import dev.huecoder.md3agent.system.secrets.ChosenKeyStore
import dev.huecoder.md3agent.system.secrets.SecretServiceStore
import dev.huecoder.md3agent.system.shell.ProcessRunner
import dev.huecoder.md3agent.system.shell.ShellAccess
import dev.huecoder.md3agent.tools.AskUserTool
import dev.huecoder.md3agent.tools.ShowChartTool
import dev.huecoder.md3agent.tools.ShowDiagramTool
import dev.huecoder.md3agent.tools.ShowUiTool
import dev.huecoder.md3agent.tools.ToolRegistry
import dev.huecoder.md3agent.tools.chats.ExportChatTool
import dev.huecoder.md3agent.tools.chats.ListChatsTool
import dev.huecoder.md3agent.tools.chats.ReadChatTool
import dev.huecoder.md3agent.tools.chats.SearchMessagesTool
import dev.huecoder.md3agent.tools.files.ListFilesTool
import dev.huecoder.md3agent.tools.files.ReadFileTool
import dev.huecoder.md3agent.tools.files.SendFilesTool
import dev.huecoder.md3agent.tools.files.WriteFileTool
import dev.huecoder.md3agent.tools.memory.MemoryAddTool
import dev.huecoder.md3agent.tools.memory.MemoryDeleteTool
import dev.huecoder.md3agent.tools.memory.MemoryListTool
import dev.huecoder.md3agent.tools.memory.MemorySearchTool
import dev.huecoder.md3agent.tools.memory.MemoryUpdateTool
import dev.huecoder.md3agent.tools.reminders.CancelReminderTool
import dev.huecoder.md3agent.tools.reminders.ListRemindersTool
import dev.huecoder.md3agent.tools.reminders.SetAlarmTool
import dev.huecoder.md3agent.tools.reminders.SetReminderTool
import dev.huecoder.md3agent.tools.reminders.SetTimerTool
import dev.huecoder.md3agent.tools.system.RunCommandTool
import dev.huecoder.md3agent.tools.system.RunPythonTool
import dev.huecoder.md3agent.tools.weather.WeatherTool
import dev.huecoder.md3agent.tools.web.PageFetcher
import dev.huecoder.md3agent.tools.web.ReadWebpageTool
import dev.huecoder.md3agent.tools.web.WebSearchTool
import dev.huecoder.md3agent.tools.web.WebSearcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/** Everything the app is made of, built once per process. */
class AppContainer(val paths: AppPaths = AppPaths(), launcher: String = "md3-agent") {
    // Background work must never take the whole app down; failures are logged where they were not already handled.
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.e("AppContainer", "Background task failed", e) })

    val bus = SessionBus.connect()
    val hapticEngine = dev.huecoder.md3agent.ui.haptics.HapticEngine()
    val settings = SettingsRepository(paths, SecretCipher(ChosenKeyStore(paths.config) { SecretServiceStore(bus) }), applicationScope)
    val httpClients = HttpClientProvider(settings.networkSettings, applicationScope)

    private val database = AppDatabase.build(paths)
    val attachments = AttachmentStore(paths.attachments) { paths.downloads }
    val chats = ChatRepository(database.chatDao(), database.messageDao(), database.folderDao(), attachments, ChatDrafts(paths.config.resolve("drafts.json")))
    val searchHistory = SearchHistory(paths.config.resolve("search-history.json"))
    val usageStats = database.statsDao()
    val memories = MemoryStore(database.memoryDao(), EmbeddingClient(httpClients), settings.settings.map { it.tools.memory })

    val notifier = Notifier(bus)
    val appearance = SystemAppearance(bus)
    val filePicker = FilePicker(bus)
    val clipboard = ClipboardReader()

    /** Messages the windows show as a snackbar (the desktop stand-in for Android toasts). */
    val notices = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val reminderAlarms = DesktopReminderAlarms(
        notify = { title, body, actions, onAction -> notifier.show(title, body, actions, onAction) },
        timers = SystemdTimers(File(System.getProperty("user.home"), ".config/systemd/user"), launcher) { ProcessRunner.run(it, SYSTEMCTL_TIMEOUT_MS) }
            .takeIf { it.available() },
        scope = applicationScope,
    )
    val reminders = ReminderRepository(database.reminderDao(), reminderAlarms)
    private val clock = DesktopClock(reminders)

    val completions = ChatCompletionClient(httpClients)
    val modelCatalogClient = ModelCatalogClient(httpClients)
    val voiceList = VoiceListClient(httpClients)
    val modelCatalog = ModelCatalog(modelCatalogClient, paths.cache.resolve(MODEL_CATALOG_DIR))
    val transcriptions = TranscriptionClient(httpClients)
    val systemPrompt = SystemPromptRenderer(DeviceInfoProvider())

    val shellAccess = ShellAccess(paths)
    val deviceFiles = DeviceFiles(paths.workspace, paths.attachments, paths.uploads)
    val library = FileLibrary(paths.uploads, paths.workspace, ChatSentFiles(database.messageDao(), attachments))
    val python = PythonRuntime(paths, shellAccess)

    val prompts = UserPrompts()
    private val mcpOAuth = McpOAuth(httpClients::current)
    val mcpSignIn = McpSignIn(mcpOAuth, settings)
    val mcp = McpToolbox(httpClients, attachments, prompts, mcpOAuth, ::saveMcpSession, applicationScope)

    val tools = ToolRegistry(
        listOf(
            WebSearchTool(WebSearcher(httpClients)),
            ReadWebpageTool(PageFetcher(httpClients)),
            WeatherTool(httpClients),
            ShowUiTool(),
            ShowChartTool(),
            ShowDiagramTool(),
            AskUserTool(prompts),
            MemoryAddTool(memories),
            MemoryListTool(memories),
            MemoryUpdateTool(memories),
            MemoryDeleteTool(memories),
            MemorySearchTool(memories),
            RunCommandTool(shellAccess),
            RunPythonTool(python, paths.workspace),
            ListFilesTool(deviceFiles),
            ReadFileTool(deviceFiles, attachments),
            WriteFileTool(deviceFiles),
            SendFilesTool(deviceFiles, attachments),
            SetReminderTool(reminders),
            ListRemindersTool(reminders),
            CancelReminderTool(reminders),
            SetAlarmTool(clock),
            SetTimerTool(clock),
            ListChatsTool(chats),
            ReadChatTool(chats),
            SearchMessagesTool(chats),
            ExportChatTool(chats, deviceFiles),
        ),
        sources = listOf(mcp),
    )

    val approvals = ToolApprovals()

    val conversations = ConversationRunner(
        chats, settings, completions, systemPrompt, tools, attachments, approvals, prompts,
        modelCatalog, GeneratedImages(attachments, httpClients), memories, applicationScope,
    )

    val updates = UpdateChecker(httpClients, BuildConfig.VERSION_NAME)

    private suspend fun saveMcpSession(serverId: String, session: McpOAuthSession) = settings.updateTools { tools ->
        tools.copy(mcpServers = tools.mcpServers.map { if (it.id == serverId) it.copy(oauth = session) else it })
    }

    init {
        Log.file = paths.state.apply { mkdirs() }.resolve("log.txt")
        reminderAlarms.onDue = { id -> applicationScope.launch { reminders.fire(id) } }
        reminderAlarms.onSnooze = { reminder ->
            applicationScope.launch {
                reminders.create(reminder.text, System.currentTimeMillis() + DesktopReminderAlarms.SNOOZE_MS, reminder.chatId, sound = true)
            }
        }
        applicationScope.launch { reminders.restore() }
        applicationScope.launch {
            settings.writeFailures.collect { notices.tryEmit(dev.huecoder.md3agent.ui.text.Strings.get(R.string.settings_write_refused)) }
        }
        applicationScope.launch {
            settings.settings.map { it.tools.mcpServers }.distinctUntilChanged().collect(mcp::sync)
        }
        appearance.start()
    }

    private companion object {
        const val MODEL_CATALOG_DIR = "model-catalog"
        const val SYSTEMCTL_TIMEOUT_MS = 5_000L
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer is not provided") }
