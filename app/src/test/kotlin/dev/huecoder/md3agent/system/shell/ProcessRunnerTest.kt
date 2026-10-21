package dev.huecoder.md3agent.system.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessRunnerTest {
    @Test
    fun capturesExitCodeAndStreams() {
        val result = ProcessRunner.run(listOf("sh", "-c", "echo out; echo err >&2; exit 3"), timeoutMs = 5_000)
        assertEquals(3, result.exitCode)
        assertEquals("out\n", result.stdout)
        assertEquals("err\n", result.stderr)
    }

    @Test
    fun killsTreeOnTimeout() {
        val marker = "md3-tree-test-${System.nanoTime()}"
        // The inner shells keep the marker in their own command line, so a leaked child is visible after the parent dies.
        val child = "sh -c 'sleep 100; : $marker'"
        val result = ProcessRunner.run(listOf("sh", "-c", "$child & $child & wait"), timeoutMs = 500)
        assertTrue(result.timedOut)
        Thread.sleep(300)
        val survivors = ProcessHandle.allProcesses().filter { it.info().commandLine().orElse("").contains(marker) }.count()
        assertEquals(0, survivors)
    }

    @Test
    fun capsOutput() {
        val result = ProcessRunner.run(listOf("sh", "-c", "yes | head -c 2000000"), timeoutMs = 10_000, maxOutputBytes = 64 * 1024)
        assertTrue(result.stdout.length <= 64 * 1024 + 100, "stdout was ${result.stdout.length} chars")
        assertTrue(result.stdout.endsWith("[output truncated]"))
    }
}
