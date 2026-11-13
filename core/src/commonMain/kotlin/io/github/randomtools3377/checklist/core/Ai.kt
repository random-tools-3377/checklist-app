package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/*
 * The optional AI. It runs in a separate app, Checklist AI, so this app stays the same size for
 * anyone who doesn't use it. Both apps are released together with the same version. Nothing
 * the AI says is saved until the user has seen it and chosen to keep it.
 */

/** The names both apps use to talk to each other. */
object AiContract {
    /** Checklist AI's package. */
    const val PACKAGE = "io.github.randomtools3377.checklist.ai"

    /** Where Checklist AI answers. Only apps signed with the same key may ask. */
    const val AUTHORITY = "io.github.randomtools3377.checklist.ai.engine"

    /** The permission both apps declare; only apps signed with the same key hold it. */
    const val PERMISSION = "io.github.randomtools3377.checklist.permission.AI"

    /** Asks for [KEY_VERSION] and [KEY_READY]. */
    const val METHOD_STATUS = "status"

    /** Sends [KEY_SYSTEM], [KEY_PROMPT], [KEY_SCHEMA] and [KEY_MAX_TOKENS]; answers [KEY_TEXT] or [KEY_ERROR]. */
    const val METHOD_GENERATE = "generate"

    const val KEY_VERSION = "version"
    const val KEY_READY = "ready"
    const val KEY_SYSTEM = "system"
    const val KEY_PROMPT = "prompt"
    const val KEY_SCHEMA = "schema"
    const val KEY_MAX_TOKENS = "maxTokens"
    const val KEY_TEXT = "text"
    const val KEY_ERROR = "error"

    /** In the status: whether the speech file is added, for voice. */
    const val KEY_SPEECH_READY = "speechReady"

    /** Opens Checklist AI's listening screen; it answers with what was said in [KEY_TEXT]. */
    const val ACTION_LISTEN = "io.github.randomtools3377.checklist.ai.LISTEN"
}

/** How a goal the AI read repeats. */
enum class IdeaRepeat { EVERY_DAY, DAYS_OF_WEEK, TIMES_PER_WEEK }

/**
 * A goal the AI read from a sentence, to fill in the goal editor for the user to check.
 * [days] are ISO day numbers (1 is Monday); [time] is when to do it.
 */
data class GoalIdea(
    val name: String,
    val icon: String?,
    val repeat: IdeaRepeat,
    val days: List<Int> = emptyList(),
    val timesPerWeek: Int? = null,
    val time: LocalTime? = null,
    val minutes: Int? = null,
    val amount: Double? = null,
    val unit: String? = null,
)

/** What the app would suggest for a goal read from a sentence: its points and skill area, with why. */
data class IdeaSuggestion(val points: Int, val effort: Effort, val area: SkillArea, val reason: String)

private val dayShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "9:00 pm", for text the shared code makes itself. */
fun clockText(time: LocalTime): String {
    val hour = if (time.hour % 12 == 0) 12 else time.hour % 12
    return "$hour:${time.minute.toString().padStart(2, '0')} ${if (time.hour < 12) "am" else "pm"}"
}

/** "Mon to Fri", "Mon, Wed, Fri" or "Sat and Sun", from ISO day numbers. */
fun daysText(days: List<Int>): String {
    val sorted = days.filter { it in 1..7 }.distinct().sorted()
    return when {
        sorted.size >= 3 && sorted.last() - sorted.first() == sorted.size - 1 -> "${dayShort[sorted.first() - 1]} to ${dayShort[sorted.last() - 1]}"
        sorted.size == 2 -> "${dayShort[sorted[0] - 1]} and ${dayShort[sorted[1] - 1]}"
        else -> sorted.joinToString(", ") { dayShort[it - 1] }
    }
}

/** What was understood, in plain words: "Read · Mon to Fri · 9:00 pm · 20 pages". */
fun GoalIdea.describe(): String = listOfNotNull(
    listOfNotNull(icon, name).joinToString(" "),
    when (repeat) {
        IdeaRepeat.EVERY_DAY -> "Every day"
        IdeaRepeat.DAYS_OF_WEEK -> daysText(days)
        IdeaRepeat.TIMES_PER_WEEK -> timesPerWeek?.let { if (it == 1) "Once a week" else "$it times a week" }
    },
    time?.let(::clockText),
    minutes?.let { "$it min" },
    amount?.let { Amounts.format(it) + (unit?.let { u -> " $u" } ?: "") },
).joinToString(" · ")

/** What the sentence didn't say, for the user to set by hand if they want: "No time given". */
fun GoalIdea.missing(): List<String> = listOfNotNull(
    "No time given".takeIf { time == null },
    "No length given".takeIf { minutes == null },
    "No amount given".takeIf { amount == null },
)

/**
 * The points and skill area the app suggests for this goal, by its own rules (never the AI's):
 * the difficulty from its length, the points from [Suggest.points], the area from its name.
 */
fun GoalIdea.suggestion(): IdeaSuggestion {
    val effort = when {
        minutes == null -> Effort.MEDIUM
        minutes <= 10 -> Effort.EASY
        minutes <= 30 -> Effort.MEDIUM
        minutes <= 60 -> Effort.HARD
        else -> Effort.VERY_HARD
    }
    val points = Suggest.points(effort, minutes)
    val area = SkillArea.guess(name, null)
    val why = if (minutes == null) "${effort.label}, as no length was given" else "${effort.label} for $minutes minutes"
    return IdeaSuggestion(points, effort, area, "${PointsName.current.amount(points)}: $why. Skill: ${area.label}, from its name.")
}

