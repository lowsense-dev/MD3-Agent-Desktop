package dev.huecoder.md3agent.data.prefs

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Tiny string key-value file, the desktop stand-in for SharedPreferences; writes are atomic (temp file + move). */
class JsonPrefs(private val file: File) {
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val values: MutableMap<String, String> = runCatching { Json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap()).toMutableMap()

    @Synchronized
    fun getString(key: String): String? = values[key]

    @Synchronized
    fun putString(key: String, value: String) {
        values[key] = value
        save()
    }

    @Synchronized
    fun remove(key: String) {
        if (values.remove(key) != null) save()
    }

    private fun save() {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(Json.encodeToString(serializer, values))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
