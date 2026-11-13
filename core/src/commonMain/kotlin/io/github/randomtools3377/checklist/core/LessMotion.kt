package io.github.randomtools3377.checklist.core

/**
 * "Less motion" (Settings → Appearance): the one rule every animation in the app asks. It starts
 * on the device's own setting and stays on it until the user moves the switch; from then on the
 * switch decides. With less motion things simply change: nothing slides, pops, rolls or glows,
 * and confetti is a still "+10".
 */
object LessMotion {
    /** The setting: "1" for less motion, "0" for motion, and missing while the device decides. Kept on this device only. */
    const val SETTING = "less_motion"

    /** Whether to hold still: the user's choice once made, else what the [device] asks for. */
    fun calm(setting: String?, device: Boolean): Boolean = choice(setting) ?: device

    /** The user's own choice, or null while the switch has never been moved. */
    fun choice(setting: String?): Boolean? = when (setting) {
        "1" -> true
        "0" -> false
        else -> null
    }

    fun encode(lessMotion: Boolean): String = if (lessMotion) "1" else "0"
}

/** What a buzz is for; each feels different (see Celebrations.buzz), and all follow the Buzz switch. */
enum class BuzzKind {
    /** One of several: "+1", a routine's item, a project's step among more to come. */
    LIGHT,

    /** A goal finished, or a reward bought: the fuller buzz. */
    DONE,

    /** Something refused, like not enough points: a soft double tap. */
    REFUSED,

    /** A tick taken back: a small tick. */
    BACK,
}

/** Dragging a row to another place in its list (Reorder, steps, checklist items, Today's sections). */
object DragOrder {
    /**
     * Where the row at [from] lands after being dragged [dy] pixels down (up when negative), in
     * a list whose rows are [heights] pixels tall, top to bottom: it takes the place of the row
     * its middle is over, and stays put until its middle passes the middle of a neighbour.
     */
    fun target(from: Int, dy: Float, heights: List<Int>): Int {
        if (from !in heights.indices) return from
        var top = 0f
        for (i in 0 until from) top += heights[i]
        val middle = top + heights[from] / 2f + dy
        var index = from
        if (dy > 0) {
            // Going down: past each next row once the dragged middle is beyond that row's middle.
            var edge = top + heights[from]
            for (i in from + 1 until heights.size) {
                if (middle > edge + heights[i] / 2f - heights[from] / 2f) index = i else break
                edge += heights[i]
            }
        } else if (dy < 0) {
            var edge = top
            for (i in from - 1 downTo 0) {
                if (middle < edge - heights[i] / 2f + heights[from] / 2f) index = i else break
                edge -= heights[i]
            }
        }
        return index
    }

    /**
     * How far, in pixels, the row at [index] steps aside while the row at [from] hovers over
     * [target]: rows between the two move one place toward where the dragged row came from.
     */
    fun shift(index: Int, from: Int, target: Int, draggedHeight: Int): Int = when {
        index == from -> 0
        from < target && index in (from + 1)..target -> -draggedHeight
        target < from && index in target until from -> draggedHeight
        else -> 0
    }

    /** [list] with the row at [from] moved to [to]; unchanged when either is outside it. */
    fun <T> moved(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }
}

/**
 * Swiping a goal's card to the right on Today (Settings → Appearance, "Swipe a card to tick it").
 * A swipe only ever does what the card's own button does, and never more: it ticks, adds one,
 * or opens the same box to type an amount. Nothing is ever skipped or taken back by a swipe.
 */
object SwipeTick {
    /** The setting: "0" turns swiping off. On unless turned off. */
    const val SETTING = "swipe_tick"

    fun on(setting: String?): Boolean = setting != "0"

    enum class Does {
        /** Nothing to do: the goal is done, so its button would only take the tick back. */
        NOTHING,

        /** The card's "Done", "+1" or "Avoided" button. */
        TICK,

        /** The box to type how much was done, as "Add" opens it. */
        AMOUNT,
    }

    /** What a swipe on [row]'s card does. */
    fun does(row: HabitRow): Does = when {
        row.goal.atMost -> Does.NOTHING
        row.done -> Does.NOTHING
        row.goal.hasAmount -> Does.AMOUNT
        else -> Does.TICK
    }

    /** What a swipe on a to-do's card does: Done, unless it is done already. */
    fun does(row: TodoRow): Does = if (row.done) Does.NOTHING else Does.TICK

    /** The share of the card's width a swipe must cross to count. */
    const val THRESHOLD = 0.35f

    /**
     * Whether a card [width] pixels wide, let go [offset] pixels to the right, was swiped far
     * enough. A short drag, or one that came back, does nothing.
     */
    fun crossed(offset: Float, width: Int): Boolean = width > 0 && offset >= width * THRESHOLD

    /** What shows behind the card while it is dragged. */
    fun label(does: Does, counted: Boolean, quit: Boolean): String = when (does) {
        Does.NOTHING -> ""
        Does.AMOUNT -> "Add an amount"
        Does.TICK -> if (counted) "+1" else if (quit) "✓ Avoided" else "✓ Done"
    }
}
