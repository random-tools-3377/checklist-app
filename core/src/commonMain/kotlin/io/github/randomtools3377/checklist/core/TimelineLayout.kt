package io.github.randomtools3377.checklist.core

/** Something on the day timeline, from [start] to [end], in minutes (see [DayHours.minuteOf]). */
data class TimelineSpan(val start: Int, val end: Int)

/** Where a box sits across the timeline: in [column] of [columns] equal columns, counted from 0. */
data class TimelineSlot(val column: Int, val columns: Int)

/**
 * The day timeline's layout: how long each box is, and which boxes sit side by side. Boxes that
 * overlap in time (goals and calendar events alike) share the width in columns, so none sits on
 * top of another and all stay within the screen.
 */
object TimelineLayout {
    /** How long a goal's box is when it has no length, and the least any box is, so its name and button fit. */
    const val MIN_MINUTES = 30

    /** How long the box of something taking [minutes] is. */
    fun length(minutes: Int?): Int = maxOf(minutes ?: MIN_MINUTES, MIN_MINUTES)

    /**
     * A goal's box from [start], for [minutes], kept within the hours shown ([from] to [to]): one
     * that would run past the end is cut short there, but never below the least length.
     */
    fun span(start: Int, minutes: Int?, from: Int, to: Int): TimelineSpan {
        val begin = start.coerceIn(from, maxOf(from, to - MIN_MINUTES))
        return TimelineSpan(begin, minOf(begin + length(minutes), maxOf(to, begin + MIN_MINUTES)))
    }

    /**
     * A column for each of [spans], in the same order. Boxes that overlap, directly or through
     * others, form a group that shares the width: each goes in the first column free at its
     * start, earliest first (longer first at the same start, then as listed), and the group has
     * as many columns as it needed. A box that ends when another starts doesn't overlap it.
     */
    fun place(spans: List<TimelineSpan>): List<TimelineSlot> {
        val order = spans.indices.sortedWith(compareBy({ spans[it].start }, { -spans[it].end }, { it }))
        val columnOf = IntArray(spans.size)
        val columnsOf = IntArray(spans.size)
        var group = mutableListOf<Int>()
        var groupEnd = Int.MIN_VALUE
        // When each column of the group is free again.
        var freeAt = mutableListOf<Int>()
        fun close() {
            group.forEach { columnsOf[it] = freeAt.size }
            group = mutableListOf()
            freeAt = mutableListOf()
        }
        for (i in order) {
            val span = spans[i]
            if (group.isNotEmpty() && span.start >= groupEnd) close()
            var column = freeAt.indexOfFirst { it <= span.start }
            if (column < 0) {
                column = freeAt.size
                freeAt.add(span.end)
            } else {
                freeAt[column] = span.end
            }
            columnOf[i] = column
            group.add(i)
            groupEnd = if (group.size == 1) span.end else maxOf(groupEnd, span.end)
        }
        close()
        return spans.indices.map { TimelineSlot(columnOf[it], columnsOf[it]) }
    }
}
