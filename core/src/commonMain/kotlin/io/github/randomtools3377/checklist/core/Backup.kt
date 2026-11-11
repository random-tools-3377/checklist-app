package io.github.randomtools3377.checklist.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the app holds, as one file. The same format on Android and iPhone, so data moves
 * between phones by exporting on one and importing on the other.
 */
@Serializable
data class Backup(
    val app: String = APP,
    val format: Int = FORMAT,
    /** When it was exported, in epoch milliseconds. */
    val exportedAt: Long,
    val goals: List<Goal>,
    val steps: List<Step>,
    val ticks: List<Tick>,
    val entries: List<Entry>,
    val rewards: List<Reward>,
    val purchases: List<Purchase>,
    val settings: Map<String, String>,
    /** Skips and misses (format 2). */
    val lapses: List<Lapse> = emptyList(),
    val penalties: List<Penalty> = emptyList(),
    val penaltyTasks: List<PenaltyTask> = emptyList(),
    /** Gaps (format 3). */
    val gaps: List<Gap> = emptyList(),
    /** The celebration GIFs the user picked, by file name, in base64 (format 4). */
    val files: Map<String, String> = emptyMap(),
    /**
     * Routine items ticked (format 5). A routine's items are its steps. Format 6 adds the user's
     * own reasons and notes on ticks; format 7 adds goals' categories; format 8 adds mystery
     * rewards' prizes; format 9 adds goals' colours; format 10 the photo behind Today; format 11
     * goals' amounts and units, and how much each tick was and how long it took; format 12 goals
     * moved to another day; format 13 quick to-dos; format 14 goals' difficulty and rewards'
     * sizes and values; format 15 savings interest in the ledger;
     * format 16 perfect-day bonuses;
     * format 17 goals repeating some days after they were last done;
     * format 18 goals to quit and challenges;
     * format 19 the daily mood check-in;
     * format 20 habit chains;
     * format 21 each goal's skill area, for the character;
     * format 22 bets on yourself in the points history;
     * format 23 rewards whose price follows what they cost in money;
     * format 24 the budget;
     * format 25 bills and saving goals;
     * format 26 a second mood each day, once everything due is done.
     */
    val checks: List<Check> = emptyList(),
    /** The user's own photo behind Today, in base64 (format 10). */
    val background: String? = null,
    /** How the user felt each day (format 19), and again once everything was done (format 26). */
    val moods: List<Mood> = emptyList(),
    /**
     * The budget (format 24): money spent and received, its categories, and work days that
     * weren't the usual ones. The pay and usual week are in [settings].
     */
    val money: List<MoneyEntry> = emptyList(),
    val moneyCategories: List<MoneyCategory> = emptyList(),
    val workDays: List<WorkDay> = emptyList(),
    /** Bills and saving goals (format 25). */
    val bills: List<Bill> = emptyList(),
    val savingGoals: List<SavingGoal> = emptyList(),
) {
    companion object {
        const val APP = "checklist"

        /** Raised whenever the file's layout changes in a way older versions can't read. */
        const val FORMAT = 26

        private val json = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun encode(backup: Backup): String = json.encodeToString(serializer(), backup)

        /** Reads a backup file, or explains in plain words why it can't be used. */
        fun decode(text: String): Backup {
            val backup = try {
                json.decodeFromString(serializer(), text)
            } catch (e: SerializationException) {
                throw BackupError("This file isn't a Checklist backup, or it is damaged.")
            } catch (e: IllegalArgumentException) {
                throw BackupError("This file isn't a Checklist backup, or it is damaged.")
            }
            if (backup.app != APP) throw BackupError("This file isn't a Checklist backup.")
            if (backup.format > FORMAT) {
                throw BackupError("This backup was made by a newer version of the app. Update the app first.")
            }
            return backup
        }
    }
}

class BackupError(message: String) : Exception(message)

/** Stores a [Repeat] or a [Reminder] as text in the database, in the same form as the backup file. */
object RepeatText {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(repeat: Repeat): String = json.encodeToString(Repeat.serializer(), repeat)
    fun decode(text: String): Repeat = json.decodeFromString(Repeat.serializer(), text)
    fun encodeReminder(reminder: Reminder): String = json.encodeToString(Reminder.serializer(), reminder)
    fun decodeReminder(text: String): Reminder = json.decodeFromString(Reminder.serializer(), text)
}
