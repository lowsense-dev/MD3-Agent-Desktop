package dev.huecoder.md3agent.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Alarm
import dev.huecoder.md3agent.AppContainer
import dev.huecoder.md3agent.LocalAppContainer
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ThemeMode
import dev.huecoder.md3agent.feature.chat.ChatScreen
import dev.huecoder.md3agent.platform.AppEvents
import dev.huecoder.md3agent.platform.AppShortcut
import dev.huecoder.md3agent.platform.BackDispatcher
import dev.huecoder.md3agent.platform.LocalBackDispatcher
import dev.huecoder.md3agent.platform.LocalPlatformContext
import dev.huecoder.md3agent.platform.PlatformContext
import dev.huecoder.md3agent.system.reminders.ActiveAlarm
import dev.huecoder.md3agent.ui.avatar.ProvideAgentLook
import dev.huecoder.md3agent.ui.haptics.ProvideHaptics
import dev.huecoder.md3agent.ui.navigation.AppNavHost
import dev.huecoder.md3agent.ui.navigation.ChatRoute
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.huecoder.md3agent.ui.text.Strings
import dev.huecoder.md3agent.ui.text.stringResource
import dev.huecoder.md3agent.ui.theme.AssistantTheme
import dev.huecoder.md3agent.ui.theme.LocalSystemAccent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the running app is showing; the instance socket, reminders and notifications change it from outside. */
class AppWindows {
    val mainVisible = MutableStateFlow(true)
    val overlayVisible = MutableStateFlow(false)
    val chatToOpen = MutableStateFlow<String?>(null)

    fun showChat(chatId: String?) {
        chatToOpen.value = chatId
        mainVisible.value = true
    }
}

/** Theme, container and the platform stand-ins every window's content needs. */
@Composable
fun AppRoot(container: AppContainer, snackbar: SnackbarHostState, content: @Composable () -> Unit) {
    WindowOwners { AppRootContent(container, snackbar, content) }
}

/** Each window is a lifecycle and view-model owner of its own, the way an Android activity is. */
@Composable
private fun WindowOwners(content: @Composable () -> Unit) {
    val owner = remember { WindowOwner() }
    DisposableEffect(owner) { onDispose { owner.destroy() } }
    CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalViewModelStoreOwner provides owner, content = content)
}

private class WindowOwner : LifecycleOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

