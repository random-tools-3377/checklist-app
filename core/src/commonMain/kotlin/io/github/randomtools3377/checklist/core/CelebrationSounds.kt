package io.github.randomtools3377.checklist.core

/**
 * The user's own sounds for celebrations: one for each kind of celebration, and one for a goal
 * of its own. A picked sound is copied into the app's own storage beside the picked GIFs, and
 * which file is used is kept in settings. With none picked, the app's own sound plays.
 */
object CelebrationSounds {
    /** The celebrations that can each have a sound, with the setting that holds its file name. */
    enum class Kind(val setting: String, val label: String) {
        TICK("sound_tick", "A tick"),
        PROJECT("sound_project", "Finishing a project"),
        REWARD("sound_reward", "Buying a reward"),
    }

    /** Each goal's own sound, as "goal id:file name" joined by commas. */
    const val GOALS_SETTING = "goal_sounds"

    const val MAX_BYTES = 5 * 1_048_576

    const val TOO_BIG = "That sound is too big. Pick one under 5 MB."
    const val CANT_PLAY = "That sound can't be played here. Pick another one."
    const val CANT_OPEN = "That file couldn't be opened."

    private val SAFE_NAME = Regex("[A-Za-z0-9_-]{1,64}\\.sound")

    /**
     * A plain file name and nothing else, so a backup file can't make the app write outside
     * its own folder.
     */
    fun isSafeName(name: String): Boolean = SAFE_NAME.matches(name)

    /** The name a newly picked sound is kept under, from 8 random bytes. */
    fun newName(random: ByteArray): String =
        "own-" + random.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') } + ".sound"

    /** The picked sound a setting names, or null for the app's own sound. */
    fun file(setting: String?): String? = setting?.takeIf { isSafeName(it) }

    fun goals(setting: String?): Map<Long, String> =
        setting.orEmpty().split(",").mapNotNull { part ->
            val id = part.substringBefore(':', "").toLongOrNull() ?: return@mapNotNull null
            val name = part.substringAfter(':', "").takeIf { isSafeName(it) } ?: return@mapNotNull null
            id to name
        }.toMap()

    fun encodeGoals(sounds: Map<Long, String>): String =
        sounds.filterValues { isSafeName(it) }.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

    /** [setting] with [goalId]'s own sound set to [file], or taken off with null. */
    fun withGoal(setting: String?, goalId: Long, file: String?): String {
        val now = goals(setting)
        return encodeGoals(if (file != null && isSafeName(file)) now + (goalId to file) else now - goalId)
    }

    /**
     * The picked sound to play: the goal's own first, then the one picked for this [kind] of
     * celebration; null plays the app's own sound.
     */
    fun choose(kind: Kind, goalSound: String?, picked: Map<Kind, String>): String? =
        file(goalSound) ?: file(picked[kind])

    /**
     * The sound files [settings] still use: one for each kind, and the own sounds of the goals
     * in [goalIds]. The rest can be deleted.
     */
    fun used(settings: Map<String, String>, goalIds: Set<Long>): Set<String> =
        Kind.entries.mapNotNull { file(settings[it.setting]) }.toSet() +
            goals(settings[GOALS_SETTING]).filterKeys { it in goalIds }.values
}

/** How big a win the glow around the screen's edges is for. */
enum class GlowSize { TICK, FINISH, BIG }

/**
 * The glow around the edges of the screen when a celebration happens, scaled with the win: one
 * quick pass for a tick, a little more for finishing a goal, and longer and brighter for
 * finishing a project or buying a reward.
 */
object EdgeGlow {
    /** How many times it runs round, for how long, how bright at its brightest (0 to 1) and how wide in dp. */
    data class Spec(val passes: Int, val millis: Int, val alpha: Float, val width: Float)

    fun spec(size: GlowSize): Spec = when (size) {
        GlowSize.TICK -> Spec(passes = 1, millis = 900, alpha = 0.55f, width = 5f)
        GlowSize.FINISH -> Spec(passes = 1, millis = 1300, alpha = 0.7f, width = 7f)
        GlowSize.BIG -> Spec(passes = 2, millis = 2800, alpha = 0.9f, width = 10f)
    }

    /** How strong the glow is at [t], from 0 at the start to 1 at the end: it fades in, holds, and fades out. */
    fun strength(t: Float): Float = when {
        t <= 0f || t >= 1f -> 0f
        t < 0.15f -> t / 0.15f
        t > 0.7f -> (1f - t) / 0.3f
        else -> 1f
    }

    /** Where the bright part is at [t], as a part of the way round the edge, 0 to 1. */
    fun head(spec: Spec, t: Float): Float = (spec.passes * t.coerceIn(0f, 1f)).let { it - it.toInt() }

    /**
     * How bright a point [at] (a part of the way round, 0 to 1) is while the bright part is at
     * [head]: brightest there, with a tail behind it, and a faint glow everywhere else.
     */
    fun brightness(at: Float, head: Float): Float {
        val behind = ((head - at) % 1f + 1f) % 1f
        val ahead = 1f - behind
        val tail = if (behind < TAIL) 1f - behind / TAIL else 0f
        val front = if (ahead < FRONT) 1f - ahead / FRONT else 0f
        return maxOf(BASE, tail, front)
    }

    private const val TAIL = 0.35f
    private const val FRONT = 0.05f
    private const val BASE = 0.25f
}
