package dev.huecoder.md3agent.system.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SandboxArgsTest {
    private val workspace = File("/tmp/a b/ws")

    @Test
    fun networkOffByDefault() {
        val args = SandboxArgs.build(workspace, network = false, command = listOf("bash", "-c", "ls"))
        assertTrue("--unshare-all" in args)
        assertFalse("--share-net" in args)
    }

    @Test
    fun networkFlagAddsShareNet() {
        val args = SandboxArgs.build(workspace, network = true, command = listOf("true"))
        assertTrue("--share-net" in args)
    }

    @Test
    fun keepsPathsWithSpaces() {
        val args = SandboxArgs.build(workspace, network = false, command = listOf("true"))
        val bind = args.indexOf("--bind")
        assertEquals("/tmp/a b/ws", args[bind + 1])
        assertEquals("/tmp/a b/ws", args[bind + 2])
        assertEquals("/tmp/a b/ws", args[args.indexOf("--chdir") + 1])
    }

    @Test
    fun commandFollowsDoubleDash() {
        val args = SandboxArgs.build(workspace, network = false, command = listOf("bash", "-c", "echo hi"))
        assertEquals("bwrap", args.first())
        assertEquals(listOf("--", "bash", "-c", "echo hi"), args.takeLast(4))
    }

    @Test
    fun extraReadOnlyBindsComeBeforeCommand() {
        val venv = File("/home/u/.local/share/md3-agent/venv")
        val args = SandboxArgs.build(workspace, network = false, command = listOf("python3"), readOnly = listOf(venv))
        val i = args.lastIndexOf("--ro-bind")
        assertEquals(venv.path, args[i + 1])
        assertTrue(i < args.indexOf("--"))
    }
}
