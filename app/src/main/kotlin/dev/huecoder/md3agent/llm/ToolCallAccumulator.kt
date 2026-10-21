package dev.huecoder.md3agent.llm

class ToolCallAccumulator {
    private class Partial(var id: String? = null, var name: String = "", val arguments: StringBuilder = StringBuilder(), var signature: String? = null)

    private val partials = sortedMapOf<Int, Partial>()
    private val slots = HashMap<Int, Int>()

    fun add(delta: CompletionEvent.ToolCallDelta) {
        val id = delta.id?.takeIf { it.isNotEmpty() }
        var slot = slots[delta.index] ?: delta.index
        if (id != null && partials[slot]?.id.let { it != null && it != id }) {
            slot = partials.lastKey() + 1
            slots[delta.index] = slot
        }
        val partial = partials.getOrPut(slot) { Partial() }
        id?.let { partial.id = it }
        delta.name?.takeIf { it.isNotEmpty() && it != partial.name }?.let { partial.name += it }
        delta.arguments?.let { partial.arguments.append(it) }
        delta.signature?.takeIf { it.isNotEmpty() }?.let { partial.signature = it }
    }

    fun calls(): List<ToolCall> = partials.mapNotNull { (index, partial) ->
        partial.name.takeIf { it.isNotEmpty() }?.let { name ->
            ToolCall(
                id = partial.id ?: "$SYNTHETIC_ID_PREFIX$index",
                name = name,
                arguments = partial.arguments.toString().ifBlank { EMPTY_ARGUMENTS },
                signature = partial.signature,
            )
        }
    }

    private companion object {
        const val SYNTHETIC_ID_PREFIX = "call_"
        const val EMPTY_ARGUMENTS = "{}"
    }
}
