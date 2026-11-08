package io.github.randomtools3377.checklist.core

import java.time.LocalDate

/** Habits repeat. Projects are made of steps and are done once. */
enum class GoalKind { HABIT, PROJECT }

/** How often a habit repeats. */
enum class Period { DAILY, WEEKLY, MONTHLY }

/**
 * A goal as it is now. Editing a goal only changes what happens from then on: every tick keeps
 * the points it paid at the time.
 */
data class Goal(
    val id: Long = 0,
    val kind: GoalKind,
    val name: String,
    /** An emoji. */
    val icon: String,
    /** Points paid for each tick of a habit. Projects pay per step instead. */
    val points: Int,
    /** How often a habit repeats. Null for a project. */
    val period: Period?,
    /** Ticks that finish a habit in one period: 1 for a plain habit, more for a counted one. */
    val target: Int = 1,
    /** Paid when a counted habit reaches its target, or when a project's last step is done. */
    val bonus: Int = 0,
    /** Projects only, optional. */
    val deadline: LocalDate? = null,
    val archived: Boolean = false,
)

/** One step of a project. */
data class Step(
    val id: Long = 0,
    val goalId: Long,
    val name: String,
    val points: Int,
    val position: Int,
    val archived: Boolean = false,
)

/**
 * One tick. For a habit it counts toward the period that starts on [periodStart]. For a project
 * it marks the step [stepId] done.
 */
data class Tick(
    val id: Long = 0,
    val goalId: Long,
    val stepId: Long?,
    /** When it was ticked, in epoch milliseconds. */
    val at: Long,
    /** The user's day the tick counts for, after the day-end setting is applied. */
    val day: LocalDate,
    /** The habit's period when it was ticked. Null for a project step. */
    val period: Period?,
    /** First day of that period. The same as [day] for a project step. */
    val periodStart: LocalDate,
)

/**
 * What a ledger entry is for. The balance is the sum of all entries.
 *
 * Later stages add their own kinds here (a penalty for going over the limit, a refund when a
 * miss turns out to be a forgotten tick) without changing how the balance works.
 */
enum class EntryKind { TICK, BONUS, PURCHASE }

/** One line in the points ledger. Positive amounts are earned, negative ones are spent. */
data class Entry(
    val id: Long = 0,
    val at: Long,
    val day: LocalDate,
    val kind: EntryKind,
    val amount: Int,
    val goalId: Long? = null,
    val tickId: Long? = null,
    val rewardId: Long? = null,
    val purchaseId: Long? = null,
    /** What it was for, as it was named at the time. */
    val label: String,
)

/** A real-life reward in the shop, at the price the user set. */
data class Reward(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val price: Int,
    val archived: Boolean = false,
)

/** A reward that was bought. It waits in the inventory until it is used. */
data class Purchase(
    val id: Long = 0,
    val rewardId: Long,
    val name: String,
    val icon: String,
    val price: Int,
    val at: Long,
    val usedAt: Long? = null,
)
