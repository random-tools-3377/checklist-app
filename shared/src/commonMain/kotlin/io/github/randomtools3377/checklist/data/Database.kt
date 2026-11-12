package io.github.randomtools3377.checklist.data

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Dates are stored as epoch days, moments as epoch milliseconds, and kinds and periods by name.
//
// Every version's layout is exported to shared/schemas. A later version that changes a table must
// add a migration; the owner's data is never dropped.

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val name: String,
    val icon: String,
    val points: Int,
    val period: String?,
    val target: Int,
    val bonus: Int,
    val deadline: Long?,
    val archived: Boolean,
    val position: Int,
    val createdAt: Long,
    /** When it is due, as JSON (see RepeatText). Null for a project. Added in version 2. */
    val repeat: String? = null,
    /** How long one tick takes, in minutes. Added in version 2. */
    val minutes: Int? = null,
    /** Clock time to do it at, in minutes after midnight. Added in version 2. */
    val atMinute: Int? = null,
    /** Time to finish it by, in minutes after midnight. Added in version 2. */
    val finishByMinute: Int? = null,
    /** For "times per": the day of its week, month or year to finish by. Added in version 2. */
    val finishDay: Int? = null,
    /** Notification settings, as JSON (see RepeatText). Null means the defaults. Added in version 3. */
    val reminder: String? = null,
    /** Skips and misses allowed per [allowancePer]. Null means the default. Added in version 4. */
    val allowance: Int? = null,
    val allowancePer: String? = null,
    /** Kept behind the privacy lock. Added in version 4. */
    @ColumnInfo(defaultValue = "0") val isPrivate: Boolean = false,
    /** Its own celebration GIF (see Celebration in the shared code). Added in version 6. */
    val celebration: String? = null,
    /** For a habit done several times a day, a time for each one, as minutes after midnight separated by commas. Added in version 7. */
    val times: String? = null,
    /** The user's own category for the goal (see Categories in the shared code). Added in version 10. */
    val category: String? = null,
    /** The goal's own colour, as 0xFFRRGGBB. Added in version 12. */
    val color: Int? = null,
    /** How much of [unit] makes it done each time, like 20 pages (see Amounts in the shared code). Added in version 13. */
    val amount: Double? = null,
    val unit: String? = null,
    /** How hard it is (Effort in the shared code), which suggests its points. Added in version 14. */
    val effort: String? = null,
    /** A goal to quit, ticked each day it was avoided. Added in version 15. */
    @androidx.room.ColumnInfo(defaultValue = "0") val quit: Boolean = false,
    /** A challenge on it: so many days in a row, its bonus, and the day it started (see Challenges). Added in version 15. */
    val challengeDays: Int? = null,
    val challengeBonus: Int? = null,
    val challengeFrom: Long? = null,
    /** The goal this one comes after, in a habit chain. Added in version 17. */
    val afterGoal: Long? = null,
    /** Its skill area for the character, by name; null to guess it. Added in version 18. */
    val skill: String? = null,
)

@Entity(tableName = "steps", indices = [Index("goalId")])
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val name: String,
    val points: Int,
    val position: Int,
    val archived: Boolean,
)

@Entity(tableName = "ticks", indices = [Index("goalId", "periodStart"), Index("stepId")])
data class TickEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val stepId: Long?,
    val at: Long,
    val day: Long,
    val period: String?,
    val periodStart: Long,
    /** A note about it, like "ran 5 km". Added in version 9. */
    val note: String? = null,
    /** For a goal with an amount: how much this entry was, and how long it took if timed. Added in version 13. */
    val amount: Double? = null,
    val seconds: Int? = null,
)

/** The points ledger. The balance is the sum of [amount]. */
@Entity(tableName = "entries", indices = [Index("tickId"), Index("day")])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val day: Long,
    val kind: String,
    val amount: Int,
    val goalId: Long?,
    val tickId: Long?,
    val rewardId: Long?,
    val purchaseId: Long?,
    val label: String,
    /** The skip or miss a penalty or refund belongs to. Added in version 4. */
    val lapseId: Long? = null,
)

