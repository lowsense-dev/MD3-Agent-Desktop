package dev.huecoder.md3agent.tools

/**
 * Marks text that came from outside the user's control (web pages, other apps' screens, MCP servers, old chats) so the
 * model treats it as data; the system prompt explains the tag. A closing tag inside the text cannot end the block early.
 */
fun untrusted(source: String, text: String): String =
    "<untrusted source=\"$source\">\n" + text.replace("</untrusted", "<\\/untrusted", ignoreCase = true) + "\n</untrusted>"

private val UNTRUSTED_BLOCK = Regex("""<untrusted source="[^"]*">\n(.*?)\n</untrusted>""", RegexOption.DOT_MATCHES_ALL)

/** The text as the tool produced it, for showing in the app or parsing; the model keeps getting the wrapped form. */
fun withoutUntrustedTags(text: String): String =
    UNTRUSTED_BLOCK.replace(text) { it.groupValues[1].replace("<\\/untrusted", "</untrusted") }
