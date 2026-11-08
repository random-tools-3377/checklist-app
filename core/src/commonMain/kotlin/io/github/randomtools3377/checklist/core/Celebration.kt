package io.github.randomtools3377.checklist.core

/**
 * A goal's own celebration GIF, shown when it is finished. Either one of the GIFs that come with
 * the app, by name, or one the user picked, kept as a file in the app's own storage and written
 * as "own:" and its file name.
 */
object Celebration {
    /** The GIFs that come with the app. */
    val BUILT_IN = listOf("confetti", "star", "fireworks", "check")

    private const val OWN = "own:"
    private val SAFE_NAME = Regex("[A-Za-z0-9_-]{1,64}\\.gif")

    fun own(fileName: String): String {
        require(isSafeName(fileName)) { "Not a celebration file name: $fileName" }
        return OWN + fileName
    }

    /** The file name of a GIF the user picked, or null for none or a built-in one. */
    fun ownFile(celebration: String?): String? =
        celebration?.takeIf { it.startsWith(OWN) }?.removePrefix(OWN)?.takeIf { isSafeName(it) }

    /** The name of a built-in GIF, or null. */
    fun builtIn(celebration: String?): String? = celebration?.takeIf { it in BUILT_IN }

    /** Whether [celebration] is something the app can show. Anything else is treated as none. */
    fun isValid(celebration: String?): Boolean = builtIn(celebration) != null || ownFile(celebration) != null

    /**
     * A plain file name and nothing else, so a backup file can't make the app write outside
     * its own folder.
     */
    fun isSafeName(name: String): Boolean = SAFE_NAME.matches(name)
}
