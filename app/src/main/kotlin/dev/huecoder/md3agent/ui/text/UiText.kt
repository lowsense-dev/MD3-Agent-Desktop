package dev.huecoder.md3agent.ui.text

import androidx.compose.runtime.Composable

sealed interface UiText {
    data class Resource(val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText

    data class Raw(val value: String) : UiText
}

@Composable
fun UiText.asString(): String = resolve()

fun UiText.resolve(): String = when (this) {
    is UiText.Resource -> Strings.get(id, *resolvedArgs(args))
    is UiText.Plural -> Strings.plural(id, count, *resolvedArgs(args))
    is UiText.Raw -> value
}

private fun resolvedArgs(args: List<Any>): Array<Any> = args.map { if (it is UiText) it.resolve() else it }.toTypedArray()
