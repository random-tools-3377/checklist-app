package io.github.randomtools3377.checklist

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.RepeatKind
import io.github.randomtools3377.checklist.data.AppDatabase
import io.github.randomtools3377.checklist.data.MIGRATIONS
import io.github.randomtools3377.checklist.data.toCore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A database made by 0.1.0 moves to the current layout with nothing lost. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun aVersion1DatabaseMovesToTheLatestWithItsHabitsAsMatchingRepeats() {
        helper.createDatabase(name, 1).apply {
            // 20406 is 14 Nov 2025 as an epoch day; createdAt is that day in milliseconds.
            val created = 20406L * 86_400_000L
            execSQL(
                "INSERT INTO goals (id, kind, name, icon, points, period, target, bonus, deadline, archived, position, createdAt) VALUES " +
                    "(1, 'HABIT', 'Water', 'W', 1, 'DAILY', 8, 5, NULL, 0, 0, $created), " +
                    "(2, 'HABIT', 'Gym', 'G', 10, 'WEEKLY', 3, 0, NULL, 0, 1, $created), " +
                    "(3, 'HABIT', 'Call', 'C', 4, 'MONTHLY', 1, 0, NULL, 0, 2, $created), " +
                    "(4, 'PROJECT', 'Shelf', 'S', 0, NULL, 1, 20, 20500, 0, 3, $created)",
            )
            execSQL("INSERT INTO ticks (id, goalId, stepId, at, day, period, periodStart) VALUES (1, 1, NULL, $created, 20405, 'DAILY', 20405)")
            execSQL("INSERT INTO entries (id, at, day, kind, amount, goalId, tickId, rewardId, purchaseId, label) VALUES (1, $created, 20405, 'TICK', 1, 1, 1, NULL, NULL, 'Water')")
            execSQL("INSERT INTO rewards (id, name, icon, price, archived, position, createdAt) VALUES (1, 'Coffee', 'C', 4, 0, 0, $created)")
            execSQL("INSERT INTO purchases (id, rewardId, name, icon, price, at, usedAt) VALUES (1, 1, 'Coffee', 'C', 4, $created, NULL)")
            close()
        }

        helper.runMigrationsAndValidate(name, 12, true, *MIGRATIONS).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*MIGRATIONS).build()
        try {
            runBlocking {
                val dao = db.dao()
                val water = dao.goal(1)!!.toCore()
                assertEquals(RepeatKind.EVERY_N_DAYS, water.repeat!!.kind)
                assertEquals(8, water.target)
                // A day before the earliest tick, so the old tick still falls inside it.
                assertEquals(20404, water.repeat!!.start.toEpochDays())

                val gym = dao.goal(2)!!.toCore()
                assertEquals(RepeatKind.TIMES_PER, gym.repeat!!.kind)
                assertEquals(Period.WEEKLY, gym.repeat!!.per)
                assertEquals(Period.MONTHLY, dao.goal(3)!!.toCore().repeat!!.per)
                assertNull(dao.goal(4)!!.toCore().repeat)

                assertEquals(1, dao.balance()) // the ledger is untouched
                assertNull(dao.purchase(1)!!.returnedAt)
                assertNull(water.reminder) // version 3: default notifications
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