@Composable
private fun AppRootContent(container: AppContainer, snackbar: SnackbarHostState, content: @Composable () -> Unit) {
    val ui by remember(container) { container.settings.settings.map { it.ui } }.collectAsStateWithLifecycle(initialValue = null)
    val systemDark by container.appearance.darkMode.collectAsStateWithLifecycle()
    val accent by container.appearance.accent.collectAsStateWithLifecycle()
    val dark = when (ui?.theme ?: ThemeMode.SYSTEM) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark ?: false
    }
    val scope = rememberCoroutineScope()
    val platform = remember(container) { PlatformContext(container) { text -> scope.launch { snackbar.showSnackbar(text) } } }
    LaunchedEffect(container) { container.notices.collect { snackbar.showSnackbar(it) } }
    CompositionLocalProvider(
        LocalAppContainer provides container,
        LocalPlatformContext provides platform,
        LocalSystemAccent provides accent,
    ) {
        AssistantTheme(darkTheme = dark) {
            ProvideAgentLook {
                ProvideHaptics {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        content()
                        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ApplicationScope.DesktopApp(container: AppContainer, windows: AppWindows) {
    // The application level has no window yet, hence no lifecycle owner: plain collectAsState here.
    val mainVisible by windows.mainVisible.collectAsState()
    val overlayVisible by windows.overlayVisible.collectAsState()
    val alarms by container.reminderAlarms.active.collectAsState()
    val title = Strings.get(R.string.app_name)

    if (mainVisible) {
        val state = rememberWindowState(size = DpSize(1180.dp, 800.dp), position = WindowPosition(Alignment.Center))
        val back = remember { BackDispatcher() }
        var navigator by remember { mutableStateOf<NavController?>(null) }
        val snackbar = remember { SnackbarHostState() }
        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = title,
            icon = WindowIcon.painter,
            onPreviewKeyEvent = { event -> handleShortcut(event, back, navigator) },
        ) {
            window.minimumSize = java.awt.Dimension(MIN_WIDTH, MIN_HEIGHT)
            CompositionLocalProvider(LocalBackDispatcher provides back) {
                AppRoot(container, snackbar) {
                    val chatToOpen by windows.chatToOpen.collectAsStateWithLifecycle()
                    Box(
                        Modifier.fillMaxSize().onPointerEvent(PointerEventType.Press) { event ->
                            // The mouse's back button works like Escape.
                            if (event.button == PointerButton.Back) back.back() || navigator?.popBackStack() == true
                        },
                    ) {
                        AppNavHost(chatToOpen = chatToOpen, onChatOpened = { windows.chatToOpen.value = null }, navigator = { navigator = it })
                    }
                }
            }
        }
    }

    if (overlayVisible) OverlayWindow(container, windows)

    alarms.forEach { alarm -> AlarmWindow(container, alarm) }
}

private fun handleShortcut(event: KeyEvent, back: BackDispatcher, navigator: NavController?): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when {
        event.key == Key.Escape -> back.back() || navigator?.popBackStack() == true
        event.isCtrlPressed && !event.isShiftPressed && event.key == Key.N -> {
            navigator?.navigate(ChatRoute(null))
            true
        }
        event.isCtrlPressed && event.key == Key.K -> {
            AppEvents.send(AppShortcut.SEARCH)
            true
        }
        event.isCtrlPressed && event.key == Key.Comma -> {
            AppEvents.send(AppShortcut.SETTINGS)
            true
        }
        else -> false
    }
}

/** A small always-on-top window for a quick question; "Open in app" moves the conversation to the main window. */
@Composable
private fun OverlayWindow(container: AppContainer, windows: AppWindows) {
    val state = rememberWindowState(size = DpSize(480.dp, 640.dp), position = WindowPosition(Alignment.Center))
    val back = remember { BackDispatcher() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Window(
        onCloseRequest = { windows.overlayVisible.value = false },
        state = state,
        title = Strings.get(R.string.overlay_title),
        alwaysOnTop = true,
        icon = WindowIcon.painter,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && !back.back()) {
                windows.overlayVisible.value = false
                true
            } else {
                false
            }
        },
    ) {
        CompositionLocalProvider(LocalBackDispatcher provides back) {
            AppRoot(container, snackbar) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            scope.launch {
                                // The newest chat is the one this window just started.
                                val latest = container.chats.observeSummaries().first().maxByOrNull { it.updatedAt }?.id
                                windows.overlayVisible.value = false
                                windows.showChat(latest)
                            }
                        }) { Text(stringResource(R.string.overlay_expand)) }
                    }
                    Box(Modifier.weight(1f)) {
                        OverlayNav { ChatScreen(chatId = null, onBack = { windows.overlayVisible.value = false }, onOpenChat = { windows.showChat(it) }) }
                    }
                }
            }
        }
    }
}

/** ChatScreen needs a navigation entry (saved state, lifecycle); the overlay has a one-screen graph. */
@Composable
private fun OverlayNav(content: @Composable () -> Unit) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "overlay") { composable("overlay") { content() } }
}

@Composable
private fun AlarmWindow(container: AppContainer, alarm: ActiveAlarm) {
    val state = rememberWindowState(size = DpSize(420.dp, 260.dp), position = WindowPosition(Alignment.Center))
    DisposableEffect(alarm.reminder.id) {
        val clip = AlarmSound.start()
        onDispose { clip?.run { stop(); close() } }
    }
    val snackbar = remember { SnackbarHostState() }
    Window(
        onCloseRequest = { container.reminderAlarms.stop(alarm.reminder.id) },
        state = state,
        title = Strings.get(R.string.alarm_notification_title),
        alwaysOnTop = true,
        resizable = false,
        icon = WindowIcon.painter,
    ) {
        AppRoot(container, snackbar) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(MaterialSymbols.Rounded.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Text(alarm.reminder.text, style = MaterialTheme.typography.headlineSmallEmphasized)
                    }
                    if (alarm.missed) Text(stringResource(R.string.alarm_missed_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { container.reminderAlarms.stop(alarm.reminder.id) }) { Text(stringResource(R.string.reminder_action_stop)) }
                        FilledTonalButton(onClick = { container.reminderAlarms.snooze(alarm.reminder.id) }) { Text(stringResource(R.string.reminder_action_snooze)) }
                    }
                }
            }
        }
    }
}

/**
 * The window icon is needed before any window exists, when Compose has no graphics backend for vector paths yet, so it
 * is a PNG decoded by Skia directly.
 */
private object WindowIcon {
    val painter: androidx.compose.ui.graphics.painter.Painter by lazy {
        val bytes = WindowIcon::class.java.getResourceAsStream("/icons/md3-agent.png")!!.use { it.readBytes() }
        androidx.compose.ui.graphics.painter.BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap())
    }
}

private const val MIN_WIDTH = 380
private const val MIN_HEIGHT = 520
