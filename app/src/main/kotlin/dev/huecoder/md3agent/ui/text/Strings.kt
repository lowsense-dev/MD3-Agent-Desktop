package dev.huecoder.md3agent.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.huecoder.md3agent.R
import org.w3c.dom.Element
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Android-style strings.xml (same files as the phone app): default English plus one localized table. */
class StringTable(defaultXml: String, localizedXml: String?, private val locale: Locale) {
    private val fallback = parse(defaultXml)
    private val localized = localizedXml?.let(::parse)

    fun get(name: String, vararg args: Any): String {
        val raw = localized?.strings?.get(name) ?: fallback.strings[name] ?: return name
        return format(raw, args)
    }

    fun plural(name: String, count: Int, vararg args: Any): String {
        val local = localized?.plurals?.get(name)
        val forms = local ?: fallback.plurals[name] ?: return name
        val category = if (local != null) category(locale, count) else category(Locale.ENGLISH, count)
        val raw = forms[category] ?: forms["other"] ?: forms.values.first()
        return format(raw, args)
    }

    private fun format(raw: String, args: Array<out Any>): String = if (args.isEmpty()) raw else String.format(locale, raw, *args)

    private class Parsed(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun parse(xml: String): Parsed {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        val strings = doc.getElementsByTagName("string").elements().associate { it.getAttribute("name") to unescape(it.textContent) }
        val plurals = doc.getElementsByTagName("plurals").elements().associate { plural ->
            plural.getAttribute("name") to plural.getElementsByTagName("item").elements().associate { it.getAttribute("quantity") to unescape(it.textContent) }
        }
        return Parsed(strings, plurals)
    }

    private fun org.w3c.dom.NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

    companion object {
        /** Android resource rules: whitespace collapses outside double quotes, quotes are dropped, backslash escapes. */
        fun unescape(text: String): String {
            val out = StringBuilder()
            var quoted = false
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    c == '\\' && i + 1 < text.length -> {
                        val next = text[i + 1]
                        when (next) {
                            'n' -> out.append('\n')
                            't' -> out.append('\t')
                            'u' -> if (i + 5 < text.length) {
                                out.append(text.substring(i + 2, i + 6).toInt(16).toChar())
                                i += 4
                            }
                            else -> out.append(next)
                        }
                        i += 2
                        continue
                    }
                    c == '"' -> quoted = !quoted
                    !quoted && c.isWhitespace() -> if (out.isNotEmpty() && out.last() != ' ' && !out.endsWith("\n")) out.append(' ')
                    else -> out.append(c)
                }
                i++
            }
            return if (text.trimStart().startsWith('"')) out.toString() else out.toString().trim(' ')
        }

        /** CLDR integer plural categories for the two locales the app ships. */
        fun category(locale: Locale, count: Int): String = when (locale.language) {
            "ru" -> {
                val mod10 = count % 10
                val mod100 = count % 100
                when {
                    mod10 == 1 && mod100 != 11 -> "one"
                    mod10 in 2..4 && mod100 !in 12..14 -> "few"
                    else -> "many"
                }
            }
            else -> if (count == 1) "one" else "other"
        }
    }
}

/** The app's strings, addressed by the generated [R] ids. */
object Strings {
    @Volatile
    var locale: Locale = Locale.getDefault()
        set(value) {
            field = value
            table = null
        }

    @Volatile
    private var table: StringTable? = null

    private fun table(): StringTable = table ?: load(locale).also { table = it }

    private fun load(locale: Locale): StringTable {
        fun read(path: String): String? = Strings::class.java.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }
        val localized = if (locale.language == "en") null else read("/strings/values-${locale.language}/strings.xml")
        return StringTable(read("/strings/values/strings.xml") ?: "<resources/>", localized, locale)
    }

    fun get(id: Int, vararg args: Any): String = table().get(R.stringNames[id], *args)

    fun plural(id: Int, count: Int, vararg args: Any): String = table().plural(R.pluralNames[id - PLURAL_BASE], count, *args)

    private const val PLURAL_BASE = 100_000
}

@Composable
fun stringResource(id: Int, vararg args: Any): String {
    val key = args.toList()
    return remember(id, key, Strings.locale) { Strings.get(id, *args) }
}

@Composable
fun pluralStringResource(id: Int, count: Int, vararg args: Any): String {
    val key = args.toList()
    return remember(id, count, key, Strings.locale) { Strings.plural(id, count, *args) }
}

/**
 * The language the session asks for, from LC_ALL, LC_MESSAGES or LANG in that order (as glibc reads them). Java only
 * honours these when the matching locale is generated, so a ru_RU session without ru_RU in locale.gen still gets Russian.
 */
fun systemLocale(env: Map<String, String> = System.getenv(), fallback: Locale = Locale.getDefault()): Locale {
    val value = listOf("LC_ALL", "LC_MESSAGES", "LANG").firstNotNullOfOrNull { key -> env[key]?.takeIf { it.isNotBlank() } } ?: return fallback
    val language = value.substringBefore('.').substringBefore('@').substringBefore('_').lowercase()
    return if (language.length !in 2..3 || language == "c" || language == "posix") fallback else Locale.forLanguageTag(language)
}