@Entity(tableName = "rewards")
data class RewardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val price: Int,
    val archived: Boolean,
    val position: Int,
    val createdAt: Long,
    /** A mystery reward's prizes, one per line (see Mystery in the shared code). Added in version 11. */
    val prizes: String? = null,
    /** How big a treat it is (RewardSize in the shared code), and what it costs in money. Added in version 14. */
    val size: String? = null,
    val value: Double? = null,
    /** Whether its price follows what it costs in money (MoneyPrices in the shared code). Added in version 19. */
    @ColumnInfo(defaultValue = "0") val followsMoney: Boolean = false,
)

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rewardId: Long,
    val name: String,
    val icon: String,
    val price: Int,
    val at: Long,
    val usedAt: Long?,
    /** When it was returned for a refund. Added in version 2. */
    val returnedAt: Long? = null,
)

/** Skips and misses. Added in version 4. */
@Entity(tableName = "lapses", indices = [Index("goalId", "day")])
data class LapseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val kind: String,
    val day: Long,
    val period: String?,
    val periodStart: Long,
    val at: Long,
    val overLimit: Boolean,
    val cost: Int,
    val reason: String?,
    val note: String?,
    val forgotten: Boolean,
    val label: String,
    /** The gap it falls in. Added in version 5. */
    val gapId: Long? = null,
    /** The words of the user's own reason, when reason is OTHER. Added in version 9. */
    val reasonText: String? = null,
)

/** A routine's items ticked in a window. A routine is a habit whose steps are a checklist. Added in version 8. */
@Entity(tableName = "checks", indices = [Index("goalId", "periodStart")])
data class CheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val stepId: Long,
    /** Epoch day the window starts on. */
    val periodStart: Long,
    val at: Long,
)

/**
 * How the user felt each day, from 1 to 5, with an optional note. Added in version 16. Version 22
 * adds how they felt once everything due that day was done, and when that was answered; version
 * 23 the note written then, and the things behind the day's mood, one to a line (see MoodFactors).
 */
@Entity(tableName = "moods")
data class MoodEntity(
    /** Epoch day. One mood a day. */
    @PrimaryKey val day: Long,
    val score: Int,
    val note: String? = null,
    val at: Long,
    val afterScore: Int? = null,
    val afterAt: Long? = null,
    val afterNote: String? = null,
    val factors: String? = null,
)

/**
 * One go at the focus timer: the minutes really focused on a goal (pauses left out), on the
 * user's day it ended. Added in version 24.
 */
@Entity(tableName = "focus_sessions", indices = [Index("day"), Index("goalId")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    /** Epoch day. */
    val day: Long,
    /** When the timer was started, in epoch milliseconds. */
    val startedAt: Long,
    val minutes: Int,
)

/** Money spent or received, in cents (MoneyEntry in the shared code). Added in version 20. */
@Entity(tableName = "money", indices = [Index("day"), Index("purchaseId")])
data class MoneyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** MoneyKind: SPENT or RECEIVED. */
    val kind: String,
    val cents: Long,
    val categoryId: Long?,
    val note: String,
    /** Epoch day. */
    val day: Long,
    val at: Long,
    /** The purchase, for a reward bought in the shop. */
    val purchaseId: Long?,
    /** The bill it was counted for, by itself. Added in version 21. */
    val billId: Long? = null,
    /** The saving goal it was put into or taken out of. Added in version 21. */
    val savingId: Long? = null,
)

/** Bills and subscriptions (Bill in the shared code). Added in version 21. */
@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val cents: Long,
    val categoryId: Long?,
    /** BillEvery: WEEKS or MONTHS. */
    val every: String,
    val count: Int,
    /** Epoch days. */
    val first: Long,
    val until: Long?,
    val archived: Boolean,
    val countedTo: Long?,
)

/** Saving goals (SavingGoal in the shared code). Added in version 21. */
@Entity(tableName = "saving_goals")
data class SavingGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val targetCents: Long,
    val position: Int,
    val archived: Boolean,
    val createdAt: Long,
)

