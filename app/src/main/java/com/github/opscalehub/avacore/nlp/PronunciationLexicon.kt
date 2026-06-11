package com.github.opscalehub.avacore.nlp

import java.io.BufferedReader
import java.io.InputStream

/**
 * A curated, high-precision pronunciation lexicon.
 *
 * eSpeak-NG's Persian G2P cannot restore unwritten short vowels or the linking
 * *ezafe* (کسرهٔ اضافه) on its own. This lexicon lets us override pronunciation for
 * specific surface forms by substituting a diacritized / re-spelled form that
 * eSpeak reads correctly (it honors explicit short-vowel diacritics).
 *
 * It is deliberately conservative: an entry only fires on an exact, boundary-
 * delimited surface match, so a wrong ezafe is never guessed. This is the seed
 * that a future ML-based ezafe/homograph model (GE2PE-style) would replace.
 *
 * File format (assets/tts/lexicon.txt), UTF-8:
 *   # comment lines start with '#'
 *   surface = replacement
 * Both single words and multi-word phrases are supported. Longer keys win.
 */
class PronunciationLexicon private constructor(
    private val entries: List<Entry>
) {
    private data class Entry(val pattern: Regex, val replacement: String)

    fun apply(text: String): String {
        if (entries.isEmpty()) return text
        var s = text
        for (e in entries) {
            s = e.pattern.replace(s, Regex.escapeReplacement(e.replacement))
        }
        return s
    }

    val size: Int get() = entries.size

    companion object {
        // Persian/Arabic letter range used to anchor whole-token boundaries.
        private const val LETTER = "؀-ۿﭐ-ﹾ"

        fun fromStream(input: InputStream?): PronunciationLexicon {
            if (input == null) return PronunciationLexicon(emptyList())
            val raw = LinkedHashMap<String, String>()
            input.bufferedReader().use { r -> parseInto(r, raw) }
            // Longest surface forms first so phrases match before their sub-words.
            val entries = raw.entries
                .sortedByDescending { it.key.length }
                .map { (k, v) -> Entry(boundaryRegex(k), v) }
            return PronunciationLexicon(entries)
        }

        /** Test/seed helper: build directly from a map. */
        fun fromMap(map: Map<String, String>): PronunciationLexicon {
            val entries = map.entries
                .sortedByDescending { it.key.length }
                .map { (k, v) -> Entry(boundaryRegex(k), v) }
            return PronunciationLexicon(entries)
        }

        private fun parseInto(reader: BufferedReader, out: MutableMap<String, String>) {
            reader.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val eq = trimmed.indexOf('=')
                if (eq <= 0) return@forEach
                val key = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) out[key] = value
            }
        }

        private fun boundaryRegex(surface: String): Regex {
            val esc = Regex.escape(surface)
            // Match only when not glued to another Persian/Arabic letter on either side.
            return Regex("(?<![$LETTER])$esc(?![$LETTER])")
        }
    }
}
