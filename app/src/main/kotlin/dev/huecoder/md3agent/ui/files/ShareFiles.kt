package dev.huecoder.md3agent.ui.files

internal fun commonMimeType(mimeTypes: List<String>): String {
    val distinct = mimeTypes.toSet()
    if (distinct.size == 1) return distinct.single()
    val families = distinct.map { it.substringBefore('/') }.toSet()
    return if (families.size == 1) "${families.single()}/*" else ANY_MIME
}

private const val ANY_MIME = "*/*"