/** The budget's categories of spending (MoneyCategory in the shared code). Added in version 20. */
@Entity(tableName = "money_categories")
data class MoneyCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val limitCents: Long?,
    val position: Int,
    val archived: Boolean,
    /** CategoryRole, for one the app files things in itself. */
    val role: String?,
)

/** Work days that weren't the usual ones (WorkDay in the shared code). Added in version 20. */
@Entity(tableName = "work_days")
data class WorkDayEntity(
    /** Epoch day. */
    @PrimaryKey val day: Long,
    /** Other hours, in minutes after midnight; null for a day off. */
    val start: Int?,
    val stop: Int?,
    val breakMinutes: Int,
    /** For a day off: whether it is paid. */
    val paid: Boolean,
)

/** Stretches when it looks like the user stopped for a while. Added in version 5. */
@Entity(tableName = "gaps")
data class GapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromDay: Long,
    val toDay: Long,
    val kind: String,
    val createdAt: Long,
    val reason: String?,
    val note: String?,
    /** The words of the user's own reason, when reason is OTHER. Added in version 9. */
    val reasonText: String? = null,
)

/** Penalty tasks owed for going over the limit. Added in version 4. */
@Entity(tableName = "penalties", indices = [Index("lapseId")])
data class PenaltyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lapseId: Long?,
    val createdAt: Long,
    val choice: String?,
    val goalId: Long?,
    val taskId: Long?,
    val label: String?,
    val doneAt: Long?,
    val cancelledAt: Long?,
    /** The gap it covers, for a gap check-in. Added in version 5. */
    val gapId: Long? = null,
)

/** The user's own penalty tasks. Added in version 4. */
@Entity(tableName = "penalty_tasks")
data class PenaltyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val archived: Boolean,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey @ColumnInfo(name = "name") val name: String,
    val value: String,
)

data class DayAmount(val day: Long, val amount: Int)

@Dao
interface ChecklistDao {
    // Goals and steps

    @Query("SELECT * FROM goals WHERE archived = 0 ORDER BY position, id")
    fun observeGoals(): Flow<List<GoalEntity>>

    /** Changes with every tick and undo, to know when to look for new badges. */
    @Query("SELECT COUNT(*) FROM ticks")
    fun observeTickCount(): Flow<Int>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun goal(id: Long): GoalEntity?

    @Insert
    suspend fun insertGoal(goal: GoalEntity): Long

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Query("SELECT * FROM goals WHERE archived = 1 ORDER BY name, id")
    fun observeRemovedGoals(): Flow<List<GoalEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM goals")
    suspend fun nextGoalPosition(): Int

    /** How much history a goal has: its ticks, routine checks, skips and misses. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM ticks WHERE goalId = :goalId) + (SELECT COUNT(*) FROM checks WHERE goalId = :goalId) + " +
            "(SELECT COUNT(*) FROM lapses WHERE goalId = :goalId)",
    )
    suspend fun goalHistoryCount(goalId: Long): Int

    /** Deletes a goal for good, for Undo after adding the examples, when it has no history. */
    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("DELETE FROM steps WHERE goalId = :goalId")
    suspend fun deleteStepsOf(goalId: Long)

    @Query("DELETE FROM rewards WHERE id = :id")
    suspend fun deleteReward(id: Long)

    @Query("SELECT * FROM steps WHERE archived = 0 ORDER BY goalId, position, id")
    fun observeSteps(): Flow<List<StepEntity>>

    @Query("SELECT * FROM steps WHERE goalId = :goalId AND archived = 0 ORDER BY position, id")
    suspend fun activeSteps(goalId: Long): List<StepEntity>

    @Query("SELECT * FROM steps WHERE id = :id")
    suspend fun step(id: Long): StepEntity?

    @Insert
    suspend fun insertStep(step: StepEntity): Long

    @Update
    suspend fun updateStep(step: StepEntity)

    // Ticks

