package dev.huecoder.md3agent.system.shell

import dev.huecoder.md3agent.data.settings.ShellMode
import dev.huecoder.md3agent.system.paths.AppPaths
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class SandboxShellTest {
    private val root = createTempDirectory("md3-sandbox").toFile()
    private val home = root.resolve("home").apply { mkdirs() }
    private val access = ShellAccess(AppPaths(env = mapOf("XDG_DATA_HOME" to root.resolve("data").path), home = home, uid = 1000))

    private fun ready() = runBlocking { access.status(ShellMode.SANDBOX) == AccessStatus.READY }

    @Test
    fun workspaceIsWritableAndTheRestOfHomeIsHidden() = runBlocking {
        assumeTrue("bubblewrap cannot run here", ready())
        val secret = File(System.getProperty("user.home"), ".bashrc").takeIf { it.exists() } ?: File(System.getProperty("user.home"))
        val result = access.shell(ShellMode.SANDBOX, network = false).run(
            "echo hi > note.txt && cat note.txt && if [ -e '${secret.path}' ]; then echo visible; else echo hidden; fi",
            10_000,
        )
        assertEquals(0, result.exitCode, result.stderr)
        assertEquals("hi\nhidden\n", result.stdout)
        assertEquals("hi\n", access.workspace.resolve("note.txt").readText())
    }
}
