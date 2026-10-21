package dev.huecoder.md3agent.conversation

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPromptsTest {
    private fun question(text: String) = UserQuestion(text, header = null, options = emptyList(), multiSelect = false, allowCustom = true)

    @Test
    fun `a newer question in the same chat skips the older one instead of leaving it hanging`() = runTest {
        val prompts = UserPrompts()
        val first = async { prompts.ask("c", question("first")) }
        runCurrent()
        val second = async { prompts.ask("c", question("second")) }
        runCurrent()

        assertEquals(PromptAnswer.Skipped, first.await())
        val shown = prompts.pending.value.getValue("c")
        assertEquals("second", (shown.prompt as UserQuestion).question)

        prompts.answer(shown.id, PromptAnswer.Chosen(listOf("ok"), custom = null))
        assertEquals(PromptAnswer.Chosen(listOf("ok"), custom = null), second.await())
    }
}