    @Query("SELECT * FROM ticks WHERE stepId IS NULL AND periodStart >= :since")
    fun observeHabitTicksSince(since: Long): Flow<List<TickEntity>>

    @Query("SELECT * FROM ticks WHERE stepId IS NOT NULL")
    fun observeStepTicks(): Flow<List<TickEntity>>

    /** Ticks of to-dos themselves (not their steps), for the Done list. */
    @Query("SELECT ticks.* FROM ticks JOIN goals ON goals.id = ticks.goalId WHERE goals.kind = 'TODO' AND ticks.stepId IS NULL")
    fun observeTodoTicks(): Flow<List<TickEntity>>

    @Query("SELECT * FROM ticks WHERE stepId IS NULL AND periodStart >= :since")
    suspend fun habitTicksSince(since: Long): List<TickEntity>

    @Query("SELECT * FROM ticks WHERE goalId = :goalId AND stepId IS NULL AND periodStart = :periodStart ORDER BY id")
    suspend fun habitTicks(goalId: Long, periodStart: Long): List<TickEntity>

    @Query("SELECT * FROM ticks WHERE stepId = :stepId ORDER BY id LIMIT 1")
    suspend fun stepTick(stepId: Long): TickEntity?

    @Query("SELECT * FROM ticks WHERE goalId = :goalId AND stepId IS NOT NULL ORDER BY id")
    suspend fun stepTicks(goalId: Long): List<TickEntity>

    @Insert
    suspend fun insertTick(tick: TickEntity): Long

    @Query("DELETE FROM ticks WHERE id = :id")
    suspend fun deleteTick(id: Long)

    @Query("SELECT * FROM ticks WHERE id = :id")
    suspend fun tick(id: Long): TickEntity?

    @Update
    suspend fun updateTick(tick: TickEntity)

    @Update
    suspend fun updateEntry(entry: EntryEntity)

    /** Ticks that have a note, for the points history. */
    @Query("SELECT * FROM ticks WHERE note IS NOT NULL")
    fun observeTickNotes(): Flow<List<TickEntity>>

    // Ledger

    @Insert
    suspend fun insertEntry(entry: EntryEntity): Long

    @Query("SELECT * FROM entries WHERE tickId IN (:tickIds)")
    suspend fun entriesForTicks(tickIds: List<Long>): List<EntryEntity>

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM entries")
    suspend fun balance(): Int

    /** The balance at the end of a day: every entry on or before it. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM entries WHERE day <= :day")
    suspend fun balanceOn(day: Long): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM entries")
    fun observeBalance(): Flow<Int>

    @Query("SELECT day, SUM(amount) AS amount FROM entries WHERE kind IN ('TICK', 'BONUS') AND day >= :since GROUP BY day")
    fun observeEarnedByDay(since: Long): Flow<List<DayAmount>>

    @Query("SELECT MIN(day) FROM entries WHERE kind IN ('TICK', 'BONUS')")
    fun observeFirstEarningDay(): Flow<Long?>

    // Shop

    @Query("SELECT * FROM rewards WHERE archived = 0 ORDER BY position, id")
    fun observeRewards(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM rewards WHERE id = :id")
    suspend fun reward(id: Long): RewardEntity?

    @Insert
    suspend fun insertReward(reward: RewardEntity): Long

    @Update
    suspend fun updateReward(reward: RewardEntity)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM rewards")
    suspend fun nextRewardPosition(): Int

    @Query("SELECT * FROM purchases WHERE usedAt IS NULL AND returnedAt IS NULL ORDER BY at DESC, id DESC")
    fun observeUnusedPurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun purchase(id: Long): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE rewardId = :rewardId")
    suspend fun purchasesOf(rewardId: Long): List<PurchaseEntity>

    /** Purchases since a moment, for prices that rise on repeat buys this week. */
    @Query("SELECT * FROM purchases WHERE at >= :since")
    fun observePurchasesSince(since: Long): Flow<List<PurchaseEntity>>

    @Insert
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Query("UPDATE purchases SET usedAt = :at WHERE id = :id")
    suspend fun markUsed(id: Long, at: Long)

