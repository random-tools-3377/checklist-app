package io.github.randomtools3377.checklist.core

/** What the user calls points, like coins 🪙 or XP (Settings → Appearance). */
data class PointsName(val one: String = "point", val many: String = "points", val emoji: String = "") {
    /** "coin" for one, "coins" for any other number. */
    fun word(n: Int): String = if (n == 1 || n == -1) one else many

    /** "126 coins 🪙". */
    fun amount(n: Int): String = "$n ${word(n)}" + if (emoji.isNotEmpty()) " $emoji" else ""

    /** "Coins", for headings. */
    val title: String get() = many.replaceFirstChar { it.uppercase() }

    companion object {
        val DEFAULT = PointsName()

        val PRESETS: List<PointsName> = listOf(
            DEFAULT,
            PointsName("coin", "coins", "🪙"),
            PointsName("star", "stars", "⭐"),
            PointsName("XP", "XP"),
            PointsName("gem", "gems", "💎"),
        )

        /** The longest a name can be. */
        const val LENGTH = 16

        /**
         * What the app calls points right now, for text made in the shared code, like the weekly
         * summary. The app sets it from Settings.
         */
        var current: PointsName = DEFAULT

        /** The user's own words, tidied; blank words fall back to the usual ones. */
        fun of(one: String, many: String, emoji: String): PointsName {
            fun tidy(text: String) = text.trim().replace(Regex("\\s+"), " ").take(LENGTH).trim()
            val single = tidy(one).ifEmpty { tidy(many) }.ifEmpty { DEFAULT.one }
            val plural = tidy(many).ifEmpty { single }
            return PointsName(single, plural, emoji.trim().take(8))
        }

        /** Stored as "coin|coins|🪙". */
        fun encode(name: PointsName): String = listOf(name.one, name.many, name.emoji).joinToString("|")

        fun decode(text: String?): PointsName {
            val parts = text?.split("|") ?: return DEFAULT
            if (parts.size != 3) return DEFAULT
            return of(parts[0], parts[1], parts[2])
        }
    }
}
