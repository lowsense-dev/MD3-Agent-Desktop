package dev.huecoder.md3agent.mcp

import kotlinx.serialization.json.JsonObject

/** How MCP JSON-RPC messages travel: Streamable HTTP ([McpTransport]) or a local process's stdin/stdout ([StdioTransport]). */
interface McpChannel {
    var sessionId: String?
    var protocolVersion: String?

    suspend fun request(method: String, params: JsonObject? = null, serverRequests: McpServerRequests = McpServerRequests.NONE): JsonObject

    suspend fun notify(method: String, params: JsonObject? = null)

    fun close() = Unit
}
