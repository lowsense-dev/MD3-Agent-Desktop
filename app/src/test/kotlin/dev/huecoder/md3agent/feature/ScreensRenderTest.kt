package dev.huecoder.md3agent.feature

import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.data.settings.AgentProfile
import dev.huecoder.md3agent.data.settings.ModelSelection
import dev.huecoder.md3agent.feature.chat.ChatScreen
import dev.huecoder.md3agent.feature.home.HomeScreen
import dev.huecoder.md3agent.feature.settings.AccessScreen
import dev.huecoder.md3agent.testing.TestApp
import dev.huecoder.md3agent.testing.TestOwners
import dev.huecoder.md3agent.testing.renderScreen
import dev.huecoder.md3agent.testing.testContainer
import dev.huecoder.md3agent.ui.text.Strings
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue

class ScreensRenderTest {
    init {
        Strings.locale = Locale.forLanguageTag("ru")
    }

    private val container = testContainer()

    private fun seededChat(): String = runBlocking {
        val chat = container.chats.createChat("Python и графики", ModelSelection("p1", "gpt-5"), AgentProfile.MAIN_AGENT_ID)
        container.chats.appendUserMessage(chat.id, "Посчитай интеграл и покажи код", emptyList())
        val reply = container.chats.startAssistantMessage(chat.id, "gpt-5")
        container.chats.save(
            reply.copy(
                content = "Готово:\n\n$$\\int_0^1 x^2\\,dx = \\frac{1}{3}$$\n\n```python\nimport numpy as np\nprint(np.trapezoid([0, 1, 4]))\n```\n\n- первый пункт\n- второй пункт",
                status = MessageStatus.COMPLETE,
            ),
        )
        chat.id
    }

    private fun assertNotBlank(name: String, image: org.jetbrains.skia.Image) {
        val bitmap = Bitmap.makeFromImage(image)
        val colors = (0 until image.width step 7).flatMap { x -> (0 until image.height step 7).map { y -> bitmap.getColor(x, y) } }.toSet()
        assertTrue(colors.size > 8, "$name looks blank")
    }

    @Test
    fun rendersChatWithMessagesNarrowAndWide() {
        val id = seededChat()
        listOf("chat-narrow" to (420 to 900), "chat-wide" to (1280 to 800)).forEach { (name, size) ->
            val image = renderScreen(name, size.first, size.second, frames = 12) {
                TestApp(container) { ChatScreen(chatId = id, onBack = {}, onOpenChat = {}) }
            }
            assertNotBlank(name, image)
        }
    }

    @Test
    fun rendersNewChatDark() {
        val image = renderScreen("chat-new-dark", 420, 900, frames = 8) {
            TestApp(container, dark = true) { ChatScreen(chatId = null, onBack = {}, onOpenChat = {}) }
        }
        assertNotBlank("chat-new-dark", image)
    }

    @Test
    fun rendersHomeWithChats() {
        seededChat()
        val image = renderScreen("home", 420, 900, frames = 8) {
            TestApp(container) { HomeScreen(onOpenChat = {}, onOpenMessage = { _, _ -> }, onOpenSettings = {}) }
        }
        assertNotBlank("home", image)
    }

    @Test
    fun rendersAccessSettings() {
        val image = renderScreen("access", 420, 1400, frames = 20) {
            TestApp(container) { AccessScreen(onBack = {}) }
        }
        assertNotBlank("access", image)
    }

    @Test
    fun rendersWideAndNarrowAppLayouts() {
        seededChat()
        listOf("app-wide" to (1280 to 800), "app-narrow" to (420 to 900)).forEach { (name, size) ->
            val image = renderScreen(name, size.first, size.second, frames = 12) {
                TestOwners {
                    dev.huecoder.md3agent.app.AppRoot(container, androidx.compose.material3.SnackbarHostState()) {
                        dev.huecoder.md3agent.ui.navigation.AppNavHost(chatToOpen = null, onChatOpened = {})
                    }
                }
            }
            assertNotBlank(name, image)
        }
    }

    @Test
    fun rendersSettingsListAndImport() {
        val settings = renderScreen("settings", 420, 1500, frames = 10) {
            TestApp(container) {
                dev.huecoder.md3agent.feature.settings.SettingsScreen(
                    dev.huecoder.md3agent.feature.settings.SettingsDestinations({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                )
            }
        }
        assertNotBlank("settings", settings)
        val import = renderScreen("import", 420, 900, frames = 6) {
            TestApp(container, dark = true) { dev.huecoder.md3agent.feature.settings.ImportScreen(onBack = {}) }
        }
        assertNotBlank("import", import)
    }
}
