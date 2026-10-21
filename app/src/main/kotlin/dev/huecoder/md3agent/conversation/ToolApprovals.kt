package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.tools.system.RunCommandTool
import dev.huecoder.md3agent.tools.system.RunPythonTool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

enum class ApprovalDecision { ALLOW, DENY, ALLOW_FOR_SESSION }

class ToolApprovals {
    private val waiting = MutableStateFlow<Map<String, Waiting>>(emptyMap())
    private val sessionAllowed = ConcurrentHashMap<String, Set<String>>()
    private val yoloChats = MutableStateFlow<Set<String>>(emptySet())

    fun isAllowedForSession(chatId: String, toolName: String): Boolean = chatId in yoloChats.value || toolName in sessionAllowed[chatId].orEmpty()

    fun yolo(chatId: String): Flow<Boolean> = yoloChats.map { chatId in it }.distinctUntilChanged()

    fun setYolo(chatId: String, on: Boolean) {
        yoloChats.update { if (on) it + chatId else it - chatId }
        if (on) waiting.value[chatId]?.decision?.complete(ApprovalDecision.ALLOW)
    }

    suspend fun request(chatId: String, call: ToolCall): Boolean {
        val entry = Waiting(call, CompletableDeferred())
        waiting.update { it + (chatId to entry) }
        try {
            val decision = entry.decision.await()
            if (decision == ApprovalDecision.ALLOW_FOR_SESSION && offersSession(call.name)) sessionAllowed.merge(chatId, setOf(call.name), Set<String>::plus)
            return decision != ApprovalDecision.DENY
        } finally {
            waiting.update { current -> if (current[chatId] === entry) current - chatId else current }
        }
    }

    fun respond(chatId: String, callId: String, decision: ApprovalDecision) {
        waiting.value[chatId]?.takeIf { it.call.id == callId }?.decision?.complete(decision)
    }

    private class Waiting(val call: ToolCall, val decision: CompletableDeferred<ApprovalDecision>)

    companion object {
        private val FULL_ACCESS_TOOLS = setOf(RunPythonTool.NAME, RunCommandTool.NAME)

        fun offersSession(toolName: String): Boolean = toolName !in FULL_ACCESS_TOOLS
    }
}
