package dev.huecoder.md3agent.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.huecoder.md3agent.AppContainer
import dev.huecoder.md3agent.LocalAppContainer
import dev.huecoder.md3agent.platform.LocalPlatformContext
import dev.huecoder.md3agent.platform.PlatformContext
import dev.huecoder.md3agent.system.dbus.SessionBus
import dev.huecoder.md3agent.system.paths.AppPaths
import dev.huecoder.md3agent.ui.theme.AssistantTheme
import kotlin.io.path.createTempDirectory

/** An app container in a temp folder, cut off from the session bus so tests never touch the real keyring or desktop. */
fun testContainer(): AppContainer {
    val root = createTempDirectory("md3-app").toFile()
    val env = mapOf(
        "XDG_DATA_HOME" to root.resolve("data").path,
        "XDG_CONFIG_HOME" to root.resolve("config").path,
        "XDG_STATE_HOME" to root.resolve("state").path,
        "XDG_CACHE_HOME" to root.resolve("cache").path,
        "XDG_RUNTIME_DIR" to root.resolve("run").path,
    )
    return AppContainer(AppPaths(env = env, home = root.resolve("home")), bus = SessionBus.unavailable(), systemdTimers = false)
}

private class TestOwner : LifecycleOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
}

/** Renders [content] the way the app window would: theme, container and a navigation entry around it. */
@Composable
fun TestApp(container: AppContainer, dark: Boolean = false, content: @Composable () -> Unit) {
    val owner = TestOwner()
    CompositionLocalProvider(
        LocalLifecycleOwner provides owner,
        LocalViewModelStoreOwner provides owner,
        LocalAppContainer provides container,
        LocalPlatformContext provides PlatformContext(container) {},
    ) {
        AssistantTheme(darkTheme = dark) {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "screen") { composable("screen") { content() } }
        }
    }
}

/** Lifecycle and view-model owners, as a window provides them, for content that brings its own navigation. */
@Composable
fun TestOwners(content: @Composable () -> Unit) {
    val owner = TestOwner()
    CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalViewModelStoreOwner provides owner, content = content)
}
