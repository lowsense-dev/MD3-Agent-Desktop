package dev.huecoder.md3agent.data.chat

import dev.huecoder.md3agent.data.prefs.JsonPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class SearchHistory(file: File) {
    private val prefs = JsonPrefs(file)
    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun add(query: String) {
        val clean = query.trim().takeIf { it.isNotEmpty() } ?: return
        update(listOf(clean) + _entries.value.filterNot { it.equals(clean, ignoreCase = true) })
    }

    fun remove(query: String) = update(_entries.value - query)

    private fun update(entries: List<String>) {
        val kept = entries.take(LIMIT)
        _entries.value = kept
        prefs.putString(KEY, kept.joinToString(SEPARATOR))
    }

    private fun read(): List<String> = prefs.getString(KEY)?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val KEY = "queries"
        const val SEPARATOR = "\n"
        const val LIMIT = 8
    }
}
