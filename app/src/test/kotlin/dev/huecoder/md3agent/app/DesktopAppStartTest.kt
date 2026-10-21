package dev.huecoder.md3agent.app

import androidx.compose.ui.window.ApplicationScope
import dev.huecoder.md3agent.testing.renderScreen
import dev.huecoder.md3agent.testing.testContainer
import kotlin.test.Test

/**
 * The application-level composition, as `application { }` runs it: no window yet, so no lifecycle or view-model owner.
 * With every window hidden nothing tries to open a real window, which a headless test could not do.
 */
class DesktopAppStartTest {
    @Test
    fun composesWithoutWindowOwners() {
        val container = testContainer()
        val windows = AppWindows().apply { mainVisible.value = false }
        val app = object : ApplicationScope {
            override fun exitApplication() = Unit
        }
        renderScreen("app-start", 10, 10, frames = 2) { with(app) { DesktopApp(container, windows) } }
    }
}
