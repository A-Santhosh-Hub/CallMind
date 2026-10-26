package com.example.callmind.core.blocking

import com.example.callmind.data.local.entities.BlockReason

enum class BlockNotifyMode { ALWAYS, EXCEPT_BLOCKLIST, NEVER }

data class BlockingSettings(
    val blockUnsaved: Boolean = false,
    val blockUnknown: Boolean = false,
    /** A number that was blocked and calls again within [BlockDecision.REPEAT_WINDOW_MS] is let through. */
    val allowRepeat: Boolean = true,
    val notifyMode: BlockNotifyMode = BlockNotifyMode.EXCEPT_BLOCKLIST
)

data class Decision(val block: Boolean, val reason: BlockReason? = null) {
    companion object {
        val Allow = Decision(false)
    }
}

object BlockDecision {
    const val REPEAT_WINDOW_MS = 3 * 60 * 1000L

    /** Digits only, so "+91 98765-43210" and "098765 43210" compare equal. */
    fun digits(number: String): String = number.filter { it.isDigit() }

    /**
     * Two numbers match when one is a suffix of the other (handles country-code / trunk-prefix
     * differences). Very short numbers must match exactly to avoid false positives.
     */
    fun numbersMatch(a: String, b: String): Boolean {
        val da = digits(a)
        val db = digits(b)
        if (da.isEmpty() || db.isEmpty()) return false
        if (da == db) return true
        if (minOf(da.length, db.length) < MIN_SUFFIX_DIGITS) return false
        // Long numbers: compare the subscriber part only, ignoring a country code (+91) or trunk "0".
        if (da.length >= SUBSCRIBER_DIGITS && db.length >= SUBSCRIBER_DIGITS) {
            return da.takeLast(SUBSCRIBER_DIGITS) == db.takeLast(SUBSCRIBER_DIGITS)
        }
        return da.endsWith(db) || db.endsWith(da)
    }

    /**
     * Order of rules: trustlist wins, then blocklist, then "unknown number", then "unsaved contact"
     * (with the repeat-caller exception applied only to the last two, never to the blocklist).
     *
     * @param isSavedContact null when it cannot be determined (no contacts permission); such a
     * call is never blocked as "unsaved".
     * @param recentlyBlocked numbers rejected inside the repeat window.
     */
    fun decide(
        number: String,
        settings: BlockingSettings,
        blockList: List<String>,
        trustList: List<String>,
        isSavedContact: Boolean?,
        recentlyBlocked: List<String>
    ): Decision {
        if (digits(number).isEmpty()) {
            return if (settings.blockUnknown) Decision(true, BlockReason.UNKNOWN_NUMBER) else Decision.Allow
        }
        if (trustList.any { numbersMatch(it, number) }) return Decision.Allow
        if (blockList.any { numbersMatch(it, number) }) return Decision(true, BlockReason.BLOCKLIST)

        if (settings.blockUnsaved && isSavedContact == false) {
            val isRepeat = settings.allowRepeat && recentlyBlocked.any { numbersMatch(it, number) }
            if (!isRepeat) return Decision(true, BlockReason.UNSAVED)
        }
        return Decision.Allow
    }

    private const val MIN_SUFFIX_DIGITS = 7
    private const val SUBSCRIBER_DIGITS = 10
}
