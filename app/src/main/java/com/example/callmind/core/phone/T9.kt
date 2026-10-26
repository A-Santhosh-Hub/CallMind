package com.example.callmind.core.phone

import java.text.Normalizer

/**
 * Keypad-letter matching, like the phone's own dialer: typing 5-2-6 finds "Kamal" (K=5, A=2, M=6),
 * and also "J 296 Muruga" - the first characters of consecutive words (J=5, 2, M=6).
 */
object T9 {
    private const val KEYS = "22233344455566677778889999" // a..z

    private fun encodeChar(c: Char): Char? = when {
        c in 'a'..'z' -> KEYS[c - 'a']
        c in '0'..'9' -> c
        else -> null
    }

    /** Lower-case base letter per character ("É" -> "e"), so indexes still line up with the original text. */
    private fun baseChars(name: String): CharArray = CharArray(name.length) { i ->
        val c = name[i]
        val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
        base.lowercaseChar()
    }

    private fun isKey(c: Char) = c in 'a'..'z' || c in '0'..'9'

    /** Runs of letters/digits as (start, endExclusive). */
    private fun words(base: CharArray): List<IntRange> {
        val result = ArrayList<IntRange>()
        var i = 0
        while (i < base.size) {
            if (isKey(base[i])) {
                val start = i
                while (i < base.size && isKey(base[i])) i++
                result.add(start until i)
            } else i++
        }
        return result
    }

    /**
     * The parts of [name] that [digits] matched, for highlighting; null when nothing matches.
     * Tried in order: the start of a word, the first characters of consecutive words, then the
     * name's characters run together from the start.
     */
    fun matchRanges(name: String, digits: String): List<IntRange>? {
        if (digits.isEmpty() || digits.any { it !in '0'..'9' }) return null
        val base = baseChars(name)
        val words = words(base)

        // 1. Start of a word.
        for (w in words) {
            if (w.last - w.first + 1 >= digits.length &&
                digits.indices.all { encodeChar(base[w.first + it]) == digits[it] }
            ) return listOf(w.first until w.first + digits.length)
        }
        // 2. First characters of consecutive words.
        for (s in words.indices) {
            if (s + digits.length > words.size) break
            if (digits.indices.all { encodeChar(base[words[s + it].first]) == digits[it] }) {
                return digits.indices.map { words[s + it].first..words[s + it].first }
            }
        }
        // 3. All characters from the start, ignoring spaces and punctuation.
        val picked = ArrayList<Int>()
        for (w in words) for (i in w) {
            if (picked.size < digits.length) picked.add(i)
        }
        if (picked.size == digits.length && digits.indices.all { encodeChar(base[picked[it]]) == digits[it] }) {
            return picked.map { it..it }
        }
        return null
    }

    fun matchesName(name: String, digits: String): Boolean = matchRanges(name, digits) != null
}
