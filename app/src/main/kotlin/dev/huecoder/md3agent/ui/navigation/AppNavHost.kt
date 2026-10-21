package dev.huecoder.md3agent.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.AgentShape
import dev.huecoder.md3agent.ui.avatar.BotAvatar
import dev.huecoder.md3agent.ui.text.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.huecoder.md3agent.feature.chat.ChatScreen
import dev.huecoder.md3agent.feature.main.MainScreen
import dev.huecoder.md3agent.feature.settings.AgentEditorScreen
import dev.huecoder.md3agent.feature.settings.AgentsScreen
import dev.huecoder.md3agent.data.files.LibraryLocation
import dev.huecoder.md3agent.data.files.LibraryRoot
import dev.huecoder.md3agent.feature.files.FolderScreen
import dev.huecoder.md3agent.feature.files.MediaScreen
import dev.huecoder.md3agent.feature.playground.PlaygroundScreen
import dev.huecoder.md3agent.feature.settings.AboutScreen
import dev.huecoder.md3agent.feature.settings.AppearanceScreen
import dev.huecoder.md3agent.feature.settings.ChatSettingsScreen
import dev.huecoder.md3agent.feature.settings.ModelsScreen
import dev.huecoder.md3agent.feature.settings.RemindersScreen
import dev.huecoder.md3agent.feature.settings.AccessScreen
import dev.huecoder.md3agent.feature.settings.MemorySettingsScreen
import dev.huecoder.md3agent.feature.settings.NetworkSettingsScreen
import dev.huecoder.md3agent.feature.settings.ProviderEditorScreen
import dev.huecoder.md3agent.feature.settings.SettingsDestinations
import dev.huecoder.md3agent.feature.settings.StatisticsScreen
import dev.huecoder.md3agent.feature.settings.ToolSettingsScreen
import dev.huecoder.md3agent.feature.settings.McpServerEditorScreen
import dev.huecoder.md3agent.feature.settings.McpServersScreen
import dev.huecoder.md3agent.feature.settings.VoiceProviderEditorScreen
import dev.huecoder.md3agent.feature.settings.VoiceSettingsScreen
import dev.huecoder.md3agent.ui.haptics.clickHaptics
import androidx.compose.ui.Modifier

/** Phone-style single column below this width; list on the left and the open screen on the right above it. */
val WIDE_LAYOUT_MIN_WIDTH = 840.dp
private val LIST_PANE_WIDTH = 400.dp

@Composable
fun AppNavHost(chatToOpen: String?, onChatOpened: () -> Unit, navigator: (NavController) -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= WIDE_LAYOUT_MIN_WIDTH) WideLayout(chatToOpen, onChatOpened, navigator) else NarrowLayout(chatToOpen, onChatOpened, navigator)
    }
}

@Composable
private fun NarrowLayout(chatToOpen: String?, onChatOpened: () -> Unit, navigator: (NavController) -> Unit) {
    val navController = rememberNavController()
    LaunchedEffect(navController) { navigator(navController) }
    LaunchedEffect(chatToOpen) {
        val chatId = chatToOpen ?: return@LaunchedEffect
        navController.navigate(ChatRoute(chatId)) { popUpTo(HomeRoute) }
        onChatOpened()
    }
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = Modifier.clickHaptics(),
        enterTransition = NavTransitions.enter,
        exitTransition = NavTransitions.exit,
        popEnterTransition = NavTransitions.popEnter,
        popExitTransition = NavTransitions.popExit,
    ) {
        screen<HomeRoute>(navController) { MainPane(navController) }
        detailScreens(navController)
    }
}

/** Desktop list-detail: the phone's main screen stays on the left, chats and settings pages open on the right. */
@Composable
private fun WideLayout(chatToOpen: String?, onChatOpened: () -> Unit, navigator: (NavController) -> Unit) {
    val detail = rememberNavController()
    LaunchedEffect(detail) { navigator(detail) }
    LaunchedEffect(chatToOpen) {
        val chatId = chatToOpen ?: return@LaunchedEffect
        detail.navigate(ChatRoute(chatId)) { popUpTo(EmptyDetailRoute) }
        onChatOpened()
    }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(LIST_PANE_WIDTH).fillMaxHeight()) { MainPane(detail, replaceDetail = true) }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavHost(
            navController = detail,
            startDestination = EmptyDetailRoute,
            modifier = Modifier.weight(1f).fillMaxHeight().clickHaptics(),
            enterTransition = NavTransitions.enter,
            exitTransition = NavTransitions.exit,
            popEnterTransition = NavTransitions.popEnter,
            popExitTransition = NavTransitions.popExit,
        ) {
            composable<EmptyDetailRoute> { EmptyDetail() }
            detailScreens(detail)
        }
    }
}

