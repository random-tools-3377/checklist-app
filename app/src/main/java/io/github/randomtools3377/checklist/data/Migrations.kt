package io.github.randomtools3377.checklist.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RepeatText

/**
 * Every change to the database layout, in order. Each one moves the owner's data forward; none
 * of them drops it. app/schemas holds the layout of every version, and the emulator tests check
 * each migration.
 */
val MIGRATIONS: Array<Migration> = arrayOf(Migration1To2)

/**
 * Version 2 (app 0.2.0): flexible repeats, lengths, clock times, finish-by times, and returning
 * bought rewards. Habits from 0.1.0 were daily, weekly or monthly; daily becomes "every day",
 * weekly and monthly become "times per week" and "times per month", with the same target.
 */
object Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE goals ADD COLUMN `repeat` TEXT")
        db.execSQL("ALTER TABLE goals ADD COLUMN `minutes` INTEGER")
        db.execSQL("ALTER TABLE goals ADD COLUMN `atMinute` INTEGER")
        db.execSQL("ALTER TABLE goals ADD COLUMN `finishByMinute` INTEGER")
        db.execSQL("ALTER TABLE goals ADD COLUMN `finishDay` INTEGER")
        db.execSQL("ALTER TABLE purchases ADD COLUMN `returnedAt` INTEGER")

        val habits = mutableListOf<Triple<Long, String?, Long>>()
        db.query(
            "SELECT g.id, g.period, MIN(g.createdAt / 86400000, COALESCE((SELECT MIN(t.day) FROM ticks t WHERE t.goalId = g.id), g.createdAt / 86400000)) " +
                "FROM goals g WHERE g.kind = 'HABIT'",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                habits += Triple(cursor.getLong(0), if (cursor.isNull(1)) null else cursor.getString(1), cursor.getLong(2))
            }
        }
        for ((id, period, startDay) in habits) {
            // A day before the earliest it was used, so nothing it was ticked on falls outside it.
            val start = (startDay - 1).toDay()
            val repeat = when (period) {
                "WEEKLY" -> Repeat.timesPer(Period.WEEKLY, start)
                "MONTHLY" -> Repeat.timesPer(Period.MONTHLY, start)
                else -> Repeat.everyDay(start)
            }
            db.execSQL("UPDATE goals SET `repeat` = ? WHERE id = ?", arrayOf<Any>(RepeatText.encode(repeat), id))
        }
    }
}