    @Query("UPDATE purchases SET returnedAt = :at WHERE id = :id")
    suspend fun markReturned(id: Long, at: Long)

    // Points history

    @Query("SELECT * FROM entries ORDER BY at DESC, id DESC")
    fun observeEntries(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM rewards WHERE archived = 0 ORDER BY position, id")
    suspend fun activeRewards(): List<RewardEntity>

    @Query("SELECT * FROM goals WHERE archived = 0 ORDER BY position, id")
    suspend fun activeGoals(): List<GoalEntity>

    // Backup: everything, including removed goals and rewards, which history still refers to.

    @Query("SELECT * FROM goals") suspend fun allGoals(): List<GoalEntity>
    @Query("SELECT * FROM steps") suspend fun allSteps(): List<StepEntity>
    @Query("SELECT * FROM ticks") suspend fun allTicks(): List<TickEntity>
    @Query("SELECT * FROM entries") suspend fun allEntries(): List<EntryEntity>
    @Query("SELECT * FROM rewards") suspend fun allRewards(): List<RewardEntity>
    @Query("SELECT * FROM purchases") suspend fun allPurchases(): List<PurchaseEntity>
    @Query("SELECT * FROM settings") suspend fun allSettings(): List<SettingEntity>

    @Query("DELETE FROM goals") suspend fun deleteGoals()
    @Query("DELETE FROM steps") suspend fun deleteSteps()
    @Query("DELETE FROM ticks") suspend fun deleteTicks()
    @Query("DELETE FROM entries") suspend fun deleteEntries()
    @Query("DELETE FROM rewards") suspend fun deleteRewards()
    @Query("DELETE FROM purchases") suspend fun deletePurchases()
    @Query("DELETE FROM settings") suspend fun deleteSettings()

    @Insert suspend fun insertGoals(rows: List<GoalEntity>)
    @Insert suspend fun insertSteps(rows: List<StepEntity>)
    @Insert suspend fun insertTicks(rows: List<TickEntity>)
    @Insert suspend fun insertEntries(rows: List<EntryEntity>)
    @Insert suspend fun insertRewards(rows: List<RewardEntity>)
    @Insert suspend fun insertPurchases(rows: List<PurchaseEntity>)
    @Insert suspend fun insertSettings(rows: List<SettingEntity>)

    // Settings

    @Query("SELECT value FROM settings WHERE name = :name")
    suspend fun setting(name: String): String?

    @Query("SELECT value FROM settings WHERE name = :name")
    fun observeSetting(name: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSetting(setting: SettingEntity)

    // Skips, misses and penalties

    @Query("SELECT * FROM lapses WHERE goalId = :goalId AND day >= :from AND day <= :to ORDER BY day, id")
    suspend fun lapses(goalId: Long, from: Long, to: Long): List<LapseEntity>

    @Query("SELECT * FROM lapses WHERE id = :id")
    suspend fun lapse(id: Long): LapseEntity?

    @Insert
    suspend fun insertLapse(lapse: LapseEntity): Long

    @Update
    suspend fun updateLapse(lapse: LapseEntity)

    /** A skip taken back: it goes, as if it was never made. */
    @Query("DELETE FROM lapses WHERE id = :id")
    suspend fun deleteLapse(id: Long)

    @Query("SELECT * FROM lapses WHERE day >= :since ORDER BY day, id")
    fun observeLapsesSince(since: Long): Flow<List<LapseEntity>>

    @Query("SELECT * FROM lapses WHERE day >= :since ORDER BY day, id")
    suspend fun lapsesSince(since: Long): List<LapseEntity>

    /** Misses still waiting for a reason. Those inside a gap get the gap's one reason instead. */
    @Query("SELECT * FROM lapses WHERE reason IS NULL AND forgotten = 0 AND kind = 'MISS' AND gapId IS NULL ORDER BY day, id")
    fun observeUnexplained(): Flow<List<LapseEntity>>

    // Gaps and the review

    @Query("SELECT * FROM lapses WHERE day >= :from AND day <= :to ORDER BY day, id")
    suspend fun allLapses(from: Long, to: Long): List<LapseEntity>

    @Query("SELECT * FROM entries WHERE day >= :from AND day <= :to ORDER BY day, id")
    suspend fun entries(from: Long, to: Long): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE day >= :from AND day <= :to ORDER BY day, id")
    fun observeEntries(from: Long, to: Long): Flow<List<EntryEntity>>

    @Query("SELECT DISTINCT day FROM ticks WHERE day >= :from AND day <= :to")
    suspend fun tickedDays(from: Long, to: Long): List<Long>

    @Query("SELECT * FROM gaps ORDER BY fromDay, id")
    suspend fun gaps(): List<GapEntity>

    @Query("SELECT * FROM gaps ORDER BY fromDay, id")
    fun observeGaps(): Flow<List<GapEntity>>

    @Query("SELECT * FROM gaps WHERE reason IS NULL ORDER BY fromDay, id")
    fun observeUnansweredGaps(): Flow<List<GapEntity>>

    @Query("SELECT * FROM gaps WHERE id = :id")
    suspend fun gap(id: Long): GapEntity?

    @Insert
    suspend fun insertGap(gap: GapEntity): Long

    @Update
    suspend fun updateGap(gap: GapEntity)

    @Query("DELETE FROM gaps WHERE id = :id")
    suspend fun deleteGap(id: Long)

    // Routines

    @Query("SELECT * FROM checks WHERE goalId = :goalId AND periodStart = :periodStart ORDER BY id")
    suspend fun checks(goalId: Long, periodStart: Long): List<CheckEntity>

    @Query("SELECT * FROM checks WHERE periodStart >= :since ORDER BY id")
    fun observeChecksSince(since: Long): Flow<List<CheckEntity>>

    @Insert suspend fun insertCheck(row: CheckEntity): Long

    @Query("DELETE FROM checks WHERE id = :id") suspend fun deleteCheck(id: Long)

    @Query("SELECT * FROM moods WHERE day = :day") fun observeMood(day: Long): Flow<MoodEntity?>
    @Query("SELECT * FROM moods WHERE day = :day") suspend fun mood(day: Long): MoodEntity?
    @Query("SELECT * FROM moods WHERE day >= :since ORDER BY day") fun observeMoodsSince(since: Long): Flow<List<MoodEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMood(mood: MoodEntity)
    @Query("SELECT * FROM moods") suspend fun allMoods(): List<MoodEntity>
    @Query("DELETE FROM moods") suspend fun deleteMoods()
    @Insert suspend fun insertMoods(rows: List<MoodEntity>)

    // Focus time.
    @Insert suspend fun insertFocusSession(row: FocusSessionEntity): Long
    @Query("SELECT * FROM focus_sessions WHERE day BETWEEN :from AND :to ORDER BY startedAt, id")
    fun observeFocusSessions(from: Long, to: Long): Flow<List<FocusSessionEntity>>
    @Query("SELECT COALESCE(SUM(minutes), 0) FROM focus_sessions WHERE goalId = :goalId AND day BETWEEN :from AND :to")
    suspend fun focusMinutes(goalId: Long, from: Long, to: Long): Int
    @Query("SELECT COALESCE(SUM(minutes), 0) FROM focus_sessions WHERE goalId = :goalId AND day BETWEEN :from AND :to")
    fun observeFocusMinutes(goalId: Long, from: Long, to: Long): Flow<Int>
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt, id") suspend fun allFocusSessions(): List<FocusSessionEntity>
    @Query("DELETE FROM focus_sessions") suspend fun deleteFocusSessions()
    @Insert suspend fun insertFocusSessions(rows: List<FocusSessionEntity>)

    // The budget.
    @Query("SELECT * FROM money WHERE day BETWEEN :from AND :to ORDER BY day DESC, at DESC")
    fun observeMoney(from: Long, to: Long): Flow<List<MoneyEntity>>
    @Query("SELECT * FROM money ORDER BY day, at") suspend fun allMoney(): List<MoneyEntity>
    @Query("SELECT * FROM money WHERE id = :id") suspend fun money(id: Long): MoneyEntity?
    @Insert suspend fun insertMoney(row: MoneyEntity): Long
    @Update suspend fun updateMoney(row: MoneyEntity)
    @Query("DELETE FROM money WHERE id = :id") suspend fun deleteMoney(id: Long)
    @Query("DELETE FROM money WHERE purchaseId = :purchaseId") suspend fun deleteMoneyForPurchase(purchaseId: Long)
    @Query("DELETE FROM money") suspend fun deleteAllMoney()
    @Insert suspend fun insertMoneyRows(rows: List<MoneyEntity>)
    @Query("SELECT * FROM money_categories ORDER BY position") fun observeMoneyCategories(): Flow<List<MoneyCategoryEntity>>
    @Query("SELECT * FROM money_categories ORDER BY position") suspend fun moneyCategories(): List<MoneyCategoryEntity>
    @Insert suspend fun insertMoneyCategory(row: MoneyCategoryEntity): Long
    @Update suspend fun updateMoneyCategory(row: MoneyCategoryEntity)
    @Query("DELETE FROM money_categories") suspend fun deleteMoneyCategories()
    @Insert suspend fun insertMoneyCategories(rows: List<MoneyCategoryEntity>)
    @Query("SELECT * FROM work_days WHERE day BETWEEN :from AND :to") fun observeWorkDays(from: Long, to: Long): Flow<List<WorkDayEntity>>
    @Query("SELECT * FROM work_days ORDER BY day") suspend fun allWorkDays(): List<WorkDayEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putWorkDay(row: WorkDayEntity)
    @Query("DELETE FROM work_days WHERE day = :day") suspend fun deleteWorkDay(day: Long)
    @Query("DELETE FROM work_days") suspend fun deleteWorkDays()
    @Insert suspend fun insertWorkDays(rows: List<WorkDayEntity>)
    @Query("SELECT * FROM bills ORDER BY name") fun observeBills(): Flow<List<BillEntity>>
    @Query("SELECT * FROM bills ORDER BY id") suspend fun allBills(): List<BillEntity>
    @Query("SELECT * FROM bills WHERE id = :id") suspend fun bill(id: Long): BillEntity?
    @Insert suspend fun insertBill(row: BillEntity): Long
    @Update suspend fun updateBill(row: BillEntity)
    @Query("DELETE FROM bills") suspend fun deleteBills()
    @Insert suspend fun insertBills(rows: List<BillEntity>)
    @Query("SELECT * FROM saving_goals ORDER BY position") fun observeSavingGoals(): Flow<List<SavingGoalEntity>>
    @Query("SELECT * FROM saving_goals ORDER BY position") suspend fun allSavingGoals(): List<SavingGoalEntity>
    @Insert suspend fun insertSavingGoal(row: SavingGoalEntity): Long
    @Update suspend fun updateSavingGoal(row: SavingGoalEntity)
    @Query("DELETE FROM saving_goals") suspend fun deleteSavingGoals()
    @Insert suspend fun insertSavingGoals(rows: List<SavingGoalEntity>)
    @Query("SELECT * FROM money WHERE savingId IS NOT NULL ORDER BY day, at") fun observeSavingMoney(): Flow<List<MoneyEntity>>

    @Query("SELECT * FROM checks") suspend fun allChecks(): List<CheckEntity>
    @Query("DELETE FROM checks") suspend fun deleteChecks()
    @Insert suspend fun insertChecks(rows: List<CheckEntity>)

    @Query("SELECT * FROM gaps") suspend fun allGaps(): List<GapEntity>
    @Query("DELETE FROM gaps") suspend fun deleteGaps()
    @Insert suspend fun insertGaps(rows: List<GapEntity>)

    @Query("SELECT * FROM entries WHERE lapseId = :lapseId")
    suspend fun entriesForLapse(lapseId: Long): List<EntryEntity>

    @Insert
    suspend fun insertPenalty(penalty: PenaltyEntity): Long

    @Update
    suspend fun updatePenalty(penalty: PenaltyEntity)

    @Query("SELECT * FROM penalties WHERE id = :id")
    suspend fun penalty(id: Long): PenaltyEntity?

    @Query("SELECT * FROM penalties WHERE lapseId = :lapseId ORDER BY id LIMIT 1")
    suspend fun penaltyForLapse(lapseId: Long): PenaltyEntity?

    @Query("SELECT * FROM penalties WHERE doneAt IS NULL AND cancelledAt IS NULL ORDER BY createdAt, id")
    suspend fun openPenalties(): List<PenaltyEntity>

    @Query("SELECT * FROM penalties WHERE doneAt IS NULL AND cancelledAt IS NULL ORDER BY createdAt, id")
    fun observeOpenPenalties(): Flow<List<PenaltyEntity>>

    @Query("SELECT * FROM penalty_tasks WHERE archived = 0 ORDER BY id")
    suspend fun penaltyTasks(): List<PenaltyTaskEntity>

    @Query("SELECT * FROM penalty_tasks WHERE archived = 0 ORDER BY id")
    fun observePenaltyTasks(): Flow<List<PenaltyTaskEntity>>

    @Insert
    suspend fun insertPenaltyTask(task: PenaltyTaskEntity): Long

    @Query("UPDATE penalty_tasks SET archived = 1 WHERE id = :id")
    suspend fun removePenaltyTask(id: Long)

    @Query("SELECT * FROM lapses") suspend fun allLapses(): List<LapseEntity>
    @Query("SELECT * FROM penalties") suspend fun allPenalties(): List<PenaltyEntity>
    @Query("SELECT * FROM penalty_tasks") suspend fun allPenaltyTasks(): List<PenaltyTaskEntity>
    @Query("DELETE FROM lapses") suspend fun deleteLapses()
    @Query("DELETE FROM penalties") suspend fun deletePenalties()
    @Query("DELETE FROM penalty_tasks") suspend fun deletePenaltyTasks()
    @Insert suspend fun insertLapses(rows: List<LapseEntity>)
    @Insert suspend fun insertPenalties(rows: List<PenaltyEntity>)
    @Insert suspend fun insertPenaltyTasks(rows: List<PenaltyTaskEntity>)
}

@Database(
    entities = [
        GoalEntity::class,
        StepEntity::class,
        TickEntity::class,
        EntryEntity::class,
        RewardEntity::class,
        PurchaseEntity::class,
        SettingEntity::class,
        LapseEntity::class,
        PenaltyEntity::class,
        PenaltyTaskEntity::class,
        GapEntity::class,
        CheckEntity::class,
        MoodEntity::class,
        MoneyEntity::class,
        MoneyCategoryEntity::class,
        WorkDayEntity::class,
        BillEntity::class,
        SavingGoalEntity::class,
        FocusSessionEntity::class,
    ],
    version = 24,
    exportSchema = true,
    // Versions 4 to 8 only add tables and columns, so Room works the migrations out from the layouts.
    autoMigrations = [AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9), AutoMigration(from = 9, to = 10), AutoMigration(from = 10, to = 11), AutoMigration(from = 11, to = 12), AutoMigration(from = 12, to = 13), AutoMigration(from = 13, to = 14), AutoMigration(from = 14, to = 15), AutoMigration(from = 15, to = 16), AutoMigration(from = 16, to = 17), AutoMigration(from = 17, to = 18), AutoMigration(from = 18, to = 19), AutoMigration(from = 19, to = 20), AutoMigration(from = 20, to = 21), AutoMigration(from = 21, to = 22), AutoMigration(from = 22, to = 23), AutoMigration(from = 23, to = 24)],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ChecklistDao

    companion object {
        const val FILE_NAME = "checklist.db"
    }
}

/** Made by Room for each phone; how the database is opened is up to each (see openDatabase). */
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Runs [block] as one transaction: all of it is saved, or none of it. */
expect suspend fun <R> AppDatabase.withTransaction(block: suspend () -> R): R
