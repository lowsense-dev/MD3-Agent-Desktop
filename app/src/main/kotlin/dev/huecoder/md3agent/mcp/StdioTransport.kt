package dev.huecoder.md3agent.mcp

import dev.huecoder.md3agent.platform.Log
import dev.huecoder.md3agent.system.environment.Programs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * MCP over stdio: the server is a local program started with [command] through the user's shell (so `npx`, `uvx` and
 * PATH from their profile work), exchanging one JSON-RPC message per line. It starts on the first message and is
 * stopped, with everything it spawned, by [close].
 */
class StdioTransport(private val command: String, private val workdir: File) : McpChannel {
    private val json = Json { ignoreUnknownKeys = true }
    private val nextId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonObject>>()
    private val handlers = ConcurrentHashMap<Long, McpServerRequests>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var process: Process? = null
    private var writer: BufferedWriter? = null

    override var sessionId: String? = null
    override var protocolVersion: String? = null

    override suspend fun request(method: String, params: JsonObject?, serverRequests: McpServerRequests): JsonObject {
        val id = nextId.getAndIncrement()
        val reply = CompletableDeferred<JsonObject>()
        pending[id] = reply
        handlers[id] = serverRequests
        try {
            send(message(method, params) { put("id", id) })
            val answer = withTimeout(REQUEST_TIMEOUT_MS) { reply.await() }
            (answer["error"] as? JsonObject)?.let { error -> throw McpException((error["message"] as? JsonPrimitive)?.content ?: "MCP error") }
            return answer["result"] as? JsonObject ?: JsonObject(emptyMap())
        } finally {
            pending.remove(id)
            handlers.remove(id)
        }
    }

    override suspend fun notify(method: String, params: JsonObject?) = send(message(method, params) {})

    override fun close() {
        val running = process ?: return
        process = null
        scope.cancel()
        runCatching { writer?.close() }
        val tree = running.toHandle().descendants().toList() + running.toHandle()
        tree.forEach { it.destroy() }
        if (!running.waitFor(STOP_WAIT_S, java.util.concurrent.TimeUnit.SECONDS)) tree.forEach { it.destroyForcibly() }
        pending.values.forEach { it.completeExceptionally(McpException("The MCP server was stopped")) }
    }

    private suspend fun send(line: String) = withContext(Dispatchers.IO) {
        val out = synchronized(this@StdioTransport) { writer ?: start() }
        synchronized(out) {
            out.write(line)
            out.newLine()
            out.flush()
        }
    }

    private fun start(): BufferedWriter {
        val shell = Programs.find("bash")?.path ?: "/bin/sh"
        workdir.mkdirs()
        val started = ProcessBuilder(shell, "-lc", command).directory(workdir).start()
        process = started
        val out = started.outputStream.bufferedWriter()
        writer = out
        scope.launch {
            started.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> if (line.isNotBlank()) runCatching { receive(json.parseToJsonElement(line).jsonObject) }.onFailure { Log.w(TAG, "Bad line from $command: ${it.message}") } }
            }
            pending.values.forEach { it.completeExceptionally(McpException("The MCP server exited")) }
        }
        scope.launch { started.errorStream.bufferedReader().useLines { lines -> lines.forEach { Log.d(TAG, it) } } }
        return out
    }

    private suspend fun receive(message: JsonObject) {
        val id = message["id"]
        val method = (message["method"] as? JsonPrimitive)?.content
        when {
            method == null && id is JsonPrimitive -> id.longOrNull?.let { pending[it]?.complete(message) }
            method != null && id != null -> {
                // A request from the server (elicitation and the like) goes to whoever is waiting on it now.
                val handler = handlers.values.lastOrNull() ?: McpServerRequests.NONE
                respond(id, runCatching { handler.handle(method, message["params"] as? JsonObject) }.getOrNull())
            }
        }
    }

    private suspend fun respond(id: JsonElement, result: JsonObject?) = send(
        buildJsonObject {
            put("jsonrpc", JSONRPC_VERSION)
            put("id", id)
            if (result != null) {
                put("result", result)
            } else {
                putJsonObject("error") {
                    put("code", METHOD_NOT_FOUND)
                    put("message", "This client does not support the request")
                }
            }
        }.toString(),
    )

    private fun message(method: String, params: JsonObject?, extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        buildJsonObject {
            put("jsonrpc", JSONRPC_VERSION)
            extra()
            put("method", method)
            if (params != null) put("params", params)
        }.toString()

    private companion object {
        const val TAG = "McpStdio"
        const val JSONRPC_VERSION = "2.0"
        const val METHOD_NOT_FOUND = -32601
        const val REQUEST_TIMEOUT_MS = 120_000L
        const val STOP_WAIT_S = 2L
    }
}
