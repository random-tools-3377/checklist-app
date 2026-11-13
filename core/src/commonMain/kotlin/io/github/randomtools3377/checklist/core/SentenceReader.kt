package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalTime

/**
 * What the app itself reads in a sentence about a habit, with plain pattern matching: the days,
 * "3 times a week", a clock time, "for 30 minutes" and "20 pages". The small AI slips with
 * numbers, so where the app read one, the app's reading wins (see [GoalFromSentence.parse]).
 * It also makes "Describe it" work with no AI at all: everything but a tidy name and an icon.
 */
data class SentenceReading(
    /** How it repeats, when the sentence says. */
    val repeat: IdeaRepeat? = null,
    val days: List<Int> = emptyList(),
    val timesPerWeek: Int? = null,
    val time: LocalTime? = null,
    val minutes: Int? = null,
    val amount: Double? = null,
    val unit: String? = null,
    /** What is left of the sentence once the facts are taken out: the name, as typed. */
    val name: String = "",
    /** Whether the sentence holds any number at all, as digits or as a word. */
    val hasNumber: Boolean = false,
)

object SentenceReader {
    private val numberWords = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "forty five" to 45, "fifty" to 50, "sixty" to 60, "ninety" to 90,
    )
    private const val NUM = "(\\d+(?:[.,]\\d+)?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen|twenty|thirty|forty five|forty|fifty|sixty|ninety)"

    private val dayNames = listOf(
        "mon(?:days?)?" to 1, "tue(?:s|sdays?)?" to 2, "wed(?:s|nesdays?)?" to 3, "thu(?:r|rs|rsdays?)?" to 4,
        "fri(?:days?)?" to 5, "sat(?:urdays?)?" to 6, "sun(?:days?)?" to 7,
    )

    /** Units that are a length of time or a count of times, never an amount of the user's own. */
    private val notUnits = setOf(
        "am", "pm", "a", "an", "x", "times", "time", "min", "mins", "minute", "minutes", "hour", "hours", "hr", "hrs", "h",
        "day", "days", "week", "weeks", "month", "months", "year", "years", "every", "each", "per", "at", "on", "for",
        "in", "the", "and", "or", "to", "of", "oclock", "o'clock",
    )

    private fun number(text: String): Double? =
        text.replace(',', '.').toDoubleOrNull() ?: numberWords[text.replace(Regex("[\\s-]+"), " ")]?.toDouble()

    /** Reads [sentence]. Anything it can't be sure of is left out. */
    fun read(sentence: String): SentenceReading {
        var rest = " " + sentence.lowercase().replace(Regex("[\"“”]"), " ").replace(Regex("\\s+"), " ").trim() + " "
        val hasNumber = Regex("\\d").containsMatchIn(rest) ||
            Regex("\\b(once|twice|half|an hour|${numberWords.keys.joinToString("|")})\\b").containsMatchIn(rest)

        fun take(regex: Regex): MatchResult? = regex.find(rest)?.also { rest = rest.replaceRange(it.range, " ") }

        // A clock time: "at 9pm", "at 9:30 pm", "at 21:00", "7am", "at noon".
        var time: LocalTime? = null
        take(Regex("\\b(?:at |by |around )?(noon|midday|midnight)\\b"))?.let { time = if (it.groupValues[1] == "midnight") LocalTime(0, 0) else LocalTime(12, 0) }
        if (time == null) {
            take(Regex("\\b(?:at |by |around )?(\\d{1,2})(?:[:.](\\d{2}))? ?(a\\.?m\\.?|p\\.?m\\.?)(?= |[.,!?])"))?.let { m ->
                val hour = m.groupValues[1].toInt()
                val minute = m.groupValues[2].toIntOrNull() ?: 0
                val pm = m.groupValues[3].startsWith("p")
                if (hour in 1..12 && minute in 0..59) time = LocalTime(if (pm) hour % 12 + 12 else hour % 12, minute)
            }
        }
        if (time == null) {
            take(Regex("\\b(?:at |by |around )(\\d{1,2})[:.](\\d{2})(?= |[.,!?])"))?.let { m ->
                val hour = m.groupValues[1].toInt()
                val minute = m.groupValues[2].toInt()
                if (hour in 0..23 && minute in 0..59) time = LocalTime(hour, minute)
            }
        }
        if (time == null) {
            // "at 7 in the evening", "at 7": a bare hour from 1 to 6 is taken as the afternoon.
            take(Regex("\\bat (\\d{1,2})(?: o'?clock)?( in the (?:morning|afternoon|evening)| at night)?(?= |[.,!?])"))?.let { m ->
                val hour = m.groupValues[1].toInt()
                val part = m.groupValues[2]
                if (hour in 0..23) {
                    time = when {
                        "morning" in part -> LocalTime(hour % 12, 0)
                        part.isNotEmpty() && hour in 1..11 -> LocalTime(hour + 12, 0)
                        part.isEmpty() && hour in 1..6 -> LocalTime(hour + 12, 0)
                        else -> LocalTime(hour, 0)
                    }
                }
            }
        }

        // How long: "for 30 minutes", "45 min", "for an hour", "half an hour", "1.5 hours".
        var minutes: Int? = null
        take(Regex("\\b(?:for )?(?:an hour and a half|one and a half hours)\\b"))?.let { minutes = 90 }
        if (minutes == null) take(Regex("\\b(?:for )?half an hour\\b"))?.let { minutes = 30 }
        if (minutes == null) take(Regex("\\b(?:for )?(?:an|one|1) hour\\b"))?.let { minutes = 60 }
        if (minutes == null) {
            take(Regex("\\b(?:for )?$NUM ?(?:hours|hrs|hr|h)\\b"))?.let { m -> minutes = number(m.groupValues[1])?.let { (it * 60).toInt() } }
        }
        if (minutes == null) {
            take(Regex("\\b(?:for )?$NUM ?(?:minutes|minute|mins|min)\\b"))?.let { m -> minutes = number(m.groupValues[1])?.toInt() }
        }
        minutes = minutes?.takeIf { it in 1..600 }

        // How often: "3 times a week", "twice a week", "3x a week", "3 days a week".
        var times: Int? = null
        take(Regex("\\b(once|twice) (?:a|per|each|every) week\\b"))?.let { times = if (it.groupValues[1] == "once") 1 else 2 }
        if (times == null) {
            take(Regex("\\b$NUM ?(?:times|x|days) (?:a|per|each|every) week\\b"))?.let { m -> times = number(m.groupValues[1])?.toInt() }
        }
        times = times?.takeIf { it in 1..7 }

        // Which days: "every weekday", "weekends", "on mondays and thursdays", "mon wed fri".
        val days = mutableSetOf<Int>()
        take(Regex("\\b(?:every |each |on )?week ?days?\\b"))?.let { days += 1..5 }
        take(Regex("\\b(?:every |each |on |at )?(?:the )?weekends?\\b"))?.let { days += 6..7 }
        for ((pattern, day) in dayNames) {
            take(Regex("\\b(?:every |each |on )?$pattern\\b,?"))?.let { days += day }
        }
        val everyDay = take(Regex("\\b(?:every ?day|each day|daily|every single day|every morning|every evening|every night)\\b")) != null

        // An amount of the user's own: "20 pages", "2 km", "8 glasses".
        var amount: Double? = null
        var unit: String? = null
        for (m in Regex("\\b(\\d+(?:[.,]\\d+)?) ?([a-z][a-z'-]{0,19})\\b( of\\b)?").findAll(rest)) {
            val word = m.groupValues[2]
            val value = number(m.groupValues[1])
            if (word !in notUnits && value != null && value > 0 && value < 1_000_000) {
                amount = value
                unit = word
                rest = rest.replaceRange(m.range, " ")
                break
            }
        }

        val repeat = when {
            times != null && times < 7 -> IdeaRepeat.TIMES_PER_WEEK
            times == 7 -> IdeaRepeat.EVERY_DAY
            days.size in 1..6 -> IdeaRepeat.DAYS_OF_WEEK
            days.size == 7 || everyDay -> IdeaRepeat.EVERY_DAY
            else -> null
        }
        return SentenceReading(
            repeat = repeat,
            days = if (repeat == IdeaRepeat.DAYS_OF_WEEK) days.sorted() else emptyList(),
            timesPerWeek = if (repeat == IdeaRepeat.TIMES_PER_WEEK) times else null,
            time = time,
            minutes = minutes,
            amount = amount,
            unit = unit,
            name = nameFrom(rest),
            hasNumber = hasNumber,
        )
    }

    private val leftovers = setOf("every", "each", "at", "on", "for", "and", "in", "the", "a", "an", "i", "want", "to", "will", "should", "need", "my", "please", "then", "by", "around")

    /** The words left over, without the joining words at either end, capitalised, at most 40 letters. */
    private fun nameFrom(rest: String): String {
        val words = rest.replace(Regex("[.,!?;:]"), " ").split(" ").filter { it.isNotBlank() }.toMutableList()
        while (words.isNotEmpty() && words.first() in leftovers) words.removeAt(0)
        while (words.isNotEmpty() && words.last() in leftovers) words.removeAt(words.lastIndex)
        return words.joinToString(" ").take(40).trim().replaceFirstChar { it.uppercaseChar() }
    }

    /**
     * A goal from [sentence] with no AI at all: the app's own reading, with the name left as
     * typed. Null when nothing is left to call it.
     */
    fun idea(sentence: String): GoalIdea? {
        val read = read(sentence)
        if (read.name.none { it.isLetterOrDigit() }) return null
        return GoalIdea(
            name = read.name,
            icon = null,
            repeat = read.repeat ?: IdeaRepeat.EVERY_DAY,
            days = read.days,
            timesPerWeek = read.timesPerWeek,
            time = read.time,
            minutes = read.minutes,
            amount = read.amount,
            unit = read.unit,
        )
    }

    /**
     * [idea], from the AI, put right by the app's own [read]ing of the same sentence: where the
     * app read a fact, that wins; and a number the AI gives that the sentence never held is
     * dropped.
     */
    fun merge(idea: GoalIdea, read: SentenceReading): GoalIdea {
        val repeat = read.repeat ?: idea.repeat.takeIf { it != IdeaRepeat.TIMES_PER_WEEK || read.hasNumber } ?: IdeaRepeat.EVERY_DAY
        val fromApp = read.repeat != null
        val amount = read.amount ?: idea.amount.takeIf { read.hasNumber && read.minutes == null && read.time == null && read.timesPerWeek == null }
        return idea.copy(
            repeat = repeat,
            days = if (repeat != IdeaRepeat.DAYS_OF_WEEK) emptyList() else if (fromApp) read.days else idea.days,
            timesPerWeek = if (repeat != IdeaRepeat.TIMES_PER_WEEK) null else if (fromApp) read.timesPerWeek else idea.timesPerWeek,
            time = read.time ?: idea.time.takeIf { read.hasNumber && read.minutes == null && read.amount == null && read.timesPerWeek == null },
            minutes = read.minutes ?: idea.minutes.takeIf { read.hasNumber && read.time == null && read.amount == null && read.timesPerWeek == null },
            amount = amount,
            unit = if (amount == null) null else read.unit ?: idea.unit,
        )
    }
}