@Composable
private fun MainPane(navController: NavController, replaceDetail: Boolean = false) {
    // In the wide layout every page opened from the list replaces what the right pane showed.
    val go: (Any) -> Unit = { route ->
        navController.navigate(route) { if (replaceDetail) popUpTo(EmptyDetailRoute) }
    }
    MainScreen(
        onOpenChat = { go(ChatRoute(it)) },
        onOpenMessage = { chatId, messageId -> go(ChatRoute(chatId, focusMessageId = messageId)) },
        onOpenFolder = { root -> go(FolderRoute(root.name)) },
        onOpenMedia = { go(MediaRoute) },
        settingsDestinations = SettingsDestinations(
            models = { go(ModelsRoute) },
            appearance = { go(AppearanceRoute) },
            chat = { go(ChatSettingsRoute) },
            about = { go(AboutRoute) },
            agent = { go(AgentsRoute) },
            memory = { go(MemorySettingsRoute) },
            network = { go(NetworkSettingsRoute) },
            tools = { go(ToolSettingsRoute) },
            reminders = { go(RemindersRoute) },
            voice = { go(VoiceSettingsRoute) },
            access = { go(AccessRoute) },
            mcp = { go(McpServersRoute) },
            statistics = { go(StatisticsRoute) },
        ),
    )
}

@Composable
private fun EmptyDetail() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        BotAvatar(isWorking = false, restShape = AgentShape.COOKIE_9, size = 96.dp)
        Text(
            stringResource(R.string.wide_empty_detail),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

private fun NavGraphBuilder.detailScreens(navController: NavController) {
    screen<ChatRoute>(navController) { entry ->
        val route = entry.toRoute<ChatRoute>()
        ChatScreen(
            chatId = route.chatId,
            focusMessageId = route.focusMessageId,
            onBack = navController::popBackStack,
            onOpenChat = { navController.navigate(ChatRoute(it)) },
        )
    }
    screen<ProviderEditorRoute>(navController) { entry ->
        ProviderEditorScreen(providerId = entry.toRoute<ProviderEditorRoute>().providerId, onDone = navController::popBackStack)
    }
    screen<AgentsRoute>(navController) {
        AgentsScreen(onBack = navController::popBackStack, onEditAgent = { navController.navigate(AgentEditorRoute(it)) })
    }
    screen<AgentEditorRoute>(navController) { entry ->
        AgentEditorScreen(agentId = entry.toRoute<AgentEditorRoute>().agentId, onDone = navController::popBackStack)
    }
    screen<MemorySettingsRoute>(navController) { MemorySettingsScreen(onBack = navController::popBackStack) }
    screen<NetworkSettingsRoute>(navController) { NetworkSettingsScreen(onBack = navController::popBackStack) }
    screen<ToolSettingsRoute>(navController) { ToolSettingsScreen(onBack = navController::popBackStack) }
    screen<RemindersRoute>(navController) { RemindersScreen(onBack = navController::popBackStack) }
    screen<AccessRoute>(navController) { AccessScreen(onBack = navController::popBackStack) }
    screen<AppearanceRoute>(navController) { AppearanceScreen(onBack = navController::popBackStack) }
    screen<ChatSettingsRoute>(navController) { ChatSettingsScreen(onBack = navController::popBackStack) }
    screen<StatisticsRoute>(navController) { StatisticsScreen(onBack = navController::popBackStack) }
    screen<AboutRoute>(navController) {
        AboutScreen(onBack = navController::popBackStack, onSecret = { navController.navigate(PlaygroundRoute) })
    }
    screen<PlaygroundRoute>(navController) { PlaygroundScreen() }
    screen<MediaRoute>(navController) { MediaScreen(onBack = navController::popBackStack) }
    screen<FolderRoute>(navController) { entry ->
        val route = entry.toRoute<FolderRoute>()
        FolderScreen(
            location = route.toLocation(),
            onOpen = { navController.navigate(it.toRoute()) },
            onBack = navController::popBackStack,
        )
    }
    screen<ModelsRoute>(navController) {
        ModelsScreen(
            onBack = navController::popBackStack,
            onEditProvider = { navController.navigate(ProviderEditorRoute(it)) },
        )
    }
    screen<VoiceSettingsRoute>(navController) {
        VoiceSettingsScreen(
            onBack = navController::popBackStack,
            onEditProvider = { navController.navigate(VoiceProviderEditorRoute(it)) },
        )
    }
    screen<McpServersRoute>(navController) {
        McpServersScreen(
            onBack = navController::popBackStack,
            onEditServer = { navController.navigate(McpServerEditorRoute(it)) },
        )
    }
    screen<McpServerEditorRoute>(navController) { entry ->
        McpServerEditorScreen(serverId = entry.toRoute<McpServerEditorRoute>().serverId, onDone = navController::popBackStack)
    }
    screen<VoiceProviderEditorRoute>(navController) { entry ->
        VoiceProviderEditorScreen(providerId = entry.toRoute<VoiceProviderEditorRoute>().providerId, onDone = navController::popBackStack)
    }
}

private fun FolderRoute.toLocation() = LibraryLocation(
    root = LibraryRoot.valueOf(root),
    segments = path.split(PATH_SEPARATOR).filter { it.isNotEmpty() },
    fromChats = fromChats,
)

private fun LibraryLocation.toRoute() = FolderRoute(root.name, segments.joinToString(PATH_SEPARATOR), fromChats)

private const val PATH_SEPARATOR = "/"

private inline fun <reified T : Any> NavGraphBuilder.screen(
    navController: NavController,
    crossinline content: @Composable (NavBackStackEntry) -> Unit,
) = composable<T> { entry -> BackPreviewCard(entry, navController) { content(entry) } }
