package io.github.randomtools3377.checklist.core

/**
 * The search at the top of Settings. Every word typed has to start a word of what it looks in,
 * so "ai" finds "AI" but not "again". Plurals and simple word forms count as the same word:
 * "quest" finds "quests", "reminders" finds "reminder", "skipping" finds "skip".
 */
object SettingsSearch {
    /** Whether every word of [query] is found in [text]; a blank query finds everything. */
    fun matches(query: String, text: String): Boolean {
        val wanted = words(query)
        if (wanted.isEmpty()) return true
        val have = words(text)
        return wanted.all { word -> have.any { found(word, it) } }
    }

    /**
     * Whether the typed [word] finds the word [there]: it starts it, or is it with one more letter,
     * for words like "add", whose "added" comes out as "ad".
     */
    private fun found(word: String, there: String): Boolean =
        there.startsWith(word) || (word.length >= 3 && word.length == there.length + 1 && word.startsWith(there))

    /** The words of [text] (runs of letters and digits), lowercased and each in its simplest form. */
    fun words(text: String): List<String> {
        val words = mutableListOf<String>()
        val word = StringBuilder()
        for (c in text.lowercase() + " ") {
            if (c.isLetterOrDigit()) {
                word.append(c)
            } else if (word.isNotEmpty()) {
                words += stem(word.toString())
                word.clear()
            }
        }
        return words
    }

    /**
     * A word's simplest form: "quests" → "quest", "categories" → "category", "skipped" → "skip".
     * The plural goes first, so "mornings" and "morning" both come out as "morn".
     */
    fun stem(word: String): String {
        var w = word.lowercase()
        w = when {
            w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
            w.length > 4 && (w.endsWith("sses") || w.endsWith("xes") || w.endsWith("ches") || w.endsWith("shes")) -> w.dropLast(2)
            w.length > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") -> w.dropLast(1)
            else -> w
        }
        // "-ing" and "-ed": reminding → remind, skipping → skip, locked → lock.
        for (ending in listOf("ing", "ed")) {
            if (w.length >= ending.length + 3 && w.endsWith(ending)) {
                w = w.dropLast(ending.length)
                if (w.length >= 3 && w.last() == w[w.length - 2] && w.last() !in "lsz") w = w.dropLast(1)
                return w
            }
        }
        return w
    }
}
