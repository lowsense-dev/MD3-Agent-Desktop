package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.tools.system.RunCommandTool
import dev.huecoder.md3agent.tools.system.RunPythonTool
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolApprovalsTest {
    @Test
    fun `only tools without full app access are remembered for the session`() = runTest {
        val approvals = ToolApprovals()
        listOf(RunPythonTool.NAME, RunCommandTool.NAME, "write_file").forEachIndexed { index, name ->
            val asked = async { approvals.request("chat", ToolCall("$index", name, "{}")) }
            runCurrent()
            approvals.respond("chat", "$index", ApprovalDecision.ALLOW_FOR_SESSION)
            assertTrue(asked.await())
        }

        assertEquals(
            listOf(false, false, true),
            listOf(RunPythonTool.NAME, RunCommandTool.NAME, "write_file").map { approvals.isAllowedForSession("chat", it) },
        )
    }

    @Test
    fun `yolo approves the waiting call and every later one in that chat only`() = runTest {
        val approvals = ToolApprovals()
        val asked = async { approvals.request("chat", ToolCall("1", RunPythonTool.NAME, "{}")) }
        runCurrent()

        approvals.setYolo("chat", true)

        assertTrue(asked.await())
        assertTrue(approvals.isAllowedForSession("chat", RunCommandTool.NAME))
        assertEquals(false, approvals.isAllowedForSession("other", RunCommandTool.NAME))
        approvals.setYolo("chat", false)
        assertEquals(false, approvals.isAllowedForSession("chat", RunCommandTool.NAME))
    }
}
