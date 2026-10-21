package dev.huecoder.md3agent.llm.models

import dev.huecoder.md3agent.llm.TokenUsage
import kotlinx.serialization.Serializable

@Serializable
enum class ReasoningEffort(val wireName: String) {
    NONE("none"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    ;

    companion object {
        val STANDARD = listOf(LOW, MEDIUM, HIGH)

        fun fromWire(value: String): ReasoningEffort? = entries.firstOrNull { it.wireName.equals(value.trim(), ignoreCase = true) }
    }
}

@Serializable
enum class ReasoningControl {
    EFFORT_FIELD,
    REASONING_OBJECT,
}

@Serializable
enum class ModelOutput { TEXT, IMAGE, TEXT_AND_IMAGE }

@Serializable
data class ModelInfo(
    val id: String,
    val name: String? = null,
    val contextLength: Int? = null,
    val vision: Boolean? = null,
    val reasoning: Boolean? = null,
    val output: ModelOutput? = null,
    val reasoningEfforts: List<ReasoningEffort> = emptyList(),
    val reasoningControl: ReasoningControl? = null,
    val toolUse: Boolean? = null,
    val price: ModelPrice? = null,
) {
    val family: ModelFamily? get() = ModelFamily.detect(id)

    /** Tokens the model accepts per request: what the catalog reported, else the known window of its model line. */
    val contextWindow: Int? get() = contextLength?.takeIf { it > 0 } ?: ModelHeuristics.contextWindow(id)

    val displayName: String get() = name?.substringAfter(": ")?.takeIf { it.isNotBlank() } ?: id.substringAfterLast('/')

    val traits: ModelTraits get() = ModelTraits.of(this)
}

// USD per token; every wire format counts cached tokens inside promptTokens.
@Serializable
data class ModelPrice(val prompt: Double, val completion: Double, val cacheRead: Double? = null) {
    fun cost(usage: TokenUsage): Double = cost(usage.promptTokens.toLong(), usage.completionTokens.toLong(), usage.cachedTokens.toLong())

    fun cost(promptTokens: Long, completionTokens: Long, cachedTokens: Long): Double {
        val cached = cachedTokens.coerceIn(0, promptTokens)
        return (promptTokens - cached) * prompt + cached * (cacheRead ?: prompt) + completionTokens * completion
    }
}

data class ModelTraits(
    val vision: Boolean,
    val reasoning: Boolean,
    val output: ModelOutput,
    val reasoningEfforts: List<ReasoningEffort>,
    val reasoningControl: ReasoningControl,
) {
    val generatesImages: Boolean get() = output != ModelOutput.TEXT

    companion object {
        fun of(info: ModelInfo): ModelTraits {
            val guess = ModelHeuristics.guess(info.id)
            val reasoning = info.reasoning ?: guess.reasoning
            return ModelTraits(
                vision = info.vision ?: guess.vision,
                reasoning = reasoning,
                output = info.output ?: guess.output,
                reasoningEfforts = info.reasoningEfforts.ifEmpty { if (reasoning) ReasoningEffort.STANDARD else emptyList() },
                reasoningControl = info.reasoningControl ?: ReasoningControl.EFFORT_FIELD,
            )
        }
    }
}
