package com.github.opscalehub.avacore.nlp

/**
 * Minimal SSML support.
 *
 * Android passes SSML through to the engine unchanged, and many accessibility
 * tools emit it. We support the subset that matters for a TTS engine:
 *   - <speak> ... </speak>           wrapper (stripped)
 *   - <break time="500ms"/>          inserts a pause
 *   - <break strength="strong"/>     inserts a pause by named strength
 *   - <say-as interpret-as="characters|digits"> X </say-as>  spells X out
 *   - all other tags are stripped, their text content kept
 *
 * Plain (non-SSML) text is returned as a single pass-through segment.
 */
object Ssml {

    /** One chunk of SSML content plus an optional forced pause after it. */
    data class Segment(val text: String, val breakAfterMs: Int, val spellOut: Boolean)

    private val TAG = Regex("<[^>]+>")
    private val BREAK_TIME = Regex("time\\s*=\\s*\"?([0-9.]+)(ms|s)?\"?", RegexOption.IGNORE_CASE)
    private val BREAK_STRENGTH = Regex("strength\\s*=\\s*\"?([a-z-]+)\"?", RegexOption.IGNORE_CASE)
    private val INTERPRET_AS = Regex("interpret-as\\s*=\\s*\"?([a-z-]+)\"?", RegexOption.IGNORE_CASE)

    fun isSsml(text: String): Boolean {
        val t = text.trimStart()
        return t.startsWith("<speak", ignoreCase = true)
    }

    fun parse(text: String): List<Segment> {
        if (!isSsml(text)) return listOf(Segment(text, 0, false))

        val segments = ArrayList<Segment>()
        val buf = StringBuilder()
        var spellOut = false
        var lastIndex = 0

        fun flushText(breakAfterMs: Int) {
            val t = buf.toString()
            buf.setLength(0)
            if (t.isNotBlank() || breakAfterMs > 0) {
                segments.add(Segment(t.trim(), breakAfterMs, spellOut))
            }
        }

        for (m in TAG.findAll(text)) {
            // text between previous tag and this one
            buf.append(text, lastIndex, m.range.first)
            lastIndex = m.range.last + 1

            val tag = m.value
            val name = tagName(tag)
            when (name) {
                "break" -> flushText(breakMs(tag))
                "say-as" -> {
                    if (!tag.startsWith("</")) {
                        flushText(0)
                        val mode = INTERPRET_AS.find(tag)?.groupValues?.get(1)?.lowercase()
                        spellOut = mode == "characters" || mode == "digits"
                    } else {
                        flushText(0)
                        spellOut = false
                    }
                }
                else -> { /* strip; keep accumulated text */ }
            }
        }
        if (lastIndex < text.length) buf.append(text, lastIndex, text.length)
        flushText(0)
        return segments.ifEmpty { listOf(Segment("", 0, false)) }
    }

    // (tag parsing helpers below)

    private fun tagName(tag: String): String {
        val inner = tag.trim('<', '>', '/', ' ')
        val end = inner.indexOfFirst { it == ' ' || it == '/' }
        return (if (end >= 0) inner.substring(0, end) else inner).lowercase()
    }

    private fun breakMs(tag: String): Int {
        BREAK_TIME.find(tag)?.let { mt ->
            val value = mt.groupValues[1].toFloatOrNull() ?: return@let
            val unit = mt.groupValues[2].lowercase()
            return if (unit == "s") (value * 1000).toInt() else value.toInt()
        }
        return when (BREAK_STRENGTH.find(tag)?.groupValues?.get(1)?.lowercase()) {
            "none", "x-weak" -> 0
            "weak" -> 150
            "medium", null -> 300
            "strong" -> 500
            "x-strong" -> 800
            else -> 300
        }
    }
}
