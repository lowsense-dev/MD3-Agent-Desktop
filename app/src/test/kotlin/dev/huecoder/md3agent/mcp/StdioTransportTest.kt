package dev.huecoder.md3agent.mcp

import dev.huecoder.md3agent.data.settings.McpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class StdioTransportTest {
    private val script = File(javaClass.getResource("/fake_mcp_server.py")!!.toURI())

    @Test
    fun listsAndCallsToolsOfAServerStartedByCommand() = runBlocking {
        assumeTrue("python3 is needed for the fake server", File("/usr/bin/python3").exists())
        val server = McpServer(id = "s", name = "Fake", url = "", command = "python3 -u '${script.path}'")
        val client = McpClient(server, authorizer = null) { OkHttpClient() }
        try {
            assertEquals(listOf("shout"), client.listTools().map { it.name })
            assertEquals("Fake server", client.instructions)
            val result = client.callTool("shout", buildJsonObject { put("text", "привет mcp") })
            assertEquals(McpContent.Text("ПРИВЕТ MCP"), result.content.single())
        } finally {
            client.close()
        }
    }

    @Test
    fun closingStopsTheServerProcess() = runBlocking {
        assumeTrue("python3 is needed for the fake server", File("/usr/bin/python3").exists())
        val client = McpClient(McpServer(id = "s", name = "Fake", url = "", command = "python3 -u '${script.path}'"), authorizer = null) { OkHttpClient() }
        client.listTools()
        client.close()
        Thread.sleep(300)
        assertEquals(0, ProcessHandle.current().descendants().filter { it.info().commandLine().orElse("").contains("fake_mcp_server") }.count())
    }
}
