package dev.huecoder.md3agent.system.python

import dev.huecoder.md3agent.data.settings.ShellMode
import dev.huecoder.md3agent.system.paths.AppPaths
import dev.huecoder.md3agent.system.shell.ShellAccess
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PythonRuntimeTest {
    private val root = createTempDirectory("md3-py").toFile()
    private val paths = AppPaths(env = mapOf("XDG_DATA_HOME" to root.resolve("data").path, "XDG_CACHE_HOME" to root.resolve("cache").path), home = root, uid = 1000)
    private val runtime = PythonRuntime(paths, ShellAccess(paths))
    private val workdir: File = paths.workspace.apply { mkdirs() }

    @BeforeTest
    fun needsPython() = assumeTrue("python3 is not installed", File("/usr/bin/python3").exists())

    @Test
    fun runsScriptAndCapturesOutput() = runBlocking {
        val result = runtime.run("print(2+2)", workdir, timeoutSeconds = 10, mode = ShellMode.SYSTEM, network = false)
        assertFalse(result.failed)
        assertEquals("4", result.output.trim())
    }

    @Test
    fun reportsFailure() = runBlocking {
        val result = runtime.run("raise ValueError('x')", workdir, timeoutSeconds = 10, mode = ShellMode.SYSTEM, network = false)
        assertTrue(result.failed)
        assertContains(result.output, "ValueError")
    }

    @Test
    fun timesOut() = runBlocking {
        val result = runtime.run("while True: pass", workdir, timeoutSeconds = 1, mode = ShellMode.SYSTEM, network = false)
        assertTrue(result.timedOut)
    }

    @Test
    fun leavesNoScriptFilesBehind() = runBlocking {
        runtime.run("print(1)", workdir, timeoutSeconds = 10, mode = ShellMode.SYSTEM, network = false)
        assertEquals(emptyList(), workdir.listFiles()!!.filter { it.name.startsWith(".md3-script") }.map { it.name })
    }
}