/** "Read 20 pages every weekday at 9pm" into a goal: what the AI is asked, and reading its answer. */
object GoalFromSentence {
    const val MAX_TOKENS = 200

    val SYSTEM = """
        You turn one sentence about a habit into JSON for a checklist app. Reply with JSON only.
        name: a short name for the habit, 1 to 4 words, capitalised, like "Read" or "Drink water".
        emoji: one emoji that fits.
        repeat: "every_day", "days_of_week" (only some days) or "times_per_week" (a number of times a week, any days).
        days: for days_of_week, the days from mon tue wed thu fri sat sun; "weekdays" is mon to fri; "weekends" is sat and sun. Otherwise [].
        times_per_week: for times_per_week, how many times. Otherwise null.
        time: the clock time in 24-hour HH:MM if one is said, like "21:00" for 9pm. Otherwise null.
        minutes: how long it takes in minutes if said, like 30 for "half an hour". Otherwise null.
        amount and unit: an amount to do each time if said, like 20 and "pages", or 2 and "km". Otherwise null.
        Examples:
        "read 20 pages every weekday at 9pm" -> {"name":"Read","emoji":"📖","repeat":"days_of_week","days":["mon","tue","wed","thu","fri"],"times_per_week":null,"time":"21:00","minutes":null,"amount":20,"unit":"pages"}
        "gym 3 times a week for an hour" -> {"name":"Gym","emoji":"🏋️","repeat":"times_per_week","days":[],"times_per_week":3,"time":null,"minutes":60,"amount":null,"unit":null}
        "drink water every day" -> {"name":"Drink water","emoji":"💧","repeat":"every_day","days":[],"times_per_week":null,"time":null,"minutes":null,"amount":null,"unit":null}
    """.trimIndent()

    /** The shape the answer must have; the engine is held to it while it writes. */
    val SCHEMA = """
        {"type":"object","properties":{
          "name":{"type":"string"},
          "emoji":{"type":"string"},
          "repeat":{"type":"string","enum":["every_day","days_of_week","times_per_week"]},
          "days":{"type":"array","items":{"type":"string","enum":["mon","tue","wed","thu","fri","sat","sun"]}},
          "times_per_week":{"type":["integer","null"]},
          "time":{"type":["string","null"]},
          "minutes":{"type":["integer","null"]},
          "amount":{"type":["number","null"]},
          "unit":{"type":["string","null"]}
        },"required":["name","emoji","repeat","days","times_per_week","time","minutes","amount","unit"]}
    """.trimIndent()

    fun prompt(sentence: String): String = "\"${sentence.trim().replace("\"", "'").take(MAX_SENTENCE)}\" ->"

    const val MAX_SENTENCE = 200

    private val days = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /**
     * The goal in the AI's [answer], checked field by field: anything missing, odd or out of
     * range is left out rather than guessed. Null when there isn't even a name.
     */
    fun parse(answer: String, sentence: String): GoalIdea? =
        parse(answer)?.let { SentenceReader.merge(it, SentenceReader.read(sentence)) }

    /** The goal in the AI's [answer] alone, before the app's own reading puts it right. */
    fun parse(answer: String): GoalIdea? {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching { json.parseToJsonElement(answer.substring(start, end + 1)) as? JsonObject }.getOrNull() ?: return null
        val name = obj.text("name")?.let(::cleanName) ?: return null
        val picked = obj["days"].let { it as? JsonArray }.orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.lowercase()?.take(3) }
            .mapNotNull { day -> days.indexOf(day).takeIf { it >= 0 }?.plus(1) }
            .distinct().sorted()
        val times = obj.int("times_per_week")?.takeIf { it in 1..7 }
        val repeat = when (obj.text("repeat")) {
            "days_of_week" -> if (picked.isNotEmpty() && picked.size < 7) IdeaRepeat.DAYS_OF_WEEK else IdeaRepeat.EVERY_DAY
            "times_per_week" -> if (times != null && times < 7) IdeaRepeat.TIMES_PER_WEEK else IdeaRepeat.EVERY_DAY
            else -> IdeaRepeat.EVERY_DAY
        }
        val amount = obj.number("amount")?.takeIf { it > 0 && it < 1_000_000 }
        return GoalIdea(
            name = name,
            icon = obj.text("emoji")?.takeIf(::looksLikeEmoji),
            repeat = repeat,
            days = if (repeat == IdeaRepeat.DAYS_OF_WEEK) picked else emptyList(),
            timesPerWeek = if (repeat == IdeaRepeat.TIMES_PER_WEEK) times else null,
            time = obj.text("time")?.let(::clockTime),
            minutes = obj.int("minutes")?.takeIf { it in 1..600 },
            amount = amount,
            unit = if (amount == null) null else obj.text("unit")?.trim()?.take(20)?.takeIf { it.isNotEmpty() },
        )
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

    /** A tidy name: no quotes or full stop, first letter capital, at most 40 letters. */
    private fun cleanName(raw: String): String? {
        val name = raw.trim().trim('"', '\'', '.').replace(Regex("\\s+"), " ").take(40).trim()
        if (name.isEmpty() || name.none { it.isLetterOrDigit() }) return null
        return name.replaceFirstChar { it.uppercaseChar() }
    }

    /** "21:00" or "9:05" into a time; anything else is left out. */
    private fun clockTime(raw: String): LocalTime? {
        val match = Regex("^(\\d{1,2}):(\\d{2})$").find(raw.trim()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        return if (hour in 0..23 && minute in 0..59) LocalTime(hour, minute) else null
    }

    /** Short, and with no letters or digits: an emoji, not a word. */
    private fun looksLikeEmoji(text: String): Boolean =
        text.length in 1..16 && text.none { it.isLetterOrDigit() || it.isWhitespace() }
}
